import AssemblyKeys._ // put this at the top of the file

assemblySettings

name := "rocket4j"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
 "org.usb4java" % "usb4java-javax" % "1.2.0",
 "com.googlecode.lanterna" % "lanterna" % "2.1.8"
)

