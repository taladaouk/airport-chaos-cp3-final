#!/bin/sh
# Full benchmark suite (about 3 minutes) + one PNG plot per benchmark, all in results/.
# Run from the project root after:  mvn -q package -DskipTests
# Add --quick after "all" for a short demo run.
java -Xmx2g -cp target/classes airport.bench.BenchmarkRunner all --out results "$@"
java -Djava.awt.headless=true -cp target/classes airport.bench.PlotBenchmarks results
