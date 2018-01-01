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

package com.simiacryptus.mindseye.opt.line;

import com.simiacryptus.mindseye.lang.DeltaSet;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.PointSample;
import com.simiacryptus.mindseye.opt.TrainingMonitor;

/**
 * A wrapper for a line search cursor which tracks the best-known point.
 */
public class FailsafeLineSearchCursor implements LineSearchCursor {
  private final LineSearchCursor direction;
  private final TrainingMonitor monitor;
  private PointSample best;
  
  /**
   * Instantiates a new Failsafe line search cursor.
   *
   * @param direction     the direction
   * @param previousPoint the previous point
   * @param monitor       the monitor
   */
  public FailsafeLineSearchCursor(final LineSearchCursor direction, final PointSample previousPoint, final TrainingMonitor monitor) {
    this.direction = direction;
    best = previousPoint.copyFull();
    this.monitor = monitor;
  }
  
  /**
   * Accumulate.
   *
   * @param step the runStep
   */
  public void accumulate(final PointSample step) {
    if (null == best || best.getMean() > step.getMean()) {
      monitor.log(String.format("New Minimum: %s > %s", best.getMean(), step.getMean()));
      best = step.copyFull();
    }
  }
  
  
  /**
   * Gets best.
   *
   * @param monitor the monitor
   * @return the best
   */
  public PointSample getBest(final TrainingMonitor monitor) {
    best.weights.restore();
    return best;
  }
  
  @Override
  public String getDirectionType() {
    return direction.getDirectionType();
  }
  
  @Override
  public DeltaSet<NNLayer> position(final double alpha) {
    return direction.position(alpha);
  }
  
  @Override
  public void reset() {
    direction.reset();
  }
  
  @Override
  public LineSearchPoint step(final double alpha, final TrainingMonitor monitor) {
    final LineSearchPoint step = direction.step(alpha, monitor);
    accumulate(step.point);
    return step;
  }
  
}
