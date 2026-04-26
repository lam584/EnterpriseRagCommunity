@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-kafka.ps1" %*
exit /b %errorlevel%