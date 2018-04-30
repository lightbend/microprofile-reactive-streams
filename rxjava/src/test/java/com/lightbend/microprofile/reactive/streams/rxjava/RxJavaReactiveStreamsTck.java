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

package com.lightbend.microprofile.reactive.streams.rxjava;

import com.lightbend.microprofile.reactive.streams.rxjava.RxJavaEngine;
import org.eclipse.microprofile.reactive.streams.tck.ReactiveStreamsTck;
import org.reactivestreams.tck.TestEnvironment;

public class RxJavaReactiveStreamsTck extends ReactiveStreamsTck<RxJavaEngine> {

  public RxJavaReactiveStreamsTck() {
    super(new TestEnvironment());
  }

  @Override
  protected RxJavaEngine createEngine() {
    return new RxJavaEngine();
  }
}
