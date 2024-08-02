package org.example

import cats.Monad
import cats.effect.{MonadCancelThrow, Outcome}
import cats.effect.syntax.monadCancel._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.trace.{Span, SpanFinalizer, StatusCode, Tracer}
import weaver.{AssertionException, Expectations, TestName}

private object TraceUtils {

  private val AnsiColorRegex = "\u001b\\[([;\\d]*)m".r

  def trace[F[_]: MonadCancelThrow: Tracer](
      suiteName: String,
      name: TestName
  )(fa: F[Expectations]): F[Expectations] = {
    val testName = name.name

    def attributes = Attributes(
      Attribute("test.name", testName),
      Attribute("test.suite", suiteName),
      Attribute("test.source", name.location.fileRelativePath + ":" + name.location.line),
      Attribute("test.tags", name.tags.toSeq)
    )

    Tracer[F]
      .spanBuilder(s"$suiteName - $testName")
      .addAttributes(attributes)
      .withFinalizationStrategy(SpanFinalizer.Strategy.empty)
      .build
      .use(span => fa.guaranteeCase(TraceUtils.recordOutcome(span, fatalCancelation = true)))
  }

  def recordOutcome[F[_]: Monad](
      span: Span[F],
      fatalCancelation: Boolean
  ): Outcome[F, Throwable, Expectations] => F[Unit] = {
    case Outcome.Succeeded(fa) =>
      fa.flatMap { expectations =>
        expectations.run.fold(
          e => span.setStatus(StatusCode.Error) >> span.recordException(removeAsciiColors(e.head)),
          _ => span.setStatus(StatusCode.Ok)
        )
      }

    case Outcome.Canceled() =>
      span.setStatus(StatusCode.Error, "canceled").whenA(fatalCancelation)

    case Outcome.Errored(error) =>
      span.setStatus(StatusCode.Error) >> span.recordException(removeAsciiColors(error))
  }

  private def removeAsciiColors(throwable: Throwable): Throwable =
    throwable match {
      case a: AssertionException =>
        a.copy(message = AnsiColorRegex.replaceAllIn(a.message, ""))

      case other =>
        other
    }

}
