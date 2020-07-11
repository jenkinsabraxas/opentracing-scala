package com.github.fehu.opentracing.v2

import cats.data.StateT
import cats.effect.{ IO, Sync }

import com.github.fehu.opentracing.v2.{ Traced, TracedLift, TracedRun }
import com.github.fehu.opentracing.v2.internal.{ TracedStateTracedInstance, TracedStateTracedLiftInstance, TracedStateTracedRunInstance }

package object impl {
  import com.github.fehu.opentracing.v2.internal.State

  type TracedState[F[_], A] = StateT[F, State[F], A]

  type TracedSTIO[A] = TracedState[IO, A]

  implicit def tracedStateTracedInstance[F[_]: Sync]: Traced[TracedState[F, *]] = new TracedStateTracedInstance

  implicit def tracedStateTracedRunInstance[F[_]: Sync]: TracedRun[TracedState, F] = new TracedStateTracedRunInstance

  implicit def tracedStateTracedLiftInstance[F[_]: Sync]: TracedLift[TracedState, F] = new TracedStateTracedLiftInstance
}