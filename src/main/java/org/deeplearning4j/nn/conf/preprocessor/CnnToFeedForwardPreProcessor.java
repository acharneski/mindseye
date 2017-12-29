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

package org.deeplearning4j.nn.conf.preprocessor;

import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.nd4j.linalg.api.ndarray.INDArray;

public class CnnToFeedForwardPreProcessor extends InputPreProcessor {
  public CnnToFeedForwardPreProcessor(int height, int width, int depth) {
  
  }
  
  public INDArray preProcess(INDArray input, int miniBatchSize) {
    throw new RuntimeException("NI");
  }
  
  public INDArray backprop(INDArray epsilons, int miniBatchSize) {
    throw new RuntimeException("NI");
  }
}
