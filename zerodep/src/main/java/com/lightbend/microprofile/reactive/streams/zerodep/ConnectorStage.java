/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.microprofile.reactive.streams.zerodep;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Connector stage. Does nothing but connects a publisher to a subscriber when the graph starts.
 */
public class ConnectorStage<T> extends GraphStage {
  private final Publisher<T> publisher;
  private final Subscriber<T> subscriber;

  public ConnectorStage(BuiltGraph builtGraph, Publisher<T> publisher, Subscriber<T> subscriber) {
    super(builtGraph);
    this.publisher = publisher;
    this.subscriber = subscriber;
  }

  @Override
  protected void postStart() {
    publisher.subscribe(subscriber);
  }
}
