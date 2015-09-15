package com.simiacryptus.mindseye.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.PointValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.deltas.DeltaBuffer;
import com.simiacryptus.mindseye.deltas.DeltaFlushBuffer;
import com.simiacryptus.mindseye.deltas.NNResult;
import com.simiacryptus.mindseye.math.MultivariateOptimizer;
import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.training.GradientDescentTrainer.ValidationResults;
import com.simiacryptus.mindseye.training.TrainingContext.TerminationCondition;
import com.simiacryptus.mindseye.util.Util;

import groovy.lang.Tuple2;

public class DynamicRateTrainer {
  public static class RateMonitor {
    private double counter0 = 0;
    private double counter1 = 0;
    private final double halfLifeMs;
    private long lastUpdateTime = System.currentTimeMillis();
    public final long startTime = System.currentTimeMillis();

    public RateMonitor(final double halfLifeMs) {
      super();
      this.halfLifeMs = halfLifeMs;
    }

    public double add(final double value) {
      final long prevUpdateTime = this.lastUpdateTime;
      final long now = System.currentTimeMillis();
      this.lastUpdateTime = now;
      final long elapsedMs = now - prevUpdateTime;
      final double elapsedHalflifes = elapsedMs / this.halfLifeMs;
      this.counter0 += elapsedMs;
      this.counter1 += value;
      final double v = this.counter1 / this.counter0;
      final double f = Math.pow(0.5, elapsedHalflifes);
      this.counter0 *= f;
      this.counter1 *= f;
      return v;
    }
  }

  public static class UniformAdaptiveRateParams {
    public final double alpha;
    public final double beta;
    public final double endRate;
    public final double startRate;
    public final double terminalETA;
    public final double terminalLearningRate;

    public UniformAdaptiveRateParams(final double startRate, final double endRate, final double alpha, final double beta, final double convergence, final double terminalETA) {
      this.endRate = endRate;
      this.alpha = alpha;
      this.beta = beta;
      this.startRate = startRate;
      this.terminalLearningRate = convergence;
      this.terminalETA = terminalETA;
    }
  }

  private static final Logger log = LoggerFactory.getLogger(DynamicRateTrainer.class);

  int currentIteration = 0;
  private long etaSec = java.util.concurrent.TimeUnit.HOURS.toSeconds(1);
  int generationsSinceImprovement = 0;
  private final GradientDescentTrainer inner = new GradientDescentTrainer();
  int lastCalibratedIteration = Integer.MIN_VALUE;
  int maxIterations = 100;
  private double maxRate = 10000;
  private double minRate = 0;
  private int recalibrationInterval = 10;
  private int recalibrationThreshold = 0;
  private double stopError = 1e-2;

  private boolean verbose = false;

  protected MultivariateFunction asMetaF(final DeltaBuffer lessonVector, final TrainingContext trainingContext) {
    final List<DeltaFlushBuffer> vector = lessonVector.vector();
    if (isVerbose()) {
      final String toString = vector.stream().map(x -> x.toString()).reduce((a, b) -> a + "\n\t" + b).get();
      log.debug(String.format("Optimizing delta vector set: \n\t%s", toString));
    }

    final GradientDescentTrainer gradientDescentTrainer = getGradientDescentTrainer();
    final double prev = gradientDescentTrainer.getError();
    final MultivariateFunction f = new MultivariateFunction() {
      double[] pos = new double[vector.size()];

      @Override
      public double value(final double x[]) {
        final int layerCount = vector.size();
        final double[] layerRates = Arrays.copyOf(x, layerCount);
        assert layerRates.length == this.pos.length;
        for (int i = 0; i < layerRates.length; i++) {
          final double prev = this.pos[i];
          final double next = layerRates[i];
          final double adj = next - prev;
          vector.get(i).write(adj);
        }
        for (int i = 0; i < layerRates.length; i++) {
          this.pos[i] = layerRates[i];
        }
        ValidationResults evalValidationData = gradientDescentTrainer.evalValidationData(trainingContext);
        if (isVerbose()) {
          DynamicRateTrainer.log.debug(String.format("f[%s] = %s (%s)", Arrays.toString(layerRates), evalValidationData.rms, prev - evalValidationData.rms));
        }
        return evalValidationData.rms;
      }
    };
    return f;
  }

  protected synchronized double calcSieves(final TrainingContext trainingContext) {
    final GradientDescentTrainer gradientDescentTrainer = getGradientDescentTrainer();
    {
      final NDArray[][] data = gradientDescentTrainer.getConstraintData(trainingContext);
      final ValidationResults results = gradientDescentTrainer.evalValidationData(trainingContext, data);
      updateConstraintSieve(results.stats);
    }
    {
      final NDArray[][] data = gradientDescentTrainer.getTrainingData(gradientDescentTrainer.getTrainingSet());
      final ValidationResults results = gradientDescentTrainer.evalValidationData(trainingContext, data);
      updateTrainingSieve(results.stats);
    }
    {
      final NDArray[][] data = gradientDescentTrainer.getValidationData(trainingContext);
      final ValidationResults results = gradientDescentTrainer.evalValidationData(trainingContext, data);
      updateValidationSieve(results.stats);
      return Util.rms(trainingContext, results.stats, gradientDescentTrainer.getValidationSet());
    }
  }

  protected synchronized boolean calibrate(final TrainingContext trainingContext) {
    synchronized (trainingContext) {
      trainingContext.calibrations.increment();
      final GradientDescentTrainer gradientDescentTrainer = getGradientDescentTrainer();
      gradientDescentTrainer.setTrainingSet(null);
      gradientDescentTrainer.setValidationSet(null);
      gradientDescentTrainer.setConstraintSet(new int[] {});
      // trainingContext.calcSieves(getInner());
      try {
        final double[] key = optimizeRates(trainingContext);
        if (setRates(trainingContext, key)) {
          calcSieves(trainingContext);
          this.lastCalibratedIteration = this.currentIteration;
          final double improvement = -gradientDescentTrainer.step(trainingContext);
          if (isVerbose()) {
            DynamicRateTrainer.log.debug(String.format("Adjusting rates by %s: (%s improvement)", Arrays.toString(key), improvement));
          }
          return true;
        }
      } catch (final Exception e) {
        if (isVerbose()) {
          DynamicRateTrainer.log.debug("Error calibrating", e);
        }
      }
      if (isVerbose()) {
        DynamicRateTrainer.log.debug(String.format("Calibration rejected at %s with %s error", //
            Arrays.toString(gradientDescentTrainer.getRates()), //
            gradientDescentTrainer.getError()));
      }
      return false;
    }
  }

  public long getEtaMs() {
    return this.etaSec;
  }

  public int getGenerationsSinceImprovement() {
    return this.generationsSinceImprovement;
  }

  public GradientDescentTrainer getGradientDescentTrainer() {
    return this.inner;
  }

  public double getMaxRate() {
    return this.maxRate;
  }

  public double getMinRate() {
    return this.minRate;
  }

  public int getRecalibrationThreshold() {
    return this.recalibrationThreshold;
  }

  public double getStopError() {
    return this.stopError;
  }

  public boolean isVerbose() {
    return this.verbose;
  }

  private double[] optimizeRates(final TrainingContext trainingContext) {
    final GradientDescentTrainer inner = getGradientDescentTrainer();
    final NDArray[][] validationSet = inner.getValidationData(trainingContext);
    inner.evalValidationData(trainingContext, validationSet);
    final double prev = inner.getError();
    // regenDataSieve(trainingContext);

    final DeltaBuffer lessonVector = inner.getVector(trainingContext);
    final MultivariateFunction f = asMetaF(lessonVector, trainingContext);
    final int numberOfParameters = lessonVector.vector().size();

    final PointValuePair x = new MultivariateOptimizer(f).setMaxRate(getMaxRate()).minimize(numberOfParameters);
    f.value(x.getFirst()); // Leave in optimal state
    // f.value(new double[numberOfParameters]); // Reset to original state

    inner.evalValidationData(trainingContext, validationSet);
    final double calcError = inner.getError();
    if (this.verbose) {
      DynamicRateTrainer.log.debug(String.format("Terminated search at position: %s (%s), error %s->%s", Arrays.toString(x.getKey()), x.getValue(), prev, calcError));
    }
    return x.getKey();
  }

  private int probeRateCount(final TrainingContext trainingContext) {
    final GradientDescentTrainer gradientDescentTrainer = getGradientDescentTrainer();
    final NNResult probe = gradientDescentTrainer.getNet().eval(getGradientDescentTrainer().getTrainingData(trainingContext)[0][0]);
    final DeltaBuffer buffer = new DeltaBuffer();
    probe.feedback(new NDArray(probe.data.getDims()), buffer);
    final int rateNumber = buffer.vector().size();
    return rateNumber;
  }

  protected boolean recalibrateWRetry(final TrainingContext trainingContext) {
    int retry = 0;
    while (!calibrate(trainingContext)) {
      DynamicRateTrainer.log.debug("Failed recalibration at iteration " + this.currentIteration);
      if (++retry > 0)
        return false;
    }
    return true;
  }

  public DynamicRateTrainer setEtaEnd(final long cnt, final java.util.concurrent.TimeUnit units) {
    this.etaSec = units.toSeconds(cnt);
    return this;
  }

  public DynamicRateTrainer setGenerationsSinceImprovement(final int generationsSinceImprovement) {
    this.generationsSinceImprovement = generationsSinceImprovement;
    return this;
  }

  public DynamicRateTrainer setMaxRate(final double maxRate) {
    this.maxRate = maxRate;
    return this;
  }

  public DynamicRateTrainer setMinRate(final double minRate) {
    this.minRate = minRate;
    return this;
  }

  private boolean setRates(final TrainingContext trainingContext, final double[] rates) {
    final boolean inBounds = DoubleStream.of(rates).allMatch(r -> getMaxRate() > r) && DoubleStream.of(rates).anyMatch(r -> getMinRate() < r);
    if (inBounds) {
      getGradientDescentTrainer().setRates(rates);
      return true;
    }
    return false;
  }

  public DynamicRateTrainer setRecalibrationThreshold(final int recalibrationThreshold) {
    this.recalibrationThreshold = recalibrationThreshold;
    return this;
  }

  public DynamicRateTrainer setStopError(final double stopError) {
    this.stopError = stopError;
    return this;
  }

  public DynamicRateTrainer setVerbose(final boolean verbose) {
    this.verbose = verbose;
    getGradientDescentTrainer().setVerbose(verbose);
    return this;
  }

  public boolean train(final TrainingContext trainingContext) throws TerminationCondition {
    this.currentIteration = 0;
    this.generationsSinceImprovement = 0;
    this.lastCalibratedIteration = Integer.MIN_VALUE;
    int lifecycle = 0;
    do {
      train(trainingContext, new UniformAdaptiveRateParams(0.1, 1e-9, 1.5, 2., this.stopError, getEtaMs()));
    } while (lifecycle++ < 1 && null != getGradientDescentTrainer().getNet().evolve());
    // train2(trainingContext);
    return false;
  }

  private void train(final TrainingContext trainingContext, final UniformAdaptiveRateParams params) {
    calcSieves(trainingContext);
    final int rateNumber = probeRateCount(trainingContext);
    final GradientDescentTrainer gradientDescentTrainer = getGradientDescentTrainer();
    double rate = params.startRate;
    final RateMonitor linearLearningRate = new RateMonitor(params.terminalETA / 32);
    while (gradientDescentTrainer.getError() > params.terminalLearningRate) {
      final double rate1 = rate;
      setRates(trainingContext, IntStream.range(0, rateNumber).mapToDouble(x -> rate1).toArray());
      final double delta = gradientDescentTrainer.step(trainingContext);
      final double error = gradientDescentTrainer.getError();
      final double rateDelta = linearLearningRate.add(delta);
      final double projectedEndSeconds = -error / (rateDelta * 1000.);
      if (isVerbose()) {
        log.debug(String.format("Projected final convergence time: %.3f sec; %s - %s/sec", projectedEndSeconds, error, rateDelta));
      }
      if (trainingContext.timeout < System.currentTimeMillis()) {
        log.debug(String.format("TIMEOUT; current err: %s", error));
        break;
      }
      if (projectedEndSeconds > params.terminalETA) {
        log.debug(String.format("TERMINAL Projected final convergence time: %.3f sec", projectedEndSeconds));
        break;
      }
      if (error < params.terminalLearningRate) {
        if (isVerbose()) {
        }
        log.debug(String.format("TERMINAL Final err: %s", error));
      }
      if (0. <= delta) {
        calcSieves(trainingContext);
        rate /= Math.pow(params.alpha, params.beta);
      } else if (0. > delta) {
        rate *= params.alpha;
      } else {
        assert false;
      }
      if (rate < params.endRate) {
        if (isVerbose()) {
        }
        log.debug(String.format("TERMINAL rate underflow: %s", rate));
        break;
      }
    }
    if (isVerbose()) {
      DynamicRateTrainer.log.debug("Final network state: " + getGradientDescentTrainer().getNet().toString());
    }
  }

  public boolean train2(final TrainingContext trainingContext) throws TerminationCondition {
    this.currentIteration = 0;
    this.generationsSinceImprovement = 0;
    this.lastCalibratedIteration = Integer.MIN_VALUE;
    while (true) {
      final GradientDescentTrainer gradientDescentTrainer = getGradientDescentTrainer();
      if (getStopError() > gradientDescentTrainer.getError()) {
        DynamicRateTrainer.log.debug("Target error reached: " + gradientDescentTrainer.getError());
        return false;
      }
      if (this.maxIterations <= this.currentIteration++) {
        DynamicRateTrainer.log.debug("Maximum recalibrations reached: " + this.currentIteration);
        return false;
      }
      if (this.lastCalibratedIteration < this.currentIteration - this.recalibrationInterval) {
        if (isVerbose()) {
          DynamicRateTrainer.log.debug("Recalibrating learning rate due to interation schedule at " + this.currentIteration);
          DynamicRateTrainer.log.debug("Network state: " + getGradientDescentTrainer().getNet().toString());
        }
        if (!recalibrateWRetry(trainingContext))
          return false;
        this.generationsSinceImprovement = 0;
      }
      if (0. != gradientDescentTrainer.step(trainingContext)) {
        this.generationsSinceImprovement = 0;
      } else {
        if (getRecalibrationThreshold() < this.generationsSinceImprovement++) {
          if (isVerbose()) {
            DynamicRateTrainer.log.debug("Recalibrating learning rate due to non-descending step");
            DynamicRateTrainer.log.debug("Network state: " + getGradientDescentTrainer().getNet().toString());
          }
          if (!recalibrateWRetry(trainingContext))
            return false;
          this.generationsSinceImprovement = 0;
        }
      }
    }
  }

  protected void updateConstraintSieve(final List<Tuple2<Double, Double>> rms) {
    getGradientDescentTrainer().setConstraintSet(IntStream.range(0, rms.size()).mapToObj(i -> new Tuple2<>(i, rms.get(0))) //
        // .sorted(Comparator.comparing(t ->
        // t.getSecond().getFirst())).limit(50)
        // .filter(t -> t.getSecond().getFirst() > 0.8)
        .mapToInt(t -> t.getFirst()).toArray());
  }

  protected void updateTrainingSieve(final List<Tuple2<Double, Double>> rms) {
    getGradientDescentTrainer().setTrainingSet(IntStream.range(0, rms.size()).mapToObj(i -> new Tuple2<>(i, rms.get(0))) //
        // .filter(t -> t.getSecond().getFirst() < 0.0)
        // .filter(t -> 1.8 * Math.random() > -0.5 - t.getSecond().getFirst())
        // .sorted(Comparator.comparing(t ->
        // -t.getSecond().getFirst())).limit(100)
        .mapToInt(t -> t.getFirst()).toArray());
  }

  protected void updateValidationSieve(final List<Tuple2<Double, Double>> rms) {
    final List<Tuple2<Integer, Tuple2<Double, Double>>> collect = new ArrayList<>(
        IntStream.range(0, rms.size()).mapToObj(i -> new Tuple2<>(i, rms.get(0))).collect(Collectors.toList()));
    Collections.shuffle(collect);
    getGradientDescentTrainer().setValidationSet(collect.stream() //
        // .limit(100)
        // .filter(t -> t.getSecond().getFirst() < -0.3)
        // .filter(t -> 0.5 * Math.random() > -0. - t.getSecond().getFirst())
        // .sorted(Comparator.comparing(t ->
        // -t.getSecond().getFirst())).limit(100)
        // .sorted(Comparator.comparing(t ->
        // -t.getSecond().getFirst())).limit(500)
        .mapToInt(t -> t.getFirst()).toArray());
  }

}
