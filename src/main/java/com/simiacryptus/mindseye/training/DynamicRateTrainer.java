package com.simiacryptus.mindseye.training;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicRateTrainer {

  private static final Logger log = LoggerFactory.getLogger(DynamicRateTrainer.class);
  
  private double maxRate = 1.;
  private double minRate = 0;
  private double rateAdaptionRate = 0.1;
  private double rate = 1.;

  public final ChampionTrainer inner;
  private boolean verbose = false;

  public DynamicRateTrainer() {
    this(new ChampionTrainer());
  }

  public DynamicRateTrainer(ChampionTrainer inner) {
    this.inner = inner;
  }

  public boolean isVerbose() {
    return this.verbose;
  }

  public DynamicRateTrainer setVerbose(final boolean verbose) {
    this.verbose = verbose;
    return this;
  }


  int lastCalibratedIteration = Integer.MIN_VALUE;
  int currentIteration = 0;
  
  public DynamicRateTrainer train() {
    if(lastCalibratedIteration < (currentIteration++ - 50)){
      log.debug("Recalibrating learning rate due to interation schedule");
      calibrate();
    }
    double lastError = inner.current.error();
    inner.train();
    double resultError = inner.current.error();
    if(resultError > lastError)
    {
      log.debug("Recalibrating learning rate due to non-descending step");
      calibrate();
    }
    //updateRate(lastError, resultError);
    return this;
  }

  public void calibrate() {
    double currentRate = inner.current.getRate();
    double adjustment = inner.current.trainLineSearch();
    if(Double.isFinite(adjustment))
    {
      inner.current.setRate(currentRate * adjustment);
    } else {
      log.debug("Local Optimum reach - gradient not useful. Now for mutation-based random search!");
      inner.revert();
      inner.current.mutate(0.2);
    }
    lastCalibratedIteration = currentIteration;
  }

  public double getRateAdaptionRate() {
    return rateAdaptionRate;
  }

  public DynamicRateTrainer setRateAdaptionRate(double rateAdaptionRate) {
    this.rateAdaptionRate = rateAdaptionRate;
    return this;
  }

  public double getMinRate() {
    return minRate;
  }

  public DynamicRateTrainer setMinRate(double minRate) {
    this.minRate = minRate;
    return this;
  }

  public double getRate() {
    return rate;
  }

  public DynamicRateTrainer setRate(double rate) {
    this.rate = rate;
    return this;
  }

//  public void updateRate(final double lastError, final double thisError) {
//    final double improvement = lastError - thisError;
//    final double expectedImprovement = lastError * this.rate;// / (50 + currentGeneration);
//    final double idealRate = inner.current.getRate() * expectedImprovement / improvement;
//    final double prevRate = inner.current.getRate();
//    if (isVerbose()) {
//      log.debug(String.format("Ideal Rate: %s (target %s change, actual %s with %s rate)", idealRate, expectedImprovement, improvement, prevRate));
//    }
//    double newRate = 0;
//    if (Double.isFinite(idealRate)) {
//      newRate = inner.current.getRate() + this.rateAdaptionRate * (Math.max(Math.min(idealRate, this.maxRate), this.minRate) - prevRate);
//      inner.current.setRate(newRate);
//    }
//    if (isVerbose()) {
//      log.debug(String.format("Rate %s -> %s", prevRate, newRate));
//    }
//  }

  public double error() {
    return inner.current.error();
  }

  public GradientDescentTrainer getBest() {
    return inner.getBest();
  }

  public double getMaxRate() {
    return maxRate;
  }

  public DynamicRateTrainer setMaxRate(double maxRate) {
    this.maxRate = maxRate;
    return this;
  }

}