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

package com.simiacryptus.mindseye.models;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.util.io.NotebookOutput;
import com.simiacryptus.util.io.NullNotebookOutput;

/**
 * The interface Demoable network factory.
 */
public interface DemoableNetworkFactory {
  
  /**
   * Build pipeline network.
   *
   * @param output the output
   * @return the pipeline network
   */
  NNLayer build(NotebookOutput output);
  
  /**
   * Build pipeline network.
   *
   * @return the pipeline network
   */
  default NNLayer build() {return build(new NullNotebookOutput());}
  
}
