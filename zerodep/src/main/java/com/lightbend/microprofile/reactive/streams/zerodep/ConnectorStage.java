/******************************************************************************
 * Licensed under Public Domain (CC0)                                         *
 *                                                                            *
 * To the extent possible under law, the person who associated CC0 with       *
 * this code has waived all copyright and related or neighboring              *
 * rights to this code.                                                       *
 *                                                                            *
 * You should have received a copy of the CC0 legalcode along with this       *
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.     *
 ******************************************************************************/

package com.lightbend.microprofile.reactive.streams.zerodep;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Connector stage. Does nothing but connects a publisher to a subscriber when the graph starts.
 */
public class ConnectorStage<T> extends GraphStage {
  private final Publisher<T> publisher;
  private final Subscriber<T> subscriber;

  public ConnectorStage(BuiltGraph builtGraph, Publisher<T> publisher, Subscriber<T> subscriber) {
    super(builtGraph);
    this.publisher = publisher;
    this.subscriber = subscriber;
  }

  @Override
  protected void postStart() {
    publisher.subscribe(subscriber);
  }
}
