#!/bin/sh
# 編譯網頁版後啟動本機伺服器，瀏覽器打開 http://localhost:8001/
set -e
cd "$(dirname "$0")"
./build.sh
python3 server.py 8001
