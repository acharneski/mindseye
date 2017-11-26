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

package com.simiacryptus.mindseye.opt.line;

import com.simiacryptus.mindseye.lang.DeltaSet;
import com.simiacryptus.mindseye.opt.TrainingMonitor;

/**
 * A mutable cursor which represents a 1-d optimization problem.
 */
public interface LineSearchCursor {
  
  /**
   * Gets direction type.
   *
   * @return the direction type
   */
  String getDirectionType();
  
  /**
   * Step line search point.
   *
   * @param alpha   the alpha
   * @param monitor the monitor
   * @return the line search point
   */
  LineSearchPoint step(double alpha, TrainingMonitor monitor);
  
  /**
   * Position delta set.
   *
   * @param alpha the alpha
   * @return the delta set
   */
  DeltaSet position(double alpha);
  
  /**
   * Reset.
   */
  void reset();
}
