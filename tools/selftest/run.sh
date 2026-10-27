#!/bin/sh
# Compiles the installer logic and runs it against a pretend PS5.
# Works from the project root:  sh tools/selftest/run.sh
set -e
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
if command -v javac >/dev/null 2>&1; then JAVAC="javac"; else JAVAC="java -m jdk.compiler/com.sun.tools.javac.Main"; fi
$JAVAC -d "$OUT" $(find app/src/main/java/io/github/skythedevil/gtavmenu/core -name '*.java') tools/selftest/MockPs5Test.java
java -cp "$OUT" MockPs5Test
