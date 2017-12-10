# ProductInputsLayer
## ProductInputsLayerTest
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
      "class": "com.simiacryptus.mindseye.layers.cudnn.f32.ProductInputsLayer",
      "id": "a864e734-2f23-44db-97c1-5040000003db",
      "isFrozen": false,
      "name": "ProductInputsLayer/a864e734-2f23-44db-97c1-5040000003db"
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
    	[ [ 0.568, -0.524 ], [ -1.684, 1.848 ] ],
    	[ [ -1.42, -0.06 ], [ 0.124, -1.412 ] ]
    ],
    [
    	[ [ 1.684, 1.412 ], [ 0.536, -0.268 ] ],
    	[ [ 1.276, -0.776 ], [ 0.904, -1.396 ] ]
    ]]
    --------------------
    Output: 
    [
    	[ [ 0.9565120339393616, -0.7398879528045654 ], [ -0.9026240110397339, -0.4952640235424042 ] ],
    	[ [ -1.811919927597046, 0.046560000628232956 ], [ 0.11209599673748016, 1.9711519479751587 ] ]
    ]
```



### Batch Execution
Code from [LayerTestBase.java:138](../../../../../../../../src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L138) executed in 0.02 seconds: 
```java
    BatchingTester batchingTester = getBatchingTester();
    return batchingTester==null?null:batchingTester.test(layer, inputPrototype);
```

Returns: 

```
    ToleranceStatistics{absoluteTol=0.0000e+00 +- 0.0000e+00 [0.0000e+00 - 0.0000e+00] (240#), relativeTol=0.0000e+00 +- 0.0000e+00 [0.0000e+00 - 0.0000e+00] (240#)}
```



### Differential Validation
Code from [LayerTestBase.java:144](../../../../../../../../src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L144) executed in 0.05 seconds: 
```java
    return getDerivativeTester().test(layer, inputPrototype);
```
Logging: 
```
    Inputs: [
    	[ [ 0.568, -0.524 ], [ -1.684, 1.848 ] ],
    	[ [ -1.42, -0.06 ], [ 0.124, -1.412 ] ]
    ],
    [
    	[ [ 1.684, 1.412 ], [ 0.536, -0.268 ] ],
    	[ [ 1.276, -0.776 ], [ 0.904, -1.396 ] ]
    ]
    Inputs Statistics: {meanExponent=-0.232447543326602, negative=5, min=-1.412, max=-1.412, mean=-0.31999999999999995, count=8.0, positive=3, stdDev=1.1212760587830277, zeros=0},
    {meanExponent=-0.04621967349914849, negative=3, min=-1.396, max=-1.396, mean=0.4215000000000001, count=8.0, positive=5, stdDev=1.0466497742798206, zeros=0}
    Output: [
    	[ [ 0.9565120339393616, -0.7398879528045654 ], [ -0.9026240110397339, -0.4952640235424042 ] ],
    	[ [ -1.811919927597046, 0.046560000628232956 ], [ 0.11209599673748016, 1.9711519479751587 ] ]
    ]
    Outputs Statistics: {meanExponent=-0.2786672195674059, negative=4, min=1.9711519479751587, max=1.9711519479751587, mean=-0.1079219919629395, count=8.0, positive=4, stdDev=1.0961532701609091, zeros=0}
    Feedback for input 0
    Inputs Values: [
    	[ [ 0.568, -0.524 ], [ -1.684, 1.848 ] ],
    	[ [ -1.42, -0.06 ], [ 0.124, -1.412 ] ]
    ]
    Value Statistics: {meanExponent=-0.232447543326602, negative=5, min=-1.412, max=-1.412, mean=-0.31999999999999995, count=8.0, positive=3, stdDev=1.1212760587830277, zeros=0}
    Implemented Feedback: [ [ 1.684000015258789, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 1.2760000228881836, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.5360000133514404, 0.0, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.9039999842643738, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 1.4119999408721924, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, -0.7760000228881836, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -0.2680000066757202, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.3960000276565552 ] ]
    Implemented Statistics: {meanExponent=-0.04621966987052171, negative=3, min=-1.3960000276565552, max=-1.3960000276565552, mean=0.05268749874085188, count=64.0, positive=5, stdDev=0.39543176172337047, zeros=56}
    Measured Feedback: [ [ 1.6832351684570312, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 1.2755393981933594, 0.0, 
```
...[skipping 1906 bytes](etc/1.txt)...
```
    0.5239999890327454, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, -0.05999999865889549, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.8480000495910645, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.4119999408721924 ] ]
    Implemented Statistics: {meanExponent=-0.2324475467513131, negative=5, min=-1.4119999408721924, max=-1.4119999408721924, mean=-0.03999999741790816, count=64.0, positive=3, stdDev=0.4103139036155082, zeros=56}
    Measured Feedback: [ [ 0.5680322647094727, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, -1.4209747314453125, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, -1.6832351684570312, 0.0, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.12405216693878174, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, -0.5239248275756836, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, -0.060014426708221436, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.8483400344848633, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.4126300811767578 ] ]
    Measured Statistics: {meanExponent=-0.23236956593290098, negative=5, min=-1.4126300811767578, max=-1.4126300811767578, mean=-0.040005543269217014, count=64.0, positive=3, stdDev=0.4103743454113181, zeros=56}
    Feedback Error: [ [ 3.224611282348633E-5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, -9.747743606567383E-4, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 7.648468017578125E-4, 0.0, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 5.2168965339660645E-5, 0.0, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 7.516145706176758E-5, 0.0, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, -1.4428049325942993E-5, 0.0, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 3.399848937988281E-4, 0.0 ], [ 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -6.301403045654297E-4 ] ]
    Error Statistics: {meanExponent=-3.816941520797231, negative=3, min=-6.301403045654297E-4, max=-6.301403045654297E-4, mean=-5.545851308852434E-6, count=64.0, positive=5, stdDev=1.7921236651240942E-4, zeros=56}
    Finite-Difference Derivative Accuracy:
    absoluteTol: 4.6618e-05 +- 1.8348e-04 [0.0000e+00 - 1.2513e-03] (128#)
    relativeTol: 1.9089e-04 +- 1.7786e-04 [1.1638e-05 - 7.0040e-04] (16#)
    
```

Returns: 

```
    ToleranceStatistics{absoluteTol=4.6618e-05 +- 1.8348e-04 [0.0000e+00 - 1.2513e-03] (128#), relativeTol=1.9089e-04 +- 1.7786e-04 [1.1638e-05 - 7.0040e-04] (16#)}
```



### Performance
Code from [LayerTestBase.java:149](../../../../../../../../src/test/java/com/simiacryptus/mindseye/layers/LayerTestBase.java#L149) executed in 0.09 seconds: 
```java
    getPerformanceTester().test(layer, inputPrototype);
```
Logging: 
```
    Evaluation performance: 4.4666 +- 0.9855 [3.5565 - 10.0968]
    Learning performance: 0.5515 +- 0.1521 [0.2992 - 1.3650]
    
```
