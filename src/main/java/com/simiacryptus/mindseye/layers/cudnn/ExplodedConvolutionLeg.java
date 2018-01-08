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

import com.simiacryptus.mindseye.lang.Delta;
import com.simiacryptus.mindseye.lang.DeltaSet;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.network.DAGNode;
import com.simiacryptus.mindseye.network.PipelineNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * The type Exploded network.
 */
public class ExplodedConvolutionLeg {
  /**
   * The Network.
   */
  public final PipelineNetwork network;
  /**
   * The Sub layers.
   */
  public final List<SimpleConvolutionLayer> subLayers;
  private final int fromBand;
  private final int toBand;
  private final int kernelBandOffset;
  private final int kernelBandSkip;
  private final int legBands;
  private final int outputBands;
  private final ConvolutionParams convolutionParams;
  
  public ExplodedConvolutionLeg(ConvolutionParams convolutionParams, int fromBand, int toBand, int kernelBandOffset, int kernelBandSkip) {
    this.fromBand = fromBand;
    this.toBand = toBand;
    this.kernelBandOffset = kernelBandOffset;
    this.kernelBandSkip = kernelBandSkip;
    this.legBands = this.toBand - this.fromBand;
    this.outputBands = convolutionParams.outputBands;
    this.convolutionParams = convolutionParams;
    subLayers = new ArrayList<>();
    // Extract Weights
    final int[] filterDimensions = this.convolutionParams.filterDimensions;
    final int legBandsSq = legBands * legBands;
    filterDimensions[2] = legBands * this.convolutionParams.outputBands;
    for (int offset = 0; offset < filterDimensions[2]; offset += legBandsSq) {
      final Tensor cellKernel = new Tensor(filterDimensions[0], filterDimensions[1], legBandsSq);
      SimpleConvolutionLayer simpleConvolutionLayer = new SimpleConvolutionLayer(cellKernel)
        .setStrideX(this.convolutionParams.strideX).setStrideY(this.convolutionParams.strideY).setPrecision(this.convolutionParams.precision);
      subLayers.add(simpleConvolutionLayer);
    }
    this.network = buildNetwork();
  }
  
  public PipelineNetwork buildNetwork() {
    final int[] filterDimensions = this.convolutionParams.filterDimensions;
    PipelineNetwork network = new PipelineNetwork(1);
    if (this.fromBand != 0 || this.toBand != this.convolutionParams.inputBands) {
      network.add(new ImgBandSelectLayer(this.fromBand, this.toBand));
    }
    if (legBands != this.convolutionParams.outputBands) {
      DAGNode input = network.getHead();
      network.add(new ImgConcatLayer().setMaxBands(this.convolutionParams.outputBands).setPrecision(this.convolutionParams.precision),
                  subLayers.stream().map(l -> {
                    return network.add(l, input);
                  }).toArray(i -> new DAGNode[i]));
    }
    else {
      assert 1 == subLayers.size();
      network.add(subLayers.get(0));
    }
    if (this.convolutionParams.paddingX != null || this.convolutionParams.paddingY != null) {
      int x = ((filterDimensions[0] - 1) / 2);
      if (this.convolutionParams.paddingX != null) x = this.convolutionParams.paddingX - x;
      int y = ((filterDimensions[1] - 1) / 2);
      if (this.convolutionParams.paddingY != null) y = this.convolutionParams.paddingY - y;
      network.add(new ImgZeroPaddingLayer(x, y));
    }
    return network;
  }
  
  public ExplodedConvolutionLeg write(Tensor kernel) {
    int[] filterDimensions = this.convolutionParams.filterDimensions;
    final int legBandsSq = legBands * legBands;
    for (int index = 0; index < subLayers.size(); index++) {
      final int legBandOffset = index * legBandsSq;
      final Tensor cellKernel = new Tensor(filterDimensions[0], filterDimensions[1], legBandsSq);
      cellKernel.setByCoord(cellCoord -> {
        int[] coords = cellCoord.getCoords();
        final int band = ConvolutionLayer.transposeCoordinates(legBands, this.convolutionParams.outputBands, legBandOffset + coords[2]);
        if (legBandOffset + coords[2] < filterDimensions[2]) {
          return kernel.get(coords[0], coords[1], band * this.kernelBandSkip + this.kernelBandOffset);
        }
        else {
          return 0;
        }
      });
      subLayers.get(index).set(cellKernel);
    }
    return this;
  }
  
  /**
   * Gets network.
   *
   * @return the network
   */
  public PipelineNetwork getNetwork() {
    return network;
  }
  
  /**
   * Gets sub layers.
   *
   * @return the sub layers
   */
  public List<SimpleConvolutionLayer> getSubLayers() {
    return subLayers;
  }
  
  public void extractDelta(DeltaSet<NNLayer> deltaSet, Tensor filterDelta, boolean remove) {
    for (int layerNumber = 0; layerNumber < this.getSubLayers().size(); layerNumber++) {
      final int legBandOffset = layerNumber * this.legBands * this.legBands;
      final SimpleConvolutionLayer subLayer = this.getSubLayers().get(layerNumber);
      final Delta<NNLayer> subnetDelta = remove ? deltaSet.getMap().remove(subLayer) : deltaSet.getMap().get(subLayer);
      if (null != subnetDelta) {
        final int[] cellDimensions = subLayer.kernel.getDimensions();
        final Tensor cellDelta = new Tensor(subnetDelta.getDelta(), cellDimensions);
        cellDelta.coordStream(false).forEach(cellCoord -> {
          int[] coords = cellCoord.getCoords();
          final int cellBand = ConvolutionLayer.transposeCoordinates(this.legBands, outputBands, legBandOffset + coords[2]);
          if (legBandOffset + cellBand < this.legBands * outputBands) {
            filterDelta.set(coords[0], coords[1], this.kernelBandOffset + this.kernelBandSkip * cellBand, cellDelta.get(cellCoord));
          }
        });
      }
    }
  }
}
