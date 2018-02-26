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
import com.simiacryptus.mindseye.models.VGG16;
import com.simiacryptus.mindseye.test.NotebookReportBase;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.util.FastRandom;
import com.simiacryptus.util.data.DoubleStatistics;

import javax.annotation.Nonnull;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.stream.IntStream;

public class ArtistryDemo extends NotebookReportBase {
  
  public void paint_noise(final Tensor canvas) {
    canvas.setByCoord(c -> FastRandom.random());
  }
  
  public void paint_LowRes(final Tensor canvas) {
    int scale = 16;
    BufferedImage originalImage = canvas.toImage();
    canvas.set(Tensor.fromRGB(TestUtil.resize(
      TestUtil.resize(originalImage, originalImage.getWidth() / scale, true),
      originalImage.getWidth(), originalImage.getHeight())));
  }
  
  public void paint_Lines(final Tensor canvas) {
    BufferedImage originalImage = canvas.toImage();
    BufferedImage newImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = (Graphics2D) newImage.getGraphics();
    IntStream.range(0, 100).forEach(i -> {
      Random random = new Random();
      graphics.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
      graphics.drawLine(
        random.nextInt(originalImage.getWidth()),
        random.nextInt(originalImage.getHeight()),
        random.nextInt(originalImage.getWidth()),
        random.nextInt(originalImage.getHeight())
      );
    });
    canvas.set(Tensor.fromRGB(newImage));
  }
  
  public void paint_Circles(final Tensor canvas) {
    int scale = 16;
    BufferedImage originalImage = canvas.toImage();
    BufferedImage newImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = (Graphics2D) newImage.getGraphics();
    IntStream.range(0, 10000).forEach(i -> {
      Random random = new Random();
      int positionX = random.nextInt(originalImage.getWidth());
      int positionY = random.nextInt(originalImage.getHeight());
      int width = 1 + random.nextInt(2 * scale);
      int height = 1 + random.nextInt(2 * scale);
      DoubleStatistics[] stats = {
        new DoubleStatistics(),
        new DoubleStatistics(),
        new DoubleStatistics()
      };
      canvas.coordStream(false).filter(c -> {
        int[] coords = c.getCoords();
        int x = coords[0];
        int y = coords[1];
        double relX = Math.pow(1 - 2 * ((double) (x - positionX) / width), 2);
        double relY = Math.pow(1 - 2 * ((double) (y - positionY) / height), 2);
        return relX + relY < 1.0;
      }).forEach(c -> stats[c.getCoords()[2]].accept(canvas.get(c)));
      graphics.setStroke(new Stroke() {
        @Override
        public Shape createStrokedShape(final Shape p) {
          return null;
        }
      });
      graphics.setColor(new Color(
        (int) stats[0].getAverage(),
        (int) stats[1].getAverage(),
        (int) stats[2].getAverage()
      ));
      graphics.fillOval(
        positionX,
        positionY,
        width,
        height
      );
    });
    canvas.set(Tensor.fromRGB(newImage));
  }
  
  /**
   * Gets target class.
   *
   * @return the target class
   */
  @Nonnull
  protected Class<?> getTargetClass() {
    return VGG16.class;
  }
  
  @Nonnull
  @Override
  public ReportType getReportType() {
    return ReportType.Demos;
  }
}