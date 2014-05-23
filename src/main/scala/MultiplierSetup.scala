package Creek

import Chisel._

class MultiplierSetup(lanes: Int, memdepth: Int)
        extends ArithmeticSetup(lanes, memdepth) {
    val unit = Module(new MultiplierUnit(lanes, memdepth))
    connectUnit(unit)
}

class MultiplierSetupTest(c: MultiplierSetup)
    extends ArithmeticSetupTest(c, (a, b) => a * b)
