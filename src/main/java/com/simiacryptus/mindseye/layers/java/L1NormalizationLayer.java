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

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.util.ArrayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Normalizes the input so that the L1 magnitude (ie sum of abs) is 1.
 */
@SuppressWarnings("serial")
public class L1NormalizationLayer extends LayerBase {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(L1NormalizationLayer.class);
  /**
   * The Max input.
   */
  double maxInput = 50;

  /**
   * Instantiates a new L 1 normalization key.
   */
  public L1NormalizationLayer() {
  }

  /**
   * Instantiates a new L 1 normalization key.
   *
   * @param id the id
   */
  protected L1NormalizationLayer(@Nonnull final JsonObject id) {
    super(id);
  }

  /**
   * From json l 1 normalization key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the l 1 normalization key
   */
  public static L1NormalizationLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new L1NormalizationLayer(json);
  }

  @Nonnull
  @Override
  public Result eval(@Nonnull final Result... input) {
    Arrays.stream(input).forEach(nnResult -> nnResult.addRef());
    final Result in = input[0];
    final TensorList inData = in.getData();
    inData.addRef();
    return new Result(TensorArray.wrap(IntStream.range(0, inData.length()).mapToObj(dataIndex -> {
      @Nullable final Tensor value = inData.get(dataIndex);
      try {
        final double sum = value.sum();
        if (!Double.isFinite(sum) || 0 == sum) {
          value.addRef();
          return value;
        } else {
          return value.scale(1.0 / sum);
        }
      } finally {
        value.freeRef();
      }
    }).toArray(i -> new Tensor[i])), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList outDelta) -> {
      if (in.isAlive()) {
        final Tensor[] passbackArray = IntStream.range(0, outDelta.length()).mapToObj(dataIndex -> {
          Tensor inputTensor = inData.get(dataIndex);
          @Nullable final double[] value = inputTensor.getData();
          Tensor outputTensor = outDelta.get(dataIndex);
          @Nullable final double[] delta = outputTensor.getData();
          final double dot = ArrayUtil.dot(value, delta);
          final double sum = Arrays.stream(value).sum();
          @Nonnull final Tensor passback = new Tensor(outputTensor.getDimensions());
          @Nullable final double[] passbackData = passback.getData();
          if (0 != sum || Double.isFinite(sum)) {
            for (int i = 0; i < value.length; i++) {
              passbackData[i] = (delta[i] - dot / sum) / sum;
            }
          }
          outputTensor.freeRef();
          inputTensor.freeRef();
          return passback;
        }).toArray(i -> new Tensor[i]);
        assert Arrays.stream(passbackArray).flatMapToDouble(x -> Arrays.stream(x.getData())).allMatch(v -> Double.isFinite(v));
        @Nonnull TensorArray tensorArray = TensorArray.wrap(passbackArray);
        in.accumulate(buffer, tensorArray);
      }
    }) {

      @Override
      protected void _free() {
        inData.freeRef();
        Arrays.stream(input).forEach(nnResult -> nnResult.freeRef());
      }


      @Override
      public boolean isAlive() {
        return in.isAlive();
      }

    };
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
    return super.getJsonStub();
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
}
