@echo off
call "%~dp0gradlew.bat" build -Pmc=26.2 || exit /b 1
call "%~dp0gradlew.bat" build -Pmc=26.3 || exit /b 1
echo.
echo Fertig: build\libs\flashback-settings-1.3.0+26.2.jar und flashback-settings-1.3.0+26.3.jar
