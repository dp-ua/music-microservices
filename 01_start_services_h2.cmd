set SPRING_PROFILES_ACTIVE=h2
start cmd /k gradle :resource-service:bootRun :song-service:bootRun --parallel