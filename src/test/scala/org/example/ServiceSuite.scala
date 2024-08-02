package org.example

import cats.effect.IO

object ServiceSuite extends TracedSimpleIOSuite with TracedIOCheckers {

  tracedTest("calculate a sum of numbers (fixed)") { implicit tracer =>
    for {
      service <- Service.create[IO]
      result  <- service.sum(1L, 2L)
    } yield expect(result == 3L)
  }

  tracedTest("calculate a sum of numbers") { implicit tracer =>
    forall { (a: Long, b: Long) =>
      for {
        service <- Service.create[IO]
        result  <- service.sum(a, b)
      } yield expect(result == (a + b))
    }
  }

}
