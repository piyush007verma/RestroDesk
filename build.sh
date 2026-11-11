#!/usr/bin/env bash
# Compile the project (Linux / macOS / Git Bash). Usage: ./build.sh
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
find src -name "*.java" > out/sources.txt
javac -d out -cp "lib/*" @out/sources.txt
echo "Build OK"
