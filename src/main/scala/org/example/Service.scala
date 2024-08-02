package org.example

import cats.effect.{Async, Temporal}
import cats.effect.std.Random
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.Tracer

import scala.concurrent.duration._

class Service[F[_]: Temporal: Tracer: Random] {

  def sum(x: Long, y: Long): F[Long] =
    Tracer[F].span("sum", Attribute("x", x), Attribute("y", y)).surround {
      for {
        delay <- Random[F].nextIntBounded(50)
        _     <- Temporal[F].sleep(delay.millis)
      } yield x + y
    }

}

object Service {

  def create[F[_]: Async: Tracer]: F[Service[F]] =
    Random.scalaUtilRandom[F].map { implicit random =>
      new Service[F]
    }

}
