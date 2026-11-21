"""本機測試用靜態伺服器，提供 docs/ 目錄，支援 HTTP Range requests。

CheerpJ 載入 .class 時會送 Range 標頭只要檔案的一部分，
Python 內建的 http.server 不一定處理 Range，所以這裡自己補上。

用法：python3 server.py [port]，預設 8001，然後打開 http://localhost:8001/
"""
import os
import re
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "docs")
RANGE_RE = re.compile(r"bytes=(\d*)-(\d*)$")


class RangeHandler(SimpleHTTPRequestHandler):
    """在 SimpleHTTPRequestHandler 上加入單一區段的 Range 支援。"""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def end_headers(self):
        # 告訴瀏覽器可以分段下載；開發時關閉快取，改完重新整理就生效
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def do_GET(self):
        range_header = self.headers.get("Range")
        path = self.translate_path(self.path)
        if not range_header or not os.path.isfile(path):
            return super().do_GET()

        match = RANGE_RE.match(range_header.strip())
        size = os.path.getsize(path)
        if not match or (match.group(1) == "" and match.group(2) == ""):
            self.send_error(416, "Invalid Range")
            return

        start_text, end_text = match.groups()
        if start_text == "":
            # bytes=-N：最後 N 個位元組
            start = max(0, size - int(end_text))
            end = size - 1
        else:
            start = int(start_text)
            end = int(end_text) if end_text else size - 1
        end = min(end, size - 1)
        if start > end:
            self.send_response(416)
            self.send_header("Content-Range", "bytes */%d" % size)
            self.end_headers()
            return

        length = end - start + 1
        self.send_response(206)
        self.send_header("Content-Type", self.guess_type(path))
        self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
        self.send_header("Content-Length", str(length))
        self.end_headers()
        with open(path, "rb") as f:
            f.seek(start)
            self.wfile.write(f.read(length))


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8001
    print("提供 %s ，打開 http://localhost:%d/" % (ROOT, port))
    ThreadingHTTPServer(("", port), RangeHandler).serve_forever()
