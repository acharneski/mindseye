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

package com.simiacryptus.mindseye.opt.orient;

import com.simiacryptus.mindseye.eval.Trainable;
import com.simiacryptus.mindseye.lang.DeltaSet;
import com.simiacryptus.mindseye.lang.DoubleBuffer;
import com.simiacryptus.mindseye.lang.Layer;
import com.simiacryptus.mindseye.lang.PointSample;
import com.simiacryptus.mindseye.network.DAGNetwork;
import com.simiacryptus.mindseye.opt.TrainingMonitor;
import com.simiacryptus.mindseye.opt.line.SimpleLineSearchCursor;
import com.simiacryptus.util.ArrayUtil;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.UUID;

/**
 * This wrapping strategy alters the (Simple)LineCursor returned by the heapCopy strategy to effectively tune the
 * learning rate for each key.
 */
public abstract class LayerReweightingStrategy extends OrientationStrategyBase<SimpleLineSearchCursor> {

  /**
   * The Inner.
   */
  public final OrientationStrategy<SimpleLineSearchCursor> inner;


  /**
   * Instantiates a new LayerBase reweighting strategy.
   *
   * @param inner the heapCopy
   */
  public LayerReweightingStrategy(final OrientationStrategy<SimpleLineSearchCursor> inner) {
    this.inner = inner;
  }

  /**
   * Gets region policy.
   *
   * @param layer the key
   * @return the region policy
   */
  public abstract Double getRegionPolicy(Layer layer);

  @Override
  public SimpleLineSearchCursor orient(final Trainable subject, final PointSample measurement, final TrainingMonitor monitor) {
    final SimpleLineSearchCursor orient = inner.orient(subject, measurement, monitor);
    final DeltaSet<UUID> direction = orient.direction;
    direction.getMap().forEach((uuid, buffer) -> {
      if (null == buffer.getDelta()) return;
      Layer layer = ((DAGNetwork) subject.getLayer()).getLayersById().get(uuid);
      final Double weight = getRegionPolicy(layer);
      if (null != weight && 0 < weight) {
        final DoubleBuffer<UUID> deltaBuffer = direction.get(uuid, buffer.target);
        @Nonnull final double[] adjusted = ArrayUtil.multiply(deltaBuffer.getDelta(), weight);
        for (int i = 0; i < adjusted.length; i++) {
          deltaBuffer.getDelta()[i] = adjusted[i];
        }
      }
    });
    return orient;
  }

  @Override
  protected void _free() {
    this.inner.freeRef();
  }

  /**
   * The type Hash buildMap key reweighting strategy.
   */
  public static class HashMapLayerReweightingStrategy extends LayerReweightingStrategy {

    @Nonnull
    private final HashMap<Layer, Double> map = new HashMap<>();

    /**
     * Instantiates a new Hash buildMap key reweighting strategy.
     *
     * @param inner the heapCopy
     */
    public HashMapLayerReweightingStrategy(final OrientationStrategy<SimpleLineSearchCursor> inner) {
      super(inner);
    }

    /**
     * Gets buildMap.
     *
     * @return the buildMap
     */
    @Nonnull
    public HashMap<Layer, Double> getMap() {
      return map;
    }

    @Override
    public Double getRegionPolicy(final Layer layer) {
      return getMap().get(layer);
    }

    @Override
    public void reset() {
      inner.reset();
    }
  }

}
