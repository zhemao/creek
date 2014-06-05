package Creek

import Chisel._

object CreekMain {
    def main(args: Array[String]) {
        val testArgs = args.slice(1, args.length)
        args(0) match {
            case "RegisterSet" => chiselMainTest(testArgs,
                () => Module(new RegisterSet(256, 32, 16))) {
                    c => new RegisterSetTest(c)
                }
            case "AdderUnit" => chiselMainTest(testArgs,
                () => Module(new AdderUnit(4))) {
                    c => new AdderUnitTest(c)
                }
            case "MultiplierUnit" => chiselMainTest(testArgs,
                () => Module(new MultiplierUnit(4))) {
                    c => new MultiplierUnitTest(c)
                }
            case "AdderSetup" => chiselMainTest(testArgs,
                () => Module(new AdderSetup(4, 256))) {
                    c => new AdderSetupTest(c)
                }
            case "MultiplierSetup" => chiselMainTest(testArgs,
                () => Module(new MultiplierSetup(4, 256))) {
                    c => new MultiplierSetupTest(c)
                }
            case "MemoryTest" => chiselMainTest(testArgs,
                () => Module(new MemoryTest())) {
                    c => new MemoryTestTest(c)
                }
            case "MemoryControllerRegisters" => chiselMainTest(testArgs,
                () => Module(new MemoryControllerRegisters(16))) {
                    c => new MemoryControllerRegistersTest(c)
                }
            case "Datapath" => chiselMainTest(testArgs,
                () => Module(new Datapath(4, 256, 7, 16))) {
                    c => new DatapathTest(c)
                }
            case "SwitchController" => chiselMainTest(testArgs,
                () => Module(new SwitchController(3, 2))) {
                    c => new SwitchControllerTest(c)
                }
        }
    }
}
