#!/usr/bin/env node
/**
 * check-decision-refs.cjs -- documentation anti-rot check (optional tooling).
 *
 * Verifies that every decision number referenced anywhere in the repo is
 * registered as a row in the authoritative summary table at the end of
 * docs/phase-0/decisions.md:
 *   - Chinese refs:  "jue ce N"  (regex written with \u escapes below)
 *   - file refs:     decision-N-<topic>.md
 *
 * Usage:  node scripts/check-decision-refs.cjs
 * Exit:   0 = all references registered; 1 = unregistered references found.
 *
 * Source kept ASCII-only (regex uses \u escapes) to follow the repo rule
 * that script files must not contain non-ASCII bytes.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const DECISIONS = path.join(ROOT, 'docs', 'phase-0', 'decisions.md');
const SELF = __filename;

// U+51B3 U+7B56 = "jue ce" (decision) in Chinese
const RE_CN = new RegExp('\u51b3\u7b56\\s*(\\d+)', 'g');
const RE_FILE = /decision-(\d+)-/g;

const SCAN_ROOTS = ['docs', 'fincontrol-backend/src', 'fincontrol-frontend/src', 'scripts'];
const EXT = new Set(['.md', '.java', '.js', '.jsx', '.cjs', '.sql', '.yml', '.yaml', '.ps1']);
const SKIP_DIRS = new Set(['node_modules', 'target', 'dist', '.git', 'uploads', 'logs', '.tmp']);

function readRegisteredIds() {
  const text = fs.readFileSync(DECISIONS, 'utf8');
  const at = text.indexOf('## \u51b3\u7b56\u603b\u7ed3\u8868'); // "decision summary table"
  if (at < 0) {
    console.error('[check-decision-refs] summary table not found in ' + path.relative(ROOT, DECISIONS));
    process.exit(2);
  }
  const ids = new Set();
  const re = /^\|\s*(\d+)(?:\s*v\d+)?\s*\|/gm;
  let m;
  const table = text.slice(at);
  while ((m = re.exec(table)) !== null) ids.add(Number(m[1]));
  return ids;
}

function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (SKIP_DIRS.has(e.name)) continue;
      walk(full, out);
    } else if (EXT.has(path.extname(e.name).toLowerCase()) && full !== SELF) {
      out.push(full);
    }
  }
  return out;
}

function main() {
  const registered = readRegisteredIds();
  const files = [];
  for (const r of SCAN_ROOTS) {
    const abs = path.join(ROOT, r);
    if (fs.existsSync(abs)) walk(abs, files);
  }

  let refCount = 0;
  const unregistered = [];
  for (const f of files) {
    const lines = fs.readFileSync(f, 'utf8').split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      let m;
      RE_CN.lastIndex = 0;
      while ((m = RE_CN.exec(line)) !== null) {
        refCount++;
        if (!registered.has(Number(m[1]))) {
          unregistered.push({ file: f, line: i + 1, id: m[1], via: 'CN-ref' });
        }
      }
      RE_FILE.lastIndex = 0;
      while ((m = RE_FILE.exec(line)) !== null) {
        if (!registered.has(Number(m[1]))) {
          unregistered.push({ file: f, line: i + 1, id: m[1], via: 'file-ref' });
        }
      }
    }
  }

  console.log('[check-decision-refs] registered decisions: ' + registered.size);
  console.log('[check-decision-refs] files scanned: ' + files.length + ', CN refs found: ' + refCount);

  if (unregistered.length === 0) {
    console.log('[check-decision-refs] OK: every referenced decision is registered in the summary table.');
    process.exit(0);
  }

  console.log('[check-decision-refs] FAIL: ' + unregistered.length + ' unregistered reference(s):');
  for (const u of unregistered) {
    console.log('  - decision ' + u.id + ' (' + u.via + ') at ' +
      path.relative(ROOT, u.file) + ':' + u.line);
  }
  console.log('[check-decision-refs] Fix: register the decision row in docs/phase-0/decisions.md, ' +
    'or correct the reference.');
  process.exit(1);
}

main();