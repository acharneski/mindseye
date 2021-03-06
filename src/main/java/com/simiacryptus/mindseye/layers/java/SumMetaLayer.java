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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * The type Sum meta key.
 */
@SuppressWarnings("serial")
public class SumMetaLayer extends LayerBase {


  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(SumMetaLayer.class);
  @Nullable
  private Tensor lastResult;
  private int minBatches = 1;

  /**
   * Instantiates a new Sum meta key.
   */
  public SumMetaLayer() {
  }

  /**
   * Instantiates a new Sum meta key.
   *
   * @param json      the id
   * @param resources the resources
   */
  protected SumMetaLayer(@Nonnull final JsonObject json, Map<CharSequence, byte[]> resources) {
    super(json);
    lastResult = Tensor.fromJson(json.get("lastResult"), resources);
    minBatches = json.get("minBatches").getAsInt();
  }

  /**
   * From json sum meta key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the sum meta key
   */
  public static SumMetaLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new SumMetaLayer(json, rs);
  }

  @Nullable
  @Override
  public Result evalAndFree(@Nonnull final Result... inObj) {
    if (1 != inObj.length) throw new IllegalArgumentException();
    final Result input = inObj[0];
    TensorList inputData = input.getData();
    final int itemCnt = inputData.length();
    if (null == lastResult || minBatches < itemCnt) {
      if(null != lastResult) lastResult.freeRef();
      @Nonnull final ToDoubleFunction<Coordinate> f = (c) ->
          IntStream.range(0, itemCnt)
              .mapToDouble(dataIndex -> {
                Tensor tensor = inputData.get(dataIndex);
                double v = tensor.get(c);
                tensor.freeRef();
                return v;
              })
              .sum();
      lastResult = inputData.get(0).mapCoordsAndFree(f);
    }
    return new Result(TensorArray.create(lastResult), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList data) -> {
      if (input.isAlive()) {
        @Nullable final Tensor delta = data.get(0);
        @Nonnull final Tensor feedback[] = new Tensor[itemCnt];
        Arrays.parallelSetAll(feedback, i -> new Tensor(delta.getDimensions()));
        delta.coordStream(false).forEach((inputCoord) -> {
          for (int inputItem = 0; inputItem < itemCnt; inputItem++) {
            feedback[inputItem].add(inputCoord, delta.get(inputCoord));
          }
        });
        @Nonnull TensorArray tensorArray = TensorArray.wrap(feedback);
        input.accumulate(buffer, tensorArray);
      }
    }) {

      @Override
      protected void _free() {
        inputData.freeRef();
        input.freeRef();
      }

      @Override
      public boolean isAlive() {
        return input.isAlive();
      }

    };
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, @Nonnull DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    if (null != lastResult) {
      json.add("lastResult", lastResult.toJson(resources, dataSerializer));
    }
    json.addProperty("minBatches", minBatches);
    return json;
  }

  /**
   * Gets min batches.
   *
   * @return the min batches
   */
  public int getMinBatches() {
    return minBatches;
  }

  /**
   * Sets min batches.
   *
   * @param minBatches the min batches
   * @return the min batches
   */
  @Nonnull
  public SumMetaLayer setMinBatches(final int minBatches) {
    this.minBatches = minBatches;
    return this;
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
}
