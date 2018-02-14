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

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.cudnn.Precision;

import java.util.Random;
import java.util.stream.IntStream;

/**
 * The type Img concat layer run.
 */
public abstract class ImgConcatLayerTest extends CuDNNLayerTestBase {
  
  /**
   * The Precision.
   */
  final Precision precision;
  int inputs;
  int bandsPerInput;
  
  /**
   * Instantiates a new Img concat layer run.
   *
   * @param precision the precision
   * @param inputs
   * @param bandsPerInput
   */
  public ImgConcatLayerTest(final Precision precision, int inputs, int bandsPerInput) {
    this.precision = precision;
    this.inputs = inputs;
    this.bandsPerInput = bandsPerInput;
  }
  
  @javax.annotation.Nonnull
  @Override
  public int[][] getSmallDims(Random random) {
    return IntStream.range(0, inputs).mapToObj(x -> new int[]{8, 8, bandsPerInput}).toArray(i -> new int[i][]);
  }
  
  @javax.annotation.Nonnull
  @Override
  public NNLayer getLayer(final int[][] inputSize, Random random) {
    return new ImgConcatLayer();
  }
  
  @javax.annotation.Nonnull
  @Override
  public int[][] getLargeDims(Random random) {
    return IntStream.range(0, inputs).mapToObj(x -> new int[]{200, 200, bandsPerInput}).toArray(i -> new int[i][]);
  }
  
  @Override
  public Class<? extends NNLayer> getReferenceLayerClass() {
    return com.simiacryptus.mindseye.layers.java.ImgConcatLayer.class;
  }
  
  /**
   * Test truncation feature that limits the image to N bands, discarding the last as needed.
   */
  public static class BandLimitTest extends ImgConcatLayerTest {
  
    /**
     * Instantiates a new Band limit run.
     */
    public BandLimitTest() {
      super(Precision.Double, 2, 1);
    }
  
    @javax.annotation.Nonnull
    @Override
    public int[][] getSmallDims(Random random) {
      return new int[][]{
        {1, 1, 3}
      };
    }
  
    @javax.annotation.Nonnull
    @Override
    public int[][] getLargeDims(Random random) {
      return getSmallDims(new Random());
    }
  
    @javax.annotation.Nonnull
    @Override
    public NNLayer getLayer(final int[][] inputSize, Random random) {
      return new ImgConcatLayer().setMaxBands(2);
    }
  }
  
  /**
   * Test truncation feature that both concatenates images and limits the image to N bands, discarding the last as
   * needed.
   */
  public static class BandConcatLimitTest extends ImgConcatLayerTest {
  
    /**
     * Instantiates a new Band limit run.
     */
    public BandConcatLimitTest() {
      super(Precision.Double, 2, 1);
    }
  
    @javax.annotation.Nonnull
    @Override
    public int[][] getSmallDims(Random random) {
      return new int[][]{
        {1, 1, 2}, {1, 1, 2}
      };
    }
  
    @javax.annotation.Nonnull
    @Override
    public int[][] getLargeDims(Random random) {
      return getSmallDims(new Random());
    }
  
    @javax.annotation.Nonnull
    @Override
    public NNLayer getLayer(final int[][] inputSize, Random random) {
      return new ImgConcatLayer().setMaxBands(3);
    }
  }
  
  /**
   * Basic 64-bit run
   */
  public static class Double extends ImgConcatLayerTest {
    /**
     * Instantiates a new Double.
     */
    public Double() {
      super(Precision.Double, 2, 1);
    }
  }
  
  /**
   * Basic 64-bit run
   */
  public static class BigDouble extends ImgConcatLayerTest {
    /**
     * Instantiates a new Double.
     */
    public BigDouble() {
      super(Precision.Double, 2, 64);
      validateDifferentials = false;
    }
  }
  
  /**
   * Basic 32-bit run
   */
  public static class Float extends ImgConcatLayerTest {
    /**
     * Instantiates a new Float.
     */
    public Float() {
      super(Precision.Float, 2, 1);
    }
  }
  
}
