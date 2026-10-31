@echo off
rem Runs the end-to-end tests (Cucumber) against the RUNNING stack. The stack is not started here.
rem
rem   FIRST start the environment:  01_start_services_wsl.cmd   (or 01_start_services_docker.cmd)
rem   THEN run this script.
rem
rem The tests use only the API gateway. Another address: set E2E_BASE_URL=http://host:8080 before the call.
cd /d "%~dp0"

if "%E2E_BASE_URL%"=="" set E2E_BASE_URL=http://localhost:8080

curl -s -o nul --max-time 5 %E2E_BASE_URL%/songs/999999999
if errorlevel 1 (
    echo.
    echo The stack does not answer at %E2E_BASE_URL%.
    echo Start the environment first: 01_start_services_wsl.cmd ^(or 01_start_services_docker.cmd^)
    echo Then run this script again.
    pause
    exit /b 1
)

call gradlew.bat :e2e-tests:e2eTest -De2e.baseUrl=%E2E_BASE_URL% --console=plain
echo.
echo ============================================================
echo   Test results  : e2e-tests\build\reports\tests\e2eTest\index.html
echo   Cucumber report: e2e-tests\build\reports\cucumber\e2e-tests.html
echo   Raw XML        : e2e-tests\build\test-results\e2eTest\
echo ============================================================
pause
