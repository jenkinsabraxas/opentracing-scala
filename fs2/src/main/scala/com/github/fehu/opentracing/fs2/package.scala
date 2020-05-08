package com.github.fehu.opentracing

import cats.~>
import cats.arrow.FunctionK
import cats.effect.{ ExitCase, Sync }
import cats.effect.syntax.bracket._
import cats.syntax.functor._
import _root_.fs2.Stream
import com.github.fehu.opentracing.Tracing.TracingSetup
import com.github.fehu.opentracing.effect.ResourceTracing
import io.opentracing.Tracer

package object fs2 {
  implicit def fs2StreamTracing[F[_]](implicit sync: Sync[F], t: Tracer, setup: TracingSetup): Tracing[Stream[F, *], Stream[F, *]] =
    new Tracing[Stream[F, *], Stream[F, *]] {
      import sync.delay

      protected def noTrace: Stream[F, *] ~> Stream[F, *] = FunctionK.id
      protected def build(spanBuilder: Tracer.SpanBuilder, activate: Boolean): Stream[F, *] ~> Stream[F, *] =
        λ[Stream[F, *] ~> Stream[F, *]] { s =>
          Stream.force {
            for {
              ((span, _), close) <- ResourceTracing.spanAndScopeResource1(spanBuilder, activate).allocated
              reportAndClose = (err: Throwable) => delay(setup.beforeStop(Left(err))(span)).guarantee(close)
            } yield s.evalTap(e => delay(span.debug("next stream element", "element" -> e.toString)))
              .onFinalizeCase {
                case ExitCase.Completed  => close
                case ExitCase.Error(err) => reportAndClose(err)
                case ExitCase.Canceled   => reportAndClose(new Exception("Canceled"))
              }
          }
        }
    }

}
