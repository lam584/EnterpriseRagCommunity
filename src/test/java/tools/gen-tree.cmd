@echo off
setlocal enabledelayedexpansion

rem --- Determine project root (dir of this script) ---
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "SRC=%ROOT%\src\test\java\tools\DirectoryTreeMarkdownGenerator.java"

if not exist "%SRC%" (
  echo [ERROR] Source file not found: "%SRC%"
  exit /b 1
)

rem --- Prefer JAVA_HOME if available ---
set "JAVA_EXE=java"
set "JAVAC_EXE=javac"
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
  set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
)

rem --- Ensure Java is available ---
"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Java not found. Please install JDK 21+ or set JAVA_HOME.
  exit /b 1
)

rem --- Try to compile with javac (UTF-8) ---
echo [INFO] Compiling with "%JAVAC_EXE%"...
"%JAVAC_EXE%" -version >nul 2>&1
if not errorlevel 0 (
  echo [WARN] javac not available; falling back to source-file mode.
  goto :RUN_SOURCE
)

"%JAVAC_EXE%" -encoding UTF-8 -d "%ROOT%" "%SRC%"
if errorlevel 1 (
  echo [WARN] Compilation failed; trying source-file mode with java --source 21...
  goto :RUN_SOURCE
)

echo [INFO] Running DirectoryTreeMarkdownGenerator...
"%JAVA_EXE%" -cp "%ROOT%" tools.DirectoryTreeMarkdownGenerator "%ROOT%"
if errorlevel 1 (
  echo [ERROR] Execution failed.
  exit /b 1
)
echo [INFO] Done. tree.md should be in "%ROOT%".
goto :EOF

:RUN_SOURCE
echo [INFO] Running in source-file mode...
"%JAVA_EXE%" --source 21 "%SRC%" "%ROOT%"
if errorlevel 1 (
  echo [ERROR] Failed to run in source-file mode. Ensure your JDK is 21+.
  exit /b 1
)
echo [INFO] Done. tree.md should be in "%ROOT%".

:EOF
endlocal

