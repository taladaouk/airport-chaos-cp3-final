@echo off
REM Full benchmark suite (about 3 minutes) + one PNG plot per benchmark, all in results\.
REM Run from the project root after:  mvn -q package -DskipTests
REM Add --quick for a short demo run:  scripts\run_benchmarks.bat --quick
java -Xmx2g -cp target\classes airport.bench.BenchmarkRunner all --out results %*
java -Djava.awt.headless=true -cp target\classes airport.bench.PlotBenchmarks results
