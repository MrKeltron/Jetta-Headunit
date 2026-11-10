#!/usr/bin/env bash
# Runs the OBD2 parser and ELM327 tests on a PC with only a JDK (no Android needed).
set -e
cd "$(dirname "$0")"
SRC=../../app/src/main/java/dev/kelton/drehzahl/obd
rm -rf out && mkdir out
javac -d out $SRC/Link.java $SRC/ObdParse.java $SRC/Elm327.java $SRC/PidScheduler.java $SRC/GattPick.java $SRC/ByteQueue.java $SRC/PullRecorder.java FakeElm.java ParseTest.java ElmTest.java SchedulerTest.java BleTest.java PullTest.java
java -cp out ParseTest
java -cp out ElmTest
java -cp out dev.kelton.drehzahl.obd.SchedulerTest
java -cp out dev.kelton.drehzahl.obd.BleTest
java -cp out dev.kelton.drehzahl.obd.PullTest
