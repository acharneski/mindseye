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

/**
 * The type Cross dot meta key.
 */
@SuppressWarnings("serial")
public class CrossDotMetaLayer extends LayerBase {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(CrossDotMetaLayer.class);

  /**
   * Instantiates a new Cross dot meta key.
   */
  public CrossDotMetaLayer() {
  }

  /**
   * Instantiates a new Cross dot meta key.
   *
   * @param id the id
   */
  protected CrossDotMetaLayer(@Nonnull final JsonObject id) {
    super(id);
  }

  /**
   * From json cross dot meta key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the cross dot meta key
   */
  public static CrossDotMetaLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new CrossDotMetaLayer(json);
  }

  @Nullable
  @Override
  public Result eval(@Nonnull final Result... inObj) {
    final Result input = inObj[0];
    final TensorList indata = input.getData();
    Arrays.stream(inObj).forEach(nnResult -> nnResult.addRef());
    indata.addRef();
    final int itemCnt = indata.length();
    final int dim = Tensor.length(indata.getDimensions());
    @Nonnull final Tensor results = new Tensor(dim, dim);
    for (int i = 0; i < dim; i++) {
      for (int j = 0; j < dim; j++) {
        if (i == j) {
          continue;
        }
        double v = 0;
        for (int k = 0; k < itemCnt; k++) {
          Tensor tensor = indata.get(k);
          @Nullable final double[] kk = tensor.getData();
          v += kk[i] * kk[j];
          tensor.freeRef();
        }
        results.set(new int[]{i, j}, v);
      }
    }
    return new Result(TensorArray.wrap(results), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList delta) -> {
      if (input.isAlive()) {
        @Nullable final Tensor deltaTensor = delta.get(0);
        @Nonnull final Tensor feedback[] = new Tensor[itemCnt];
        Arrays.parallelSetAll(feedback, i -> new Tensor(dim));

        for (int i = 0; i < dim; i++) {
          for (int j = 0; j < dim; j++) {
            if (i == j) {
              continue;
            }
            final double v = deltaTensor.get(i, j);
            for (int k = 0; k < itemCnt; k++) {
              Tensor tensor = indata.get(k);
              @Nullable final double[] kk = tensor.getData();
              feedback[k].add(i, v * kk[j]);
              feedback[k].add(j, v * kk[i]);
              tensor.freeRef();
            }
          }
        }
        deltaTensor.freeRef();

        @Nonnull TensorArray tensorArray = TensorArray.wrap(feedback);
        input.accumulate(buffer, tensorArray);
      }
    }) {

      @Override
      protected void _free() {
        indata.freeRef();
        Arrays.stream(inObj).forEach(nnResult -> nnResult.freeRef());
      }

      @Override
      public boolean isAlive() {
        return input.isAlive();
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
