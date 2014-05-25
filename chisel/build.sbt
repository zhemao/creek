organization := "com.mao.howard"

version := "0.1-SNAPSHOT"

name := "creek"

scalaVersion := "2.10.3"

addSbtPlugin("com.github.scct" % "sbt-scct" % "0.2")

libraryDependencies ++= Seq(
    "edu.berkeley.cs" %% "chisel" % "2.3-SNAPSHOT",
    "com.mao.howard" %% "chisel-float" % "0.1-SNAPSHOT",
    "com.mao.howard" %% "chisel-crossbar" % "0.1-SNAPSHOT"
)
