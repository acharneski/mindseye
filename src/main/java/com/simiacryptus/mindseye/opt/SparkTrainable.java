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

package com.simiacryptus.mindseye.opt;

import com.simiacryptus.mindseye.graph.dag.DAGNetwork;
import com.simiacryptus.mindseye.net.DeltaSet;
import com.simiacryptus.mindseye.net.NNResult;
import com.simiacryptus.util.ml.Tensor;

import java.util.Arrays;

public class SparkTrainable implements Trainable {
  
  private final Tensor[][] trainingData;
  private final DAGNetwork network;
  
  public SparkTrainable(Tensor[][] trainingData, DAGNetwork network) {
    this.trainingData = trainingData;
    this.network = network;
    resetSampling();
  }
  
  @Override
  public PointSample measure() {
    NNResult[] input = NNResult.batchResultArray(trainingData);
    NNResult result = network.eval(input);
    DeltaSet deltaSet = new DeltaSet();
    result.accumulate(deltaSet);
    DeltaSet stateSet = new DeltaSet();
    deltaSet.map.forEach((layer, layerDelta) -> {
      stateSet.get(layer, layerDelta.target).accumulate(layerDelta.target);
    });
    assert (Arrays.stream(result.data).allMatch(x -> x.dim() == 1));
    double meanValue = Arrays.stream(result.data).mapToDouble(x -> x.getData()[0]).average().getAsDouble();
    return new PointSample(deltaSet, stateSet, meanValue);
  }
  
  @Override
  public void resetToFull() {
  }
  
  @Override
  public void resetSampling() {
  }
  
}