#!/bin/sh
# Saves the three official scenario outputs for the report.
# Run from the project root:  mvn -q package -DskipTests  &&  sh scripts/run_scenarios.sh
mkdir -p results
for s in A B C; do
  java -jar target/airport-chaos.jar --batch scripts/scenario$s.txt > results/scenario${s}_output.txt
  echo "wrote results/scenario${s}_output.txt"
done
