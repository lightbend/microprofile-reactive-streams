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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

class FindFirstStage<T> extends GraphStage implements InletListener {

  private final StageInlet<T> inlet;
  private final CompletableFuture<Optional<T>> result;

  FindFirstStage(BuiltGraph builtGraph, StageInlet<T> inlet, CompletableFuture<Optional<T>> result) {
    super(builtGraph);
    this.inlet = inlet;
    this.result = result;

    inlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!inlet.isClosed()) {
      inlet.pull();
    }
  }

  @Override
  public void onPush() {
    result.complete(Optional.of(inlet.grab()));
    inlet.cancel();
  }

  @Override
  public void onUpstreamFinish() {
    result.complete(Optional.empty());
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    result.completeExceptionally(error);
  }
}
