/*
 * Copyright (c) 2017 by Andrew Charneski.
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The type Product inputs layer.
 */
public class ProductInputsLayer extends NNLayer {
  
  /**
   * Instantiates a new Product inputs layer.
   *
   * @param id the id
   */
  protected ProductInputsLayer(JsonObject id) {
    super(id);
  }
  
  /**
   * Instantiates a new Product inputs layer.
   */
  public ProductInputsLayer() {
  }
  
  /**
   * From json product inputs layer.
   *
   * @param json the json
   * @return the product inputs layer
   */
  public static ProductInputsLayer fromJson(JsonObject json) {
    return new ProductInputsLayer(json);
  }
  
  public JsonObject getJson() {
    return super.getJsonStub();
  }
  
  @Override
  public NNResult eval(NNExecutionContext nncontext, final NNResult... inObj) {
    assert inObj.length > 1;
    for (int i = 1; i < inObj.length; i++) {
      int dim0 = Tensor.dim(inObj[0].getData().get(0).getDimensions());
      int dimI = Tensor.dim(inObj[i].getData().get(0).getDimensions());
      if (dim0 != 1 && dimI != 1 && dim0 != dimI) {
        throw new IllegalArgumentException(Arrays.toString(inObj[0].getData().get(0).getDimensions()) + " != " + Arrays.toString(inObj[i].getData().get(0).getDimensions()));
      }
    }
    TensorList result = Arrays.stream(inObj).parallel().map(x -> x.getData()).reduce((l, r) -> {
      Stream<Tensor> tensorStream = IntStream.range(0, Math.max(l.length(), r.length())).parallel()
        .mapToObj(i -> {
          Tensor left = l.get(Math.min(i, l.length() - 1));
          Tensor right = r.get(Math.min(i, r.length() - 1));
          return Tensor.product(left, right);
        });
      return new TensorArray(tensorStream.toArray(i -> new Tensor[i]));
    }).get();
    return new NNResult(result) {
      @Override
      public void accumulate(final DeltaSet buffer, final TensorList delta) {
        assert delta.stream().flatMapToDouble(x -> Arrays.stream(x.getData())).allMatch(v -> Double.isFinite(v));
        for (final NNResult input : inObj) {
          if (input.isAlive()) {
            int inputBatches = input.getData().length();
            int deltaBatches = delta.length();
            Tensor[] data = IntStream.range(0, deltaBatches).parallel().mapToObj(i -> {
              Tensor deltaTensor = delta.get(i);
              Tensor inputTensor = input.getData().get(Math.min(i, inputBatches - 1));
              Tensor passbackTensor = inputTensor.mapIndex((v, c) -> {
                Tensor resultTensor = result.get(i);
                if (1 == inputTensor.dim() && 1 < deltaTensor.dim()) {
                  double sum = 0;
                  for (int j = 0; j < deltaTensor.dim(); j++) {
                    double r = deltaTensor.get(j) * resultTensor.get(j) / v;
                    sum += Double.isFinite(r) ? r : 0.0;
                  }
                  return sum;
                }
                else {
                  double r = deltaTensor.get(c) * resultTensor.get(c) / v;
                  return Double.isFinite(r) ? r : 0.0;
                }
              });
              return passbackTensor;
            }).toArray(i -> new Tensor[i]);
            if (1 == inputBatches && 1 < deltaBatches) {
              data = new Tensor[]{Arrays.stream(data).reduce((a, b) -> a.add(b)).get()};
            }
            input.accumulate(buffer, new TensorArray(data));
          }
        }
      }
      
      @Override
      public boolean isAlive() {
        for (final NNResult element : inObj)
          if (element.isAlive()) {
            return true;
          }
        return false;
      }
      
    };
  }
  
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
}