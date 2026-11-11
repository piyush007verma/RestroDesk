@echo off
REM Test the MySQL connection step by step. Usage: check-db.bat
cd /d "%~dp0"
call build.bat
if errorlevel 1 (
  pause
  exit /b 1
)
java -cp "out;lib/*" com.restaurant.db.DbCheck
pause
