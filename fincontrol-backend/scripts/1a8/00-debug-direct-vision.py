#!/usr/bin/env python3
"""1a.8 Step 0 v5: 直接调 minimax M3 API（绕过应用代码）看 raw response.

本脚本直接调 minimax M3，看 4 张样本图真实返回。
目的：判断 "1 fund per image" 是模型能力问题还是我们代码 bug。
"""

import base64
import json
import os
import sys
import urllib.request
import urllib.error
from pathlib import Path

# 复用 application.yml 的配置
BASE_URL = "https://api.minimaxi.com/v1"
MODEL = "MiniMax-M3"
API_KEY = os.environ.get("VISION_API_KEY", "")  # 从 env var 读

# 1a.7 application-local.yml 里的 key（debug 专用，明文写这里）
# 如果你不想明文存，可以 export VISION_API_KEY=sk-... 走 env
if not API_KEY:
    sys.exit("请设置 VISION_API_KEY env var 或在脚本里填入 API key")

BACKEND_ROOT = Path(__file__).resolve().parents[3]
SAMPLES_DIR = BACKEND_ROOT / "uploads" / "samples"
LOG_DIR = BACKEND_ROOT / "docs" / "test-records" / "automated-smoke" / "1a8"
LOG_DIR.mkdir(parents=True, exist_ok=True)

SAMPLES = [
    "phase1a2-alipay-fund-list-20260715-2355-1.jpg",
    "phase1a2-alipay-fund-list-20260715-2355-2.jpg",
    "phase1a2-alipay-fund-list-20260715-2355-3.jpg",
    "phase1a2-alipay-fund-list-20260715-2356-1.jpg",
]

# 复用 prompt_versions 表里的 screenshot_parser prompt
import subprocess
result = subprocess.run(
    ["docker", "exec", "fincontrol-mysql", "mysql", "-uroot", "-proot", "fincontrol", "-N", "-e",
     "SELECT prompt_content FROM prompt_versions WHERE prompt_name='screenshot_parser' LIMIT 1"],
    capture_output=True, text=True, timeout=10
)
SYSTEM_PROMPT = result.stdout.strip()
print(f"System prompt length: {len(SYSTEM_PROMPT)} chars")
print(f"System prompt (first 200 chars): {SYSTEM_PROMPT[:200]}...")
print()


def call_minimax(image_path: Path) -> dict:
    """直接 POST 到 minimax M3, 不用我们的 client 包装。"""
    image_b64 = base64.b64encode(image_path.read_bytes()).decode("ascii")
    mime = "image/jpeg" if image_path.suffix.lower() in (".jpg", ".jpeg") else "image/png"
    data_url = f"data:{mime};base64,{image_b64}"

    body = {
        "model": MODEL,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": [
                {"type": "text", "text": "请解析以下支付宝资产截图"},
                {"type": "image_url", "image_url": {"url": data_url}},
            ]},
        ],
        "stream": False,
    }

    req = urllib.request.Request(
        f"{BASE_URL}/chat/completions",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return {"ok": True, "status": resp.status, "data": data}
    except urllib.error.HTTPError as e:
        return {"ok": False, "status": e.code, "error": e.read().decode("utf-8")}
    except Exception as e:
        return {"ok": False, "error": str(e)}


def summarize(content: str) -> dict:
    """从 model raw response 抽 JSON，看有多少只 fund。"""
    if not content:
        return {"fund_count": 0, "error": "empty content"}

    # Try parse as JSON first
    try:
        data = json.loads(content)
        return {"raw_json": data, "strategy": "direct_json"}
    except json.JSONDecodeError:
        pass

    # Find first JSON object
    start = content.find("{")
    if start < 0:
        return {"fund_count": 0, "error": "no JSON in content", "preview": content[:300]}

    # Brace match
    depth = 0
    end = -1
    for i in range(start, len(content)):
        if content[i] == "{":
            depth += 1
        elif content[i] == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break

    if end < 0:
        return {"fund_count": 0, "error": "unbalanced JSON", "preview": content[:300]}

    try:
        data = json.loads(content[start:end])
        return {"raw_json": data, "strategy": "extract_first_json"}
    except json.JSONDecodeError as e:
        return {"fund_count": 0, "error": f"json parse failed: {e}", "preview": content[:300]}


def main():
    print("=" * 60)
    print(" 1a.8 Step 0 v5: 直接调 minimax M3")
    print("=" * 60)

    for f in SAMPLES:
        sample_path = SAMPLES_DIR / f
        if not sample_path.exists():
            print(f"\nSKIP: {f} not found")
            continue
        print(f"\n========== {f} ==========")
        print(f"file size: {sample_path.stat().st_size} bytes")

        # Save image b64 to log for debugging
        b64 = base64.b64encode(sample_path.read_bytes()).decode("ascii")
        (LOG_DIR / f"00-img-{sample_path.stem}.b64.txt").write_text(b64[:200] + "...")

        result = call_minimax(sample_path)
        (LOG_DIR / f"00-resp-{sample_path.stem}.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))

        if not result["ok"]:
            print(f"FAILED: status={result.get('status')} error={result.get('error','')[:200]}")
            continue

        resp = result["data"]
        # OpenAI 格式: choices[0].message.content
        try:
            content = resp["choices"][0]["message"]["content"]
        except (KeyError, IndexError) as e:
            print(f"Unexpected response shape: {e}")
            print(f"Response keys: {list(resp.keys())}")
            continue

        print(f"Raw content length: {len(content)} chars")
        print(f"First 300 chars: {content[:300]!r}")

        summary = summarize(content)
        print(f"Strategy: {summary.get('strategy', '?')}")
        if 'raw_json' in summary:
            data = summary['raw_json']
            print(f"Top-level keys: {list(data.keys())}")
            if 'holdings' in data:
                print(f"  -> 'holdings' array length: {len(data['holdings'])}")
                for h in data['holdings'][:5]:
                    print(f"     - {h}")
            if 'categories' in data:
                print(f"  -> 'categories' array length: {len(data['categories'])}")
                for c in data['categories'][:3]:
                    print(f"     - {c}")
            if 'funds' in data:
                print(f"  -> 'funds' array length: {len(data['funds'])}")
        else:
            print(f"Summary: {summary}")


if __name__ == "__main__":
    main()