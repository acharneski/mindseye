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

package com.simiacryptus.mindseye.layers.cudnn;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.DataSerializer;
import com.simiacryptus.mindseye.lang.LayerBase;
import com.simiacryptus.mindseye.lang.Result;
import com.simiacryptus.mindseye.lang.cudnn.MultiPrecision;
import com.simiacryptus.mindseye.lang.cudnn.Precision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Increases the resolution of the input by selecting a larger centered window. The output png will have the same
 * number of color bands, and the area outside the source png will be setWeights to 0.
 */
@SuppressWarnings("serial")
public class ImgZeroPaddingLayer extends LayerBase implements MultiPrecision<ImgZeroPaddingLayer> {
  private static final Logger log = LoggerFactory.getLogger(ImgZeroPaddingLayer.class);
  /**
   * The Created by.
   */
  public StackTraceElement[] createdBy = Thread.currentThread().getStackTrace();
  private int sizeX;
  private int sizeY;
  private Precision precision = Precision.Double;

  /**
   * Instantiates a new Img eval key.
   */
  private ImgZeroPaddingLayer() {
  }

  /**
   * Instantiates a new Img zero padding key.
   *
   * @param sizeX the size x
   * @param sizeY the size y
   */
  public ImgZeroPaddingLayer(int sizeX, int sizeY) {
    this.sizeX = sizeX;
    this.sizeY = sizeY;
    assert sizeY != 0 || sizeX != 0;
  }

  /**
   * Instantiates a new Img eval key.
   *
   * @param json the json
   * @param rs   the rs
   */
  protected ImgZeroPaddingLayer(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    super(json);
    sizeX = json.get("sizeX").getAsInt();
    sizeY = json.get("sizeY").getAsInt();
    this.precision = Precision.valueOf(json.getAsJsonPrimitive("precision").getAsString());
    assert sizeY != 0 || sizeX != 0;
  }

  /**
   * From json img eval key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the img eval key
   */
  public static ImgZeroPaddingLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new ImgZeroPaddingLayer(json, rs);
  }

  @Nullable
  @Override
  public Result evalAndFree(@Nonnull final Result... inObj) {
    if (sizeX == 0 && sizeY == 0) {
      return inObj[0];
    }
    assert inObj.length == 1;
    @Nonnull int[] dimensions = inObj[0].getData().getDimensions();
    @Nonnull ImgCropLayer imgCropLayer = new ImgCropLayer(dimensions[0] + 2 * this.sizeX, dimensions[1] + 2 * this.sizeY).setPrecision(precision);
    @Nullable Result eval = imgCropLayer.evalAndFree(inObj);
    imgCropLayer.freeRef();
    return eval;
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    json.addProperty("sizeY", sizeY);
    json.addProperty("sizeX", sizeX);
    json.addProperty("precision", precision.name());
    return json;
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }

  @Override
  public Precision getPrecision() {
    return precision;
  }

  @Nonnull
  @Override
  public ImgZeroPaddingLayer setPrecision(final Precision precision) {
    this.precision = precision;
    return this;
  }

}
