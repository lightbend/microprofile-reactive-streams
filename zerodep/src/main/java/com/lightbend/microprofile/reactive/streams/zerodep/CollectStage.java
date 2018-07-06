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

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;

/**
 * Stage that collects elements into a collector.
 */
class CollectStage<T, A, R> extends GraphStage implements InletListener {
  private final StageInlet<T> inlet;
  private final CompletableFuture<R> result;
  private final Collector<T, A, R> collector;
  private A container;

  public CollectStage(BuiltGraph builtGraph, StageInlet<T> inlet,
      CompletableFuture<R> result, Collector<T, A, R> collector) {
    super(builtGraph);
    this.inlet = inlet;
    this.result = result;
    this.collector = collector;

    container = collector.supplier().get();
    inlet.setListener(this);
  }

  @Override
  protected void postStart() {
    // It's possible that an earlier stage finished immediately, so check first
    if (!inlet.isClosed()) {
      inlet.pull();
    }
  }

  @Override
  public void onPush() {
    collector.accumulator().accept(container, inlet.grab());
    inlet.pull();
  }

  @Override
  public void onUpstreamFinish() {
    result.complete(collector.finisher().apply(container));
    container = null;
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    result.completeExceptionally(error);
    container = null;
  }
}
