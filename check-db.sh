#!/usr/bin/env bash
# Test the MySQL connection step by step. Usage: ./check-db.sh
cd "$(dirname "$0")"
./build.sh || exit 1
java -cp "out:lib/*" com.restaurant.db.DbCheck
