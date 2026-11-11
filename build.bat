@echo off
REM Compile the project on Windows. Usage: build.bat
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > out\sources.txt
powershell -NoProfile -Command "(Get-Content out\sources.txt) -replace '\\','/' | Set-Content out\sources.txt"
javac -d out -cp "lib/*" @out\sources.txt
if errorlevel 1 (
  echo Build FAILED
  exit /b 1
)
echo Build OK
