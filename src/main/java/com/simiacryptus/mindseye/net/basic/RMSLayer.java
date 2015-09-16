package com.simiacryptus.mindseye.net.basic;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.deltas.DeltaBuffer;
import com.simiacryptus.mindseye.deltas.NNResult;
import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.net.NNLayer;
import com.simiacryptus.mindseye.net.dag.EvaluationContext;

import groovy.lang.Tuple2;

public class RMSLayer extends NNLayer<RMSLayer> {

  private static final Logger log = LoggerFactory.getLogger(RMSLayer.class);

  public RMSLayer() {
  }

  @Override
  public NNResult eval(final EvaluationContext evaluationContext, final NNResult... inObj) {
    final NDArray a = inObj[0].data;
    final NDArray b = inObj[1].data;
    final NDArray r = new NDArray(a.getDims());
    double total = 0;
    for(int i=0;i<a.dim();i++) {
      double x = a.getData()[i] - b.getData()[i];
      r.getData()[i] = x;
      total += x*x;
    }
    double rms = Math.sqrt(total/a.dim());
    final NDArray output = new NDArray(new int[]{1}, new double[]{rms});
    if (isVerbose()) {
      RMSLayer.log.debug(String.format("Feed forward: %s - %s => %s", inObj[0].data, inObj[1].data, rms));
    }
    return new NNResult(evaluationContext, output) {
      @Override
      public void feedback(final NDArray data, final DeltaBuffer buffer) {
        if (inObj[0].isAlive()||inObj[1].isAlive()) {
          final NDArray passback = new NDArray(r.getDims());
          for (int i = 0; i < a.dim(); i++) {
            passback.set(i, data.get(0) * (1/(2*rms)) * r.get(i) * 2 / a.dim());
          }
          if (isVerbose()) {
            RMSLayer.log.debug(String.format("Feed back @ %s: %s => %s", output, data, passback));
          }
          if (inObj[0].isAlive()) {
            inObj[0].feedback(passback, buffer);
          }
          if (inObj[1].isAlive()) {
            inObj[1].feedback(passback.scale(-1), buffer);
          }
        }
      }

      @Override
      public boolean isAlive() {
        return inObj[0].isAlive();
      }

    };
  }

  @Override
  public List<Tuple2<Integer, Integer>> permuteInput(final List<Tuple2<Integer, Integer>> permute) {
    return permute;
  }

  @Override
  public List<Tuple2<Integer, Integer>> permuteOutput(final List<Tuple2<Integer, Integer>> permute) {
    return permute;
  }

  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
}
