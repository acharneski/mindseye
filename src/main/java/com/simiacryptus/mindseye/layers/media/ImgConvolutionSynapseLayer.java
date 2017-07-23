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

package com.simiacryptus.mindseye.layers.media;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.layers.DeltaSet;
import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.layers.NNResult;
import com.simiacryptus.mindseye.opencl.ConvolutionController;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.ml.Coordinate;
import com.simiacryptus.util.ml.Tensor;

import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

public class ImgConvolutionSynapseLayer extends NNLayer {
  
  
  public JsonObject getJson() {
    JsonObject json = super.getJsonStub();
    json.add("kernel", kernel.getJson());
    json.add("skip", skip.getJson());
    json.addProperty("simple", simple);
    return json;
  }
  
  public static ImgConvolutionSynapseLayer fromJson(JsonObject json) {
    return new ImgConvolutionSynapseLayer(json);
  }
  protected ImgConvolutionSynapseLayer(JsonObject json) {
    super(json);
    this.kernel = Tensor.fromJson(json.getAsJsonObject("kernel"));
    this.skip = Tensor.fromJson(json.getAsJsonObject("skip"));
    this.simple = json.getAsJsonPrimitive("simple").getAsBoolean();
  }
  
  
  public final Tensor kernel;
  public final Tensor skip;
  public final boolean simple;
  
  protected ImgConvolutionSynapseLayer() {
    this((Tensor)null, (Tensor)null, true);
  }
  
  protected ImgConvolutionSynapseLayer(Tensor kernel, Tensor skip, boolean simple) {
    super();
    this.simple = simple;
    this.skip = skip;
    if(kernel.getDims().length != 3) throw new IllegalArgumentException();
    if(kernel.getDims()[0] <= 0) throw new IllegalArgumentException();
    if(kernel.getDims()[1] <= 0) throw new IllegalArgumentException();
    if(kernel.getDims()[2] <= 0) throw new IllegalArgumentException();
    this.kernel = kernel;
  }
  
  public ImgConvolutionSynapseLayer(final int width, int height, final int inputBands, final int outputBands) {
    this(width, height, inputBands * outputBands);
  }
  
  public ImgConvolutionSynapseLayer(final int width, int height, final int bands, boolean simple) {
    this(new Tensor(width,height,bands), new Tensor(new int[]{1,1}), simple);
    assert(!simple || 0 == (width-1) % 2) : "Simple kernels must have odd width";
    assert(!simple || 0 == (height-1) % 2) : "Simple kernels must have odd height";
  }
  
  public ImgConvolutionSynapseLayer(final int width, int height, final int bands) {
    this(width, height, bands, true);
  }
  
  public ImgConvolutionSynapseLayer(final int width, int height, final int inputBands, final int outputBands, boolean simple) {
    this(width, height, inputBands * outputBands, simple);
  }
  
  public ImgConvolutionSynapseLayer addWeights(final DoubleSupplier f) {
    Util.add(f, this.kernel.getData());
    return this;
  }
  
  @Override
  public NNResult eval(final NNResult... inObj) {
    assert Arrays.stream(inObj).flatMapToDouble(input->Arrays.stream(input.data).flatMapToDouble(x-> Arrays.stream(x.getData()))).allMatch(v->Double.isFinite(v));
    
    final NNResult input = inObj[0];
    final Tensor[] batch = input.data;
    final int[] inputDims = batch[0].getDims();
    int[] kernelDims = this.kernel.getDims();
    ConvolutionController convolutionController = new ConvolutionController(inputDims, kernelDims, simple);
    Tensor[] output = IntStream.range(0, batch.length)
                           .mapToObj(dataIndex -> new Tensor(convolutionController.getOutputDims()))
                           .toArray(i -> new Tensor[i]);
    {
      double[][] inputBuffers = Arrays.stream(batch).map(x -> x.getData()).toArray(i -> new double[i][]);
      double[][] outputBuffers = Arrays.stream(output).map(x -> x.getData()).toArray(i -> new double[i][]);
      convolutionController.convolve(inputBuffers, this.kernel.getData(), outputBuffers);
    }
    assert Arrays.stream(output).flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
  
    return new NNResult(output) {
      @Override
      public void accumulate(final DeltaSet buffer, final Tensor[] error) {
        assert Arrays.stream(error).flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
        if (!isFrozen()) {
          double[][] inputBuffers = Arrays.stream(batch).map(x -> x.getData()).toArray(i -> new double[i][]);
          double[][] outputBuffers = Arrays.stream(error).map(x -> x.getData()).toArray(i -> new double[i][]);
          final Tensor kernel = ImgConvolutionSynapseLayer.this.kernel;
          final Tensor weightGradient = new Tensor(kernel.getDims());
          convolutionController.gradient(inputBuffers, weightGradient.getData(), outputBuffers);
          buffer.get(ImgConvolutionSynapseLayer.this, kernel).accumulate(weightGradient.getData());
        }
        if (input.isAlive()) {
          Tensor[] inputBufferTensors = IntStream.range(0, data.length).mapToObj(dataIndex -> new Tensor(inputDims)).toArray(i -> new Tensor[i]);
          double[][] inputBuffers = Arrays.stream(inputBufferTensors).map(x -> x.getData()).toArray(i -> new double[i][]);
          double[][] outputBuffers = Arrays.stream(error).map(x -> x.getData()).toArray(i -> new double[i][]);
          convolutionController.backprop(inputBuffers, ImgConvolutionSynapseLayer.this.kernel.getData(), outputBuffers);
          assert Arrays.stream(inputBufferTensors).flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
          input.accumulate(buffer, inputBufferTensors);
        }
      }
      
      @Override
      public boolean isAlive() {
        return input.isAlive() || !isFrozen();
      }
    };
  }
  
  public ImgConvolutionSynapseLayer setWeights(final ToDoubleFunction<Coordinate> f) {
    this.kernel.coordStream().parallel().forEach(c -> {
      this.kernel.set(c, f.applyAsDouble(c));
    });
    return this;
  }
  
  public ImgConvolutionSynapseLayer setWeights(final DoubleSupplier f) {
    this.kernel.coordStream().parallel().forEach(c -> {
      this.kernel.set(c, f.getAsDouble());
    });
    return this;
  }
  
  @Override
  public List<double[]> state() {
    return Arrays.asList(this.kernel.getData());
  }
  
}
