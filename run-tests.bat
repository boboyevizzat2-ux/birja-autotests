@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo eBirja avtotestlari ishga tushyapti...
call mvn test
echo.
echo Hisobot: target\surefire-reports\index.html
start "" "target\surefire-reports\index.html"
pause
