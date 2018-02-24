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

package com.simiacryptus.mindseye.lang;

/**
 * The type Cuda settings.
 */
public class CoreSettings implements Settings {
  /**
   * The constant INSTANCE.
   */
  public static final CoreSettings INSTANCE = new CoreSettings();
  /**
   * The Backprop aggregation size.
   */
  public final int backpropAggregationSize;
  private final boolean lifecycleDebug;
  private final boolean conservative;
  private final PersistanceMode doubleCacheMode;
  
  private CoreSettings() {
    this.conservative = Settings.get("CONSERVATIVE", false);
    this.lifecycleDebug = Settings.get("DEBUG_LIFECYCLE", false || isConservative());
    this.doubleCacheMode = Settings.get("DOUBLE_CACHE_MODE", PersistanceMode.WEAK);
    this.backpropAggregationSize = Settings.get("BACKPROP_AGG_SIZE", 4);
  }
  
  /**
   * Is lifecycle debug boolean.
   *
   * @return the boolean
   */
  public boolean isLifecycleDebug() {
    return lifecycleDebug;
  }
  
  /**
   * Is conservative boolean.
   *
   * @return the boolean
   */
  public boolean isConservative() {
    return conservative;
  }
  
  /**
   * Gets double cache mode.
   *
   * @return the double cache mode
   */
  public PersistanceMode getDoubleCacheMode() {
    return doubleCacheMode;
  }
  
}
