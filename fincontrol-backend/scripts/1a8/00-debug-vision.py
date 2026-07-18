#!/usr/bin/env python3
"""1a.8 Step 0: 调查 "1 fund per image" 之谜
直接调真实 minimax M3，看 4 张样本图的原始 JSON"""

import json
import subprocess
import sys
import time
from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parents[3]
LOG_DIR = BACKEND_ROOT / "docs" / "test-records" / "automated-smoke" / "1a8"
LOG_DIR.mkdir(parents=True, exist_ok=True)
SAMPLES_DIR = BACKEND_ROOT / "uploads" / "samples"
SAMPLES = [
    "phase1a2-alipay-fund-list-20260715-2355-1.jpg",
    "phase1a2-alipay-fund-list-20260715-2355-2.jpg",
    "phase1a2-alipay-fund-list-20260715-2355-3.jpg",
    "phase1a2-alipay-fund-list-20260715-2356-1.jpg",
]


def curl(method, path, **kwargs):
    """Run curl, return parsed JSON or text."""
    cmd = ["curl", "-sS", "-X", method, f"http://127.0.0.1:8080{path}"]
    for k, v in kwargs.items():
        if k == "data":
            cmd += ["-d", v]
        elif k == "headers":
            for hk, hv in v.items():
                cmd += ["-H", f"{hk}: {hv}"]
        elif k == "form_file":
            cmd += ["-F", f"file=@{v}"]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if result.returncode != 0:
        print(f"  curl error: {result.stderr}")
        return None
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError:
        return result.stdout


def main():
    # 健康检查
    health = curl("GET", "/actuator/health")
    if not health or "status" not in str(health):
        print("Backend not running, start with 02-up-backend.sh")
        return 1
    print(f"Backend health: {health.get('status')}\n")

    for f in SAMPLES:
        sample_path = SAMPLES_DIR / f
        print(f"\n========== {f} ==========")
        print(f"file size: {sample_path.stat().st_size} bytes")

        # Upload
        upload = curl("POST", "/api/screenshot/upload",
                      headers={"X-User-Id": "1"},
                      form_file=str(sample_path))
        if not upload or upload.get("code") != 0:
            print(f"  upload failed: {upload}")
            continue
        file_id = upload["data"]["fileId"]
        print(f"fileId: {file_id}")

        # Parse
        parse = curl("POST", "/api/screenshot/parse",
                     headers={"Content-Type": "application/json", "X-User-Id": "1"},
                     data=json.dumps({"fileId": file_id, "userId": 1}))

        # Save full response
        out_file = LOG_DIR / f"00-raw-{sample_path.stem}.json"
        with open(out_file, "w", encoding="utf-8") as fp:
            json.dump(parse, fp, ensure_ascii=False, indent=2)

        # Summarize
        if not parse or not isinstance(parse, dict):
            print(f"  parse returned non-JSON: {str(parse)[:200]}")
            continue
        print(f"  top-level code: {parse.get('code')}")
        if parse.get("code") != 0:
            print(f"  message: {parse.get('message')}")
            continue
        data = parse.get("data", {})
        if not data:
            print(f"  no data: {parse}")
            continue
        print(f"  conversationId: {data.get('conversationId')}")
        categories = data.get("categories", [])
        total_funds = 0
        fund_names = []
        for cat in categories:
            funds = cat.get("funds", [])
            total_funds += len(funds)
            for f_line in funds:
                fund_names.append(f_line.get("fundName"))
        print(f"  categories: {len(categories)}")
        print(f"  total funds: {total_funds}")
        print(f"  fund names: {fund_names}")

        # Also query chat_history for the raw assistant content
        conv_id = data.get("conversationId")
        try:
            db_out = subprocess.run([
                "docker", "exec", "fincontrol-mysql", "mysql", "-uroot", "-proot", "fincontrol", "-N", "-e",
                f"SELECT LENGTH(content) FROM chat_history WHERE conversation_id='{conv_id}' AND role='assistant' LIMIT 1;"
            ], capture_output=True, text=True, timeout=10)
            if db_out.stdout.strip():
                size = int(db_out.stdout.strip())
                print(f"  chat_history assistant content: {size} bytes")
                # Extract just the JSON part from content
                if size > 0:
                    raw_out = subprocess.run([
                        "docker", "exec", "fincontrol-mysql", "mysql", "-uroot", "-proot", "fincontrol", "-N", "-e",
                        f"SELECT content FROM chat_history WHERE conversation_id='{conv_id}' AND role='assistant' LIMIT 1;"
                    ], capture_output=True, text=True, timeout=10)
                    raw_content = raw_out.stdout.strip()
                    # Save raw to file
                    raw_file = LOG_DIR / f"00-raw-content-{sample_path.stem}.txt"
                    with open(raw_file, "w", encoding="utf-8") as fp:
                        fp.write(raw_content)
                    # Count occurrences of "fund_name" in raw content
                    raw_fund_count = raw_content.count('"fund_name"')
                    print(f"  raw content fund_name occurrences: {raw_fund_count}")
                    # Preview first 500 chars
                    preview = raw_content[:500].replace("\n", " ")
                    print(f"  raw preview: {preview}...")
        except Exception as e:
            print(f"  db query error: {e}")

    print(f"\n{'='*60}")
    print("Step 0 complete. Output in:", LOG_DIR)
    print(f"{'='*60}")
    return 0


if __name__ == "__main__":
    sys.exit(main())