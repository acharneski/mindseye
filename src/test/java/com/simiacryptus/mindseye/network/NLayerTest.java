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

package com.simiacryptus.mindseye.network;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.mindseye.test.unit.JsonTest;
import com.simiacryptus.mindseye.test.unit.StandardLayerTests;
import com.simiacryptus.mindseye.test.unit.TrainingTester;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.MarkdownNotebookOutput;
import com.simiacryptus.util.io.NotebookOutput;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;


/**
 * The type N layer test.
 */
public abstract class NLayerTest {
  
  /**
   * The Dim list.
   */
  final List<int[]> dimList;
  
  
  /**
   * Instantiates a new N layer test.
   *
   * @param dimList the dim list
   */
  public NLayerTest(final int[]... dimList) {
    this.dimList = Arrays.asList(dimList);
  }
  
  /**
   * Add layer.
   *
   * @param network the network
   * @param in      the in
   * @param out     the dims
   */
  public abstract void addLayer(PipelineNetwork network, int[] in, int[] out);
  
  /**
   * Build network nn layer.
   *
   * @param dimList the dim list
   * @return the nn layer
   */
  public NNLayer buildNetwork(final int[]... dimList) {
    final PipelineNetwork network = new PipelineNetwork(1);
    int[] last = null;
    for (final int[] dims : dimList) {
      if (null != last) {
        addLayer(network, last, dims);
      }
      last = dims;
    }
    return network;
  }
  
  /**
   * Concat int [ ] [ ].
   *
   * @param a the a
   * @param b the b
   * @return the int [ ] [ ]
   */
  public int[][] concat(final int[] a, final List<int[]> b) {
    return Stream.concat(Stream.of(a), b.stream()).toArray(i -> new int[i][]);
  }
  
  /**
   * Get input dims int [ ] [ ].
   *
   * @return the int [ ] [ ]
   */
  public abstract int[] getInputDims();
  
  /**
   * Graphviz.
   *
   * @param log   the log
   * @param layer the layer
   */
  public void graphviz(final NotebookOutput log, final NNLayer layer) {
    if (layer instanceof DAGNetwork) {
      log.p("This is a network with the following layout:");
      log.code(() -> {
        return Graphviz.fromGraph(TestUtil.toGraph((DAGNetwork) layer))
          .height(400).width(600).render(Format.PNG).toImage();
      });
    }
  }
  
  /**
   * Random double.
   *
   * @return the double
   */
  public double random() {
    return Math.round(1000.0 * (Util.R.get().nextDouble() - 0.5)) / 250.0;
  }
  
  /**
   * Random tensor [ ].
   *
   * @param inputDims the input dims
   * @return the tensor [ ]
   */
  public Tensor[] randomize(final int[][] inputDims) {
    return Arrays.stream(inputDims).map(dim -> new Tensor(dim).set(this::random)).toArray(i -> new Tensor[i]);
  }
  
  /**
   * Test.
   *
   * @throws Throwable the throwable
   */
  @Test
  public void test() throws Throwable {
    try (NotebookOutput log = MarkdownNotebookOutput.get(this)) {
      test(log);
    }
  }
  
  /**
   * Test.
   *
   * @param log the log
   */
  public void test(final NotebookOutput log) {
    if (null != StandardLayerTests.originalOut) {
      log.addCopy(StandardLayerTests.originalOut);
    }
    log.h1("%s", getClass().getSimpleName());
    final int[] inputDims = getInputDims();
    final ArrayList<int[]> workingSpec = new ArrayList<>();
    for (final int[] l : dimList) {
      workingSpec.add(l);
      final NNLayer layer = buildNetwork(concat(inputDims, workingSpec));
      graphviz(log, layer);
      test(log, layer, inputDims);
    }
  }
  
  /**
   * Test double.
   *
   * @param log       the log
   * @param layer     the layer
   * @param inputDims the input dims
   * @return the double
   */
  public TrainingTester.ComponentResult test(final NotebookOutput log, final NNLayer layer, final int[]... inputDims) {
    final NNLayer component = layer.copy();
    final Tensor[] randomize = randomize(inputDims);
    new JsonTest().test(log, component, randomize);
    return new TrainingTester().test(log, component, randomize);
  }
  
}