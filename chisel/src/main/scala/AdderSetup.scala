package Creek

import Chisel._
import ChiselFloat.FloatUtils.floatAdd

class AdderSetup(lanes: Int, memdepth: Int)
        extends ArithmeticSetup(lanes, memdepth) {
    val unit = Module(new AdderUnit(lanes))
    connectUnit(unit)
}

class AdderSetupTest(c: AdderSetup) extends ArithmeticSetupTest(c, floatAdd) {}
