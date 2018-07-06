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

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Flat maps to completion stages of elements.
 */
class FlatMapCompletionStage<T, R> extends GraphStage implements InletListener {
  private final StageInlet<T> inlet;
  private final StageOutlet<R> outlet;
  private final Function<T, CompletionStage<R>> mapper;

  private Throwable error;

  FlatMapCompletionStage(BuiltGraph builtGraph, StageInlet<T> inlet, StageOutlet<R> outlet, Function<T, CompletionStage<R>> mapper) {
    super(builtGraph);
    this.inlet = inlet;
    this.outlet = outlet;
    this.mapper = mapper;

    inlet.setListener(this);
    outlet.forwardTo(inlet);
  }

  @Override
  public void onPush() {
    CompletionStage<R> future = mapper.apply(inlet.grab());
    future.whenCompleteAsync((result, error) -> {
      if (!outlet.isClosed()) {
        if (error == null) {
          outlet.push(result);
          if (inlet.isClosed()) {
            if (this.error != null) {
              outlet.fail(this.error);
            } else {
              outlet.complete();
            }
          }
        } else {

          outlet.fail(error);
          if (!inlet.isClosed()) {
            inlet.cancel();
          }
        }
      }
    }, executor());
  }

  @Override
  public void onUpstreamFinish() {
    if (!activeCompletionStage()) {
      outlet.complete();
    }
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    if (activeCompletionStage()) {
      this.error = error;
    } else {
      outlet.fail(error);
    }
  }

  private boolean activeCompletionStage() {
    return outlet.isAvailable() && !inlet.isPulled();
  }
}
