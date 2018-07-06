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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;

/**
 * An outlet that is a publisher.
 *
 * This is either the last outlet for a graph that has an outlet, or is used to connect a Processor or Publisher stage
 * in a graph.
 */
final class PublisherOutlet<T> implements StageOutlet<T>, Publisher<T>, Subscription, Port, UnrolledSignal {

  private final BuiltGraph builtGraph;

  private Subscriber<? super T> subscriber;
  private boolean pulled;
  private long demand;
  private boolean finished;
  private Throwable failure;
  private OutletListener listener;

  PublisherOutlet(BuiltGraph builtGraph) {
    this.builtGraph = builtGraph;
  }

  @Override
  public void onStreamFailure(Throwable reason) {
    if (!finished) {
      finished = true;
      demand = 0;
      if (subscriber != null) {
        try {
          subscriber.onError(reason);
        } catch (Exception e) {
          // Ignore
        }
      } else {
        failure = reason;
      }
      listener.onDownstreamFinish();
    }
  }

  @Override
  public void verifyReady() {
    if (listener == null) {
      throw new IllegalStateException("Cannot start stream without inlet listener set");
    }
  }

  @Override
  public void subscribe(Subscriber<? super T> subscriber) {
    Objects.requireNonNull(subscriber, "Subscriber must not be null");
    builtGraph.execute(() -> {
      if (this.subscriber != null) {
        subscriber.onSubscribe(new Subscription() {
          @Override
          public void request(long n) {
          }

          @Override
          public void cancel() {
          }
        });
        subscriber.onError(new IllegalStateException("This publisher only supports one subscriber"));
      } else {
        this.subscriber = subscriber;
        subscriber.onSubscribe(this);
        if (finished) {
          if (failure != null) {
            subscriber.onError(failure);
            failure = null;
            this.subscriber = null;
          } else {
            subscriber.onComplete();
            this.subscriber = null;
          }
        }
      }
    });
  }

  @Override
  public void request(long n) {
    builtGraph.execute(() -> {
      if (!finished) {
        if (n <= 0) {
          onStreamFailure(new IllegalArgumentException("Request demand must be greater than zero"));
        } else {
          boolean existingDemand = demand > 0;
          demand = demand + n;
          if (demand <= 0) {
            demand = Long.MAX_VALUE;
          }
          if (!existingDemand) {
            doPull();
          }
        }
      }
    });
  }

  @Override
  public void signal() {
    if (!finished && !pulled) {
      doPull();
    }
  }

  private void doPull() {
    pulled = true;
    listener.onPull();
  }

  @Override
  public void cancel() {
    builtGraph.execute(() -> {
      subscriber = null;
      if (!finished) {
        finished = true;
        demand = 0;
        listener.onDownstreamFinish();
      }
    });
  }

  @Override
  public void push(T element) {
    Objects.requireNonNull(element, "Elements cannot be null");
    if (finished) {
      throw new IllegalStateException("Can't push after publisher is finished");
    } else if (demand <= 0) {
      throw new IllegalStateException("Push without pull");
    }
    pulled = false;
    if (demand != Long.MAX_VALUE) {
      demand -= 1;
    }
    subscriber.onNext(element);
    if (demand > 0) {
      builtGraph.enqueueSignal(this);
    }
  }

  @Override
  public boolean isAvailable() {
    return !finished && pulled;
  }

  @Override
  public void complete() {
    if (finished) {
      throw new IllegalStateException("Can't complete twice");
    } else {
      finished = true;
      demand = 0;
      if (subscriber != null) {
        subscriber.onComplete();
        subscriber = null;
      }
    }
  }

  @Override
  public boolean isClosed() {
    return finished;
  }

  @Override
  public void fail(Throwable error) {
    Objects.requireNonNull(error, "Error must not be null");
    if (finished) {
      throw new IllegalStateException("Can't complete twice");
    } else {
      finished = true;
      demand = 0;
      if (subscriber != null) {
        subscriber.onError(error);
        subscriber = null;
      } else {
        failure = error;
      }
    }
  }

  @Override
  public void setListener(OutletListener listener) {
    this.listener = Objects.requireNonNull(listener, "Listener must not be null");
  }
}
