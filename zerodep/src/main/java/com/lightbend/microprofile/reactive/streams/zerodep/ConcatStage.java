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

public class ConcatStage<T> extends GraphStage implements OutletListener {

  private final StageInlet<T> first;
  private final StageInlet<T> second;
  private final StageOutlet<T> outlet;

  private Throwable secondError;

  public ConcatStage(BuiltGraph builtGraph, StageInlet<T> first, StageInlet<T> second, StageOutlet<T> outlet) {
    super(builtGraph);
    this.first = first;
    this.second = second;
    this.outlet = outlet;

    first.setListener(new FirstInletListener());
    second.setListener(new SecondInletListener());
    outlet.setListener(this);
  }

  @Override
  public void onPull() {
    if (first.isClosed()) {
      second.pull();
    } else {
      first.pull();
    }
  }

  @Override
  public void onDownstreamFinish() {
    if (!first.isClosed()) {
      first.cancel();
    }
    if (!second.isClosed()) {
      second.cancel();
    }
  }

  private class FirstInletListener implements InletListener {
    @Override
    public void onPush() {
      outlet.push(first.grab());
    }

    @Override
    public void onUpstreamFinish() {
      if (second.isClosed()) {
        if (secondError != null) {
          outlet.fail(secondError);
        } else {
          outlet.complete();
        }
      } else if (outlet.isAvailable()) {
        second.pull();
      }
    }

    @Override
    public void onUpstreamFailure(Throwable error) {
      outlet.fail(error);
      if (!second.isClosed()) {
        second.cancel();
      }
    }
  }

  private class SecondInletListener implements InletListener {
    @Override
    public void onPush() {
      outlet.push(second.grab());
    }

    @Override
    public void onUpstreamFinish() {
      if (first.isClosed()) {
        outlet.complete();
      }
    }

    @Override
    public void onUpstreamFailure(Throwable error) {
      if (first.isClosed()) {
        outlet.fail(error);
      } else {
        secondError = error;
      }
    }
  }
}
