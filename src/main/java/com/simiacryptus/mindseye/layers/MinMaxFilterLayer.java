package com.simiacryptus.mindseye.layers;

import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.deltas.DeltaBuffer;
import com.simiacryptus.mindseye.deltas.NNResult;
import com.simiacryptus.mindseye.math.LogNDArray;
import com.simiacryptus.mindseye.math.LogNumber;
import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.training.EvaluationContext;

public class MinMaxFilterLayer extends NNLayer {
  private final class DenseSynapseResult extends NNResult {
    private final NNResult inObj;
    
    private DenseSynapseResult(final NDArray data, final NNResult inObj) {
      super(data);
      this.inObj = inObj;
    }
    
    @Override
    public void feedback(final LogNDArray delta, final DeltaBuffer buffer) {
      if (isVerbose()) {
        MinMaxFilterLayer.log.debug(String.format("Feed back: %s", this.data));
      }
      final LogNumber[] deltaData = delta.getData();
      if (this.inObj.isAlive()) {
        int[] dims = this.inObj.data.getDims();
        final LogNDArray passback = new LogNDArray(dims);
        for (int i = 0; i < passback.dim(); i++) {
          if (this.inObj.data.getData()[i] > getThreshold()) {
            if (deltaData[i].isNegative()) passback.set(i, deltaData[i]);
          } else if (this.inObj.data.getData()[i] < -getThreshold()) {
            if (!deltaData[i].isNegative()) passback.set(i, deltaData[i]);
          } else {
            passback.set(i, deltaData[i]);
          }
        }
        this.inObj.feedback(passback, buffer);
        if (isVerbose()) {
          MinMaxFilterLayer.log.debug(String.format("Feed back @ %s=>%s: %s => %s", this.inObj.data, DenseSynapseResult.this.data, delta, passback));
        }
      } else {
        if (isVerbose()) {
          MinMaxFilterLayer.log.debug(String.format("Feed back via @ %s=>%s: %s => null", this.inObj.data, DenseSynapseResult.this.data, delta));
        }
      }
    }
    
    @Override
    public boolean isAlive() {
      return this.inObj.isAlive();
    }
    
  }
  
  private static final Logger log = LoggerFactory.getLogger(MinMaxFilterLayer.class);
  
  private boolean verbose = false;
  
  public MinMaxFilterLayer() {
    super();
  }
  
  private double threshold = 20;
  
  @Override
  public NNResult eval(EvaluationContext evaluationContext, final NNResult... inObj) {
    final NDArray input = inObj[0].data;
    final NDArray output = new NDArray(input.getDims());
    IntStream.range(0, input.dim()).forEach(i -> {
      final double x = input.getData()[i];
      final double r = Math.min(Math.max(x, -getThreshold()), getThreshold());
      output.set(i, r);
    });
    if (isVerbose()) {
      MinMaxFilterLayer.log.debug(String.format("Feed forward: %s => %s", inObj[0].data, output));
    }
    return new DenseSynapseResult(output, inObj[0]);
  }
  
  protected double getMobility() {
    return 1;
  }
  
  private boolean isVerbose() {
    return this.verbose;
  }
  
  public MinMaxFilterLayer setVerbose(final boolean verbose) {
    this.verbose = verbose;
    return this;
  }

  public double getThreshold() {
    return threshold;
  }

  public MinMaxFilterLayer setThreshold(double threshold) {
    this.threshold = threshold;
    return this;
  }
  
}