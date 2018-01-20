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

package com.simiacryptus.mindseye.test;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.TensorList;
import com.simiacryptus.mindseye.lang.cudnn.CudaPtr;
import com.simiacryptus.mindseye.lang.cudnn.GpuHandle;
import com.simiacryptus.mindseye.lang.cudnn.GpuTensorList;
import com.simiacryptus.mindseye.lang.cudnn.Precision;

/**
 * The type Simple gpu eval.
 */
public class SimpleGpuEval extends SimpleListEval {
  
  private final GpuHandle gpu;
  
  /**
   * Instantiates a new Simple gpu eval.
   *
   * @param layer the layer
   * @param gpu   the gpu
   * @param input the input
   */
  public SimpleGpuEval(NNLayer layer, GpuHandle gpu, TensorList... input) {
    super(layer, input);
    this.gpu = gpu;
  }
  
  /**
   * Run simple result.
   *
   * @param layer  the layer
   * @param gpu    the gpu
   * @param tensor the tensor
   * @return the simple result
   */
  public static SimpleResult run(final NNLayer layer, final GpuHandle gpu, final TensorList... tensor) {
    return new SimpleGpuEval(layer, gpu, tensor).call();
  }
  
  @Override
  public TensorList getFeedback(final TensorList original) {
    CudaPtr cudaPtr = CudaPtr.write(gpu.getDeviceNumber(), Precision.Double, original);
    return GpuTensorList.create(cudaPtr, original.length(), original.getDimensions(), Precision.Double);
  }
  
}
