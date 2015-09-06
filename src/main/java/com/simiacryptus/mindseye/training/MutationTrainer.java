package com.simiacryptus.mindseye.training;

import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.jblas.DoubleMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.layers.BiasLayer;
import com.simiacryptus.mindseye.layers.DenseSynapseLayer;
import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.math.Coordinate;
import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.training.TrainingContext.TerminationCondition;
import com.simiacryptus.mindseye.util.Util;

public class MutationTrainer {

  private static final Logger log = LoggerFactory.getLogger(MutationTrainer.class);

  private final DynamicRateTrainer inner = new DynamicRateTrainer();
  private int maxIterations = 100;
  private double mutationAmplitude = 5.;
  private double mutationFactor = .1;
  private double stopError = 0.1;
  private boolean verbose = false;

  public boolean continueTraining(final TrainingContext trainingContext, final DynamicRateTrainer dynamicRateTrainer, int currentGeneration) {
    if (this.maxIterations < currentGeneration) {
      if (this.verbose) {
        MutationTrainer.log.debug("Reached max iterations: " + currentGeneration);
      }
      return false;
    }
    final double error = dynamicRateTrainer.getGradientDescentTrainer().getError();
    if (error < this.stopError) {
      if (this.verbose) {
        MutationTrainer.log.debug("Reached convergence: " + error);
      }
      return false;
    }
    return true;
  }

  private double entropy(final BiasLayer l) {
    return 0;
  }

  private double entropy(final DenseSynapseLayer l, final Coordinate idx) {
    final NDArray weights = l.weights;
    final int[] dims = weights.getDims();
    final int columns = dims[0];
    final int rows = dims[1];
    final DoubleMatrix matrix = new DoubleMatrix(columns, rows, weights.getData()).transpose();
    // DoubleMatrix matrix = new DoubleMatrix(rows, columns,
    // l.weights.getData());
    return IntStream.range(0, rows).filter(i -> i == idx.coords[1]).mapToDouble(i -> i).flatMap(i -> {
      return IntStream.range(0, rows).mapToDouble(j -> {
        final ArrayRealVector vi = new ArrayRealVector(matrix.getRow((int) i).toArray());
        if (vi.getNorm() <= 0.)
          return 0.;
        vi.unitize();
        final ArrayRealVector vj = new ArrayRealVector(matrix.getRow(j).toArray());
        if (vj.getNorm() <= 0.)
          return 0.;
        vj.unitize();
        return Math.acos(vi.cosine(vj));
      });
    }).average().getAsDouble();
  }

  public DynamicRateTrainer getDynamicRateTrainer() {
    return this.inner;
  }

  public int getMaxIterations() {
    return this.maxIterations;
  }

  public double getMutationAmplitude() {
    return this.mutationAmplitude;
  }

  public double getMutationFactor() {
    return this.mutationFactor;
  }

  public DAGNetwork getNet() {
    return getDynamicRateTrainer().getGradientDescentTrainer().getNet();
  }

  public double getStopError() {
    return this.stopError;
  }

  protected int initialize(final BiasLayer l) {
    final double[] a = l.bias;
    final Random random = Util.R.get();
    int sum = 0;
    for (int i = 0; i < a.length; i++) {
      final double prev = a[i];
      final double prevEntropy = entropy(l);
      a[i] = randomWeight(l, random);
      final double nextEntropy = entropy(l);
      if (nextEntropy < prevEntropy) {
        a[i] = prev;
      } else {
        sum += 1;
      }
    }
    return sum;
  }

  protected int initialize(final DenseSynapseLayer l) {
    final double[] a = l.weights.getData();
    final Random random = Util.R.get();
    return l.weights.coordStream().mapToInt(idx -> {
      final int i = idx.index;
      final double prev = a[i];
      final double prevEntropy = entropy(l, idx);
      a[i] = randomWeight(l, random);
      final double nextEntropy = entropy(l, idx);
      if (nextEntropy < prevEntropy) {
        a[i] = prev;
        return 0;
      } else
        return 1;
    }).sum();
  }

  protected void initialize(final DynamicRateTrainer dynamicRateTrainer) {
    if (this.verbose) {
      MutationTrainer.log.debug(String.format("Initialize %s", dynamicRateTrainer));
    }
    final List<NNLayer> layers = dynamicRateTrainer.getGradientDescentTrainer().getNet().getChildren();
    for (int i = 0; i < 5; i++) {
      layers.stream().filter(l -> (l instanceof DenseSynapseLayer)).map(l -> (DenseSynapseLayer) l).filter(l -> !l.isFrozen()).forEach(this::initialize);
      layers.stream().filter(l -> (l instanceof BiasLayer)).map(l -> (BiasLayer) l).filter(l -> !l.isFrozen()).forEach(this::initialize);
    }
    dynamicRateTrainer.getGradientDescentTrainer().setError(Double.NaN);
  }

  public boolean isVerbose() {
    return this.verbose;
  }

  protected int mutate(final BiasLayer l, final double amount) {
    final double[] a = l.bias;
    final Random random = Util.R.get();
    int sum = 0;
    for (int i = 0; i < a.length; i++) {
      if (random.nextDouble() < amount) {
        final double prev = a[i];
        final double prevEntropy = entropy(l);
        a[i] = randomWeight(l, random);
        final double nextEntropy = entropy(l);
        if (nextEntropy < prevEntropy) {
          a[i] = prev;
        } else {
          sum += 1;
        }
      }
    }
    return sum;
  }

  protected int mutate(final DenseSynapseLayer l, final double amount) {
    final double[] a = l.weights.getData();
    final Random random = Util.R.get();
    return l.weights.coordStream().mapToInt(idx -> {
      final int i = idx.index;
      if (random.nextDouble() < amount) {
        final double prev = a[i];
        final double prevEntropy = entropy(l, idx);
        a[i] = randomWeight(l, random);
        final double nextEntropy = entropy(l, idx);
        if (nextEntropy < prevEntropy) {
          a[i] = prev;
          return 0;
        } else
          return 1;
      } else
        return 0;
    }).sum();
  }

  private void mutate(final DynamicRateTrainer dynamicRateTrainer) {
    dynamicRateTrainer.generationsSinceImprovement = dynamicRateTrainer.getRecalibrationThreshold() - 1;
    boolean continueLoop = true;
    while (continueLoop) {
      final double amount = getMutationFactor();
      if (this.verbose) {
        MutationTrainer.log.debug(String.format("Mutating %s by %s", dynamicRateTrainer, amount));
      }
      final List<NNLayer> layers = dynamicRateTrainer.getGradientDescentTrainer().getNet().getChildren();
      final Stream<DenseSynapseLayer> layers1 = layers.stream().filter(l -> (l instanceof DenseSynapseLayer)).map(l -> (DenseSynapseLayer) l).filter(l -> !l.isFrozen());
      final Stream<BiasLayer> layers2 = layers.stream().filter(l -> (l instanceof BiasLayer)).map(l -> (BiasLayer) l).filter(l -> !l.isFrozen());
      final int sum = layers1.mapToInt(l -> mutate(l, amount)).sum() + layers2.mapToInt(l -> mutate(l, amount)).sum();
      dynamicRateTrainer.getGradientDescentTrainer().setError(Double.NaN);
      continueLoop = (0 == sum);
    }
    dynamicRateTrainer.lastCalibratedIteration = dynamicRateTrainer.currentIteration;
  }

  protected double randomWeight(final BiasLayer l, final Random random) {
    return this.mutationAmplitude * random.nextGaussian() * 0.2;
  }

  protected double randomWeight(final DenseSynapseLayer l, final Random random) {
    return this.mutationAmplitude * random.nextGaussian() / Math.sqrt(l.weights.getDims()[0]);
  }

  public MutationTrainer setGenerationsSinceImprovement(final int generationsSinceImprovement) {
    getDynamicRateTrainer().generationsSinceImprovement = generationsSinceImprovement;
    return this;
  }

  public MutationTrainer setMaxIterations(final int maxIterations) {
    this.maxIterations = maxIterations;
    return this;
  }

  public MutationTrainer setMutationAmplitude(final double mutationAmplitude) {
    this.mutationAmplitude = mutationAmplitude;
    return this;
  }

  public void setMutationFactor(final double mutationRate) {
    this.mutationFactor = mutationRate;
  }

  public MutationTrainer setStopError(final double stopError) {
    getDynamicRateTrainer().setStopError(stopError);
    this.stopError = stopError;
    return this;
  }

  public MutationTrainer setVerbose(final boolean verbose) {
    this.verbose = verbose;
    getDynamicRateTrainer().setVerbose(verbose);
    return this;
  }

  public boolean test(final int maxIter, final double convergence, final TrainingContext trainingContext, final List<BiFunction<DAGNetwork, TrainingContext, Void>> handler) {
    boolean hasConverged = false;
    try {
      final Double error = trainingContext.overallTimer.time(() -> {
        return setMaxIterations(maxIter).setStopError(convergence).train(trainingContext);
      });
      final DAGNetwork net = getNet();
      handler.stream().forEach(h -> h.apply(net, trainingContext));
      hasConverged = error <= convergence;
      if (!hasConverged) {
        Tester.log.debug(String.format("Not Converged: %s <= %s", error, convergence));
      }
    } catch (final Throwable e) {
      Tester.log.debug("Not Converged", e);
    }
    return hasConverged;
  }

  public Double train(final TrainingContext trainingContext) {
    final long startMs = System.currentTimeMillis();
    final DynamicRateTrainer dynamicRateTrainer = getDynamicRateTrainer();
    int currentGeneration = trainIndividual(trainingContext, dynamicRateTrainer);
    final GradientDescentTrainer gradientDescentTrainer = dynamicRateTrainer.getGradientDescentTrainer();
    MutationTrainer.log.info(String.format("Completed training to %.5f in %.03fs (%s iterations) - %s", gradientDescentTrainer.getError(),
        (System.currentTimeMillis() - startMs) / 1000., currentGeneration, trainingContext));
    return null == gradientDescentTrainer ? Double.POSITIVE_INFINITY : gradientDescentTrainer.getError();
  }

  private int trainIndividual(final TrainingContext trainingContext, final DynamicRateTrainer dynamicRateTrainer) {
    int currentGeneration = 0;
    DAGNetwork initial = null;
    try {
      while (continueTraining(trainingContext, dynamicRateTrainer, currentGeneration)) {
        if (0 == currentGeneration++) {
          initialize(dynamicRateTrainer);
          initial = Util.kryo().copy(dynamicRateTrainer.getGradientDescentTrainer().getNet());
        } else {
          trainingContext.mutations.increment();
          dynamicRateTrainer.getGradientDescentTrainer().setNet(Util.kryo().copy(initial));
          mutate(dynamicRateTrainer);
        }
        dynamicRateTrainer.trainToLocalOptimum(trainingContext);
        if (this.verbose) {
          final GradientDescentTrainer gradientDescentTrainer = dynamicRateTrainer.getGradientDescentTrainer();
          MutationTrainer.log.debug(String.format("Trained Iteration %s Error: %s with rate %s\n%s", currentGeneration, gradientDescentTrainer.getError(),
              gradientDescentTrainer.getRate(), gradientDescentTrainer.getNet()));
        }
      }
    } catch (final TerminationCondition e) {
      MutationTrainer.log.debug("Terminated training", e);
    }
    return currentGeneration;
  }
}
