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

package com.simiacryptus.mindseye.test.unit;

import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.lang.cudnn.*;
import com.simiacryptus.mindseye.test.SimpleGpuEval;
import com.simiacryptus.mindseye.test.SimpleResult;
import com.simiacryptus.mindseye.test.ToleranceStatistics;
import com.simiacryptus.util.io.NotebookOutput;
import jcuda.jcudnn.cudnnTensorFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The type Batching tester.
 */
public class CudaDataTester extends ComponentTestBase<ToleranceStatistics> {
  private static final Logger logger = LoggerFactory.getLogger(CudaDataTester.class);
  
  private final double tolerance;
  private int batchSize = 1;
  
  /**
   * Instantiates a new Batching tester.
   *
   * @param tolerance the tolerance
   */
  public CudaDataTester(final double tolerance) {
    this.tolerance = tolerance;
  }
  
  /**
   * Gets randomize.
   *
   * @return the randomize
   */
  public double getRandom() {
    return 5 * (Math.random() - 0.5);
  }
  
  /**
   * Test tolerance statistics.
   *
   * @param reference      the reference
   * @param inputPrototype the input prototype
   * @return the tolerance statistics
   */
  public ToleranceStatistics test(@Nullable final Layer reference, @javax.annotation.Nonnull final Tensor[] inputPrototype) {
    if (null == reference) return new ToleranceStatistics();
    ToleranceStatistics testInterGpu = testInterGpu(reference, inputPrototype);
    testInterGpu = testInterGpu.combine(testNonstandardBounds(reference, inputPrototype));
    return testInterGpu;
  }
  
  @Nonnull
  public ToleranceStatistics testInterGpu(@Nullable final Layer reference, @Nonnull final Tensor[] inputPrototype) {
    final TensorList[] heapInput = Arrays.stream(inputPrototype).map(t ->
      TensorArray.wrap(IntStream.range(0, getBatchSize()).mapToObj(i -> t.map(v -> getRandom()))
        .toArray(i -> new Tensor[i]))).toArray(i -> new TensorList[i]);
    TensorList[] gpuInput = CudaSystem.eval(gpu -> {
      return Arrays.stream(heapInput).map(original -> {
        return CudaTensorList.wrap(gpu.getTensor(original, Precision.Double, MemoryType.Managed), original.length(), original.getDimensions(), Precision.Double);
      }).toArray(i -> new TensorList[i]);
    }, 0);
    @Nonnull final SimpleResult fromHeap = CudaSystem.eval(gpu -> SimpleGpuEval.run(reference, gpu, heapInput), 1);
    @Nonnull final SimpleResult fromGPU = CudaSystem.eval(gpu -> SimpleGpuEval.run(reference, gpu, gpuInput), 1);
    try {
      ToleranceStatistics compareOutput = compareOutput(fromHeap, fromGPU);
      ToleranceStatistics compareDerivatives = compareDerivatives(fromHeap, fromGPU);
      return compareDerivatives.combine(compareOutput);
    } finally {
      Arrays.stream(gpuInput).forEach(ReferenceCounting::freeRef);
      Arrays.stream(heapInput).forEach(x -> x.freeRef());
      fromGPU.freeRef();
      fromHeap.freeRef();
    }
  }
  
  @Nonnull
  public ToleranceStatistics testNonstandardBounds(@Nullable final Layer reference, @Nonnull final Tensor[] inputPrototype) {
    Tensor[] randomized = Arrays.stream(inputPrototype).map(x -> x.map(v -> getRandom())).toArray(i -> new Tensor[i]);
    Precision precision = Precision.Double;
    final TensorList[] irregularInput = CudaSystem.eval(gpu -> {
      return Arrays.stream(randomized).map(original -> {
        return buildIrregularCudaTensor(gpu, precision, original);
      }).toArray(i -> new TensorList[i]);
    }, 0);
    TensorList[] controlInput = CudaSystem.eval(gpu -> {
      return Arrays.stream(randomized).map(original -> {
        return CudaTensorList.wrap(gpu.getTensor(TensorArray.wrap(randomized), precision, MemoryType.Managed), 1, original.getDimensions(), precision);
      }).toArray(i -> new TensorList[i]);
    }, 0);
    @Nonnull final SimpleResult testResult = CudaSystem.eval(gpu -> SimpleGpuEval.run(reference, gpu, irregularInput), 1);
    @Nonnull final SimpleResult controlResult = CudaSystem.eval(gpu -> SimpleGpuEval.run(reference, gpu, controlInput), 1);
    try {
      ToleranceStatistics compareOutput = compareOutput(controlResult, testResult);
      ToleranceStatistics compareDerivatives = compareDerivatives(controlResult, testResult);
      return compareDerivatives.combine(compareOutput);
    } finally {
      Arrays.stream(controlInput).forEach(ReferenceCounting::freeRef);
      Arrays.stream(irregularInput).forEach(x -> x.freeRef());
      controlResult.freeRef();
      testResult.freeRef();
    }
  }
  
  public CudaTensorList buildIrregularCudaTensor(final CudnnHandle gpu, final Precision precision, final Tensor original) {
    TensorArray data = TensorArray.create(original);
    int[] inputSize = original.getDimensions();
    int channels = inputSize.length < 3 ? 1 : inputSize[2];
    int height = inputSize.length < 2 ? 1 : inputSize[1];
    int width = inputSize.length < 1 ? 1 : inputSize[0];
    final int listLength = 1;
    final int elementLength = data.getElements();
    
    @Nonnull final CudaMemory ptr0 = gpu.allocate((long) elementLength * listLength * precision.size, MemoryType.Managed, false);
    @Nonnull final CudaDevice.CudaTensorDescriptor descriptor0 = gpu.newTensorDescriptor(
      precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, listLength, channels, height, width);
    for (int i = 0; i < listLength; i++) {
      Tensor tensor = data.get(i);
      assert null != data;
      assert null != tensor;
      assert Arrays.equals(tensor.getDimensions(), data.getDimensions()) : Arrays.toString(tensor.getDimensions()) + " != " + Arrays.toString(data.getDimensions());
      ptr0.write(precision, tensor.getData(), (long) i * elementLength);
      tensor.freeRef();
    }
    
    @Nonnull final CudaMemory ptr1 = gpu.allocate((long) (channels + 2) * (height + 2) * (width + 2) * listLength * precision.size, MemoryType.Managed, false);
    @Nonnull final CudaDevice.CudaTensorDescriptor descriptor1 = gpu.newTensorDescriptor(precision.code,
      listLength, channels, height, width,
      (height + 2) * (width + 2) * (channels + 2), (height + 2) * (width + 2), width + 2, 1);
    gpu.cudnnTransformTensor(
      precision.getPointer(1.0), descriptor0.getPtr(), ptr0.getPtr(),
      precision.getPointer(0.0), descriptor1.getPtr(), ptr1.getPtr()
    );
    
    return CudaTensorList.wrap(CudaTensor.wrap(ptr1, descriptor1, precision), 1, original.getDimensions(), precision);
  }
  
  @Nonnull
  public ToleranceStatistics compareDerivatives(final SimpleResult fromHeap, final SimpleResult fromGPU) {
    @Nonnull final ToleranceStatistics derivativeAgreement = IntStream.range(0, getBatchSize()).mapToObj(batch -> {
      @Nonnull IntFunction<ToleranceStatistics> statisticsFunction = input -> {
        Tensor b = fromGPU.getDerivative()[input].get(batch);
        Tensor a = fromHeap.getDerivative()[input].get(batch);
        ToleranceStatistics statistics = new ToleranceStatistics().accumulate(a.getData(), b.getData());
        a.freeRef();
        b.freeRef();
        return statistics;
      };
      return IntStream.range(0, fromHeap.getOutput().length()).mapToObj(statisticsFunction).reduce((a, b) -> a.combine(b)).get();
    }).reduce((a, b) -> a.combine(b)).get();
    if (!(derivativeAgreement.absoluteTol.getMax() < tolerance)) {
      throw new AssertionError("Derivatives Corrupt: " + derivativeAgreement);
    }
    return derivativeAgreement;
  }
  
  @Nonnull
  public ToleranceStatistics compareOutput(final SimpleResult expected, final SimpleResult actual) {
    @Nonnull final ToleranceStatistics outputAgreement = IntStream.range(0, getBatchSize()).mapToObj(batch -> {
        Tensor a = expected.getOutput().get(batch);
        Tensor b = actual.getOutput().get(batch);
        ToleranceStatistics statistics = new ToleranceStatistics().accumulate(a.getData(), b.getData());
        a.freeRef();
        b.freeRef();
        return statistics;
      }
    ).reduce((a, b) -> a.combine(b)).get();
    if (!(outputAgreement.absoluteTol.getMax() < tolerance)) {
      logger.info("Expected Output: " + expected.getOutput().stream().map(x -> {
        String str = x.prettyPrint();
        x.freeRef();
        return str;
      }).collect(Collectors.toList()));
      logger.info("Actual Output: " + actual.getOutput().stream().map(x -> {
        String str = x.prettyPrint();
        x.freeRef();
        return str;
      }).collect(Collectors.toList()));
      throw new AssertionError("Output Corrupt: " + outputAgreement);
    }
    return outputAgreement;
  }
  
  /**
   * Test tolerance statistics.
   *
   * @param log
   * @param reference      the reference
   * @param inputPrototype the input prototype
   * @return the tolerance statistics
   */
  @Override
  public ToleranceStatistics test(@javax.annotation.Nonnull final NotebookOutput log, final Layer reference, @javax.annotation.Nonnull final Tensor... inputPrototype) {
    log.h1("Multi-GPU Compatibility");
    log.p("This layer should be able to eval using a GPU context other than the one used to create the inputs.");
    return log.code(() -> {
      return test(reference, inputPrototype);
    });
  }
  
  /**
   * Gets batch size.
   *
   * @return the batch size
   */
  public int getBatchSize() {
    return batchSize;
  }
  
  /**
   * Sets batch size.
   *
   * @param batchSize the batch size
   * @return the batch size
   */
  @javax.annotation.Nonnull
  public CudaDataTester setBatchSize(int batchSize) {
    this.batchSize = batchSize;
    return this;
  }
  
  @javax.annotation.Nonnull
  @Override
  public String toString() {
    return "CudaDataTester{" +
      "tolerance=" + tolerance +
      ", batchSize=" + batchSize +
      '}';
  }
}
