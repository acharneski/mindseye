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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.lang.cudnn.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * This key works as a scaling function, similar to a father wavelet. Allows convolutional and pooling layers to work
 * across larger png regions.
 */
@SuppressWarnings("serial")
public class ImgLinearSubnetLayer extends LayerBase implements MultiPrecision<ImgLinearSubnetLayer> {

  private static final Logger logger = LoggerFactory.getLogger(ImgLinearSubnetLayer.class);
  private final List<SubnetLeg> legs = new ArrayList<>();
  private Precision precision = Precision.Double;
  private boolean parallel = true;

  /**
   * Instantiates a new Rescaled subnet key.
   */
  public ImgLinearSubnetLayer() {
    super();
  }

  /**
   * Instantiates a new Rescaled subnet key.
   *
   * @param json the json
   * @param rs   the rs
   */
  protected ImgLinearSubnetLayer(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    super(json);
    this.precision = Precision.valueOf(json.getAsJsonPrimitive("precision").getAsString());
    setParallel(json.get("parallel").getAsBoolean());
    JsonArray jsonArray = json.get("legs").getAsJsonArray();
    for (int i = 0; i < jsonArray.size(); i++) {
      legs.add(new SubnetLeg(jsonArray.get(i).getAsJsonObject(), rs));
    }
  }

  /**
   * From json rescaled subnet key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the rescaled subnet key
   */
  public static ImgLinearSubnetLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new ImgLinearSubnetLayer(json, rs);
  }

  /**
   * Gets legs.
   *
   * @return the legs
   */
  public List<SubnetLeg> getLegs() {
    return legs;
  }

  /**
   * Add img linear subnet key.
   *
   * @param from  the from
   * @param to    the to
   * @param layer the key
   * @return the img linear subnet key
   */
  public ImgLinearSubnetLayer add(final int from, final int to, final Layer layer) {
    getLegs().add(new SubnetLeg(layer, from, to));
    return this;
  }

  @Override
  protected void _free() {
    super._free();
    legs.stream().forEach(ReferenceCounting::freeRef);
  }

  @Nullable
  @Override
  public Result evalAndFree(@Nonnull final Result... inObj) {
    assert 1 == inObj.length;
    Result input = inObj[0];
    TensorList inputData = input.getData();
    @Nonnull final int[] inputDims = inputData.getDimensions();
    assert 3 == inputDims.length;
    int length = inputData.length();
    int maxBand = legs.stream().mapToInt(x -> x.toBand).max().getAsInt();
    assert maxBand == inputDims[2] : maxBand + " != " + inputDims[2];
    assert IntStream.range(0, maxBand).allMatch(i ->
        1 == legs.stream().filter(x -> x.fromBand <= i && x.toBand > i).count()
    );
    CudaTensor passback = CudaSystem.run(gpu -> {
      return CudaTensor.wrap(
          gpu.allocate(inputData.getElements() * precision.size, MemoryType.Device, true),
          gpu.newTensorDescriptor(precision, length, inputDims[2], inputDims[1], inputDims[0]), precision);
    });
    try {
      AtomicInteger counter = new AtomicInteger(0);
      SumInputsLayer sumInputsLayer = new SumInputsLayer();
      try {
        Result[] legResults = legs.stream().map(leg -> {
          passback.addRef();
          ImgBandSelectLayer imgBandSelectLayer = new ImgBandSelectLayer(leg.fromBand, leg.toBand);
          input.addRef();
          TensorList legData = imgBandSelectLayer.eval(input).getDataAndFree();
          imgBandSelectLayer.freeRef();
          return leg.inner.evalAndFree(new Result(legData, (DeltaSet<UUID> ctx, TensorList delta) -> {
            int[] outputDimensions = delta.getDimensions();
            int[] inputDimensions = inputDims;
            synchronized (passback) {
              CudaSystem.run(gpu -> {
                @Nonnull final CudaDevice.CudaTensorDescriptor viewDescriptor = gpu.newTensorDescriptor(
                    precision, length, outputDimensions[2], outputDimensions[1], outputDimensions[0], //
                    inputDimensions[2] * inputDimensions[1] * inputDimensions[0], //
                    inputDimensions[1] * inputDimensions[0], //
                    inputDimensions[0], //
                    1);
                final int byteOffset = viewDescriptor.cStride * leg.fromBand * precision.size;
                assert delta.length() == inputData.length();
                assert passback.getDeviceId() == gpu.getDeviceId();
                //assert error.stream().flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(Double::isFinite);
                @Nullable final CudaTensor deltaTensor = gpu.getTensor(delta, precision, MemoryType.Device, true);
                @Nonnull final CudaMemory passbackBuffer = passback.getMemory(gpu);
                CudaMemory errorPtrMemory = deltaTensor.getMemory(gpu);
                passbackBuffer.synchronize();
                gpu.cudnnTransformTensor(
                    precision.getPointer(1.0), deltaTensor.descriptor.getPtr(), errorPtrMemory.getPtr(),
                    precision.getPointer(0.0), viewDescriptor.getPtr(), passbackBuffer.getPtr().withByteOffset(byteOffset)
                );
                errorPtrMemory.dirty();
                passbackBuffer.dirty();
                Stream.<ReferenceCounting>of(deltaTensor, viewDescriptor, passbackBuffer, errorPtrMemory).forEach(ReferenceCounting::freeRef);
              }, passback);
            }
            if (counter.incrementAndGet() >= legs.size()) {
              counter.set(0);
              input.accumulate(ctx, CudaTensorList.create(passback, length, inputDims, precision));
            }
          }) {
            @Override
            protected void _free() {
              super._free();
              input.freeRef();
              passback.freeRef();
            }
          });
        }).toArray(i -> new Result[i]);
        return sumInputsLayer.setParallel(parallel).setPrecision(precision).evalAndFree(legResults);
      } finally {
        sumInputsLayer.freeRef();
        input.freeRef();
      }
    } finally {
      passback.freeRef();
    }
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    json.addProperty("precision", precision.name());
    json.addProperty("parallel", isParallel());
    JsonArray jsonArray = new JsonArray();
    legs.stream().map(x -> x.getJson(resources, dataSerializer)).forEach(jsonArray::add);
    json.add("legs", jsonArray);
    return json;
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return new ArrayList<>();
  }

  @Override
  public Precision getPrecision() {
    return precision;
  }

  @Nonnull
  @Override
  public ImgLinearSubnetLayer setPrecision(Precision precision) {
    this.precision = precision;
    return this;
  }

  @Nonnull
  @Override
  public Layer setFrozen(final boolean frozen) {
    legs.stream().map(x -> x.inner).forEach(x -> x.setFrozen(frozen));
    return super.setFrozen(frozen);
  }

  /**
   * Is parallel boolean.
   *
   * @return the boolean
   */
  public boolean isParallel() {
    return parallel;
  }

  /**
   * Sets parallel.
   *
   * @param parallel the parallel
   * @return the parallel
   */
  public ImgLinearSubnetLayer setParallel(boolean parallel) {
    this.parallel = parallel;
    return this;
  }

  /**
   * The type Subnet leg.
   */
  public static class SubnetLeg extends ReferenceCountingBase {

    private final Layer inner;
    private final int fromBand;
    private final int toBand;

    /**
     * Instantiates a new Subnet leg.
     *
     * @param inner    the inner
     * @param fromBand the from band
     * @param toBand   the to band
     */
    public SubnetLeg(final Layer inner, final int fromBand, final int toBand) {
      this.inner = inner;
      this.fromBand = fromBand;
      this.toBand = toBand;
      this.inner.addRef();
    }

    /**
     * Instantiates a new Rescaled subnet key.
     *
     * @param json the json
     * @param rs   the rs
     */
    protected SubnetLeg(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
      fromBand = json.getAsJsonPrimitive("fromBand").getAsInt();
      toBand = json.getAsJsonPrimitive("toBand").getAsInt();
      inner = Layer.fromJson(json.getAsJsonObject("network"), rs);
    }

    @Override
    protected void _free() {
      super._free();
      inner.freeRef();
    }

    /**
     * Gets json.
     *
     * @param resources      the resources
     * @param dataSerializer the data serializer
     * @return the json
     */
    @Nonnull
    public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
      @Nonnull final JsonObject json = new JsonObject();
      json.addProperty("fromBand", fromBand);
      json.addProperty("toBand", toBand);
      json.add("network", inner.getJson(resources, dataSerializer));
      return json;
    }

  }
}
