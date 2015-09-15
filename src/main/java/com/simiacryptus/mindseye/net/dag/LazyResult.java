package com.simiacryptus.mindseye.net.dag;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.deltas.NNResult;

public abstract class LazyResult {

  public final UUID key = UUID.randomUUID();

  public LazyResult() {
    super();
  }

  protected abstract NNResult[] eval(EvaluationContext t);

  public NNResult[] get(final EvaluationContext t) {
    return t.cache.computeIfAbsent(this.key, k -> eval(t));
  }

  protected abstract JsonObject toJson();
}
