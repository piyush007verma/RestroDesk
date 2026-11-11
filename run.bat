@echo off
REM Build and run the application on Windows. Usage: run.bat
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
java -cp "out;lib/*" com.restaurant.app.Main
