package org.example

import cats.effect.{IO, Ref, Resource}
import org.typelevel.otel4s.sdk.exporter.otlp.trace.autoconfigure.OtlpSpanExporterAutoConfigure
import org.typelevel.otel4s.sdk.trace.SdkTraces
import org.typelevel.otel4s.trace.{SpanFinalizer, Tracer}
import weaver._
import weaver.scalacheck.Checkers

trait TracedSimpleIOSuite extends SimpleIOSuite {

  def tracedTest(name: TestName)(run: Tracer[IO] => IO[Expectations]): Unit =
    registerTest(name)(_ => Test(name.name, traced(name)(run)))

  private def traced(testName: TestName)(body: Tracer[IO] => IO[Expectations]): IO[Expectations] =
    SdkTraces
      .autoConfigured[IO](_.addExporterConfigurer(OtlpSpanExporterAutoConfigure[IO]))
      .evalMap(_.tracerProvider.get(getClass.getName.stripSuffix("$")))
      .use(implicit tracer => TraceUtils.trace(name, testName)(body(tracer)))

}

trait TracedIOCheckers extends Checkers { self: MutableIOSuite =>

  // it must be 'def', so for every materialization there will be a new counter
  implicit protected def unwrapWithTrace(implicit T: Tracer[F]): PropF[F[Expectations]] =
    new Checkers.Prop[F, F[Expectations]] {
      private val counter = Ref.unsafe[IO, Int](0)
      def lift(a: F[Expectations]): F[Expectations] =
        for {
          count <- counter.getAndUpdate(_ + 1)
          result <- Tracer[F]
            .spanBuilder(s"forall#$count")
            .withFinalizationStrategy(SpanFinalizer.Strategy.empty)
            .build
            .use(span => a.guaranteeCase(TraceUtils.recordOutcome(span, fatalCancelation = false)))
        } yield result
    }

}

trait TracedIOSuite extends IOSuite { self =>
  type Res = Tracer[IO]

  def sharedResource: Resource[IO, Tracer[IO]] =
    SdkTraces
      .autoConfigured[IO](_.addExporterConfigurer(OtlpSpanExporterAutoConfigure[IO]))
      .evalMap(_.tracerProvider.get(getClass.getName.stripSuffix("$")))

  override def test(name: TestName): PartiallyAppliedTest = new TracedPartiallyAppliedTest(name)

  private final class TracedPartiallyAppliedTest(
      testName: TestName
  ) extends PartiallyAppliedTest(testName) {

    override def apply(run: Res => IO[Expectations]): Unit =
      registerTest(testName) { tracer =>
        Test(testName.name, (log: Log[IO]) => traced(log, tracer)((res, _) => run(res)))
      }

    override def apply(run: (Res, Log[IO]) => IO[Expectations]): Unit =
      registerTest(testName) { tracer =>
        Test(testName.name, (log: Log[IO]) => traced(log, tracer)(run))
      }

    private def traced(
        log: Log[IO],
        tracer: Tracer[IO]
    )(run: (Tracer[IO], Log[IO]) => IO[Expectations]): IO[Expectations] =
      TraceUtils.trace(name, testName)(run(tracer, log))(implicitly, tracer)
  }

}
