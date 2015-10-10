package com.simiacryptus.mindseye.net.dev;

import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.stream.IntStream;

import org.jblas.DoubleMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.DeltaSet;
import com.simiacryptus.mindseye.NDArray;
import com.simiacryptus.mindseye.NNResult;
import com.simiacryptus.mindseye.Util;
import com.simiacryptus.mindseye.net.NNLayer;

public class LinearActivationLayer extends NNLayer<LinearActivationLayer> {
  private final class Result extends NNResult {
    private final NNResult inObj;

    private Result(final NDArray data, final NNResult inObj) {
      super(data);
      this.inObj = inObj;
    }

    @Override
    public void feedback(final NDArray delta, final DeltaSet buffer) {
      if (isVerbose()) {
        LinearActivationLayer.log.debug(String.format("Feed back: %s", this.data));
      }
      final double[] deltaData = delta.getData();

      if (!isFrozen()) {
        final double[] inputData = this.inObj.data.getData();
        final NDArray weightDelta = new NDArray(LinearActivationLayer.this.weights.getDims());
        for (int i = 0; i < deltaData.length; i++) {
          weightDelta.add(0, deltaData[i] * inputData[i]);
        }
        buffer.get(LinearActivationLayer.this, LinearActivationLayer.this.weights).feed(weightDelta.getData());
      }
      if (this.inObj.isAlive()) {
        final DoubleMatrix matrix = LinearActivationLayer.this.weights.asRowMatrix();
        final int[] dims = this.inObj.data.getDims();
        final NDArray passback = new NDArray(dims);
        for (int i = 0; i < passback.dim(); i++) {
          passback.set(i, deltaData[i] * matrix.get(0, 0));
        }
        this.inObj.feedback(passback, buffer);
        if (isVerbose()) {
          LinearActivationLayer.log.debug(String.format("Feed back @ %s=>%s: %s => %s", this.inObj.data, Result.this.data, delta, passback));
        }
      } else {
        if (isVerbose()) {
          LinearActivationLayer.log.debug(String.format("Feed back via @ %s=>%s: %s => null", this.inObj.data, Result.this.data, delta));
        }
      }
    }

    @Override
    public boolean isAlive() {
      return this.inObj.isAlive() || !isFrozen();
    }

  }

  private static final Logger log = LoggerFactory.getLogger(LinearActivationLayer.class);

  /**
   * 
   */
  private static final long serialVersionUID = -2105152439043901220L;

  public final NDArray weights;

  public LinearActivationLayer() {
    super();
    this.weights = new NDArray(1);
    this.weights.set(0, 1.);
  }

  public LinearActivationLayer addWeights(final DoubleSupplier f) {
    Util.add(f, this.weights.getData());
    return this;
  }

  @Override
  public NNResult eval(final NNResult... inObj) {
    final NDArray input = inObj[0].data;
    final NDArray output = new NDArray(input.getDims());
    IntStream.range(0, input.dim()).forEach(i -> {
      final double a = this.weights.get(0);
      final double b = input.getData()[i];
      final double value = b * a;
      if (Double.isFinite(value)) {
        output.add(i, value);
      }
    });
    if (isVerbose()) {
      LinearActivationLayer.log.debug(String.format("Feed forward: %s * %s => %s", inObj[0].data, this.weights, output));
    }
    return new Result(output, inObj[0]);
  }

  @Override
  public JsonObject getJson() {
    final JsonObject json = super.getJson();
    json.addProperty("weights", this.weights.toString());
    return json;
  }

  protected double getMobility() {
    return 1;
  }

  public LinearActivationLayer setWeights(final double[] data) {
    this.weights.set(data);
    return this;
  }

  public LinearActivationLayer setWeights(final DoubleSupplier f) {
    Arrays.parallelSetAll(this.weights.getData(), i -> f.getAsDouble());
    return this;
  }

  @Override
  public List<double[]> state() {
    return Arrays.asList(this.weights.getData());
  }

}
