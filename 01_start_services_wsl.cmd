@echo off
rem Starts the whole stack from compose.yaml: PostgreSQL (2 databases), LocalStack (S3), RabbitMQ, Eureka,
rem resource-service, song-service, resource-processor and the API gateway.
rem Variant for Docker that runs inside WSL2 (docker is not in the Windows PATH).
rem Without WSL use 01_start_services_docker.cmd.
cd /d "%~dp0"
wsl docker compose up -d --build
echo.
wsl docker compose ps
echo.
echo ============================================================
echo   API gateway   : http://localhost:8080   (all requests of the tests go here)
echo   Eureka        : http://localhost:8761
echo   RabbitMQ UI   : http://localhost:15672
echo.
echo   Logs of a service : wsl docker compose logs -f resource-processor
echo   Stop the stack    : wsl docker compose down
echo ============================================================
pause
