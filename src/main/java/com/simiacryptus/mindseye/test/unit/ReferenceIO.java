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

package com.simiacryptus.mindseye.test.unit;

import com.simiacryptus.mindseye.lang.Layer;
import com.simiacryptus.mindseye.lang.ReferenceCounting;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.test.SimpleEval;
import com.simiacryptus.mindseye.test.ToleranceStatistics;
import com.simiacryptus.notebook.NotebookOutput;
import com.simiacryptus.util.data.DoubleStatistics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The type Reference io.
 */
public class ReferenceIO extends ComponentTestBase<ToleranceStatistics> {
  /**
   * The Reference io.
   */
  HashMap<Tensor[], Tensor> referenceIO;

  /**
   * Instantiates a new Reference io.
   *
   * @param referenceIO the reference io
   */
  public ReferenceIO(final HashMap<Tensor[], Tensor> referenceIO) {
    this.referenceIO = referenceIO;
  }

  @Override
  protected void _free() {
    referenceIO.keySet().stream().flatMap(x -> Arrays.stream(x)).forEach(ReferenceCounting::freeRef);
    referenceIO.values().forEach(ReferenceCounting::freeRef);
    super._free();
  }

  @Nullable
  @Override
  public ToleranceStatistics test(@Nonnull final NotebookOutput log, @Nonnull final Layer layer, @Nonnull final Tensor... inputPrototype) {
    if (!referenceIO.isEmpty()) {
      log.h1("Reference Input/Output Pairs");
      log.p("Display pre-setBytes input/output example pairs:");
      referenceIO.forEach((input, output) -> {
        log.eval(() -> {
          @Nonnull final SimpleEval eval = SimpleEval.run(layer, input);
          Tensor add = output.scale(-1).addAndFree(eval.getOutput());
          @Nonnull final DoubleStatistics error = new DoubleStatistics().accept(add.getData());
          add.freeRef();
          String format = String.format("--------------------\nInput: \n[%s]\n--------------------\nOutput: \n%s\nError: %s\n--------------------\nDerivative: \n%s",
              Arrays.stream(input).map(t -> t.prettyPrint()).reduce((a, b) -> a + ",\n" + b).get(),
              eval.getOutput().prettyPrint(), error,
              Arrays.stream(eval.getDerivative()).map(t -> t.prettyPrint()).reduce((a, b) -> a + ",\n" + b).get());
          eval.freeRef();
          return format;
        });
      });
    } else {
      log.h1("Example Input/Output Pair");
      log.p("Display input/output pairs from random executions:");
      log.eval(() -> {
        @Nonnull final SimpleEval eval = SimpleEval.run(layer, inputPrototype);
        String format = String.format("--------------------\nInput: \n[%s]\n--------------------\nOutput: \n%s\n--------------------\nDerivative: \n%s",
            Arrays.stream(inputPrototype).map(t -> t.prettyPrint()).reduce((a, b) -> a + ",\n" + b).orElse(""),
            eval.getOutput().prettyPrint(),
            Arrays.stream(eval.getDerivative()).map(t -> t.prettyPrint()).reduce((a, b) -> a + ",\n" + b).orElse(""));
        eval.freeRef();
        return format;
      });
    }
    return null;
  }

  @Nonnull
  @Override
  public String toString() {
    return "ReferenceIO{" +
        "referenceIO=" + referenceIO +
        '}';
  }
}
