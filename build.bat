@echo off
REM 網頁版編譯（Windows）：把 Java 原始碼以 Java 8 bytecode 編到 docs\，給 CheerpJ 載入。
REM docs\ 裡的 .class 要一起 commit（GitHub Pages 不會幫忙編譯）。
chcp 65001 > nul
cd /d "%~dp0"

if exist docs\g9 rmdir /s /q docs\g9
dir /s /b src\main\java\*.java > sources.txt
javac --release 8 -Xlint:-options -encoding UTF-8 -d docs @sources.txt
set ERR=%ERRORLEVEL%
del sources.txt
if %ERR% neq 0 exit /b %ERR%
echo 編譯完成：docs\g9\enrollflow\
