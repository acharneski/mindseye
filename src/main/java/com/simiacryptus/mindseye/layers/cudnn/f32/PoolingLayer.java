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

package com.simiacryptus.mindseye.layers.cudnn.f32;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.layers.DeltaSet;
import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.layers.NNResult;
import com.simiacryptus.mindseye.layers.TensorList;
import com.simiacryptus.mindseye.layers.cudnn.*;
import com.simiacryptus.mindseye.layers.cudnn.CudaPtr;
import com.simiacryptus.mindseye.layers.cudnn.CudaResource;
import com.simiacryptus.util.ml.Tensor;
import jcuda.Sizeof;
import jcuda.jcudnn.cudnnPoolingDescriptor;
import jcuda.jcudnn.cudnnTensorDescriptor;
import jcuda.runtime.JCuda;

import java.util.Arrays;
import java.util.List;

import static jcuda.jcudnn.JCudnn.*;
import static jcuda.jcudnn.cudnnDataType.CUDNN_DATA_FLOAT;
import static jcuda.jcudnn.cudnnPoolingMode.CUDNN_POOLING_AVERAGE_COUNT_EXCLUDE_PADDING;
import static jcuda.jcudnn.cudnnPoolingMode.CUDNN_POOLING_MAX;
import static jcuda.jcudnn.cudnnTensorFormat.CUDNN_TENSOR_NCHW;

/**
 * The type Pooling layer.
 */
public class PoolingLayer extends NNLayer {

  /**
   * From json pooling layer.
   *
   * @param json the json
   * @return the pooling layer
   */
  public static PoolingLayer fromJson(JsonObject json) {
    return new PoolingLayer(json);
  }

  public JsonObject getJson() {
    JsonObject json = super.getJsonStub();
    json.addProperty("mode",mode);
    json.addProperty("windowX",windowX);
    json.addProperty("windowY",windowY);
    json.addProperty("paddingX",paddingX);
    json.addProperty("paddingY",paddingY);
    json.addProperty("strideX",strideX);
    json.addProperty("strideY",strideY);
    return json;
  }

  /**
   * Instantiates a new Pooling layer.
   *
   * @param json the json
   */
  protected PoolingLayer(JsonObject json) {
    super(json);
    mode = json.get("mode").getAsInt();
    windowX = json.get("windowX").getAsInt();
    windowY = json.get("windowY").getAsInt();
    paddingX = json.get("paddingX").getAsInt();
    paddingY = json.get("paddingY").getAsInt();
    strideX = json.get("strideX").getAsInt();
    strideY = json.get("strideY").getAsInt();
  }

  /**
   * Instantiates a new Pooling layer.
   */
  public PoolingLayer() {
    super();
  }

  public enum PoolingMode {
    Max(CUDNN_POOLING_MAX),
    Avg(CUDNN_POOLING_AVERAGE_COUNT_EXCLUDE_PADDING);
    final int id;
  
    PoolingMode(int id) {
      this.id = id;
    }
  }
  
  private int mode = CUDNN_POOLING_MAX;
  private int windowX = 2;
  private int windowY = 2;
  private int paddingX = 0;
  private int paddingY = 0;
  private int strideX = 2;
  private int strideY = 2;

  @Override
  public NNResult eval(NNExecutionContext nncontext, final NNResult... inObj) {
    final int poolDims = 2;
    final int windowSize[] = {windowX, windowY};
    final int padding[] = {paddingX, paddingY};
    final int stride[] = {strideX, strideY};
    try {
      CuDNN.setDevice(nncontext.getCudaDeviceId());
      //assert Arrays.stream(inObj).flatMapToDouble(input->input.data.stream().flatMapToDouble(x-> Arrays.stream(x.getData()))).allMatch(v->Double.isFinite(v));
      final NNResult input = inObj[0];
      final TensorList batch = input.data;
      final int[] inputSize = batch.getDimensions();
      int length = batch.length();
      int inputDims = Tensor.dim(inputSize);
      CudaResource<cudnnPoolingDescriptor> poolingDesc = CuDNN.createPoolingDescriptor(
              mode, poolDims, windowSize, padding, stride);
      CudaResource<cudnnTensorDescriptor> inputDescriptor = CuDNN.newTensorDescriptor(
              CUDNN_DATA_FLOAT, CUDNN_TENSOR_NCHW, length, inputSize[2], inputSize[1], inputSize[0]);
      int[] outputSize = new int[4];
      CuDNN.handle(cudnnGetPoolingNdForwardOutputDim(poolingDesc.getPtr(), inputDescriptor.getPtr(), 4, outputSize));
      assert(inputSize[2] == outputSize[1]);
      CudaResource<cudnnTensorDescriptor> outputDescriptor = CuDNN.newTensorDescriptor(
              CUDNN_DATA_FLOAT, CUDNN_TENSOR_NCHW, outputSize[0], outputSize[1], outputSize[2], outputSize[3]);
      CudaPtr alpha = CuDNN.javaPtr(nncontext.getCudaDeviceId(), 1.0f);
      CudaPtr beta = CuDNN.javaPtr(nncontext.getCudaDeviceId(), 0.0f);
      CudaPtr inputData = CudaPtr.toDeviceAsFloat(nncontext.getCudaDeviceId(), batch);
      CudaPtr outputData = CuDNN.alloc(nncontext.getCudaDeviceId(), Sizeof.FLOAT * 1l * Tensor.dim(outputSize));
      CuDNN.devicePool.with(device -> {
        CuDNN.handle(cudnnPoolingForward(device.cudnnHandle, poolingDesc.getPtr(),
                alpha.getPtr(),
                inputDescriptor.getPtr(), inputData.getPtr(),
                beta.getPtr(),
                outputDescriptor.getPtr(), outputData.getPtr()));
      });
      TensorList output = CudaPtr.fromDeviceFloat(outputData, length, new int[]{outputSize[3], outputSize[2], outputSize[1]});
      return new NNResult(output) {
        @Override
        public void accumulate(final DeltaSet buffer, final TensorList error) {
          CuDNN.setDevice(nncontext.getCudaDeviceId());
          assert (error.length() == batch.length());
          //assert error.stream().flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
          CudaPtr errorPtr = CudaPtr.toDeviceAsFloat(nncontext.getCudaDeviceId(), error);
          if (input.isAlive()) {
            CudaPtr passbackBuffer = CuDNN.alloc(nncontext.getCudaDeviceId(), inputDims * 1l * Sizeof.FLOAT * length);
            CuDNN.devicePool.with(device -> {
              CuDNN.handle(cudnnPoolingBackward(device.cudnnHandle, poolingDesc.getPtr(),
                      alpha.getPtr(),
                      outputDescriptor.getPtr(), outputData.getPtr(),
                      outputDescriptor.getPtr(), errorPtr.getPtr(),
                      inputDescriptor.getPtr(), inputData.getPtr(),
                      beta.getPtr(),
                      inputDescriptor.getPtr(), passbackBuffer.getPtr()));
            });
            input.accumulate(buffer, CudaPtr.fromDeviceFloat(passbackBuffer, length, inputSize));
            outputData.finalize();
            passbackBuffer.finalize();
          }
        }

        @Override
        public boolean isAlive() {
          return input.isAlive() || !isFrozen();
        }
      };
    } catch (Throwable e) {
      throw new RuntimeException("Error",e);
    }
  }


  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
  
  public int getMode() {
    return mode;
  }
  
  public PoolingLayer setMode(PoolingMode mode) {
    this.mode = mode.id;
    return this;
  }
  
  public int getWindowX() {
    return windowX;
  }
  
  public PoolingLayer setWindowX(int windowX) {
    this.windowX = windowX;
    return this;
  }
  
  public int getWindowY() {
    return windowY;
  }
  
  public PoolingLayer setWindowY(int windowY) {
    this.windowY = windowY;
    return this;
  }
  
  public PoolingLayer setWindowXY(int windowX, int windowY) {
    this.windowY = windowY;
    this.windowX = windowX;
    return this;
  }
  
  public int getPaddingX() {
    return paddingX;
  }
  
  public PoolingLayer setPaddingX(int paddingX) {
    this.paddingX = paddingX;
    return this;
  }
  
  public PoolingLayer setPaddingXY(int paddingX, int paddingY) {
    this.paddingX = paddingX;
    this.paddingY = paddingY;
    return this;
  }
  
  public int getPaddingY() {
    return paddingY;
  }
  
  public PoolingLayer setPaddingY(int paddingY) {
    this.paddingY = paddingY;
    return this;
  }
  
  public int getStrideX() {
    return strideX;
  }
  
  public PoolingLayer setStrideX(int strideX) {
    this.strideX = strideX;
    return this;
  }
  
  public int getStrideY() {
    return strideY;
  }
  
  public PoolingLayer setStrideY(int strideY) {
    this.strideY = strideY;
    return this;
  }
  public PoolingLayer setStrideXY(int strideX, int strideY) {
    this.strideX = strideX;
    this.strideY = strideY;
    return this;
  }
}
