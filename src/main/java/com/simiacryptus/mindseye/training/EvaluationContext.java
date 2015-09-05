package com.simiacryptus.mindseye.training;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;

public class EvaluationContext {
  
  public static abstract class LazyResult<T> {

    UUID key = UUID.randomUUID();

    public LazyResult() {
      super();
    }

    @SuppressWarnings("unchecked")
    public T get(final EvaluationContext t) {
      return (T) t.cache.computeIfAbsent(this.key, k -> initialValue(t));
    }

    protected abstract T initialValue(EvaluationContext t);

    protected abstract JsonObject toJson();
  }
  
  public final Map<UUID, Object> cache = new HashMap<>();

}