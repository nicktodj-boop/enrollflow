#!/bin/sh
# 網頁版編譯：把 Java 原始碼以 Java 8 bytecode 編到 docs/，給 CheerpJ 載入。
# 與 Maven 的輸出目錄 target/ 分開；docs/ 裡的 .class 要一起 commit（GitHub Pages 不會幫忙編譯）。
# -Xlint:-options 關掉新版 JDK「source value 8 is obsolete」警告，不影響結果。
set -e
cd "$(dirname "$0")"

rm -rf docs/g9
javac --release 8 -Xlint:-options -encoding UTF-8 -d docs $(find src/main/java -name '*.java')
echo "編譯完成：docs/g9/enrollflow/"

# 快取版本號：GitHub Pages 讓瀏覽器把檔案快取 10 分鐘，更新後一般重新整理可能還拿到舊的 CSS／JS。
# index.html 引用時加上「檔案內容的雜湊」，例如 app.js?v=1a2b3c4d；內容變了網址就變，瀏覽器會重新下載。
# 先去掉 \r 再算，Windows（CRLF）與 Mac（LF）算出來一樣，build.bat 用同樣做法。
hash8() { tr -d '\r' < "$1" | { shasum 2>/dev/null || sha1sum; } | cut -c1-8; }
for f in style.css app.js; do
    h=$(hash8 "docs/$f")
    re=$(printf '%s' "$f" | sed 's/\./\\./g')
    sed -i.bak -E "s#\"$re(\?v=[0-9a-f]+)?\"#\"$f?v=$h\"#" docs/index.html
    rm -f docs/index.html.bak
    echo "快取版本：$f?v=$h"
done
