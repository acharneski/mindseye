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

package com.simiacryptus.mindseye.layers.java;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.Layer;
import com.simiacryptus.mindseye.network.DAGNetwork;
import com.simiacryptus.mindseye.network.DAGNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

/**
 * The type Sign reducer key.
 */
@SuppressWarnings("serial")
public class SignReducerLayer extends DAGNetwork {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(SignReducerLayer.class);
  private final DAGNode head;

  /**
   * Instantiates a new Sign reducer key.
   */
  public SignReducerLayer() {
    super(1);
    final DAGNode avgInput = wrap(new AvgReducerLayer(), getInput(0));
    final DAGNode stdDevInput = wrap(new NthPowerActivationLayer().setPower(0.5),
        wrap(new SumInputsLayer(),
            wrap(new AvgReducerLayer(), wrap(new SqActivationLayer(), getInput(0))),
            wrap(new LinearActivationLayer().setScale(-1), wrap(new SqActivationLayer(), avgInput))
        ));
    head = wrap(new SigmoidActivationLayer().setBalanced(false),
        wrap(new ProductInputsLayer(),
            avgInput,
            wrap(new NthPowerActivationLayer().setPower(-1), stdDevInput)));
  }

  /**
   * Instantiates a new Sign reducer key.
   *
   * @param json the json
   * @param rs   the rs
   */
  protected SignReducerLayer(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    super(json, rs);
    head = getNodeById(UUID.fromString(json.getAsJsonPrimitive("head").getAsString()));
  }

  /**
   * From json nn key.
   *
   * @param inner the heapCopy
   * @param rs    the rs
   * @return the nn key
   */
  public static Layer fromJson(@Nonnull final JsonObject inner, Map<CharSequence, byte[]> rs) {
    return new SignReducerLayer(inner, rs);
  }

  @Override
  public DAGNode getHead() {
    return head;
  }
}
