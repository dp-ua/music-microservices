set SPRING_PROFILES_ACTIVE=docker
start cmd /k gradle :resource-service:bootRun :song-service:bootRun --parallel