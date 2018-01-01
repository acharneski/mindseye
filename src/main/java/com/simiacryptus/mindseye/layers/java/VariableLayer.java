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
import com.simiacryptus.mindseye.lang.DataSerializer;
import com.simiacryptus.mindseye.lang.NNLayer;

import java.util.List;
import java.util.Map;

/**
 * Acts as a mutable placeholder layer, whose inner implementation can be setByCoord and changed.
 */
@SuppressWarnings("serial")
public class VariableLayer extends WrapperLayer {
  
  /**
   * Instantiates a new Variable layer.
   *
   * @param json the json
   */
  protected VariableLayer(final JsonObject json) {
    super(json);
  }
  
  /**
   * Instantiates a new Variable layer.
   *
   * @param inner the inner
   */
  public VariableLayer(final NNLayer inner) {
    super();
    setInner(inner);
  }
  
  /**
   * From json variable layer.
   *
   * @param json the json
   * @param rs   the rs
   * @return the variable layer
   */
  public static VariableLayer fromJson(final JsonObject json, Map<String, byte[]> rs) {
    return new VariableLayer(json);
  }
  
  @Override
  public List<NNLayer> getChildren() {
    return super.getChildren();
  }
  
  @Override
  public JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
    final JsonObject json = super.getJsonStub();
    json.add("inner", getInner().getJson(resources, dataSerializer));
    return json;
  }
  
  /**
   * Sets inner.
   *
   * @param inner the inner
   */
  public final void setInner(final NNLayer inner) {
    this.inner = inner;
  }
  
}
