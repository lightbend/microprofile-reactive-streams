/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep;

/**
 * A signal, used by inlets and outlets.
 *
 * A signal represents a callback to an inlet or outlet listener. All callbacks should be wrapped in a signal to
 * allow signals to be enqueued, so that they can be executed in an unrolled fashion, rather than recursively.
 *
 * Implementations of signal are only allocated when a stream or sub stream starts. They should carry no state, any
 * state associated with the signal should be held by the inlet or outlet. This ensures that no allocations are done
 * of signals while running the stream.
 */
public interface Signal {
  /**
   * Invoke the signal.
   */
  void signal();

  /**
   * This will be invoked if {@link #signal()} throws an exception. Implementations of this should then terminate both
   * upstream and downstream. This mechanism means that {@code signal} doesn't have to do its own exception handling,
   * but rather exceptions can be handled generically by a stage.
   */
  void signalFailed(Throwable t);
}
