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

import java.util.Iterator;

/**
 * Of stage.
 */
class OfStage<T> extends GraphStage implements OutletListener {
  private final StageOutlet<T> outlet;
  private Iterator<T> elements;

  public OfStage(BuiltGraph builtGraph, StageOutlet<T> outlet, Iterable<T> elements) {
    super(builtGraph);
    this.outlet = outlet;
    this.elements = elements.iterator();

    outlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!outlet.isClosed()) {
      if (!elements.hasNext()) {
        outlet.complete();
      }
    }
  }

  @Override
  public void onPull() {
    outlet.push(elements.next());
    if (!elements.hasNext() && !outlet.isClosed()) {
      outlet.complete();
    }
  }

  @Override
  public void onDownstreamFinish() {
  }
}
