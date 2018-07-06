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
 * An outlet that a stage may interact with.
 *
 * @param <T> The type of elements that this outlet supports.
 */
interface StageOutlet<T> {

  /**
   * Push an element.
   * <p>
   * An element may only be pushed if an {@link OutletListener#onPull()} signal has been received, and the outlet
   * hasn't been completed, failed or a {@link OutletListener#onDownstreamFinish()} hasn't been received.
   *
   * @param element The element to push.
   */
  void push(T element);

  /**
   * Whether this outlet is available for an element to be pushed.
   */
  boolean isAvailable();

  /**
   * Complete this outlet.
   */
  void complete();

  /**
   * Whether this outlet is closed, either due to sending a complete or fail signal, or due to downstream
   * completing by invoking {@link OutletListener#onDownstreamFinish()}.
   */
  boolean isClosed();

  /**
   * Fail this outlet.
   *
   * @param error The error to fail it with.
   */
  void fail(Throwable error);

  /**
   * Set the listener for signals from this outlet.
   *
   * @param listener The listener to set.
   */
  void setListener(OutletListener listener);

  /**
   * Convenience method for configuring an outlet to simply forward all signals to an inlet.
   *
   * @param inlet The inlet to forward signals to.
   */
  default void forwardTo(StageInlet<?> inlet) {
    class ForwardingOutletListener implements OutletListener {
      @Override
      public void onPull() {
        inlet.pull();
      }

      @Override
      public void onDownstreamFinish() {
        inlet.cancel();
      }
    }
    setListener(new ForwardingOutletListener());
  }
}

/**
 * An listener to receive signals from an outlet.
 */
interface OutletListener {
  /**
   * A pull signal, indicates that downstream is ready to be pushed to.
   */
  void onPull();

  /**
   * A completion signal, indicates that downstream has completed. No further signals may be sent to this outlet after
   * this signal is received.
   */
  void onDownstreamFinish();
}
