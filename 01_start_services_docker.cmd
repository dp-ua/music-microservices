@echo off
rem Starts the whole stack from compose.yaml: PostgreSQL (2 databases), LocalStack (S3), RabbitMQ, Eureka,
rem resource-service, song-service, resource-processor and the API gateway.
rem Variant for Docker that is available directly in the Windows PATH (for example Docker Desktop).
rem Docker inside WSL2: use 01_start_services_wsl.cmd.
cd /d "%~dp0"
docker compose up -d --build
echo.
docker compose ps
echo.
echo ============================================================
echo   API gateway   : http://localhost:8080   (all requests of the tests go here)
echo   Eureka        : http://localhost:8761
echo   RabbitMQ UI   : http://localhost:15672
echo.
echo   Logs of a service : docker compose logs -f resource-processor
echo   Stop the stack    : docker compose down
echo ============================================================
pause
