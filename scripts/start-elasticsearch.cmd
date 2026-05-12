@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-elasticsearch.ps1" %*
exit /b %errorlevel%