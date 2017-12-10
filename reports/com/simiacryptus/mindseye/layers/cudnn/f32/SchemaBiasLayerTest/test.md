# SchemaBiasLayer
## SchemaBiasLayerTest
### Json Serialization
Code from [LayerTestBase.java:83](../../../../../../../../src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L83) executed in 0.00 seconds: 
```java
    JsonObject json = layer.getJson();
    NNLayer echo = NNLayer.fromJson(json);
    assert (echo != null) : "Failed to deserialize";
    assert (layer != echo) : "Serialization did not copy";
    Assert.assertEquals("Serialization not equal", layer, echo);
    return new GsonBuilder().setPrettyPrinting().create().toJson(json);
```

Returns: 

```
    {
      "class": "com.simiacryptus.mindseye.layers.cudnn.f32.SchemaBiasLayer",
      "id": "a864e734-2f23-44db-97c1-5040000003ec",
      "isFrozen": false,
      "name": "SchemaBiasLayer/a864e734-2f23-44db-97c1-5040000003ec",
      "selected": [
        "test1",
        "test2"
      ],
      "features": {
        "test2": 0.0,
        "test1": 0.0
      }
    }
```



### Example Input/Output Pair
Code from [LayerTestBase.java:120](../../../../../../../../src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L120) executed in 0.00 seconds: 
```java
    SimpleEval eval = SimpleEval.run(layer, inputPrototype);
    return String.format("--------------------\nInput: \n[%s]\n--------------------\nOutput: \n%s",
      Arrays.stream(inputPrototype).map(t->t.prettyPrint()).reduce((a,b)->a+",\n"+b).get(),
      eval.getOutput().prettyPrint());
```

Returns: 

```
    --------------------
    Input: 
    [[
    	[ [ 1.384, 1.812 ], [ -0.616, 0.148 ], [ -1.508, -0.048 ] ],
    	[ [ -0.996, 0.024 ], [ 0.68, -1.456 ], [ 0.796, 0.808 ] ],
    	[ [ -1.752, 0.984 ], [ -1.172, 0.72 ], [ 1.472, 0.216 ] ]
    ]]
    --------------------
    Output: 
    [
    	[ [ 1.3839999437332153, 1.812000036239624 ], [ -0.6159999966621399, 0.14800000190734863 ], [ -1.5080000162124634, -0.04800000041723251 ] ],
    	[ [ -0.9959999918937683, 0.024000000208616257 ], [ 0.6800000071525574, -1.4559999704360962 ], [ 0.7960000038146973, 0.8080000281333923 ] ],
    	[ [ -1.7519999742507935, 0.984000027179718 ], [ -1.1720000505447388, 0.7200000286102295 ], [ 1.472000002861023, 0.2160000056028366 ] ]
    ]
```



### Batch Execution
Code from [LayerTestBase.java:138](../../../../../../../../src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L138) executed in 0.01 seconds: 
```java
    BatchingTester batchingTester = getBatchingTester();
    return batchingTester==null?null:batchingTester.test(layer, inputPrototype);
```

Returns: 

```
    ToleranceStatistics{absoluteTol=0.0000e+00 +- 0.0000e+00 [0.0000e+00 - 0.0000e+00] (360#), relativeTol=0.0000e+00 +- 0.0000e+00 [0.0000e+00 - 0.0000e+00] (360#)}
```



### Differential Validation
Code from [LayerTestBase.java:144](../../../../../../../../src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L144) executed in 0.05 seconds: 
```java
    return getDerivativeTester().test(layer, inputPrototype);
```
Logging: 
```
    Inputs: [
    	[ [ 1.384, 1.812 ], [ -0.616, 0.148 ], [ -1.508, -0.048 ] ],
    	[ [ -0.996, 0.024 ], [ 0.68, -1.456 ], [ 0.796, 0.808 ] ],
    	[ [ -1.752, 0.984 ], [ -1.172, 0.72 ], [ 1.472, 0.216 ] ]
    ]
    Inputs Statistics: {meanExponent=-0.21853358578943174, negative=7, min=0.216, max=0.216, mean=0.08311111111111115, count=18.0, positive=11, stdDev=1.0738504390426529, zeros=0}
    Output: [
    	[ [ 1.3839999437332153, 1.812000036239624 ], [ -0.6159999966621399, 0.14800000190734863 ], [ -1.5080000162124634, -0.04800000041723251 ] ],
    	[ [ -0.9959999918937683, 0.024000000208616257 ], [ 0.6800000071525574, -1.4559999704360962 ], [ 0.7960000038146973, 0.8080000281333923 ] ],
    	[ [ -1.7519999742507935, 0.984000027179718 ], [ -1.1720000505447388, 0.7200000286102295 ], [ 1.472000002861023, 0.2160000056028366 ] ]
    ]
    Outputs Statistics: {meanExponent=-0.21853358192166572, negative=7, min=0.2160000056028366, max=0.2160000056028366, mean=0.0831111158347792, count=18.0, positive=11, stdDev=1.0738504416206451, zeros=0}
    Feedback for input 0
    Inputs Values: [
    	[ [ 1.384, 1.812 ], [ -0.616, 0.148 ], [ -1.508, -0.048 ] ],
    	[ [ -0.996, 0.024 ], [ 0.68, -1.456 ], [ 0.796, 0.808 ] ],
    	[ [ -1.752, 0.984 ], [ -1.172, 0.72 ], [ 1.472, 0.216 ] ]
    ]
    Value Statistics: {meanExponent=-0.21853358578943174, negative=7, min=0.216, max=0.216, mean=0.08311111111111115, count=18.0, positive=11, stdDev=1.0738504390426529, zeros=0}
    Implemented Feedback: [ [ 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, ... ], [ 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, ... ], [ 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, ... ], [ 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, ... ], [ 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, ... ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, ... ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, ... ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, ... ], ... ]
    Implemented Statistics: {meanExponent=0.0, negative=0, min=1.0, max=1.0, mean=0.05555555555555555, count=324.0, positive=18, stdDev=0.2290614236454256, zeros=306}
    Measured Feedback: [ [ 1.0001659393310547, 0.0, 0.0, 0.0,
```
...[skipping 817 bytes](etc/1.txt)...
```
    0, 0.0, 0.0, 0.0, ... ], [ 0.0, 0.0, 1.659393310546875E-4, 0.0, 0.0, 0.0, 0.0, 0.0, ... ], [ 0.0, 0.0, 0.0, 1.659393310546875E-4, 0.0, 0.0, 0.0, 0.0, ... ], [ 0.0, 0.0, 0.0, 0.0, 1.659393310546875E-4, 0.0, 0.0, 0.0, ... ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 1.659393310546875E-4, 0.0, 0.0, ... ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.659393310546875E-4, 0.0, ... ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.659393310546875E-4, ... ], ... ]
    Error Statistics: {meanExponent=-3.9868837295980466, negative=4, min=1.6927719116210938E-5, max=1.6927719116210938E-5, mean=1.8027645570260507E-6, count=324.0, positive=14, stdDev=5.145057602843562E-5, zeros=306}
    Learning Gradient for weight set 0
    Weights: [ 0.0, 0.0 ]
    Implemented Gradient: [ [ 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, ... ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, ... ] ]
    Implemented Statistics: {meanExponent=0.0, negative=0, min=1.0, max=1.0, mean=0.5, count=36.0, positive=18, stdDev=0.5, zeros=18}
    Measured Gradient: [ [ 1.0001659393310547, 1.0001659393310547, 1.0001659393310547, 1.0001659393310547, 1.0001659393310547, 1.0001659393310547, 1.0001659393310547, 1.0001659393310547, ... ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, ... ] ]
    Measured Statistics: {meanExponent=5.7231372637685114E-5, negative=0, min=1.0000169277191162, max=1.0000169277191162, mean=0.500065895418326, count=36.0, positive=18, stdDev=0.5000658974659936, zeros=18}
    Gradient Error: [ [ 1.659393310546875E-4, 1.659393310546875E-4, 1.659393310546875E-4, 1.659393310546875E-4, 1.659393310546875E-4, 1.659393310546875E-4, 1.659393310546875E-4, 1.659393310546875E-4, ... ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, ... ] ]
    Error Statistics: {meanExponent=-4.05582161186394, negative=1, min=1.6927719116210938E-5, max=1.6927719116210938E-5, mean=6.589541832605998E-5, count=36.0, positive=17, stdDev=7.993837372178337E-5, zeros=18}
    Finite-Difference Derivative Accuracy:
    absoluteTol: 1.5399e-05 +- 5.6758e-05 [0.0000e+00 - 4.3011e-04] (360#)
    relativeTol: 7.6996e-05 +- 5.2144e-05 [8.4937e-07 - 2.1510e-04] (36#)
    
```

Returns: 

```
    ToleranceStatistics{absoluteTol=1.5399e-05 +- 5.6758e-05 [0.0000e+00 - 4.3011e-04] (360#), relativeTol=7.6996e-05 +- 5.2144e-05 [8.4937e-07 - 2.1510e-04] (36#)}
```



### Performance
Code from [LayerTestBase.java:149](../../../../../../../../src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L149) executed in 0.11 seconds: 
```java
    getPerformanceTester().test(layer, inputPrototype);
```
Logging: 
```
    Evaluation performance: 3.6226 +- 0.5008 [3.0749 - 5.5913]
    Learning performance: 3.7213 +- 0.3937 [3.1918 - 6.2296]
    
```
