package ru.stas.cadence.samples.utils;

import brave.Tracer;
import brave.Tracing;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpringBeans implements ApplicationContextAware {
  private Tracer tracer;
  private Tracing tracing;
  private static ApplicationContext ac;

  public static Tracer getTracer() {
    return ac.getBean(Tracer.class);
  }

  public static Tracing getTracing() {
    return ac.getBean(Tracing.class);
  }

  @Override
  public void setApplicationContext(ApplicationContext ac) {
    this.ac = ac;
  }

}
