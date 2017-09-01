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
import com.simiacryptus.mindseye.data.Tensor;
import com.simiacryptus.mindseye.data.TensorList;
import com.simiacryptus.mindseye.layers.DeltaSet;
import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.layers.NNResult;
import com.simiacryptus.mindseye.layers.cudnn.CuDNN;
import com.simiacryptus.mindseye.layers.cudnn.CudaExecutionContext;
import com.simiacryptus.mindseye.layers.cudnn.CudaPtr;
import com.simiacryptus.mindseye.layers.cudnn.CudaResource;
import jcuda.Sizeof;
import jcuda.jcudnn.cudnnPoolingDescriptor;
import jcuda.jcudnn.cudnnTensorDescriptor;

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
public class BandPoolingLayer extends NNLayer {
  
  /**
   * From json pooling layer.
   *
   * @param json the json
   * @return the pooling layer
   */
  public static BandPoolingLayer fromJson(JsonObject json) {
    return new BandPoolingLayer(json);
  }

  public JsonObject getJson() {
    JsonObject json = super.getJsonStub();
    json.addProperty("mode", mode);
    return json;
  }
  
  /**
   * Instantiates a new Pooling layer.
   *
   * @param json the json
   */
  protected BandPoolingLayer(JsonObject json) {
    super(json);
    mode = json.get("mode").getAsInt();
  }
  
  /**
   * Instantiates a new Pooling layer.
   */
  public BandPoolingLayer() {
    super();
  }
  
  /**
   * The enum Pooling mode.
   */
  public enum PoolingMode {
    /**
     * Max pooling mode.
     */
    Max(CUDNN_POOLING_MAX),
    /**
     * Avg pooling mode.
     */
    Avg(CUDNN_POOLING_AVERAGE_COUNT_EXCLUDE_PADDING);
    /**
     * The Id.
     */
    final int id;

    PoolingMode(int id) {
      this.id = id;
    }
  }
  
  private int mode = CUDNN_POOLING_MAX;

  @Override
  public NNResult eval(NNExecutionContext nncontext, final NNResult... inObj) {
    try {
      ((CudaExecutionContext) nncontext).initThread();
      //assert Arrays.stream(inObj).flatMapToDouble(input->input.data.stream().flatMapToDouble(x-> Arrays.stream(x.getData()))).allMatch(v->Double.isFinite(v));
      final NNResult input = inObj[0];
      final TensorList batch = input.getData();
      final int[] inputSize = batch.getDimensions();

      int windowX = inputSize[0];
      int windowY = inputSize[1];
      int paddingX = 0;
      int paddingY = 0;
      int strideX = 1;
      int strideY = 1;
      final int poolDims = 2;
      final int windowSize[] = {windowX, windowY};
      final int padding[] = {paddingX, paddingY};
      final int stride[] = {strideX, strideY};

      int length = batch.length();
      int inputDims = Tensor.dim(inputSize);
      CudaResource<cudnnPoolingDescriptor> poolingDesc = CuDNN.createPoolingDescriptor(
        mode, poolDims, windowSize, padding, stride);
      CudaResource<cudnnTensorDescriptor> inputDescriptor = CuDNN.newTensorDescriptor(
        CUDNN_DATA_FLOAT, CUDNN_TENSOR_NCHW, length, inputSize[2], inputSize[1], inputSize[0]);
      int[] outputSize = new int[4];
      CuDNN.handle(cudnnGetPoolingNdForwardOutputDim(poolingDesc.getPtr(), inputDescriptor.getPtr(), 4, outputSize));
      assert (inputSize[2] == outputSize[1]);
      CudaResource<cudnnTensorDescriptor> outputDescriptor = CuDNN.newTensorDescriptor(
        CUDNN_DATA_FLOAT, CUDNN_TENSOR_NCHW, outputSize[0], outputSize[1], outputSize[2], outputSize[3]);
      CudaPtr alpha = CuDNN.javaPtr(((CudaExecutionContext) nncontext).getDeviceNumber(), 1.0f);
      CudaPtr beta = CuDNN.javaPtr(((CudaExecutionContext) nncontext).getDeviceNumber(), 0.0f);
      CudaPtr inputData = CudaPtr.toDeviceAsFloat(((CudaExecutionContext) nncontext).getDeviceNumber(), batch);
      CudaPtr outputData = CuDNN.alloc(((CudaExecutionContext) nncontext).getDeviceNumber(), Sizeof.FLOAT * 1l * Tensor.dim(outputSize));
      CuDNN.handle(cudnnPoolingForward(((CuDNN) ((CudaExecutionContext) nncontext)).cudnnHandle, poolingDesc.getPtr(),
        alpha.getPtr(),
        inputDescriptor.getPtr(), inputData.getPtr(),
        beta.getPtr(),
        outputDescriptor.getPtr(), outputData.getPtr()));
      TensorList output = CudaPtr.fromDeviceFloat(outputData, length, new int[]{outputSize[3], outputSize[2], outputSize[1]}, ((CuDNN) ((CudaExecutionContext) nncontext)).cudnnHandle);
      return new NNResult(output) {
        @Override
        public void accumulate(final DeltaSet buffer, final TensorList error) {
          ((CudaExecutionContext) nncontext).initThread();
          assert (error.length() == batch.length());
          //assert error.stream().flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
          CudaPtr errorPtr = CudaPtr.toDeviceAsFloat(((CudaExecutionContext) nncontext).getDeviceNumber(), error);
          if (input.isAlive()) {
            CudaPtr passbackBuffer = CuDNN.alloc(((CudaExecutionContext) nncontext).getDeviceNumber(), inputDims * 1l * Sizeof.FLOAT * length);
            CuDNN.handle(cudnnPoolingBackward(((CuDNN) ((CudaExecutionContext) nncontext)).cudnnHandle, poolingDesc.getPtr(),
              alpha.getPtr(),
              outputDescriptor.getPtr(), outputData.getPtr(),
              outputDescriptor.getPtr(), errorPtr.getPtr(),
              inputDescriptor.getPtr(), inputData.getPtr(),
              beta.getPtr(),
              inputDescriptor.getPtr(), passbackBuffer.getPtr()));
            input.accumulate(buffer, CudaPtr.fromDeviceFloat(passbackBuffer, length, inputSize, ((CuDNN) ((CudaExecutionContext) nncontext)).cudnnHandle));
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
      throw new RuntimeException("Error", e);
    }
  }


  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
  
  /**
   * Gets mode.
   *
   * @return the mode
   */
  public int getMode() {
    return mode;
  }
  
  /**
   * Sets mode.
   *
   * @param mode the mode
   * @return the mode
   */
  public BandPoolingLayer setMode(PoolingMode mode) {
    this.mode = mode.id;
    return this;
  }
  
}
