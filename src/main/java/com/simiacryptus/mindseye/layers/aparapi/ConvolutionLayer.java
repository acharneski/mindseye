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

package com.simiacryptus.mindseye.layers.aparapi;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.util.Util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * This convolution key is often used as the reference implementation for other convolution implementation. It uses
 * OpenCL via Aparapi to compile Java into GPU-accellerated kernels. Due to its simple implementation and limitations of
 * Aparapi, it is not as fast as CudaSystem-powered layers.
 */
@SuppressWarnings("serial")
public class ConvolutionLayer extends LayerBase {


  /**
   * The Kernel.
   */
  @Nullable
  public final Tensor kernel;
  @Nullable
  private Integer paddingX = null;
  @Nullable
  private Integer paddingY = null;


  /**
   * Instantiates a new Convolution key.
   */
  protected ConvolutionLayer() {
    this(null, true);
  }

  /**
   * Instantiates a new Convolution key.
   *
   * @param width  the width
   * @param height the height
   * @param bands  the bands
   */
  public ConvolutionLayer(final int width, final int height, final int bands) {
    this(width, height, bands, true);
  }

  /**
   * Instantiates a new Convolution key.
   *
   * @param width  the width
   * @param height the height
   * @param bands  the bands
   * @param simple the simple
   */
  public ConvolutionLayer(final int width, final int height, final int bands, final boolean simple) {
    this(new Tensor(width, height, bands), simple);
    assert !simple || 0 == (width - 1) % 2 : "Simple kernels must have odd width";
    assert !simple || 0 == (height - 1) % 2 : "Simple kernels must have odd height";
  }

  /**
   * Instantiates a new Convolution key.
   *
   * @param width       the width
   * @param height      the height
   * @param inputBands  the input bands
   * @param outputBands the output bands
   */
  public ConvolutionLayer(final int width, final int height, final int inputBands, final int outputBands) {
    this(width, height, inputBands * outputBands);
  }

  /**
   * Instantiates a new Convolution key.
   *
   * @param width       the width
   * @param height      the height
   * @param inputBands  the input bands
   * @param outputBands the output bands
   * @param simple      the simple
   */
  public ConvolutionLayer(final int width, final int height, final int inputBands, final int outputBands, final boolean simple) {
    this(width, height, inputBands * outputBands, simple);
  }

  /**
   * Instantiates a new Convolution key.
   *
   * @param json      the json
   * @param resources the resources
   */
  protected ConvolutionLayer(@Nonnull final JsonObject json, Map<CharSequence, byte[]> resources) {
    super(json);
    kernel = Tensor.fromJson(json.get("filter"), resources);
    JsonElement paddingX = json.get("paddingX");
    if (null != paddingX && paddingX.isJsonPrimitive()) this.setPaddingX((paddingX.getAsInt()));
    JsonElement paddingY = json.get("paddingY");
    if (null != paddingY && paddingY.isJsonPrimitive()) this.setPaddingY((paddingY.getAsInt()));
  }

  /**
   * Instantiates a new Convolution key.
   *
   * @param kernel the kernel
   * @param simple the simple
   */
  protected ConvolutionLayer(@Nonnull final Tensor kernel, final boolean simple) {
    super();
    this.paddingX = simple ? null : 0;
    this.paddingY = simple ? null : 0;
    @Nonnull int[] dimensions = kernel.getDimensions();
    if (dimensions.length != 3) throw new IllegalArgumentException(Arrays.toString(dimensions));
    if (dimensions[0] <= 0) throw new IllegalArgumentException(Arrays.toString(dimensions));
    if (dimensions[1] <= 0) throw new IllegalArgumentException(Arrays.toString(dimensions));
    if (dimensions[2] <= 0) throw new IllegalArgumentException(Arrays.toString(dimensions));
    if (dimensions[2] <= 0) throw new IllegalArgumentException(Arrays.toString(dimensions));
    this.kernel = kernel;
  }

  /**
   * From json convolution key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the convolution key
   */
  public static ConvolutionLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new ConvolutionLayer(json, rs);
  }

  /**
   * Add weights convolution key.
   *
   * @param f the f
   * @return the convolution key
   */
  @Nonnull
  public ConvolutionLayer addWeights(@Nonnull final DoubleSupplier f) {
    Util.add(f, kernel.getData());
    return this;
  }

  @Nonnull
  @Override
  public Result eval(@Nonnull final Result... inObj) {
    Arrays.stream(inObj).forEach(nnResult -> nnResult.addRef());

    final Result input = inObj[0];
    final TensorList batch = input.getData();
    batch.addRef();
    @Nonnull final int[] inputDims = batch.get(0).getDimensions();
    @Nonnull final int[] kernelDims = kernel.getDimensions();
    @Nullable final double[] kernelData = ConvolutionLayer.this.kernel.getData();
    @Nonnull final ConvolutionController convolutionController = new ConvolutionController(inputDims, kernelDims, paddingX, paddingY);
    final Tensor[] output = IntStream.range(0, batch.length())
        .mapToObj(dataIndex -> new Tensor(convolutionController.getOutputDims()))
        .toArray(i -> new Tensor[i]);
    try {
      final double[][] inputBuffers = batch.stream().map(x -> {
        @Nullable double[] data = x.getData();
        x.detach();
        return data;
      }).toArray(i -> new double[i][]);
      final double[][] outputBuffers = Arrays.stream(output).map(x -> x.getData()).toArray(i -> new double[i][]);
      convolutionController.convolve(inputBuffers, kernelData, outputBuffers);
    } catch (@Nonnull final Throwable e) {
      throw new RuntimeException("Error mapCoords png res " + Arrays.toString(inputDims), e);
    }
    int outputLength = output.length;
    return new Result(TensorArray.wrap(output), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList error) -> {
      if (!isFrozen()) {
        final double[][] inputBuffers = batch.stream().map(x -> {
          @Nullable double[] data = x.getData();
          x.freeRef();
          return data;
        }).toArray(i -> new double[i][]);
        final double[][] outputBuffers = error.stream().map(x -> {
          @Nullable double[] data = x.getData();
          x.freeRef();
          return data;
        }).toArray(i -> new double[i][]);
        @Nonnull final Tensor weightGradient = new Tensor(kernelDims);
        convolutionController.gradient(inputBuffers, weightGradient.getData(), outputBuffers);

        buffer.get(ConvolutionLayer.this.getId(), kernelData).addInPlace(weightGradient.getData()).freeRef();
        weightGradient.freeRef();
      }
      if (input.isAlive()) {
        final Tensor[] inputBufferTensors = IntStream.range(0, outputLength).mapToObj(dataIndex -> new Tensor(inputDims)).toArray(i -> new Tensor[i]);
        final double[][] inputBuffers = Arrays.stream(inputBufferTensors).map(x -> {
          @Nullable double[] data = x.getData();
          return data;
        }).toArray(i -> new double[i][]);
        final double[][] outputBuffers = error.stream().map(x -> {
          @Nullable double[] data = x.getData();
          x.freeRef();
          return data;
        }).toArray(i -> new double[i][]);
        convolutionController.backprop(inputBuffers, kernelData, outputBuffers);
        @Nonnull TensorArray tensorArray = TensorArray.wrap(inputBufferTensors);
        input.accumulate(buffer, tensorArray);
      }
    }) {

      @Override
      protected void _free() {
        Arrays.stream(inObj).forEach(nnResult -> nnResult.freeRef());
        batch.freeRef();
      }


      @Override
      public boolean isAlive() {
        return input.isAlive() || !isFrozen();
      }
    };
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, @Nonnull DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    json.add("filter", kernel.toJson(resources, dataSerializer));
    JsonElement paddingX = json.get("paddingX");
    if (null != paddingX && paddingX.isJsonPrimitive()) this.setPaddingX((paddingX.getAsInt()));
    JsonElement paddingY = json.get("paddingY");
    if (null != paddingY && paddingY.isJsonPrimitive()) this.setPaddingY((paddingY.getAsInt()));
    return json;
  }

  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  @Nonnull
  public ConvolutionLayer setWeights(@Nonnull final DoubleSupplier f) {
    kernel.coordStream(true).forEach(c -> {
      kernel.set(c, f.getAsDouble());
    });
    return this;
  }

  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  @Nonnull
  public ConvolutionLayer setWeights(@Nonnull final ToDoubleFunction<Coordinate> f) {
    kernel.coordStream(true).forEach(c -> {
      kernel.set(c, f.applyAsDouble(c));
    });
    return this;
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList(kernel.getData());
  }

  /**
   * Gets padding x.
   *
   * @return the padding x
   */
  @Nullable
  public Integer getPaddingX() {
    return paddingX;
  }

  /**
   * Sets padding x.
   *
   * @param paddingX the padding x
   * @return the padding x
   */
  @Nonnull
  public ConvolutionLayer setPaddingX(Integer paddingX) {
    this.paddingX = paddingX;
    return this;
  }

  /**
   * Gets padding y.
   *
   * @return the padding y
   */
  @Nullable
  public Integer getPaddingY() {
    return paddingY;
  }

  /**
   * Sets padding y.
   *
   * @param paddingY the padding y
   * @return the padding y
   */
  @Nonnull
  public ConvolutionLayer setPaddingY(Integer paddingY) {
    this.paddingY = paddingY;
    return this;
  }

  @Override
  protected void _free() {
    this.kernel.freeRef();
    super._free();
  }
}
