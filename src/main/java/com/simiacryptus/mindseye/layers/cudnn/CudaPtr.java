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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.lang.TensorList;
import com.simiacryptus.mindseye.layers.cudnn.f32.CuDNNFloatTensorList;
import com.simiacryptus.mindseye.layers.cudnn.f64.CuDNNDoubleTensorList;
import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.jcudnn.cudnnHandle;
import jcuda.runtime.JCuda;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static jcuda.runtime.JCuda.*;
import static jcuda.runtime.cudaMemcpyKind.*;

/**
 * The type Cu dnn ptr.
 */
public class CudaPtr extends CudaResource<Pointer> {
  
  /**
   * From device double tensor list.
   *
   * @param ptr        the ptr
   * @param length     the length
   * @param dimensions the dimensions
   * @return the tensor list
   */
  public static TensorList fromDeviceDouble(CudaPtr ptr, int length, int[] dimensions) {
    return new CuDNNDoubleTensorList(ptr, length, dimensions);
  }
  
  /**
   * From device float tensor list.
   *
   * @param ptr         the ptr
   * @param length      the length
   * @param dimensions  the dimensions
   * @param cudnnHandle the cudnn handle
   * @return the tensor list
   */
  public static TensorList fromDeviceFloat(CudaPtr ptr, int length, int[] dimensions, cudnnHandle cudnnHandle) {
    return new CuDNNFloatTensorList(ptr, length, dimensions, cudnnHandle);
  }
  
  /**
   * To device as double cu dnn . cu dnn ptr.
   *
   * @param deviceId the device id
   * @param data     the data
   * @return the cu dnn . cu dnn ptr
   */
  public static CudaPtr toDeviceAsDouble(int deviceId, TensorList data) {
    if (data instanceof CuDNNDoubleTensorList) {
      return ((CuDNNDoubleTensorList) data).ptr;
//        } else if(data instanceof CuDNNFloatTensorList) {
//            CuDNNFloatTensorList floatData = (CuDNNFloatTensorList) data;
//            int[] dimensions = floatData.dimensions;
//            int length = floatData.length;
//            CuDNN.CudaResource<cudnnTensorDescriptor> fromFormat = CuDNN.newTensorDescriptor(
//                    CUDNN_DATA_FLOAT, CUDNN_TENSOR_NCHW, length, dimensions[2], dimensions[1], dimensions[0]);
//            CuDNN.CudaResource<cudnnTensorDescriptor> toFormat = CuDNN.newTensorDescriptor(
//                    CUDNN_DATA_DOUBLE, CUDNN_TENSOR_NCHW, length, dimensions[2], dimensions[1], dimensions[0]);
//            CuDNN.CudaPtr destPtr = CuDNN.alloc(Sizeof.DOUBLE * length * Tensor.dim(dimensions[2], dimensions[1], dimensions[0]));
//            CuDNN.devicePool.mapCoords(cudnn->{
//                cudnnTransformTensor(cudnn, );
//            });
//            return destPtr;
    }
    else {
      int listLength = data.length();
      int elementLength = data.get(0).dim();
      double[][] inputBuffers = data.stream().map(x -> x.getData()).toArray(i -> new double[i][]);
      final double[] inputBuffer = Tensor.obtain(elementLength * listLength);
      for (int i = 0; i < listLength; i++) {
        assert elementLength == inputBuffers[0 + i].length;
        System.arraycopy(inputBuffers[0 + i], 0, inputBuffer, i * elementLength, elementLength);
      }
      //assert(0 < inputBuffer.length);
      CudaPtr ptr = CuDNN.write(deviceId, inputBuffer);
      Tensor.recycle(inputBuffer);
      return ptr;
    }
  }
  
  /**
   * To device as float cu dnn . cu dnn ptr.
   *
   * @param deviceId the device id
   * @param data     the data
   * @return the cu dnn . cu dnn ptr
   */
  public static CudaPtr toDeviceAsFloat(int deviceId, TensorList data) {
    if (data instanceof CuDNNFloatTensorList) {
      return ((CuDNNFloatTensorList) data).ptr;
//        } else if(data instanceof CuDNNDoubleTensorList) {
//            return ((CuDNNDoubleTensorList)data).ptr;
    }
    else {
      int listLength = data.length();
      int elementLength = data.get(0).dim();
      float[][] inputBuffers = data.stream().map(x -> x.getDataAsFloats()).toArray(i -> new float[i][]);
      final float[] inputBuffer = new float[elementLength * listLength];
      for (int i = 0; i < listLength; i++) {
        assert elementLength == inputBuffers[0 + i].length;
        System.arraycopy(inputBuffers[0 + i], 0, inputBuffer, i * elementLength, elementLength);
      }
      assert (0 < inputBuffer.length);
      //assert isNontrivial(inputBuffer);
      CudaPtr ptr = CuDNN.write(deviceId, inputBuffer);
      return ptr;
    }
  }
  
  /**
   * Is nontrivial boolean.
   *
   * @param data the data
   * @return the boolean
   */
  public static boolean isNontrivial(float[] data) {
    for (int i = 0; i < data.length; i++) if (!Double.isFinite(data[i])) return false;
    for (int i = 0; i < data.length; i++) if (data[i] != 0) return true;
    return false;
  }
  
  /**
   * Is nontrivial boolean.
   *
   * @param data the data
   * @return the boolean
   */
  public static boolean isNontrivial(double[] data) {
    for (int i = 0; i < data.length; i++) if (!Double.isFinite(data[i])) return false;
    for (int i = 0; i < data.length; i++) if (data[i] != 0) return true;
    return false;
  }
  
  /**
   * From device float tensor.
   *
   * @param filterData the filter data
   * @param dimensions the dimensions
   * @return the tensor
   */
  public static Tensor fromDeviceFloat(CudaPtr filterData, int[] dimensions) {
    final Tensor weightGradient = new Tensor(dimensions);
    int length = weightGradient.dim();
    float[] data = new float[length];
    filterData.read(data);
    double[] doubles = weightGradient.getData();
    for (int i = 0; i < length; i++) doubles[i] = data[i];
    return weightGradient;
  }
  
  /**
   * From device double tensor.
   *
   * @param filterData the filter data
   * @param dimensions the dimensions
   * @return the tensor
   */
  public static Tensor fromDeviceDouble(CudaPtr filterData, int[] dimensions) {
    final Tensor weightGradient = new Tensor(dimensions);
    filterData.read(weightGradient.getData());
    return weightGradient;
  }
  
  /**
   * Free.
   *
   * @param data the data
   */
  public static void free(TensorList data) {
    if (data instanceof CuDNNFloatTensorList) {
      ((CuDNNFloatTensorList) data).ptr.finalize();
    }
    else if (data instanceof CuDNNDoubleTensorList) {
      ((CuDNNDoubleTensorList) data).ptr.finalize();
    }
  }
  
  private static final boolean lockPci = Boolean.parseBoolean(System.getProperty("lockPci", "true"));

  private static Object getPciBusLock() {
    return lockPci ? pciBusLock : new Object();
  }
  
  private static void setPciBusLock(Object pciBusLock) {
    CudaPtr.pciBusLock = pciBusLock;
  }
  
  /**
   * The type Gpu stats.
   */
  public static class GpuStats {
    /**
     * The Used memory.
     */
    public final AtomicLong usedMemory = new AtomicLong(0);
    /**
     * The Peak memory.
     */
    public final AtomicLong peakMemory = new AtomicLong(0);
    /**
     * The Memory writes.
     */
    public final AtomicLong memoryWrites = new AtomicLong(0);
    /**
     * The Memory reads.
     */
    public final AtomicLong memoryReads = new AtomicLong(0);
  }


  private static final long MAX = 4l * 1024 * 1024 * 1024;
  /**
   * The constant METRICS.
   */
  public static final LoadingCache<Integer, GpuStats> METRICS = CacheBuilder.newBuilder().build(new CacheLoader<Integer, GpuStats>() {
    @Override
    public GpuStats load(Integer integer) throws Exception {
      return new GpuStats();
    }
  });
  
  /**
   * The Size.
   */
  public final long size;
  private final int deviceId;
  
  /**
   * Instantiates a new Cu dnn ptr.
   *
   * @param size     the size
   * @param deviceId the device id
   */
  protected CudaPtr(long size, int deviceId) {
    super(new Pointer(), JCuda::cudaFree);
    this.size = size;
    this.deviceId = deviceId;
    GpuStats metrics = getGpuStats(deviceId);
    if (size < 0) {
      throw new OutOfMemoryError("Allocated block is too large: " + size);
    }
    if (size > MAX) {
      throw new OutOfMemoryError("Allocated block is too large: " + size);
    }
    try {
      CuDNN.handle(cudaMalloc(this.getPtr(), size));
    } catch (Exception e) {
      try {
        long startMemory = metrics.usedMemory.get();
        System.gc(); // Force any dead objects to be finalized
        System.runFinalization();
        long freedMemory = startMemory - metrics.usedMemory.get();
        CuDNN.handle(cudaMalloc(this.getPtr(), size));
        System.err.println(String.format("Low GPU Memory while allocating %s bytes; %s freed resulting in %s total (triggered by %s)",
          size, freedMemory, metrics.usedMemory.get() + size, e.getMessage()));
      } catch (Exception e2) {
        throw new com.simiacryptus.mindseye.lang.OutOfMemoryError(String.format("Error allocating %s bytes; %s currently allocated to device %s", size, metrics.usedMemory.get(), deviceId), e2);
      }
    }
    long finalMemory = metrics.usedMemory.addAndGet(size);
    metrics.peakMemory.updateAndGet(l -> Math.max(finalMemory, l));
    CuDNN.handle(cudaMemset(this.getPtr(), 0, size));
  }

  private GpuStats getGpuStats(int deviceId) {
    GpuStats devivceMemCtr;
    try {
      devivceMemCtr = METRICS.get(deviceId);
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
    return devivceMemCtr;
  }

  @Override
  protected void free() {
    super.free();
    getGpuStats(deviceId).usedMemory.addAndGet(-size);
  }
  
  /**
   * Instantiates a new Cu dnn ptr.
   *
   * @param ptr      the ptr
   * @param size     the size
   * @param deviceId the device id
   */
  protected CudaPtr(Pointer ptr, long size, int deviceId) {
    super(ptr, x -> 0);
    this.size = size;
    this.deviceId = deviceId;
  }
  
  /**
   * Copy cuda ptr.
   *
   * @return the cuda ptr
   */
  public CudaPtr copy() {
    CudaPtr copy = new CudaPtr(size, deviceId);
    CuDNN.handle(cudaMemcpy(getPtr(), copy.getPtr(), size, cudaMemcpyDeviceToDevice));
    return copy;
  }
  
  private static Object pciBusLock = new Object();
  
  /**
   * Write cu dnn ptr.
   *
   * @param data the data
   * @return the cu dnn ptr
   */
  public CudaPtr write(float[] data) {
    synchronized (getPciBusLock()) {
      if (this.size != data.length * Sizeof.FLOAT) throw new IllegalArgumentException();
      CuDNN.handle(cudaMemcpy(getPtr(), Pointer.to(data), size, cudaMemcpyHostToDevice));
      getGpuStats(deviceId).memoryWrites.addAndGet(size);
      return this;
    }
  }
  
  /**
   * Write cu dnn ptr.
   *
   * @param data the data
   * @return the cu dnn ptr
   */
  public CudaPtr write(double[] data) {
    synchronized (getPciBusLock()) {
      if (this.size != data.length * Sizeof.DOUBLE) throw new IllegalArgumentException();
      CuDNN.handle(cudaMemcpy(getPtr(), Pointer.to(data), size, cudaMemcpyHostToDevice));
      getGpuStats(deviceId).memoryWrites.addAndGet(size);
      return this;
    }
  }
  
  /**
   * Read cu dnn ptr.
   *
   * @param data the data
   * @return the cu dnn ptr
   */
  public CudaPtr read(double[] data) {
    synchronized (getPciBusLock()) {
      if (this.size != data.length * Sizeof.DOUBLE) {
        throw new IllegalArgumentException(this.size + " != " + data.length * Sizeof.DOUBLE);
      }
      CuDNN.handle(cudaMemcpy(Pointer.to(data), getPtr(), size, cudaMemcpyDeviceToHost));
      getGpuStats(deviceId).memoryReads.addAndGet(size);
      return this;
    }
  }
  
  /**
   * Read cu dnn ptr.
   *
   * @param data the data
   * @return the cu dnn ptr
   */
  public CudaPtr read(float[] data) {
    synchronized (getPciBusLock()) {
      if (this.size != data.length * 1l * Sizeof.FLOAT) throw new IllegalArgumentException(this.size + " != " + (data.length * 1l * Sizeof.FLOAT));
      CuDNN.handle(cudaMemcpy(Pointer.to(data), getPtr(), size, cudaMemcpyDeviceToHost));
      getGpuStats(deviceId).memoryReads.addAndGet(size);
      return this;
    }
  }
}