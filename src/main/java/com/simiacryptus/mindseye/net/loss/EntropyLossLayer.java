package com.simiacryptus.mindseye.net.loss;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.util.ml.NDArray;
import com.simiacryptus.mindseye.core.delta.DeltaSet;
import com.simiacryptus.mindseye.core.delta.NNLayer;
import com.simiacryptus.mindseye.core.delta.NNResult;

public class EntropyLossLayer extends NNLayer<EntropyLossLayer> {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(EntropyLossLayer.class);
  /**
   * 
   */
  private static final long serialVersionUID = -6257785994031662519L;

  public EntropyLossLayer() {
  }

  @Override
  public NNResult eval(final NNResult... inObj) {
    NDArray gradientA[] = new NDArray[inObj[0].data.length];
    NDArray[] outputA = java.util.stream.IntStream.range(0, inObj[0].data.length).mapToObj(dataIndex->{
      final NDArray l = inObj[0].data[dataIndex];
      final NDArray b = inObj[1].data[dataIndex];
      final NDArray gradient = new NDArray(l.getDims());
      gradientA[dataIndex] = gradient;
      final double[] gradientData = gradient.getData();
      final double descriptiveNats;
      {
        double total = 0;
        for (int i = 0; i < l.dim(); i++) {
          final double ad = Math.max(Math.min(l.getData()[i], 1.), 1e-12);
          final double bd = b.getData()[i];
          gradientData[i] = -bd / ad;
          total += -bd * Math.log(ad);
        }
        descriptiveNats = total;
      }
      
      return new NDArray(new int[] { 1 }, new double[] { descriptiveNats });
    }).toArray(i->new NDArray[i]);
    return new NNResult(outputA) {
      @Override
      public void accumulate(final DeltaSet buffer, final NDArray[] data) {
        if (inObj[0].isAlive() || inObj[1].isAlive()) {
          NDArray[] passbackA = java.util.stream.IntStream.range(0, inObj[0].data.length).mapToObj(dataIndex->{
            final NDArray passback = new NDArray(gradientA[dataIndex].getDims());
            for (int i = 0; i < inObj[0].data[0].dim(); i++) {
              passback.set(i, data[dataIndex].get(0) * gradientA[dataIndex].get(i));
            }
            return passback;
          }).toArray(i->new NDArray[i]);
          if (inObj[0].isAlive()) {
            inObj[0].accumulate(buffer, passbackA);
          }
          if (inObj[1].isAlive())
            throw new RuntimeException();
        }
      }

      @Override
      public boolean isAlive() {
        return inObj[0].isAlive();
      }

    };
  }

  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
}
