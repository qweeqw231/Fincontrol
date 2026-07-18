#!/usr/bin/env python3
"""Extract fileId from JSON file (first arg). Print to stdout."""
import json, sys
try:
    with open(sys.argv[1], encoding='utf-8') as f:
        d = json.load(f)
    print(d.get('data', {}).get('fileId', ''), end='')
except Exception as e:
    sys.exit(1)