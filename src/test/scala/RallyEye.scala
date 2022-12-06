package rallyeye

import utest._

object HelloTests extends TestSuite {
  val tests = Tests {
    test("test1") {
      (1 to 10).foreach(i => println(yScale(i)))
      println(yScale.domain())
    }
  }
}
