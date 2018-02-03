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

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.lang.cudnn.GpuError;
import com.simiacryptus.mindseye.layers.cudnn.Explodable;
import com.simiacryptus.mindseye.network.DAGNetwork;
import com.simiacryptus.mindseye.test.NotebookReportBase;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.mindseye.test.ToleranceStatistics;
import com.simiacryptus.util.io.NotebookOutput;
import com.simiacryptus.util.test.SysOutInterceptor;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The type Layer run base.
 */
public abstract class StandardLayerTests extends NotebookReportBase {
  
  /**
   * The constant seed.
   */
  public static final long seed = 51389; //System.nanoTime();
  
  static {
    SysOutInterceptor.INSTANCE.init();
  }
  
  private final Random random = getRandom();
  /**
   * The Validate batch execution.
   */
  protected boolean validateBatchExecution = true;
  /**
   * The Validate differentials.
   */
  protected boolean validateDifferentials = true;
  private ArrayList<ComponentTest<?>> finalTests;
  private ArrayList<ComponentTest<?>> bigTests;
  private ArrayList<ComponentTest<?>> littleTests;
  private boolean testTraining = false;
  
  /**
   * Instantiates a new Standard layer tests.
   */
  public StandardLayerTests() {
    logger.info("Seed: " + seed);
  }
  
  /**
   * Gets batching tester.
   *
   * @return the batching tester
   */
  public ComponentTest<ToleranceStatistics> getBatchingTester() {
    if (!validateBatchExecution) return null;
    return new BatchingTester(1e-2) {
      @Override
      public double getRandom() {
        return random();
      }
    };
  }
  
  /**
   * Gets big tests.
   *
   * @return the big tests
   */
  public ArrayList<ComponentTest<?>> getBigTests() {
    if (null == bigTests) {
      synchronized (this) {
        if (null == bigTests) {
          bigTests = new ArrayList<>(Arrays.asList(
            getPerformanceTester(),
            getBatchingTester(),
            getReferenceIOTester(),
            getEquivalencyTester()
                                                  ));
        }
      }
    }
    return bigTests;
  }
  
  /**
   * Gets big tests.
   *
   * @return the big tests
   */
  public ArrayList<ComponentTest<?>> getFinalTests() {
    if (null == finalTests) {
      synchronized (this) {
        if (null == finalTests) {
          finalTests = new ArrayList<>(Arrays.asList(
            getTrainingTester()
                                                    ));
        }
      }
    }
    return finalTests;
  }
  
  /**
   * Gets derivative tester.
   *
   * @return the derivative tester
   */
  public ComponentTest<ToleranceStatistics> getDerivativeTester() {
    if (!validateDifferentials) return null;
    return new SingleDerivativeTester(1e-3, 1e-4);
  }
  
  /**
   * Gets equivalency tester.
   *
   * @return the equivalency tester
   */
  public ComponentTest<ToleranceStatistics> getEquivalencyTester() {
    final NNLayer referenceLayer = getReferenceLayer();
    if (null == referenceLayer) return null;
    return new EquivalencyTester(1e-2, referenceLayer);
  }
  
  /**
   * Get input dims int [ ] [ ].
   *
   * @param random the random
   * @return the int [ ] [ ]
   */
  public abstract int[][] getSmallDims(Random random);
  
  /**
   * Gets json tester.
   *
   * @return the json tester
   */
  protected ComponentTest<ToleranceStatistics> getJsonTester() {
    return new SerializationTest();
  }
  
  /**
   * Gets layer.
   *
   * @param inputSize the input size
   * @param random    the random
   * @return the layer
   */
  public abstract NNLayer getLayer(int[][] inputSize, Random random);
  
  /**
   * Gets little tests.
   *
   * @return the little tests
   */
  public ArrayList<ComponentTest<?>> getLittleTests() {
    if (null == littleTests) {
      synchronized (this) {
        if (null == littleTests) {
          littleTests = new ArrayList<>(Arrays.asList(
            getJsonTester(),
            getDerivativeTester()
                                                     ));
        }
      }
    }
    return littleTests;
  }
  
  /**
   * Get perf dims int [ ] [ ].
   *
   * @param random the random
   * @return the int [ ] [ ]
   */
  public int[][] getLargeDims(Random random) {
    return getSmallDims(new Random());
  }
  
  /**
   * Gets reference io.
   *
   * @return the reference io
   */
  protected HashMap<Tensor[], Tensor> getReferenceIO() {
    return new HashMap<>();
  }
  
  /**
   * Gets performance tester.
   *
   * @return the performance tester
   */
  public ComponentTest<ToleranceStatistics> getPerformanceTester() {
    return new PerformanceTester();
  }
  
  /**
   * Gets reference io tester.
   *
   * @return the reference io tester
   */
  protected ComponentTest<ToleranceStatistics> getReferenceIOTester() {
    return new ReferenceIO(getReferenceIO());
  }
  
  /**
   * Gets reference layer.
   *
   * @return the reference layer
   */
  public NNLayer getReferenceLayer() {
    return cvt(getLayer(getSmallDims(new Random()), new Random()));
  }
  
  /**
   * Gets test class.
   *
   * @return the test class
   */
  public Class<? extends NNLayer> getTestClass() {
    return getLayer(getSmallDims(new Random()), new Random()).getClass();
  }
  
  /**
   * Cvt nn layer.
   *
   * @param layer the layer
   * @return the nn layer
   */
  protected NNLayer cvt(NNLayer layer) {
    if (layer instanceof DAGNetwork) {
      ((DAGNetwork) layer).visitNodes(node -> {
        NNLayer from = node.getLayer();
        node.setLayer(cvt(from));
      });
      return layer;
    }
    else if (getTestClass().isAssignableFrom(layer.getClass())) {
      Class<? extends NNLayer> referenceLayerClass = getReferenceLayerClass();
      if (null == referenceLayerClass) return null;
      return layer.as(referenceLayerClass);
    }
    else {
      return layer;
    }
  }
  
  /**
   * Gets reference layer class.
   *
   * @return the reference layer class
   */
  public Class<? extends NNLayer> getReferenceLayerClass() {
    return null;
  }
  
  /**
   * Gets learning tester.
   *
   * @return the learning tester
   */
  public ComponentTest<TrainingTester.ComponentResult> getTrainingTester() {
    return isTestTraining() ? new TrainingTester() : null;
  }
  
  /**
   * Random double.
   *
   * @return the double
   */
  public double random() {
    return random(random);
  }
  
  /**
   * Random double.
   *
   * @param random the random
   * @return the double
   */
  public double random(Random random) {
    return Math.round(1000.0 * (random.nextDouble() - 0.5)) / 250.0;
  }
  
  /**
   * Random tensor [ ].
   *
   * @param inputDims the input dims
   * @return the tensor [ ]
   */
  public Tensor[] randomize(final int[][] inputDims) {
    return Arrays.stream(inputDims).map(dim -> new Tensor(dim).set(() -> random())).toArray(i -> new Tensor[i]);
  }
  
  
  /**
   * Test.
   *
   * @param log the log
   */
  public void run(final NotebookOutput log) {
    long seed = (long) (Math.random() * Long.MAX_VALUE);
    int[][] smallDims = getSmallDims(new Random(seed));
    final NNLayer smallLayer = getLayer(smallDims, new Random(seed));
    int[][] largeDims = getLargeDims(new Random(seed));
    final NNLayer largeLayer = getLayer(largeDims, new Random(seed));
    if (smallLayer instanceof DAGNetwork) {
      try {
        log.h1("Network Diagram");
        log.p("This is a network with the following layout:");
        log.code(() -> {
          return Graphviz.fromGraph(TestUtil.toGraph((DAGNetwork) smallLayer))
                         .height(400).width(600).render(Format.PNG).toImage();
        });
      } catch (Throwable e) {
        logger.info("Error plotting graph", e);
      }
    }
    else if (smallLayer instanceof Explodable) {
      try {
        NNLayer explode = ((Explodable) smallLayer).explode();
        if (explode instanceof DAGNetwork) {
          log.h1("Exploded Network Diagram");
          log.p("This is a network with the following layout:");
          DAGNetwork network = (DAGNetwork) explode;
          log.code(() -> {
            Graphviz graphviz = Graphviz.fromGraph(TestUtil.toGraph(network)).height(400).width(600);
            File file = new File(log.getResourceDir(), log.getName() + "_network.svg");
            graphviz.render(Format.SVG_STANDALONE).toFile(file);
            log.link(file, "Saved to File");
            return graphviz.render(Format.SVG).toString();
          });
        }
      } catch (Throwable e) {
        logger.info("Error plotting graph", e);
      }
    }
    ArrayList<TestError> exceptions = standardTests(log, seed);
    if (!exceptions.isEmpty()) {
      if (smallLayer instanceof DAGNetwork) {
        for (Invocation invocation : getInvocations(smallLayer, smallDims)) {
          log.h2("Small SubTest: " + invocation.getLayer().getClass().getSimpleName());
          littleTests(log, exceptions, invocation);
        }
      }
      if (largeLayer instanceof DAGNetwork) {
        for (Invocation invocation : getInvocations(largeLayer, largeDims)) {
          log.h2("Large SubTest: " + invocation.getLayer().getClass().getSimpleName());
          bigTests(log, exceptions, invocation);
        }
      }
    }
    log.code(() -> {
      throwException(exceptions);
    });
    getFinalTests().stream().filter(x -> null != x).forEach(test -> {
      final NNLayer perfLayer = getLayer(largeDims, new Random(seed));
      test.test(log, perfLayer.copy(), randomize(largeDims));
    });
  }
  
  public Collection<Invocation> getInvocations(NNLayer smallLayer, int[][] smallDims) {
    DAGNetwork smallCopy = (DAGNetwork) smallLayer.copy();
    HashSet<Invocation> invocations = new HashSet<>();
    smallCopy.visitNodes(node -> {
      NNLayer inner = node.getLayer();
      node.setLayer(new NNLayer() {
        @Override
        public NNResult eval(NNResult... array) {
          if (null == inner) return null;
          NNResult result = inner.eval(array);
          invocations.add(new Invocation(inner, Arrays.stream(array).map(x -> x.getData().getDimensions()).toArray(i -> new int[i][])));
          return result;
        }
        
        @Override
        public JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
          return inner.getJson(resources, dataSerializer);
        }
        
        @Override
        public List<double[]> state() {
          return inner.state();
        }
      });
    });
    smallCopy.eval(Arrays.stream(smallDims).map(i -> new Tensor(i)).<Tensor>toArray(i -> new Tensor[i]));
    return invocations;
  }
  
  /**
   * Monte carlo.
   *
   * @param log the log
   */
  public void monteCarlo(final NotebookOutput log) {
    long timeout = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(3);
    while (System.currentTimeMillis() < timeout) {
      long seed = (long) (Math.random() * Long.MAX_VALUE);
      final NNLayer layer = getLayer(getSmallDims(new Random(seed)), new Random(seed));
      throwException(standardTests(log, seed));
    }
  }
  
  /**
   * Throw exception.
   *
   * @param exceptions the exceptions
   */
  public void throwException(ArrayList<TestError> exceptions) {
    for (TestError exception : exceptions) {
      logger.info(String.format("Layer: %s", exception.layer));
      logger.info("Error", exception);
    }
    for (Throwable exception : exceptions) {
      throw new RuntimeException(exception);
    }
  }
  
  /**
   * Standard tests array list.
   *
   * @param log  the log
   * @param seed the seed
   * @return the array list
   */
  public ArrayList<TestError> standardTests(NotebookOutput log, long seed) {
    final NNLayer layer = getLayer(getSmallDims(new Random(seed)), new Random(seed));
    ArrayList<TestError> exceptions = new ArrayList<>();
    log.p(String.format("Using Seed %d", seed));
    littleTests(log, exceptions, new Invocation(layer, getSmallDims(new Random(seed))));
    final NNLayer perfLayer = getLayer(getLargeDims(new Random(seed)), new Random(seed));
    bigTests(log, seed, perfLayer, exceptions);
    log.code(() -> {
      System.gc();
      ReferenceCountingBase.logFreeWarnings();
    });
    return exceptions;
  }
  
  public void bigTests(NotebookOutput log, long seed, NNLayer perfLayer, ArrayList<TestError> exceptions) {
    getBigTests().stream().filter(x -> null != x).forEach(test -> {
      NNLayer layer = perfLayer.copy();
      try {
        Tensor[] input = randomize(getLargeDims(new Random(seed)));
        test.test(log, layer, input);
        layer.freeRef();
        for (Tensor t : input) {
          t.freeRef();
        }
      } catch (LifecycleException e) {
        throw e;
      } catch (GpuError e) {
        throw e;
      } catch (Throwable e) {
        exceptions.add(new TestError(e, test, layer));
      }
    });
  }
  
  public void bigTests(NotebookOutput log, ArrayList<TestError> exceptions, Invocation invocation) {
    getBigTests().stream().filter(x -> null != x).forEach((ComponentTest<?> test) -> {
      NNLayer layer = invocation.getLayer().copy();
      try {
        test.test(log, layer, randomize(invocation.getDims()));
      } catch (LifecycleException e) {
        throw e;
      } catch (Throwable e) {
        exceptions.add(new TestError(e, test, layer));
      }
    });
  }
  
  public void littleTests(NotebookOutput log, ArrayList<TestError> exceptions, Invocation invocation) {
    getLittleTests().stream().filter(x -> null != x).forEach((ComponentTest<?> test) -> {
      NNLayer layer = invocation.getLayer().copy();
      try {
        Tensor[] inputs = randomize(invocation.getDims());
        test.test(log, layer, inputs);
        layer.freeRef();
        for (Tensor tensor : inputs) tensor.freeRef();
      } catch (LifecycleException e) {
        throw e;
      } catch (Throwable e) {
        exceptions.add(new TestError(e, test, layer));
      }
    });
  }
  
  /**
   * With big tests standard layer tests.
   *
   * @param fn the fn
   * @return the standard layer tests
   */
  public StandardLayerTests withBigTests(final Consumer<ArrayList<ComponentTest<?>>> fn) {
    fn.accept(getBigTests());
    return this;
  }
  
  /**
   * With little tests standard layer tests.
   *
   * @param fn the fn
   * @return the standard layer tests
   */
  public StandardLayerTests withLittleTests(final Consumer<ArrayList<ComponentTest<?>>> fn) {
    fn.accept(getLittleTests());
    return this;
  }
  
  @Override
  protected Class<?> getTargetClass() {
    NNLayer layer = getLayer(getSmallDims(new Random()), new Random());
    Class<? extends NNLayer> layerClass = layer.getClass();
    layer.freeRef();
    return layerClass;
  }
  
  @Override
  public ReportType getReportType() {
    return ReportType.Components;
  }
  
  /**
   * Is test training boolean.
   *
   * @return the boolean
   */
  public boolean isTestTraining() {
    return testTraining;
  }
  
  /**
   * Sets test training.
   *
   * @param testTraining the test training
   * @return the test training
   */
  public StandardLayerTests setTestTraining(boolean testTraining) {
    this.testTraining = testTraining;
    return this;
  }
  
  /**
   * Gets random.
   *
   * @return the random
   */
  public Random getRandom() {
    return new Random(seed);
  }
  
  private static class Invocation {
    private final NNLayer layer;
    private final int[][] smallDims;
    
    private Invocation(NNLayer layer, int[][] smallDims) {
      this.layer = layer;
      this.smallDims = smallDims;
    }
    
    public NNLayer getLayer() {
      return layer;
    }
    
    public int[][] getDims() {
      return smallDims;
    }
    
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Invocation)) return false;
      
      Invocation that = (Invocation) o;
      
      if (layer != null ? !layer.getClass().equals(that.layer.getClass()) : that.layer != null) return false;
      return Arrays.deepEquals(smallDims, that.smallDims);
    }
    
    @Override
    public int hashCode() {
      int result = layer != null ? layer.getClass().hashCode() : 0;
      result = 31 * result + Arrays.deepHashCode(smallDims);
      return result;
    }
  }
}
