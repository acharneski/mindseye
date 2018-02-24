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

package com.simiacryptus.mindseye.demo;

import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.lang.cudnn.CudaSystem;
import com.simiacryptus.mindseye.models.VGG16;
import com.simiacryptus.mindseye.models.VGG16_HDF5;
import com.simiacryptus.mindseye.test.NotebookReportBase;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.mindseye.test.data.Caltech101;
import com.simiacryptus.util.io.NotebookOutput;
import org.junit.Test;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The type Deep dream demo.
 */
public class DeepDreamDemo extends NotebookReportBase {
  
  /**
   * Run.
   */
  @Test
  public void run() {
    run(this::run);
  }
  
  /**
   * Run.
   *
   * @param log the log
   */
  public void run(@javax.annotation.Nonnull NotebookOutput log) {
    
    
    @javax.annotation.Nonnull String logName = "cuda_" + log.getName() + ".log";
    log.p(log.file((String) null, logName, "GPU Log"));
    CudaSystem.addLog(new PrintStream(log.file(logName)));
    
    log.h1("Model");
    VGG16_HDF5 vgg16 = log.code(() -> {
      return VGG16.fromS3_HDF5().setLarge(false);
    });
    
    log.h1("Data");
    Tensor[] images = log.code(() -> {
      return Caltech101.trainingDataStream().sorted(getShuffleComparator()).map(labeledObj -> {
        @Nullable BufferedImage img = labeledObj.data.get();
        //img = TestUtil.resize(img, 224);
        return Tensor.fromRGB(img);
      }).limit(50).toArray(i1 -> new Tensor[i1]);
    });
    
    List<String> vgg16Categories = vgg16.getCategories();
    for (int itemNumber = 0; itemNumber < images.length; itemNumber++) {
      log.h1("Image " + itemNumber);
      Tensor image = images[itemNumber];
      TestUtil.monitorUI(image);
      List<String> categories = vgg16.predict(5, image).stream().flatMap(x -> x.keySet().stream()).collect(Collectors.toList());
      log.p("Predictions: %s", categories);
      log.p("Evolve from %s to %s", categories.get(0), categories.get(1));
      int targetCategoryIndex = vgg16Categories.indexOf(categories.get(1));
      int totalCategories = vgg16Categories.size();
      vgg16.deepDream(log, image, targetCategoryIndex, totalCategories);
      try {
        log.p(log.image(image.toImage(), "result"));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
    log.setFrontMatterProperty("status", "OK");
  }
  
  /**
   * Gets shuffle comparator.
   *
   * @param <T> the type parameter
   * @return the shuffle comparator
   */
  public <T> Comparator<T> getShuffleComparator() {
    final int seed = (int) ((System.nanoTime() >>> 8) % (Integer.MAX_VALUE - 84));
    return Comparator.comparingInt(a1 -> System.identityHashCode(a1) ^ seed);
  }
  
  @javax.annotation.Nonnull
  protected Class<?> getTargetClass() {
    return VGG16.class;
  }
  
  @javax.annotation.Nonnull
  @Override
  public ReportType getReportType() {
    return ReportType.Demos;
  }
}
