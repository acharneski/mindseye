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

package com.simiacryptus.mindseye.graph;

import com.simiacryptus.mindseye.graph.dag.DAGNode;
import com.simiacryptus.mindseye.net.NNLayer;

public class SimpleLossNetwork extends SupervisedNetwork {
  
  
  public final DAGNode studentNode;
  public final DAGNode lossNode;
  
  public SimpleLossNetwork(final NNLayer student, final NNLayer loss) {
    super(2);
    studentNode = add(student, getInput(0));
    lossNode = add(loss, studentNode, getInput(1));
  }
  
  @Override
  public DAGNode getHead() {
    return lossNode;
  }
}