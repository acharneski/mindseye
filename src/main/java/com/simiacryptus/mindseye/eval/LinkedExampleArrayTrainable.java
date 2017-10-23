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

package com.simiacryptus.mindseye.eval;

import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.lang.DeltaSet;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.NNResult;
import com.simiacryptus.util.Util;

import java.util.Arrays;
import java.util.Comparator;

/**
 * The type Linked example array trainable.
 */
public class LinkedExampleArrayTrainable implements Trainable {
  
  private final Tensor[][][] trainingData;
  private final NNLayer network;
  private long hash = Util.R.get().nextLong();
  private int trainingSize = Integer.MAX_VALUE;
  private Tensor[][] sampledData;
  
  /**
   * Instantiates a new Linked example array trainable.
   *
   * @param trainingData the training data
   * @param network      the network
   * @param trainingSize the training size
   */
  public LinkedExampleArrayTrainable(Tensor[][][] trainingData, NNLayer network, int trainingSize) {
    this.trainingData = trainingData;
    this.network = network;
    this.trainingSize = trainingSize;
    resetSampling();
  }
  
  @Override
  public PointSample measure() {
    NNResult[] input = NNResult.batchResultArray(sampledData);
    NNResult result = network.eval(new NNLayer.NNExecutionContext() {
    }, input);
    DeltaSet deltaSet = new DeltaSet();
    result.accumulate(deltaSet);
    DeltaSet stateSet = new DeltaSet();
    deltaSet.map.forEach((layer, layerDelta) -> {
      stateSet.get(layer, layerDelta.target).accumulate(layerDelta.target);
    });
    assert (result.getData().stream().allMatch(x -> x.dim() == 1));
    double meanValue = result.getData().stream().mapToDouble(x -> x.getData()[0]).average().getAsDouble();
    return new PointSample(deltaSet, stateSet, meanValue);
  }
  
  @Override
  public void resetToFull() {
    sampledData = Arrays.stream(trainingData).parallel().flatMap(x -> Arrays.stream(x)).toArray(i -> new Tensor[i][]);
  }
  
  @Override
  public boolean resetSampling() {
    setHash(Util.R.get().nextLong());
    return true;
  }
  
  /**
   * Gets training size.
   *
   * @return the training size
   */
  public int getTrainingSize() {
    return this.trainingSize;
  }
  
  /**
   * Sets training size.
   *
   * @param trainingSize the training size
   * @return the training size
   */
  public LinkedExampleArrayTrainable setTrainingSize(final int trainingSize) {
    this.trainingSize = trainingSize;
    refreshSampledData();
    return this;
  }
  
  private void setHash(long newValue) {
    if (this.hash == newValue) return;
    this.hash = newValue;
    refreshSampledData();
  }
  
  private void refreshSampledData() {
    assert 0 < getTrainingSize();
    this.sampledData = Arrays.stream(trainingData).parallel() //
                         .sorted(Comparator.comparingLong(y -> System.identityHashCode(y) ^ this.hash)) //
                         .flatMap(x -> Arrays.stream(x)) //
                         .limit(getTrainingSize()) //
                         .toArray(i -> new Tensor[i][]);
  }
}