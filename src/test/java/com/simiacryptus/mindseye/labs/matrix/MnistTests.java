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

package com.simiacryptus.mindseye.labs.matrix;

import com.simiacryptus.mindseye.layers.cudnn.ConvolutionLayer;
import com.simiacryptus.mindseye.layers.cudnn.PoolingLayer;
import com.simiacryptus.mindseye.layers.java.BiasLayer;
import com.simiacryptus.mindseye.layers.java.FullyConnectedLayer;
import com.simiacryptus.mindseye.layers.java.ReLuActivationLayer;
import com.simiacryptus.mindseye.layers.java.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import com.simiacryptus.mindseye.test.*;
import com.simiacryptus.util.io.MarkdownNotebookOutput;
import com.simiacryptus.util.io.NotebookOutput;
import com.simiacryptus.util.test.TestCategories;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;

/**
 * The type Mnist run base.
 */
public class MnistTests {
  private static final int timeoutMinutes = 15;
  /**
   * The constant fwd_linear_1.
   */
  public static FwdNetworkFactory fwd_linear_1 = (log, features) -> {
    log.p("The image-to-vector network is a single layer, fully connected:");
    return log.code(() -> {
      PipelineNetwork network = new PipelineNetwork();
      network.add(new BiasLayer(28, 28, 1));
      network.add(new FullyConnectedLayer(new int[]{28, 28, 1}, new int[]{features})
        .setWeights(() -> 0.001 * (Math.random() - 0.45)));
      network.add(new SoftmaxActivationLayer());
      return network;
    });
  };
  /**
   * The constant fwd_conv_1.
   */
  public static FwdNetworkFactory fwd_conv_1 = (log, features) -> {
    log.p("The image-to-vector network is a single layer convolutional:");
    return log.code(() -> {
      PipelineNetwork network = new PipelineNetwork();
      network.add(new ConvolutionLayer(3, 3, 1, 5).setWeights(i -> 1e-8 * (Math.random() - 0.5)));
      network.add(new PoolingLayer().setMode(PoolingLayer.PoolingMode.Max));
      network.add(new ReLuActivationLayer());
      network.add(new BiasLayer(14, 14, 5));
      network.add(new FullyConnectedLayer(new int[]{14, 14, 5}, new int[]{features})
        .setWeights(() -> 0.001 * (Math.random() - 0.45)));
      network.add(new SoftmaxActivationLayer());
      return network;
    });
  };
  /**
   * The constant rev_linear_1.
   */
  public static RevNetworkFactory rev_linear_1 = (log, features) -> {
    log.p("The vector-to-image network is a single fully connected layer:");
    return log.code(() -> {
      PipelineNetwork network = new PipelineNetwork();
      network.add(new FullyConnectedLayer(new int[]{features}, new int[]{28, 28, 1})
        .setWeights(() -> 0.25 * (Math.random() - 0.5)));
      network.add(new BiasLayer(28, 28, 1));
      return network;
    });
  };
  /**
   * The constant rev_conv_1.
   */
  public static RevNetworkFactory rev_conv_1 = (log, features) -> {
    log.p("The vector-to-image network uses a fully connected layer then a single convolutional layer:");
    return log.code(() -> {
      PipelineNetwork network = new PipelineNetwork();
      network.add(new FullyConnectedLayer(new int[]{features}, new int[]{28, 28, 5})
        .setWeights(() -> 0.25 * (Math.random() - 0.5)));
      network.add(new ReLuActivationLayer());
      network.add(new ConvolutionLayer(3, 3, 5, 1)
        .setWeights(i -> 1e-8 * (Math.random() - 0.5)));
      network.add(new BiasLayer(28, 28, 1));
      network.add(new ReLuActivationLayer());
      return network;
    });
  };
  
  /**
   * The type Gd.
   */
  public static class GD extends AllTests {
    /**
     * Instantiates a new Gd.
     */
    public GD() {
      super(TextbookOptimizers.simple_gradient_descent, MnistTests.rev_linear_1, MnistTests.fwd_linear_1);
    }
    
    @Override
    protected void intro(NotebookOutput log) {
      log.p("");
    }
  }
  
  /**
   * The type Sgd.
   */
  public static class SGD extends AllTests {
    /**
     * Instantiates a new Sgd.
     */
    public SGD() {
      super(TextbookOptimizers.stochastic_gradient_descent, MnistTests.rev_linear_1, MnistTests.fwd_linear_1);
    }
    
    @Override
    protected void intro(NotebookOutput log) {
      log.p("");
    }
  }
  
  /**
   * The type Cj gd.
   */
  public static class CjGD extends AllTests {
    /**
     * Instantiates a new Cj gd.
     */
    public CjGD() {
      super(TextbookOptimizers.conjugate_gradient_descent, MnistTests.rev_linear_1, MnistTests.fwd_linear_1);
    }
    
    @Override
    protected void intro(NotebookOutput log) {
      log.p("");
    }
  }
  
  /**
   * The type Lbfgs.
   */
  public static class LBFGS extends AllTests {
    /**
     * Instantiates a new Lbfgs.
     */
    public LBFGS() {
      super(TextbookOptimizers.limited_memory_bfgs, MnistTests.rev_conv_1, MnistTests.fwd_conv_1);
    }
    
    @Override
    protected void intro(NotebookOutput log) {
      log.p("");
    }
  }
  
  /**
   * The type Owl qn.
   */
  public static class OWL_QN extends AllTests {
    /**
     * Instantiates a new Owl qn.
     */
    public OWL_QN() {
      super(TextbookOptimizers.orthantwise_quasi_newton, MnistTests.rev_conv_1, MnistTests.fwd_conv_1);
    }
    
    @Override
    protected void intro(NotebookOutput log) {
      log.p("");
    }
  }
  
  /**
   * The type Qqn.
   */
  public static class QQN extends AllTests {
    /**
     * Instantiates a new Qqn.
     */
    public QQN() {
      super(OptimizerComparison.quadratic_quasi_newton, MnistTests.rev_conv_1, MnistTests.fwd_conv_1);
    }
    
    @Override
    protected void intro(NotebookOutput log) {
      log.p("");
    }
    
  }
  
  private abstract static class AllTests {
  
  
    /**
     * The Rev factory.
     */
    protected final RevNetworkFactory revFactory;
    /**
     * The Optimization strategy.
     */
    protected final OptimizationStrategy optimizationStrategy;
    /**
     * The Fwd factory.
     */
    protected final FwdNetworkFactory fwdFactory;
    /**
     * The Data.
     */
    protected final MnistProblemData data = new MnistProblemData();
  
    /**
     * Instantiates a new All tests.
     *
     * @param optimizationStrategy the optimization strategy
     * @param revFactory           the rev factory
     * @param fwdFactory           the fwd factory
     */
    public AllTests(OptimizationStrategy optimizationStrategy, RevNetworkFactory revFactory, FwdNetworkFactory fwdFactory) {
      this.revFactory = revFactory;
      this.optimizationStrategy = optimizationStrategy;
      this.fwdFactory = fwdFactory;
    }
  
    /**
     * Encoding test.
     *
     * @throws IOException the io exception
     */
    @Test
    @Category(TestCategories.Report.class)
    public void encoding_test() throws IOException {
      try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
        if (null != TestUtil.originalOut) log.addCopy(TestUtil.originalOut);
        log.h1("MNIST Image-to-Vector Encoding");
        intro(log);
        new EncodingProblem(revFactory, optimizationStrategy, data).setTimeoutMinutes(timeoutMinutes).run(log);
      }
    }
  
    /**
     * Intro.
     *
     * @param log the log
     */
    protected abstract void intro(NotebookOutput log);
  
    /**
     * Classification test.
     *
     * @throws IOException the io exception
     */
    @Test
    @Category(TestCategories.Report.class)
    public void classification_test() throws IOException {
      try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
        if (null != TestUtil.originalOut) log.addCopy(TestUtil.originalOut);
        log.h1("MNIST Digit Classification");
        intro(log);
        new ClassifyProblem(fwdFactory, optimizationStrategy, data, 10).setTimeoutMinutes(timeoutMinutes).run(log);
      }
    }
  
    /**
     * Autoencoder test.
     *
     * @throws IOException the io exception
     */
    @Test
    @Category(TestCategories.Report.class)
    public void autoencoder_test() throws IOException {
      try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
        if (null != TestUtil.originalOut) log.addCopy(TestUtil.originalOut);
        log.h1("MNIST Denoising Autoencoder");
        intro(log);
        new AutoencodingProblem(fwdFactory, optimizationStrategy, revFactory, data).setTimeoutMinutes(timeoutMinutes).run(log);
      }
    }
    
  }
  
}