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
 * An inlet that a stage may interact with.
 *
 * @param <T> The type of signal this stage deals with.
 */
interface StageInlet<T> {

  /**
   * Send a pull signal to this inlet. This will allow an upstream stage to push an element.
   * <p>
   * The inlet may only be pulled if it is not closed and hasn't already been pulled since it last received an element.
   */
  void pull();

  /**
   * Whether this inlet has been pulled.
   */
  boolean isPulled();

  /**
   * Whether this inlet is available to be grabbed.
   */
  boolean isAvailable();

  /**
   * Whether this inlet has been closed, either due to it being explicitly cancelled, or due to an
   * upstream finish or failure being received.
   */
  boolean isClosed();

  /**
   * Cancel this inlet. No signals may be sent after this is invoked, and no signals will be received.
   */
  void cancel();

  /**
   * Grab the last pushed element from this inlet.
   * <p>
   * Grabbing the element will cause it to be removed from the inlet - an element cannot be grabbed twice.
   * <p>
   * This may only be invoked if a prior {@link InletListener#onPush()} signal has been received.
   *
   * @return The grabbed element.
   */
  T grab();

  /**
   * Set the listener for signals from this inlet.
   *
   * @param listener The listener.
   */
  void setListener(InletListener listener);

  /**
   * Convenience method for configuring an inlet to simply forward all signals to an outlet.
   *
   * @param outlet The outlet to forward signals to.
   */
  default void forwardTo(StageOutlet<T> outlet) {
    class ForwardingInletListener implements InletListener {
      @Override
      public void onPush() {
        outlet.push(grab());
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
    setListener(new ForwardingInletListener());
  }
}

/**
 * A listener for signals to an inlet.
 */
interface InletListener {

  /**
   * Indicates that an element has been pushed. The element can be received using {@link StageInlet#grab()}.
   */
  void onPush();

  /**
   * Indicates that upstream has completed the stream. No signals may be sent to the inlet after this has been invoked.
   */
  void onUpstreamFinish();

  /**
   * Indicates that upstream has completed the stream with a failure. No signals may be sent to the inlet after this has
   * been invoked.
   */
  void onUpstreamFailure(Throwable error);
}