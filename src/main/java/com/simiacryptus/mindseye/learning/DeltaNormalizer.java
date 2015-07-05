package com.simiacryptus.mindseye.learning;

import com.simiacryptus.mindseye.NDArray;

public class DeltaNormalizer implements DeltaSink {
  
  private DeltaSink sink;
  private double[] values;
  private double alpha = 2.;
  private double beta = 4.;
  
  public DeltaNormalizer() {
    super();
  }
  
  public DeltaNormalizer(final double[] values) {
    this(new DeltaMemoryWriter(values), values);
  }
  
  public DeltaNormalizer(final DeltaSink sink, final double[] values) {
    this.sink = sink;
    this.values = values;
  }
  
  public DeltaNormalizer(final NDArray values) {
    this(values.data);
  }
  
  private boolean enabled = false;
  
  @Override
  public void feed(final double[] data) {
    
    final int dim = length();
    double[] v = new double[data.length];
    for (int i = 0; i < dim; i++) {
      double f;
      if (enabled) {
        double before = Math.log(Math.abs(values[i]) / alpha) * beta;
        double after = Math.log(Math.abs(values[i] + data[i]) / alpha) * beta;
        f = before > after ? 1 : Math.min(Math.abs(1. / (Math.exp(after) - Math.exp(before))), 1.);
        assert Double.isFinite(f);
        assert f <= 1.0;
      }
      else
      {
        f = 1.;
      }
      v[i] += f * data[i];
    }
    this.sink.feed(v);
  }
  
  @Override
  public int length() {
    return values.length;
  }
  
  public double getAlpha() {
    return alpha;
  }
  
  public DeltaNormalizer setAlpha(double alpha) {
    this.alpha = alpha;
    return this;
  }
  
  public double getBeta() {
    return beta;
  }
  
  public DeltaNormalizer setBeta(double beta) {
    this.beta = beta;
    return this;
  }
  
  public boolean isEnabled() {
    return enabled;
  }
  
  public DeltaNormalizer setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }
  
}