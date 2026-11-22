@echo off
title pack.cod test instance
cd /d "%~dp0"
echo.
echo  pack.cod test instance
echo  ----------------------
echo  First launch downloads Minecraft 1.21.11 + Fabric (a few minutes).
echo  Packs in run\resourcepacks are picked up automatically.
echo.
if not exist run\resourcepacks mkdir run\resourcepacks
call gradlew.bat runClient
pause
