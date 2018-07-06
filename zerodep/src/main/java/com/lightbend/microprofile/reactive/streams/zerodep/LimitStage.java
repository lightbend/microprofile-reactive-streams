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
 * Take while stage.
 */
class LimitStage<T> extends GraphStage implements InletListener {
  private final StageInlet<T> inlet;
  private final StageOutlet<T> outlet;
  private final long maxSize;
  private long count = 0;

  LimitStage(BuiltGraph builtGraph, StageInlet<T> inlet, StageOutlet<T> outlet, long maxSize) {
    super(builtGraph);
    this.inlet = inlet;
    this.outlet = outlet;
    this.maxSize = maxSize;

    inlet.setListener(this);
    outlet.forwardTo(inlet);
  }

  @Override
  protected void postStart() {
    if (maxSize == 0) {
      closeAll();
    }
  }

  @Override
  public void onPush() {
    T element = inlet.grab();
    count++;
    if (count <= maxSize) {
      outlet.push(element);
      if (count == maxSize) {
        closeAll();
      }
    }
  }

  private void closeAll() {
    if (!outlet.isClosed()) outlet.complete();
    if (!inlet.isClosed()) inlet.cancel();
  }

  @Override
  public void onUpstreamFinish() {
    outlet.complete();
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    outlet.fail(error);
  }
}
