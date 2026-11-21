#!/bin/sh
# 網頁版編譯：把 Java 原始碼以 Java 8 bytecode 編到 docs/，給 CheerpJ 載入。
# 與 Maven 的輸出目錄 target/ 分開；docs/ 裡的 .class 要一起 commit（GitHub Pages 不會幫忙編譯）。
# -Xlint:-options 關掉新版 JDK「source value 8 is obsolete」警告，不影響結果。
set -e
cd "$(dirname "$0")"

rm -rf docs/g9
javac --release 8 -Xlint:-options -encoding UTF-8 -d docs $(find src/main/java -name '*.java')
echo "編譯完成：docs/g9/enrollflow/"
