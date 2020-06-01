package ru.stas.cadence.samples.propagator;

import brave.Span;
import brave.Tracer;
import brave.propagation.TraceContext;
import com.uber.cadence.context.ContextPropagator;
import ru.stas.cadence.samples.utils.SpringBeans;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TracingContextPropagator implements ContextPropagator {
  private final Tracer tracer = SpringBeans.getTracer();

  @Override
  public String getName() {
    return this.getClass().getName();
  }

  @Override
  public Object getCurrentContext() {
    TraceContext tc;
    if (tracer.currentSpan() == null) {
      tc = tracer.newTrace().context();
    } else {
      tc= tracer.currentSpan().context();
    }

    Map<String, String> context = new HashMap<>();
    context.put("traceId", String.valueOf(tc.traceId()));
    context.put("spanId", String.valueOf(tc.spanId()));
    return context;
  }

  @Override
  public void setCurrentContext(Object context) {
    Map<String, String> contextMap = (Map<String, String>)context;
    TraceContext tc = TraceContext.newBuilder()
        .traceId(Long.parseLong(contextMap.get("traceId")))
        .spanId(Long.parseLong(contextMap.get("spanId")))
        .build();
    Span span = this.tracer.newChild(tc);
    span.name("cadence").start();
    this.tracer.withSpanInScope(span);
  }

  public Map<String, byte[]> serializeContext(Object context) {
    Map<String, String> contextMap = (Map<String, String>)context;
    Map<String, byte[]> serializedContext = new HashMap<>();
    for (Map.Entry<String, String> entry : contextMap.entrySet()) {
      serializedContext.put(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
    }
    return serializedContext;
  }

  public Object deserializeContext(Map<String, byte[]> context) {
    Map<String, String> contextMap = new HashMap<>();
    for (Map.Entry<String, byte[]> entry : context.entrySet()) {
      contextMap.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));
    }
    return contextMap;
  }
}
