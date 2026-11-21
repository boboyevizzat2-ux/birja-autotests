@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo To'liq auksion testi ishga tushyapti (taxminan 15 daqiqa)...
echo Klaviatura va sichqonchaga tegmang: E-IMZO paroli avtomatik kiritiladi.
call mvn test -Dsurefire.suiteXmlFiles= -Dtest=AuctionFlowTest
echo.
echo Hisobot: target\surefire-reports\index.html
echo Xato rasmlari (bo'lsa): target\xato-rasmlar
start "" "target\surefire-reports\index.html"
pause
