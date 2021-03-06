/*
 * Copyright (c) 2018 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.mindseye.layers.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Concatenates two or more images apply the same resolution so the output contains all input color bands.
 */
@SuppressWarnings("serial")
public class TensorConcatLayer extends LayerBase {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(TensorConcatLayer.class);
  private int maxBands;

  /**
   * Instantiates a new Img eval key.
   */
  public TensorConcatLayer() {
    setMaxBands(0);
  }

  /**
   * Instantiates a new Img eval key.
   *
   * @param json the json
   */
  protected TensorConcatLayer(@Nonnull final JsonObject json) {
    super(json);
    JsonElement maxBands = json.get("maxBands");
    if (null != maxBands) setMaxBands(maxBands.getAsInt());
  }

  /**
   * From json img eval key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the img eval key
   */
  public static TensorConcatLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new TensorConcatLayer(json);
  }

  @Nullable
  @Override
  public Result eval(@Nonnull final Result... inObj) {
    Arrays.stream(inObj).forEach(nnResult -> nnResult.addRef());
    final int numBatches = inObj[0].getData().length();
    assert Arrays.stream(inObj).allMatch(x -> x.getData().length() == numBatches) : "All inputs must use same batch size";
    int[] outputDims = new int[]{
        Arrays.stream(inObj).mapToInt(x->Tensor.length(x.getData().getDimensions())).sum()
    };

    @Nonnull final List<Tensor> outputTensors = new ArrayList<>();
    for (int b = 0; b < numBatches; b++) {
      @Nonnull final Tensor outputTensor = new Tensor(outputDims);
      int pos = 0;
      @Nullable final double[] outputTensorData = outputTensor.getData();
      for (int i = 0; i < inObj.length; i++) {
        @Nullable Tensor tensor = inObj[i].getData().get(b);
        @Nullable final double[] data = tensor.getData();
        System.arraycopy(data, 0, outputTensorData, pos, Math.min(data.length, outputTensorData.length - pos));
        pos += data.length;
        tensor.freeRef();
      }
      outputTensors.add(outputTensor);
    }
    return new Result(TensorArray.wrap(outputTensors.toArray(new Tensor[]{})), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList data) -> {
      assert numBatches == data.length();

      @Nonnull final List<Tensor[]> splitBatches = new ArrayList<>();
      for (int b = 0; b < numBatches; b++) {
        @Nullable final Tensor tensor = data.get(b);
        @Nonnull final Tensor[] outputTensors2 = new Tensor[inObj.length];
        int pos = 0;
        for (int i = 0; i < inObj.length; i++) {
          @Nonnull final Tensor dest = new Tensor(inObj[i].getData().getDimensions());
          @Nullable double[] tensorData = tensor.getData();
          System.arraycopy(tensorData, pos, dest.getData(), 0, Math.min(dest.length(), tensorData.length - pos));
          pos += dest.length();
          outputTensors2[i] = dest;
        }
        tensor.freeRef();
        splitBatches.add(outputTensors2);
      }

      @Nonnull final Tensor[][] splitData = new Tensor[inObj.length][];
      for (int i = 0; i < splitData.length; i++) {
        splitData[i] = new Tensor[numBatches];
      }
      for (int i = 0; i < inObj.length; i++) {
        for (int b = 0; b < numBatches; b++) {
          splitData[i][b] = splitBatches.get(b)[i];
        }
      }

      for (int i = 0; i < inObj.length; i++) {
        @Nonnull TensorArray tensorArray = TensorArray.wrap(splitData[i]);
        inObj[i].accumulate(buffer, tensorArray);
      }
    }) {

      @Override
      protected void _free() {
        Arrays.stream(inObj).forEach(nnResult -> nnResult.freeRef());
      }

      @Override
      public boolean isAlive() {
        for (@Nonnull final Result element : inObj)
          if (element.isAlive()) {
            return true;
          }
        return false;
      }

    };
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
    @Nonnull JsonObject json = super.getJsonStub();
    json.addProperty("maxBands", maxBands);
    return json;
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }

  /**
   * Gets max bands.
   *
   * @return the max bands
   */
  public int getMaxBands() {
    return maxBands;
  }

  /**
   * Sets max bands.
   *
   * @param maxBands the max bands
   * @return the max bands
   */
  @Nonnull
  public TensorConcatLayer setMaxBands(int maxBands) {
    this.maxBands = maxBands;
    return this;
  }
}
