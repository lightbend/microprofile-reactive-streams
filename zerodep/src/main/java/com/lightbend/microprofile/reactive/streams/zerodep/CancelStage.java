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

/**
 * A cancel stage.
 */
class CancelStage extends GraphStage implements InletListener {
  private final StageInlet<?> inlet;
  private final CompletableFuture<Void> result;

  CancelStage(BuiltGraph builtGraph, StageInlet<?> inlet, CompletableFuture<Void> result) {
    super(builtGraph);
    this.inlet = inlet;
    this.result = result;

    inlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!inlet.isClosed()) {
      inlet.cancel();
    }
    result.complete(null);
  }

  @Override
  public void onPush() {
  }

  @Override
  public void onUpstreamFinish() {
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
  }
}
