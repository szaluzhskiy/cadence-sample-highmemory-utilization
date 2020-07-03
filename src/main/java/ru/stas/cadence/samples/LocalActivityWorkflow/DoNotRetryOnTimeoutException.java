package ru.stas.cadence.samples.LocalActivityWorkflow;

import java.util.concurrent.TimeoutException;

public class DoNotRetryOnTimeoutException extends RuntimeException {
  public DoNotRetryOnTimeoutException(TimeoutException e) {
    super(e);
  }

  public DoNotRetryOnTimeoutException(RuntimeException e) {
    super(e);
  }
}
