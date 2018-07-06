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

/**
 * A failed stage. Does nothing but fails the stream when the graph starts.
 */
class FailedStage extends GraphStage implements OutletListener {
  private final Throwable error;
  private final StageOutlet<?> outlet;

  public FailedStage(BuiltGraph builtGraph, StageOutlet<?> outlet, Throwable error) {
    super(builtGraph);
    this.outlet = outlet;
    this.error = error;

    outlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!outlet.isClosed()) {
      outlet.fail(error);
    }
  }

  @Override
  public void onPull() {
  }

  @Override
  public void onDownstreamFinish() {
  }
}
