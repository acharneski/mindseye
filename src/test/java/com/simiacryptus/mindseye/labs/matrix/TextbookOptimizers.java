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

package com.simiacryptus.mindseye.labs.matrix;

import com.simiacryptus.mindseye.opt.ValidatingTrainer;
import com.simiacryptus.mindseye.opt.line.ArmijoWolfeSearch;
import com.simiacryptus.mindseye.opt.line.QuadraticSearch;
import com.simiacryptus.mindseye.opt.line.StaticLearningRate;
import com.simiacryptus.mindseye.opt.orient.GradientDescent;
import com.simiacryptus.mindseye.opt.orient.LBFGS;
import com.simiacryptus.mindseye.opt.orient.MomentumStrategy;
import com.simiacryptus.mindseye.opt.orient.OwlQn;
import com.simiacryptus.mindseye.test.ProblemRun;
import com.simiacryptus.mindseye.test.StepRecord;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.mindseye.test.integration.MnistProblemData;
import com.simiacryptus.mindseye.test.integration.OptimizationStrategy;
import com.simiacryptus.notebook.NotebookOutput;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

/**
 * We compare a variety of conventional "textbook" optimizer configurations against a standard optimization benchmarking
 * suite.
 */
public class TextbookOptimizers extends OptimizerComparison {

  /**
   * The constant conjugate_gradient_descent.
   */
  @Nonnull
  public static OptimizationStrategy conjugate_gradient_descent = (log, trainingSubject, validationSubject, monitor) -> {
    log.p("Optimized via the Conjugate Gradient Descent method:");
    return log.eval(() -> {
      @Nonnull final ValidatingTrainer trainer = new ValidatingTrainer(trainingSubject, validationSubject)
          .setMinTrainingSize(Integer.MAX_VALUE)
          .setMonitor(monitor);
      trainer.getRegimen().get(0)
          .setOrientation(new GradientDescent())
          .setLineSearchFactory(name -> new QuadraticSearch().setRelativeTolerance(1e-5));
      return trainer;
    });
  };
  /**
   * The constant limited_memory_bfgs.
   */
  @Nonnull
  public static OptimizationStrategy limited_memory_bfgs = (log, trainingSubject, validationSubject, monitor) -> {
    log.p("Optimized via the Limited-Memory BFGS method:");
    return log.eval(() -> {
      @Nonnull final ValidatingTrainer trainer = new ValidatingTrainer(trainingSubject, validationSubject)
          .setMinTrainingSize(Integer.MAX_VALUE)
          .setMonitor(monitor);
      trainer.getRegimen().get(0)
          .setOrientation(new LBFGS())
          .setLineSearchFactory(name -> new ArmijoWolfeSearch()
              .setAlpha(name.toString().contains("LBFGS") ? 1.0 : 1e-6));
      return trainer;
    });
  };
  /**
   * The constant orthantwise_quasi_newton.
   */
  @Nonnull
  public static OptimizationStrategy orthantwise_quasi_newton = (log, trainingSubject, validationSubject, monitor) -> {
    log.p("Optimized via the Orthantwise Quasi-Newton search method:");
    return log.eval(() -> {
      @Nonnull final ValidatingTrainer trainer = new ValidatingTrainer(trainingSubject, validationSubject)
          .setMinTrainingSize(Integer.MAX_VALUE)
          .setMonitor(monitor);
      trainer.getRegimen().get(0)
          .setOrientation(new OwlQn())
          .setLineSearchFactory(name -> new ArmijoWolfeSearch()
              .setAlpha(name.toString().contains("OWL") ? 1.0 : 1e-6));
      return trainer;
    });
  };
  /**
   * The constant simple_gradient_descent.
   */
  @Nonnull
  public static OptimizationStrategy simple_gradient_descent = (log, trainingSubject, validationSubject, monitor) -> {
    log.p("Optimized via the Stochastic Gradient Descent method:");
    return log.eval(() -> {
      final double rate = 0.05;
      @Nonnull final ValidatingTrainer trainer = new ValidatingTrainer(trainingSubject, validationSubject)
          .setMinTrainingSize(Integer.MAX_VALUE)
          .setMaxEpochIterations(100)
          .setMonitor(monitor);
      trainer.getRegimen().get(0)
          .setOrientation(new GradientDescent())
          .setLineSearchFactory(name -> new StaticLearningRate(rate));
      return trainer;
    });
  };
  /**
   * The constant stochastic_gradient_descent.
   */
  @Nonnull
  public static OptimizationStrategy stochastic_gradient_descent = (log, trainingSubject, validationSubject, monitor) -> {
    log.p("Optimized via the Stochastic Gradient Descent method apply momentum and adaptve learning rate:");
    return log.eval(() -> {
      final double carryOver = 0.5;
      @Nonnull final ValidatingTrainer trainer = new ValidatingTrainer(trainingSubject, validationSubject)
          .setMaxEpochIterations(100)
          .setMonitor(monitor);
      trainer.getRegimen().get(0)
          .setOrientation(new MomentumStrategy(new GradientDescent()).setCarryOver(carryOver))
          .setLineSearchFactory(name -> new ArmijoWolfeSearch());
      return trainer;
    });
  };

  /**
   * Instantiates a new Compare textbook.
   */
  public TextbookOptimizers() {
    super(MnistTests.fwd_conv_1, MnistTests.rev_conv_1, new MnistProblemData());
  }

  @Override
  public void compare(@Nonnull final NotebookOutput log, @Nonnull final Function<OptimizationStrategy, List<StepRecord>> test) {
    log.h1("Textbook Optimizer Comparison");
    log.h2("GD");
    @Nonnull final ProblemRun gd = new ProblemRun("GD", test.apply(TextbookOptimizers.simple_gradient_descent),
        Color.BLACK, ProblemRun.PlotType.Line);
    log.h2("SGD");
    @Nonnull final ProblemRun sgd = new ProblemRun("SGD", test.apply(TextbookOptimizers.stochastic_gradient_descent),
        Color.GREEN, ProblemRun.PlotType.Line);
    log.h2("CGD");
    @Nonnull final ProblemRun cgd = new ProblemRun("CjGD", test.apply(TextbookOptimizers.conjugate_gradient_descent),
        Color.BLUE, ProblemRun.PlotType.Line);
    log.h2("L-BFGS");
    @Nonnull final ProblemRun lbfgs = new ProblemRun("L-BFGS", test.apply(TextbookOptimizers.limited_memory_bfgs),
        Color.MAGENTA, ProblemRun.PlotType.Line);
    log.h2("OWL-QN");
    @Nonnull final ProblemRun owlqn = new ProblemRun("OWL-QN", test.apply(TextbookOptimizers.orthantwise_quasi_newton),
        Color.ORANGE, ProblemRun.PlotType.Line);
    log.h2("Comparison");
    log.eval(() -> {
      return TestUtil.compare("Convergence Plot", gd, sgd, cgd, lbfgs, owlqn);
    });
    log.eval(() -> {
      return TestUtil.compareTime("Convergence Plot", gd, sgd, cgd, lbfgs, owlqn);
    });
  }
}
