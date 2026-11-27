@echo off
call mvn -q package || exit /b 1
java -jar target\airport-chaos.jar %*