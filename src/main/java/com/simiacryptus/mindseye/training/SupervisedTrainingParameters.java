package com.simiacryptus.mindseye.training;

import com.simiacryptus.mindseye.NDArray;
import com.simiacryptus.mindseye.learning.NNResult;

public class SupervisedTrainingParameters {
  private double weight = 1;
  private PipelineNetwork net;
  private final NDArray[][] trainingData;
  
  protected SupervisedTrainingParameters() {
    super();
    this.net = null;
    this.trainingData = null;
  }

  public SupervisedTrainingParameters(PipelineNetwork net, NDArray[][] trainingData) {
    this.net = net;
    this.trainingData = trainingData;
  }
  
  public PipelineNetwork getNet() {
    return net;
  }
  
  public void setNet(PipelineNetwork net) {
    this.net = net;
  }
  
  public final NDArray[][] getTrainingData() {
    return trainingData;
  }

  public double getWeight() {
    return weight;
  }

  public SupervisedTrainingParameters setWeight(double weight) {
    this.weight = weight;
    return this;
  }

  public NDArray getIdeal(NNResult eval, NDArray preset) {
    return preset;
  }
}