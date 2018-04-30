/******************************************************************************
 * Licensed under Public Domain (CC0)                                         *
 *                                                                            *
 * To the extent possible under law, the person who associated CC0 with       *
 * this code has waived all copyright and related or neighboring              *
 * rights to this code.                                                       *
 *                                                                            *
 * You should have received a copy of the CC0 legalcode along with this       *
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.     *
 ******************************************************************************/

package com.lightbend.microprofile.reactive.streams.rxjava;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class TerminationWatchingSubscriber<T> implements Subscriber<T> {

  private final CompletableFuture<Void> termination = new CompletableFuture<>();
  private final Subscriber<T> delegate;

  TerminationWatchingSubscriber(Subscriber<T> delegate) {
    this.delegate = delegate;
  }

  CompletionStage<Void> getTermination() {
    return termination;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    Objects.requireNonNull(subscription, "Subscription must not be null");
    delegate.onSubscribe(new SubscriptionWrapper(subscription));
  }

  @Override
  public void onNext(T item) {
    delegate.onNext(item);
  }

  @Override
  public void onError(Throwable throwable) {
    termination.completeExceptionally(throwable);
    delegate.onError(throwable);
  }

  @Override
  public void onComplete() {
    termination.complete(null);
    delegate.onComplete();
  }

  private class SubscriptionWrapper implements Subscription {
    private final Subscription delegate;

    public SubscriptionWrapper(Subscription delegate) {
      this.delegate = delegate;
    }

    @Override
    public void request(long n) {
      delegate.request(n);
    }

    @Override
    public void cancel() {
      termination.completeExceptionally(new CancellationException());
      delegate.cancel();

    }
  }
}
