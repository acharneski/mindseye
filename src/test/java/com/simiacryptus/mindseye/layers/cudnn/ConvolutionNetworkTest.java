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

package com.simiacryptus.mindseye.layers.cudnn;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import com.simiacryptus.mindseye.test.unit.SingleDerivativeTester;

/**
 * The type Convolution network run.
 */
public abstract class ConvolutionNetworkTest extends CudnnLayerTestBase {
  
  /**
   * The Precision.
   */
  final Precision precision;
  
  /**
   * Instantiates a new Convolution network run.
   *
   * @param precision the precision
   */
  public ConvolutionNetworkTest(final Precision precision) {
    this.precision = precision;
  }
  
  @Override
  public int[][] getInputDims() {
    return new int[][]{
      {1, 1, 3}
    };
  }
  
  @Override
  public NNLayer getLayer(final int[][] inputSize) {
    final PipelineNetwork network = new PipelineNetwork(1);
    network.add(new ImgConcatLayer().setPrecision(precision));
    network.add(new ImgBandBiasLayer(3).setPrecision(precision).addWeights(this::random));
    network.add(new ActivationLayer(ActivationLayer.Mode.RELU).setPrecision(precision));
    return network;
    
    
  }
  
  @Override
  public int[][] getPerfDims() {
    return new int[][]{
      {100, 100, 3}
    };
  }
  
  /**
   * Expands the an example low-level network implementing general convolutions. (64-bit)
   */
  public static class DoubleConvolutionNetwork extends ConvolutionNetworkTest {
    /**
     * Instantiates a new Double.
     */
    public DoubleConvolutionNetwork() {
      super(Precision.Double);
    }
  }
  
  /**
   * Expands the an example low-level network implementing general convolutions. (32-bit)
   */
  public static class FloatConvolutionNetwork extends ConvolutionNetworkTest {
    /**
     * Instantiates a new Float.
     */
    public FloatConvolutionNetwork() {
      super(Precision.Float);
    }
  
    @Override
    public SingleDerivativeTester getDerivativeTester() {
      return new SingleDerivativeTester(1e-2, 1e-3);
    }
  
  }
  
  
}
