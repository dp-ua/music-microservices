@echo off
pushd "%~dp0test-data"
call newman run module_2.postman_collection.json
popd
pause
