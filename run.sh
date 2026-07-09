#!/bin/bash
# Compiles and runs the Campus Placement Eligibility Checker.
# Requires only a JDK (no Maven, no internet access needed).
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/eligibility/*.java
echo "Compiled. Starting server on http://localhost:8080 ..."
java -cp out eligibility.Server
