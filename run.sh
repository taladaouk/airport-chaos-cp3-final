#!/usr/bin/env bash
set -e
mvn -q package
java -jar target/airport-chaos.jar "$@"