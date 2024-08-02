package org.example

import cats.effect.IO

object ServiceSharedTracerSuite extends TracedIOSuite with TracedIOCheckers {

  test("calculate a sum of numbers (fixed)") { implicit tracer =>
    for {
      service <- Service.create[IO]
      result  <- service.sum(1L, 2L)
    } yield expect(result == 3L)
  }

  test("calculate a sum of numbers") { implicit tracer =>
    forall { (a: Long, b: Long) =>
      for {
        service <- Service.create[IO]
        result  <- service.sum(a, b)
      } yield expect(result == (a + b))
    }
  }

}
