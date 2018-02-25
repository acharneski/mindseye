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
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.lang.cudnn.*;
import jcuda.jcudnn.cudnnTensorDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reduces the resolution of the input by selecting a centered window. The output image will have the same number of
 * color bands.
 */
@SuppressWarnings("serial")
public class ImgTileAssemblyLayer extends LayerBase implements MultiPrecision<ImgTileAssemblyLayer> {
  private static final Logger log = LoggerFactory.getLogger(ImgTileAssemblyLayer.class);
  
  private int columns;
  private int rows;
  private Precision precision = Precision.Double;
  private boolean parallel;
  
  /**
   * Instantiates a new Img concat layer.
   */
  private ImgTileAssemblyLayer() {
  }
  
  /**
   * Instantiates a new Img crop layer.
   *
   * @param columns the size x
   * @param rows    the size y
   */
  public ImgTileAssemblyLayer(int columns, int rows) {
    this.columns = columns;
    this.rows = rows;
  }
  
  /**
   * Instantiates a new Img concat layer.
   *
   * @param json the json
   * @param rs   the rs
   */
  protected ImgTileAssemblyLayer(@Nonnull final JsonObject json, Map<String, byte[]> rs) {
    super(json);
    columns = json.get("columns").getAsInt();
    rows = json.get("rows").getAsInt();
    this.parallel = json.get("parallel").getAsBoolean();
    this.precision = Precision.valueOf(json.getAsJsonPrimitive("precision").getAsString());
  }
  
  /**
   * From json img concat layer.
   *
   * @param json the json
   * @param rs   the rs
   * @return the img concat layer
   */
  public static ImgTileAssemblyLayer fromJson(@Nonnull final JsonObject json, Map<String, byte[]> rs) {
    return new ImgTileAssemblyLayer(json, rs);
  }
  
  /**
   * Gets compatibility layer.
   *
   * @return the compatibility layer
   */
  @Nonnull
  public Layer getCompatibilityLayer() {
    return this.as(com.simiacryptus.mindseye.layers.java.ImgTileAssemblyLayer.class);
  }
  
  @Nullable
  @Override
  public Result eval(@Nonnull final Result... inObj) {
    if (!CudaSystem.isEnabled()) return getCompatibilityLayer().eval(inObj);
    final TensorList prototype = inObj[0].getData();
    if (1 == inObj.length) {
      inObj[0].addRef();
      prototype.addRef();
      return inObj[0];
    }
    int[] inputDimensions = prototype.getDimensions();
    assert 3 == inputDimensions.length;
    final int length = prototype.length();
    int[] outputDims = getOutputDims(inObj);
    Arrays.stream(inObj).forEach(nnResult -> nnResult.addRef());
    final TensorList outputData = CudaSystem.eval(gpu -> {
      assert outputDims[0] > 0;
      assert outputDims[1] > 0;
      assert outputDims[2] > 0;
      @Nonnull final CudaMemory outputBuffer = gpu.allocate(
        (long) length * outputDims[2] * outputDims[1] * outputDims[0] * precision.size, MemoryType.Managed, false);
      int totalWidth = 0;
      int totalHeight = 0;
      int inputIndex = 0;
      List<CopyParams> copies = new ArrayList<>();
      for (int row = 0; row < rows; row++) {
        int positionX = 0;
        int rowHeight = 0;
        for (int col = 0; col < columns; col++) {
          TensorList tileTensor = inObj[inputIndex].getData();
          int[] tileDimensions = tileTensor.getDimensions();
          rowHeight = Math.max(rowHeight, tileDimensions[1]);
          copies.add(new CopyParams(gpu, inObj, outputBuffer, length, outputDims, tileDimensions, inputIndex, positionX, totalHeight));
          positionX += tileDimensions[0];
          inputIndex += 1;
        }
        totalHeight += rowHeight;
        totalWidth = Math.max(totalWidth, positionX);
      }
      Stream<CopyParams> stream = copies.stream();
      if (!CoreSettings.INSTANCE.isConservative() && parallel) stream = stream.parallel();
      stream.forEach(this::copy);
      return CudaTensorList.wrap(outputBuffer, length, outputDims, precision);
    });
    
    
    return new Result(outputData, (@Nonnull final DeltaSet<Layer> buffer, @Nonnull final TensorList error) -> {
      if (!Arrays.equals(error.getDimensions(), outputData.getDimensions())) {
        throw new AssertionError(Arrays.toString(error.getDimensions()) + " != " + Arrays.toString(outputData.getDimensions()));
      }
      if (error.length() != outputData.length()) {
        throw new AssertionError(error.length() + " != " + outputData.length());
      }
      assert error.length() == prototype.length();
      
      
      int totalHeight = 0;
      int inputIndex = 0;
      List<BackpropParams> tasks = new ArrayList<>();
      for (int row = 0; row < rows; row++) {
        int positionX = 0;
        int rowHeight = 0;
        for (int col = 0; col < columns; col++) {
          Result in = inObj[inputIndex];
          int[] tileDimensions = in.getData().getDimensions();
          rowHeight = Math.max(rowHeight, tileDimensions[1]);
          if (inObj[inputIndex].isAlive()) {
            tasks.add(new BackpropParams(inObj, buffer, error, outputDims, tileDimensions, length, positionX, totalHeight, inputIndex));
          }
          positionX += tileDimensions[0];
          inputIndex += 1;
        }
        totalHeight += rowHeight;
      }
      Stream<BackpropParams> stream = tasks.stream();
      if (!CoreSettings.INSTANCE.isConservative() && parallel) stream = stream.parallel();
      stream.forEach(this::backprop);
    }) {

      @Override
      protected void _free() {
        Arrays.stream(inObj).forEach(nnResult -> nnResult.freeRef());
      }

      @Override
      public boolean isAlive() {
        return Arrays.stream(inObj).anyMatch(x -> x.isAlive());
      }
    };
  }
  
  public void backprop(final BackpropParams backpropParams) {
    final TensorList passbackTensorList = CudaSystem.eval(gpu -> {
      @Nullable final CudaMemory errorPtr = gpu.getPtr(backpropParams.getError(), precision, MemoryType.Device);
      @Nonnull final CudaMemory passbackBuffer = gpu.allocate(
        (long) (backpropParams.getLength() * backpropParams.getTileDimensions()[2] * backpropParams.getTileDimensions()[1] * backpropParams.getTileDimensions()[0] * precision.size), MemoryType.Managed, false);
      copy(gpu, backpropParams.getLength(), backpropParams.getOutputDims(), errorPtr, backpropParams.getTileDimensions(), passbackBuffer, -backpropParams.getPositionX(), -backpropParams.getTotalHeight());
      Arrays.stream(new ReferenceCounting[]{errorPtr}).forEach(ReferenceCounting::freeRef);
      return CudaTensorList.wrap(passbackBuffer, backpropParams.getLength(), backpropParams.getTileDimensions(), precision);
    });
    backpropParams.getInObj()[backpropParams.getInputIndex()].accumulate(backpropParams.getBuffer(), passbackTensorList);
  }
  
  public void copy(final CopyParams copyParams) {
    @Nullable final CudaMemory inputBuffer = copyParams.getGpu().getPtr(copyParams.getInObj()[copyParams.getInputIndex()].getData(), precision, MemoryType.Device);
    copy(copyParams.getGpu(), copyParams.getLength(), copyParams.getTileDimensions(), inputBuffer, copyParams.getOutputDims(), copyParams.getOutputBuffer(), copyParams.getPositionX(), copyParams.getTotalHeight());
    Arrays.stream(new ReferenceCounting[]{inputBuffer}).forEach(ReferenceCounting::freeRef);
  }
  
  private int[] getOutputDims(final Result[] inObj) {
    int bands = inObj[0].getData().getDimensions()[2];
    int totalWidth = 0;
    int totalHeight = 0;
    int inputIndex = 0;
    for (int row = 0; row < rows; row++) {
      int positionX = 0;
      int rowHeight = 0;
      for (int col = 0; col < columns; col++) {
        int[] dimensions = inObj[inputIndex].getData().getDimensions();
        rowHeight = Math.max(rowHeight, dimensions[1]);
        positionX += dimensions[0];
        inputIndex += 1;
      }
      totalHeight += rowHeight;
      totalWidth = Math.max(totalWidth, positionX);
    }
    return new int[]{totalWidth, totalHeight, bands};
  }
  
  /**
   * Copy.
   *
   * @param gpu                   the gpu
   * @param length                the length
   * @param sourceDimensions      the length in
   * @param source                the input buffer
   * @param destinationDimensions the length out
   * @param destination           the output buffer
   * @param positionX             the position x
   * @param positionY             the position y
   * @return the int [ ]
   */
  public int[] copy(@Nonnull CudnnHandle gpu, int length, @Nonnull int[] sourceDimensions, @Nonnull CudaMemory source, @Nonnull int[] destinationDimensions, @Nonnull CudaMemory destination, int positionX, int positionY) {
    if (3 != sourceDimensions.length) throw new IllegalArgumentException("inputDimensions.length");
    if (3 != destinationDimensions.length) throw new IllegalArgumentException("dimOut.length");
    int bands = sourceDimensions[2];
    if (bands != destinationDimensions[2])
      throw new IllegalArgumentException(String.format("%d != %d", bands, destinationDimensions[2]));
    //_log.info(String.format("offset=%d,%d", offsetX, offsetY));
    @Nonnull final int[] viewDim = getViewDimensions(sourceDimensions, destinationDimensions, new int[]{positionX, positionY, 0});
    @Nonnull final CudaResource<cudnnTensorDescriptor> sourceViewDescriptor = gpu.newTensorDescriptor(
      precision.code,//
      length,//
      viewDim[2],//
      viewDim[1],//
      viewDim[0],//
      bands * sourceDimensions[1] * sourceDimensions[0],//
      sourceDimensions[1] * sourceDimensions[0],//
      sourceDimensions[0],//
      1);
    @Nonnull final CudaResource<cudnnTensorDescriptor> destinationViewDescriptor = gpu.newTensorDescriptor(
      precision.code,//
      length,//
      viewDim[2],//
      viewDim[1],//
      viewDim[0],//
      destinationDimensions[2] * destinationDimensions[1] * destinationDimensions[0],//
      destinationDimensions[1] * destinationDimensions[0],//
      destinationDimensions[0],//
      1);
    int sourceOffset = 0;
    int destinationOffset = 0;
    
    if (positionX > 0) {
      destinationOffset += Math.abs(positionX);
    }
    else {
      sourceOffset += Math.abs(positionX);
    }
    if (positionY > 0) {
      destinationOffset += destinationDimensions[0] * Math.abs((positionY));
    }
    else {
      sourceOffset += sourceDimensions[0] * (Math.abs(positionY));
    }
    assert sourceOffset >= 0;
    assert destinationOffset >= 0;
    assert sourceOffset + Tensor.length(viewDim) <= Tensor.length(sourceDimensions);
    assert destinationOffset + Tensor.length(viewDim) <= Tensor.length(destinationDimensions);
    
    CudaSystem.handle(gpu.cudnnTransformTensor(
      precision.getPointer(1.0),
      sourceViewDescriptor.getPtr(), source.getPtr().withByteOffset(sourceOffset * precision.size),
      precision.getPointer(1.0),
      destinationViewDescriptor.getPtr(), destination.getPtr().withByteOffset(destinationOffset * precision.size)
    ));
    Arrays.stream(new ReferenceCounting[]{sourceViewDescriptor, destinationViewDescriptor}).forEach(ReferenceCounting::freeRef);
    return viewDim;
    
  }
  
  /**
   * Get view dimensions int [ ].
   *
   * @param sourceDimensions      the source dimensions
   * @param destinationDimensions the destination dimensions
   * @param offset                the offset
   * @return the int [ ]
   */
  @Nonnull
  public int[] getViewDimensions(int[] sourceDimensions, int[] destinationDimensions, int[] offset) {
    @Nonnull final int[] viewDim = new int[3];
    Arrays.parallelSetAll(viewDim, i ->
      Math.min(sourceDimensions[i] + offset[i], destinationDimensions[i]) -
        Math.max(offset[i], 0)
    );
    return viewDim;
  }
  
  @Nonnull
  @Override
  public JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    json.addProperty("rows", rows);
    json.addProperty("columns", columns);
    json.addProperty("precision", precision.name());
    json.addProperty("parallel", isParallel());
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
  public ImgTileAssemblyLayer setPrecision(final Precision precision) {
    this.precision = precision;
    return this;
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
  public ImgTileAssemblyLayer setParallel(final boolean parallel) {
    this.parallel = parallel;
    return this;
  }
  
  private static class CopyParams {
    private final int length;
    private final int[] outputDims;
    private final CudnnHandle gpu;
    private final CudaMemory outputBuffer;
    private final int totalHeight;
    private final int inputIndex;
    private final int positionX;
    private final int[] tileDimensions;
    @Nonnull
    private final Result[] inObj;
    
    private CopyParams(final CudnnHandle gpu, @Nonnull final Result[] inObj, final CudaMemory outputBuffer, final int length, final int[] outputDims, final int[] tileDimensions, final int inputIndex, final int positionX, final int totalHeight) {
      this.length = length;
      this.outputDims = outputDims;
      this.gpu = gpu;
      this.outputBuffer = outputBuffer;
      this.totalHeight = totalHeight;
      this.inputIndex = inputIndex;
      this.positionX = positionX;
      this.tileDimensions = tileDimensions;
      this.inObj = inObj;
    }
    
    public int getLength() {
      return length;
    }
    
    public int[] getOutputDims() {
      return outputDims;
    }
    
    public CudnnHandle getGpu() {
      return gpu;
    }
    
    public CudaMemory getOutputBuffer() {
      return outputBuffer;
    }
    
    public int getTotalHeight() {
      return totalHeight;
    }
    
    public int getInputIndex() {
      return inputIndex;
    }
    
    public int getPositionX() {
      return positionX;
    }
    
    public int[] getTileDimensions() {
      return tileDimensions;
    }
    
    public Result[] getInObj() {
      return inObj;
    }
  }
  
  private static class BackpropParams {
    @Nonnull
    private final Result[] inObj;
    @Nonnull
    private final DeltaSet<Layer> buffer;
    @Nonnull
    private final TensorList error;
    private final int[] outputDims;
    private final int[] tileDimensions;
    private final int length;
    private final int positionX;
    private final int totalHeight;
    private final int inputIndex;
    
    private BackpropParams(@Nonnull final Result[] inObj, @Nonnull final DeltaSet<Layer> buffer, @Nonnull final TensorList error, final int[] outputDims, final int[] tileDimensions, final int length, final int positionX, final int totalHeight, final int inputIndex) {
      this.inObj = inObj;
      this.buffer = buffer;
      this.error = error;
      this.outputDims = outputDims;
      this.tileDimensions = tileDimensions;
      this.length = length;
      this.positionX = positionX;
      this.totalHeight = totalHeight;
      this.inputIndex = inputIndex;
    }
    
    public Result[] getInObj() {
      return inObj;
    }
    
    public DeltaSet<Layer> getBuffer() {
      return buffer;
    }
    
    public TensorList getError() {
      return error;
    }
    
    public int[] getOutputDims() {
      return outputDims;
    }
    
    public int[] getTileDimensions() {
      return tileDimensions;
    }
    
    public int getLength() {
      return length;
    }
    
    public int getPositionX() {
      return positionX;
    }
    
    public int getTotalHeight() {
      return totalHeight;
    }
    
    public int getInputIndex() {
      return inputIndex;
    }
  }
}
