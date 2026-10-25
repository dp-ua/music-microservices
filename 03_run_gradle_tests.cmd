@echo off
rem Runs all Gradle test suites of Module 3 and builds the coverage report.
rem Integration and component tests need Docker (it runs in WSL, see task/modul3/progress.md).
rem --continue: a failing suite does not stop the remaining ones.
call gradlew.bat test integrationTest componentTest contractConsumerTest contractProviderTest jacocoCoverageReport --continue
pause
