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

package com.simiacryptus.mindseye.opt.trainable;

import com.simiacryptus.mindseye.data.Tensor;
import com.simiacryptus.mindseye.data.TensorList;
import com.simiacryptus.mindseye.layers.DeltaSet;
import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.layers.NNResult;
import com.simiacryptus.mindseye.layers.cudnn.CudaExecutionContext;
import com.simiacryptus.mindseye.layers.cudnn.GpuController;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.rdd.RDD;
import org.apache.spark.storage.StorageLevel;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The type Spark trainable.
 */
public class SparkTrainable implements Trainable {
  
  private final int sampleSize;
  private int partitions;
  
  public StorageLevel getStorageLevel() {
    return storageLevel;
  }
  
  public CachedTrainable<SparkTrainable> cached() {
    setStorageLevel(StorageLevel.MEMORY_AND_DISK());
    return new CachedTrainable<SparkTrainable>(this);
  }
  
  public SparkTrainable setStorageLevel(StorageLevel storageLevel) {
    this.storageLevel = storageLevel;
    this.sampledRDD.persist(storageLevel);
    return this;
  }
  
  public int getPartitions() {
    return partitions;
  }
  
  public SparkTrainable setPartitions(int partitions) {
    this.partitions = partitions;
    return this;
  }
  
  private static class ReducableResult implements Serializable {
    /**
     * The Deltas.
     */
    public final Map<String, double[]> deltas;
    /**
     * The Sum.
     */
    public final double sum;
    
    /**
     * Instantiates a new Reducable result.
     *  @param deltas the deltas
     * @param sum    the sum
     */
    public ReducableResult(Map<String, double[]> deltas, double sum) {
      this.deltas = deltas;
      this.sum = sum;
    }
    
    /**
     * Accumulate.
     *
     * @param source the source
     */
    public void accumulate(DeltaSet source) {
      Map<String, NNLayer> idIndex = source.map.entrySet().stream().collect(Collectors.toMap(
        e -> e.getKey().id.toString(), e -> e.getKey()
      ));
      deltas.forEach((k, v) -> source.get(idIndex.get(k), (double[]) null).accumulate(v));
    }
    
    /**
     * Add spark trainable . reducable result.
     *
     * @param right the right
     * @return the spark trainable . reducable result
     */
    public SparkTrainable.ReducableResult add(SparkTrainable.ReducableResult right) {
      HashMap<String, double[]> map = new HashMap<>();
      Set<String> keys = Stream.concat(deltas.keySet().stream(), right.deltas.keySet().stream()).collect(Collectors.toSet());
      for (String key : keys) {
        double[] l = deltas.get(key);
        double[] r = right.deltas.get(key);
        if (null != r) {
          if (null != l) {
            assert (l.length == r.length);
            double[] x = new double[l.length];
            for (int i = 0; i < l.length; i++) x[i] = l[i] + r[i];
            map.put(key, x);
          }
          else {
            map.put(key, r);
          }
        }
        else {
          assert (null != l);
          map.put(key, l);
        }
      }
      return new SparkTrainable.ReducableResult(map, sum + right.sum);
    }
  
  }
  
  private static void debug(String msg, Object... args) {
    String format = String.format(msg, args);
    System.out.println(format);
  }
  
  private static class PartitionTask implements FlatMapFunction<Iterator<Tensor[]>, SparkTrainable.ReducableResult> {
    /**
     * The Network.
     */
    final NNLayer network;
    boolean verbose = true;
    
    private PartitionTask(NNLayer network) {
      this.network = network;
    }
    
    @Override
    public Iterator<SparkTrainable.ReducableResult> call(Iterator<Tensor[]> partition) throws Exception {
      GpuTrainable trainable = new GpuTrainable(network);
      Tensor[][] tensors = SparkTrainable.getStream(partition).toArray(i -> new Tensor[i][]);
      if(verbose) debug("Materialized %s records", tensors.length);
      PointSample measure = trainable.setData(Arrays.asList(tensors)).measure();
      return Arrays.asList(SparkTrainable.getResult(measure.delta, new double[]{measure.value})).iterator();
    }
  }
  
  private static SparkTrainable.ReducableResult getResult(DeltaSet delta, double[] values) {
    Map<String, double[]> deltas = delta.map.entrySet().stream().collect(Collectors.toMap(
      e -> e.getKey().id.toString(), e -> e.getValue().getDelta()
    ));
    return new SparkTrainable.ReducableResult(deltas, Arrays.stream(values).sum());
  }
  protected PointSample eval(NNResult[] input, CudaExecutionContext nncontext) {
    NNResult result = network.eval(nncontext, input);
    DeltaSet deltaSet = new DeltaSet();
    result.accumulate(deltaSet);
    assert (deltaSet.vector().stream().allMatch(x -> Arrays.stream(x.getDelta()).allMatch(Double::isFinite)));
    DeltaSet stateBackup = new DeltaSet();
    deltaSet.map.forEach((layer, layerDelta) -> {
      stateBackup.get(layer, layerDelta.target).accumulate(layerDelta.target);
    });
    assert (stateBackup.vector().stream().allMatch(x -> Arrays.stream(x.getDelta()).allMatch(Double::isFinite)));
    TensorList resultData = result.getData();
    assert (resultData.stream().allMatch(x -> x.dim() == 1));
    assert (resultData.stream().allMatch(x -> Arrays.stream(x.getData()).allMatch(Double::isFinite)));
    double sum = resultData.stream().mapToDouble(x -> Arrays.stream(x.getData()).sum()).sum();
    return new PointSample(deltaSet, stateBackup, sum);
  }
  
  
  private DeltaSet getDelta(SparkTrainable.ReducableResult reduce) {
    DeltaSet deltaSet = new DeltaSet();
    Tensor[] prototype = dataRDD.toJavaRDD().take(1).get(0);
    NNResult result = CudaExecutionContext.gpuContexts.map(exe->network.eval(exe, NNResult.batchResultArray(new Tensor[][]{prototype})));
    result.accumulate(deltaSet, 0);
    reduce.accumulate(deltaSet);
    return deltaSet;
  }
  
  private final RDD<Tensor[]> dataRDD;
  private RDD<Tensor[]> sampledRDD;
  private final NNLayer network;
  
  /**
   * Instantiates a new Spark trainable.
   *
   * @param trainingData the training data
   * @param network      the network
   */
  public SparkTrainable(RDD<Tensor[]> trainingData, NNLayer network) {
    this(trainingData, network, -1);
  }
  
  public SparkTrainable(RDD<Tensor[]> trainingData, NNLayer network, int sampleSize) {
    this.dataRDD = trainingData;
    this.network = network;
    this.sampleSize = sampleSize;
    this.setPartitions(dataRDD.sparkContext().executorEnvs().size());
    resetSampling();
  }
  
  @Override
  public Trainable.PointSample measure() {
    SparkTrainable.ReducableResult result = this.sampledRDD.toJavaRDD().mapPartitions(new PartitionTask(network))
                                              .reduce(SparkTrainable.ReducableResult::add);
    DeltaSet deltaSet = getDelta(result);
    DeltaSet stateSet = new DeltaSet();
    deltaSet.map.forEach((layer, layerDelta) -> {
      stateSet.get(layer, layerDelta.target).accumulate(layerDelta.target);
    });
    return new Trainable.PointSample(deltaSet, stateSet, result.sum);
  }
  
  @Override
  public boolean resetSampling() {
    if(this.sampleSize > 0) {
      long count = dataRDD.count();
      if(this.sampleSize < count) {
        this.sampledRDD = dataRDD.sample(false, sampleSize * 1.0 / count, System.currentTimeMillis())
          .repartition(getPartitions(), null)
          .persist(getStorageLevel());
        return true;
      }
    }
    return false;
  }
  
  private StorageLevel storageLevel = StorageLevel.MEMORY_AND_DISK();
  
  @Override
  public void resetToFull() {
    this.sampledRDD = this.dataRDD.repartition(dataRDD.sparkContext().executorEnvs().size(), null).persist(getStorageLevel());
  }
  
  private static Stream<Tensor[]> getStream(Iterator<Tensor[]> partition) {
    int characteristics = Spliterator.ORDERED;
    boolean parallel = false;
    Spliterator<Tensor[]> spliterator = Spliterators.spliteratorUnknownSize(partition, characteristics);
    return StreamSupport.stream(spliterator, parallel);
  }
  
  
}
