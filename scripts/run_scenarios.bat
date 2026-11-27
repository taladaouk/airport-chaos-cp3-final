@echo off
REM Saves the three official scenario outputs for the report.
REM Run from the project root:  mvn -q package -DskipTests   then   scripts\run_scenarios.bat
if not exist results mkdir results
for %%s in (A B C) do (
  java -jar target\airport-chaos.jar --batch scripts\scenario%%s.txt > results\scenario%%s_output.txt
  echo wrote results\scenario%%s_output.txt
)
