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

package com.simiacryptus.mindseye.layers.java;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * This wrapper adds a highly verbose amount of logging to System.out detailing all inputs and outputs during forward
 * and backwards evaluation. Intended as a diagnostic and demonstration tool.
 */
@SuppressWarnings("serial")
public final class LoggingWrapperLayer extends WrapperLayer {
  /**
   * The Logger.
   */
  static final Logger log = LoggerFactory.getLogger(LoggingWrapperLayer.class);


  /**
   * Instantiates a new Monitoring wrapper key.
   *
   * @param json the json
   * @param rs   the rs
   */
  protected LoggingWrapperLayer(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    super(json, rs);
  }

  /**
   * Instantiates a new Monitoring wrapper key.
   *
   * @param inner the heapCopy
   */
  public LoggingWrapperLayer(final Layer inner) {
    super(inner);
  }

  /**
   * From json monitoring wrapper key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the monitoring wrapper key
   */
  public static LoggingWrapperLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new LoggingWrapperLayer(json, rs);
  }

  @Override
  public Result eval(@Nonnull final Result... inObj) {
    final Result[] wrappedInput = IntStream.range(0, inObj.length).mapToObj(i -> {
      final Result inputToWrap = inObj[i];
      inputToWrap.addRef();
      return new Result(inputToWrap.getData(), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList data) -> {
        @Nonnull final String formatted = data.stream().map(x -> {
          String str = x.prettyPrint();
          x.freeRef();
          return str;
        })
            .reduce((a, b) -> a + "\n" + b).get();
        log.info(String.format("Feedback Output %s for key %s: \n\t%s", i, getInner().getName(), formatted.replaceAll("\n", "\n\t")));
        data.addRef();
        inputToWrap.accumulate(buffer, data);
      }) {

        @Override
        protected void _free() {
          inputToWrap.freeRef();
        }

        @Override
        public boolean isAlive() {
          return inputToWrap.isAlive();
        }
      };
    }).toArray(i -> new Result[i]);
    for (int i = 0; i < inObj.length; i++) {
      final TensorList tensorList = inObj[i].getData();
      @Nonnull final String formatted = tensorList.stream().map(x -> {
        String str = x.prettyPrint();
        x.freeRef();
        return str;
      }).reduce((a, b) -> a + "\n" + b).get();
      log.info(String.format("Input %s for key %s: \n\t%s", i, getInner().getName(), formatted.replaceAll("\n", "\n\t")));
    }
    @Nullable final Result output = getInner().eval(wrappedInput);
    Arrays.stream(wrappedInput).forEach(ReferenceCounting::freeRef);
    {
      final TensorList tensorList = output.getData();
      @Nonnull final String formatted = tensorList.stream().map(x -> {
        String str = x.prettyPrint();
        x.freeRef();
        return str;
      })
          .reduce((a, b) -> a + "\n" + b).get();
      log.info(String.format("Output for key %s: \n\t%s", getInner().getName(), formatted.replaceAll("\n", "\n\t")));
    }
    return new Result(output.getData(), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList data) -> {
      @Nonnull final String formatted = data.stream().map(x -> {
        String str = x.prettyPrint();
        x.freeRef();
        return str;
      })
          .reduce((a, b) -> a + "\n" + b).get();
      log.info(String.format("Feedback Input for key %s: \n\t%s", getInner().getName(), formatted.replaceAll("\n", "\n\t")));
      data.addRef();
      output.accumulate(buffer, data);
    }) {

      @Override
      protected void _free() {
        output.freeRef();
      }


      @Override
      public boolean isAlive() {
        return output.isAlive();
      }
    };
  }

}
