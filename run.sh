#!/usr/bin/env bash
# Build and run the application (Linux / macOS / Git Bash). Usage: ./run.sh
cd "$(dirname "$0")"
./build.sh || exit 1
java -cp "out:lib/*" com.restaurant.app.Main
