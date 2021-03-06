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

package com.simiacryptus.mindseye.layers.cudnn;

import com.simiacryptus.mindseye.lang.Layer;
import com.simiacryptus.mindseye.layers.cudnn.conv.ConvolutionLayer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;

/**
 * The type Rascaled subnet key apply.
 */
public abstract class ImgTileSubnetLayerTest extends CudaLayerTestBase {

  private final ConvolutionLayer convolutionLayer = new ConvolutionLayer(3, 3, 1, 1).set(() -> this.random());

  @Nonnull
  @Override
  public int[][] getSmallDims(Random random) {
    return new int[][]{
        {5, 5, 1}
    };
  }

  @Override
  public int[][] getLargeDims(final Random random) {
    return new int[][]{
        {1200, 1200, 1}
    };
  }

  @Nonnull
  @Override
  public Layer getLayer(final int[][] inputSize, Random random) {
    return new ImgTileSubnetLayer(new ActivationLayer(ActivationLayer.Mode.RELU), 3, 3, 2, 2);
  }

  @Nullable
  @Override
  public Layer getReferenceLayer() {
    return new ActivationLayer(ActivationLayer.Mode.RELU);
  }

  @Nullable
  @Override
  public Class<? extends Layer> getReferenceLayerClass() {
    return com.simiacryptus.mindseye.layers.java.ImgTileSubnetLayer.class;
  }

  /**
   * Basic Test
   */
  public static class Basic extends ImgTileSubnetLayerTest {

    @Nullable
    @Override
    public Class<? extends Layer> getReferenceLayerClass() {
      return null;
    }

  }

}
