package Creek

import Chisel._

object CreekMain {
    def main(args: Array[String]) {
        val testArgs = args.slice(1, args.length)
        args(0) match {
            case "RegisterSet" => chiselMainTest(testArgs,
                () => Module(new RegisterSet(128, 32))) {
                    c => new RegisterSetTest(c)
                }
        }
    }
}
