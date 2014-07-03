#!/bin/sh
sbt assembly && sudo java -jar target/scala-2.10/rocket4j-assembly-0.1-SNAPSHOT.jar
