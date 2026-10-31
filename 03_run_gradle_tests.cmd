@echo off
rem Runs all Gradle test suites of Module 3 and builds the coverage report.
rem Integration and component tests need Docker (it runs in WSL, see task/modul3/progress.md).
rem Contract tests: consumers write the pact files, publishPacts copies them, providers replay them.
rem The end-to-end tests are NOT here, they need the running stack: 01_start_services_wsl.cmd, then 04_run_e2e.cmd.
rem --continue: a failing suite does not stop the remaining ones.
call gradlew.bat test integrationTest componentTest contractTests jacocoCoverageReport --continue
echo.
echo ============================================================
echo Reports (open index.html in a browser), for every module:
echo   resource-processor, resource-service, song-service
echo.
echo   Test results   : ^<module^>\build\reports\tests\^<suite^>\index.html
echo                    suite = test, integrationTest, componentTest,
echo                            contractConsumerTest, contractProviderTest
echo   Raw XML results: ^<module^>\build\test-results\^<suite^>\
echo   Code coverage  : ^<module^>\build\reports\jacoco\jacocoCoverageReport\html\index.html
echo   Pact files     : ^<module^>\build\pacts
echo.
echo "Problems report" of Gradle is not a test report, it only lists build warnings.
echo ============================================================
pause
