@echo off
chcp 65001 >nul
cd /d "%~dp0"
rem Ishlatish: run-auction.bat spirt   (turlar: oddiy, spirt, oltin, kumush, subsidiya)
set TUR=%~1
if "%TUR%"=="" set TUR=Oddiy
for %%T in (Oddiy Spirt Oltin Kumush Subsidiya) do if /I "%%T"=="%TUR%" set TUR=%%T
echo Auksion testi (%TUR%) ishga tushyapti (taxminan 15 daqiqa)...
echo Klaviatura va sichqonchaga tegmang: E-IMZO paroli avtomatik kiritiladi.
call mvn test -Dsurefire.suiteXmlFiles= -Dtest=Auksion%TUR%Test
echo.
echo Hisobot: target\surefire-reports\index.html
echo Xato rasmlari (bo'lsa): target\xato-rasmlar
start "" "target\surefire-reports\index.html"
pause
