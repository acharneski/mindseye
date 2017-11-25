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

package com.simiacryptus.mindseye.opt.orient;

import com.simiacryptus.mindseye.eval.SampledArrayTrainable;
import com.simiacryptus.mindseye.eval.Trainable;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.layers.java.EntropyLossLayer;
import com.simiacryptus.mindseye.mnist.MnistTestBase;
import com.simiacryptus.mindseye.network.SimpleLossNetwork;
import com.simiacryptus.mindseye.opt.IterativeTrainer;
import com.simiacryptus.mindseye.opt.TrainingMonitor;
import com.simiacryptus.util.io.NotebookOutput;

import java.util.concurrent.TimeUnit;

/**
 * The type Gd run.
 */
public class GDTest extends MnistTestBase {
  
  @Override
  public void train(NotebookOutput log, NNLayer network, Tensor[][] trainingData, TrainingMonitor monitor) {
    log.code(() -> {
      SimpleLossNetwork supervisedNetwork = new SimpleLossNetwork(network, new EntropyLossLayer());
//      new DerivativeTester(1e-2, 1e-6) {
//        @Override
//        protected void testFeedbackPerformance(NNLayer component, int i, Tensor outputPrototype, Tensor... inputPrototype) {
//          if (i == 0) super.testFeedbackPerformance(component, i, outputPrototype, inputPrototype);
//        }
//      }.run(supervisedNetwork, new Tensor(1), trainingData[0]);
      Trainable trainable = new SampledArrayTrainable(trainingData, supervisedNetwork, 1000);
      return new IterativeTrainer(trainable)
        .setMonitor(monitor)
        .setOrientation(new ValidatingOrientationWrapper(new GradientDescent()))
        .setTimeout(3, TimeUnit.MINUTES)
        .setMaxIterations(500)
        .run();
    });
  }
  
}
