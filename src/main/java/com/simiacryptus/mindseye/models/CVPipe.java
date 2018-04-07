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

package com.simiacryptus.mindseye.models;

import com.simiacryptus.mindseye.network.PipelineNetwork;

import java.util.Map;
import java.util.UUID;

/**
 * The interface Multi layer image network.
 *
 * @param <T> the type parameter
 */
public interface CVPipe<T extends LayerEnum<T>> {
  
  ;
  
  /**
   * Gets nodes.
   *
   * @return the nodes
   */
  Map<T, UUID> getNodes();
  
  /**
   * Gets prototypes.
   *
   * @return the prototypes
   */
  Map<T, PipelineNetwork> getPrototypes();
  
  /**
   * Gets network.
   *
   * @return the network
   */
  PipelineNetwork getNetwork();
}