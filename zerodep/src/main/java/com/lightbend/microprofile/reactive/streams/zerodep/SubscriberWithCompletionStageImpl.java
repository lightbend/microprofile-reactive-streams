/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep;

import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.reactivestreams.Subscriber;

import java.util.concurrent.CompletionStage;

class SubscriberWithCompletionStageImpl<T, R> implements SubscriberWithCompletionStage<T, R> {
  private final Subscriber<T> subscriber;
  private final CompletionStage<R> completionStage;

  SubscriberWithCompletionStageImpl(Subscriber<T> subscriber, CompletionStage<R> completionStage) {
    this.subscriber = subscriber;
    this.completionStage = completionStage;
  }

  @Override
  public CompletionStage<R> getCompletion() {
    return completionStage;
  }

  @Override
  public Subscriber<T> getSubscriber() {
    return subscriber;
  }
}
