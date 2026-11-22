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

REM 快取版本號：與 build.sh 相同，index.html 引用 style.css、app.js 時加上「檔案內容（去掉 CR）的 SHA-1 前 8 碼」。
REM [char]34 是雙引號，避免和 cmd 的引號衝突。失敗只會少了版本號，不影響編譯結果。
powershell -NoProfile -ExecutionPolicy Bypass -Command "$q=[char]34; $p='docs\index.html'; $t=[IO.File]::ReadAllText($p); foreach ($f in 'style.css','app.js') { $b=[Text.Encoding]::UTF8.GetBytes(([IO.File]::ReadAllText('docs\'+$f)).Replace([string][char]13,'')); $h=((([Security.Cryptography.SHA1]::Create()).ComputeHash($b) | ForEach-Object { $_.ToString('x2') }) -join '').Substring(0,8); $t=[regex]::Replace($t, $q+[regex]::Escape($f)+'(\?v=[0-9a-f]+)?'+$q, $q+$f+'?v='+$h+$q); Write-Output ('快取版本：'+$f+'?v='+$h) }; [IO.File]::WriteAllText($p, $t, (New-Object Text.UTF8Encoding $false))"
