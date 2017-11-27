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

package com.simiacryptus.mindseye.layers.cudnn.f64;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.layers.EquivalencyTester;
import com.simiacryptus.mindseye.layers.LayerTestBase;

import java.util.Random;

/**
 * The type Convolution layer run.
 */
public class ConvolutionLayerTest extends LayerTestBase {
  
  /**
   * The Convolution layer.
   */
  ConvolutionLayer convolutionLayer;
  
  /**
   * Instantiates a new Convolution layer test.
   */
  public ConvolutionLayerTest() {
    convolutionLayer = new ConvolutionLayer(3, 3, 2, 2);
    convolutionLayer.filter.fill(() -> random());
  }
  
  @Override
  public NNLayer getLayer() {
    return convolutionLayer;
  }
  
  @Override
  public NNLayer getReferenceLayer() {
    com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer referenceLayer = new com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer(3, 3, 2, 2, true);
    referenceLayer.kernel.set(convolutionLayer.filter);
    return referenceLayer;
  }
  
  @Override
  public int[][] getInputDims() {
    return new int[][]{
      {3, 3, 2}
    };
  }
  
  /**
   * The type Asymmetric test.
   */
  public static class AsymmetricTest extends ConvolutionLayerTest {
  
    /**
     * The Convolution layer.
     */
    ConvolutionLayer convolutionLayer;
  
    /**
     * Instantiates a new Asymmetric test.
     */
    public AsymmetricTest() {
      convolutionLayer = new ConvolutionLayer(3, 3, 2, 4);
      convolutionLayer.filter.fill(() -> random());
    }
  
    @Override
    public NNLayer getLayer() {
      return convolutionLayer;
    }
    
    @Override
    public NNLayer getReferenceLayer() {
      com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer referenceLayer = new com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer(3, 3, 2, 4, true);
      referenceLayer.kernel.set(convolutionLayer.filter);
      return referenceLayer;
    }
    
    @Override
    public int[][] getInputDims() {
      return new int[][]{
        {3, 3, 2}
      };
    }
    
  }
  
  /**
   * The type Irregular test.
   */
  public static class IrregularTest extends ConvolutionLayerTest {
  
    /**
     * The Convolution layer.
     */
    ConvolutionLayer convolutionLayer;
  
    /**
     * Instantiates a new Irregular test.
     */
    public IrregularTest() {
      convolutionLayer = new ConvolutionLayer(1, 1, 2, 3);
      convolutionLayer.filter.fill(() -> random());
    }
  
    @Override
    public NNLayer getLayer() {
      return convolutionLayer;
    }
    
    @Override
    public NNLayer getReferenceLayer() {
      com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer referenceLayer = new com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer(1, 1, 2, 3, true);
      referenceLayer.kernel.set(convolutionLayer.filter);
      return referenceLayer;
    }
    
    @Override
    public int[][] getInputDims() {
      return new int[][]{
        {1, 1, 2}
      };
    }
    
  }
}
