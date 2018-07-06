/*******************************************************************************
 * Copyright (c) 2018 Lightbend Inc.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.lightbend.microprofile.reactive.streams.zerodep;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Processor that wraps a subscriber and publisher.
 */
public class WrappedProcessor<T, R> implements Processor<T, R> {
  private final Subscriber<T> subscriber;
  private final Publisher<R> publisher;

  public WrappedProcessor(Subscriber<T> subscriber, Publisher<R> publisher) {
    this.subscriber = subscriber;
    this.publisher = publisher;
  }

  @Override
  public void subscribe(Subscriber<? super R> subscriber) {
    publisher.subscribe(subscriber);
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    subscriber.onSubscribe(subscription);
  }

  @Override
  public void onNext(T item) {
    subscriber.onNext(item);
  }

  @Override
  public void onError(Throwable throwable) {
    subscriber.onError(throwable);
  }

  @Override
  public void onComplete() {
    subscriber.onComplete();
  }
}

