#!/usr/bin/env bash
set -e

echo "================================================================="
echo "  Building Cab Dispatch & Surge Pricing Simulator (CSE2006)"
echo "================================================================="

mkdir -p bin

echo "[1/3] Compiling source files and test suite..."
javac -encoding UTF-8 -cp "lib/*:bin" -d bin \
    src/main/java/com/cabdispatch/model/*.java \
    src/main/java/com/cabdispatch/exception/*.java \
    src/main/java/com/cabdispatch/service/*.java \
    src/main/java/com/cabdispatch/dao/*.java \
    src/main/java/com/cabdispatch/thread/*.java \
    src/main/java/com/cabdispatch/test/*.java \
    src/main/java/com/cabdispatch/*.java

echo "[2/3] Running automated validation test suite..."
java -cp "bin:lib/*" com.cabdispatch.test.TestRunner

echo "[3/3] Packaging standalone executable CabDispatch.jar..."
jar cfe CabDispatch.jar com.cabdispatch.Main -C bin .

echo ""
echo "================================================================="
echo "  BUILD SUCCESSFUL!"
echo "  Run simulation with: java -jar CabDispatch.jar"
echo "  Or run tests with:   java -cp \"bin:lib/*\" com.cabdispatch.test.TestRunner"
echo "================================================================="
