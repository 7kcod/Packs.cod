@echo off
title pack.cod build
cd /d "%~dp0"
set "LOG=%~dp0build-log.txt"
echo pack.cod build log > "%LOG%"
set "JHOME="
for /f "delims=" %%J in ('where javac 2^>nul') do if not defined JHOME for %%A in ("%%J\..\..") do set "JHOME=%%~fA"
if defined JHOME set "JAVA_HOME=%JHOME%"
echo JAVA_HOME=%JAVA_HOME% >> "%LOG%"
java -version >> "%LOG%" 2>&1
echo Building pack.cod... this can take a few minutes the first time.
del /q "build\libs\*.jar" >nul 2>&1
call gradlew.bat build --no-daemon --console=plain >> "%LOG%" 2>&1
echo EXIT=%ERRORLEVEL% >> "%LOG%"
for %%F in ("build\libs\pack-cod-*.jar") do (
  del /q "..\mods\pack-cod-*.jar" >> "%LOG%" 2>&1
  copy /y "%%F" "..\mods\%%~nxF" >> "%LOG%"
  echo COPIED %%~nxF >> "%LOG%"
)
echo DONE >> "%LOG%"
