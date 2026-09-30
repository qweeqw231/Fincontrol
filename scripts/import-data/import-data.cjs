/**
 * FinControl Phase 3 数据导入脚本
 * 
 * 数据源：
 *   1. asset_table_total_20260302-20260924.xlsx → asset_raw + asset_snapshot + fund_category_map + snapshot_meta
 *   2. portfolio_daily_complete_v3 (1).xlsx → nav_history + nav_milestone
 * 
 * 用法： node import-data.cjs
 */
const xlsx = require('xlsx');
const mysql = require('mysql2/promise');
const path = require('path');

// ======================== Config ========================
const DB_CONFIG = {
  host: 'localhost',
  port: 3306,
  user: 'root',
  password: 'root',
  database: 'fincontrol',
  charset: 'utf8mb4',
  multipleStatements: true
};

const EXCEL_DIR = 'c:\\Work\\基金数据可视化';
const USER_ID = 1;

// ======================== Helpers ========================

/** Excel serial date → 'YYYY-MM-DD' */
function excelDateToStr(serial) {
  if (typeof serial === 'string') {
    // Already a date string like "2025-10-13"
    return serial;
  }
  if (serial instanceof Date) {
    return serial.toISOString().slice(0, 10);
  }
  if (typeof serial !== 'number') return null;
  const ms = Date.UTC(1899, 11, 30) + Math.round(serial * 86400000);
  return new Date(ms).toISOString().slice(0, 10);
}

/** Check if a cell value is a valid number (not "-", not null, not empty string) */
function isNum(v) {
  if (v === null || v === undefined || v === '' || v === '-') return false;
  if (typeof v === 'number') return !isNaN(v);
  const n = parseFloat(v);
  return !isNaN(n) && isFinite(n);
}

/** Parse a cell value to number, return null if not valid */
function toNum(v) {
  if (!isNum(v)) return null;
  return typeof v === 'number' ? v : parseFloat(v);
}

/** Round to N decimal places */
function round(v, dp) {
  if (v === null || v === undefined) return null;
  const f = Math.pow(10, dp);
  return Math.round(v * f) / f;
}

// ======================== Fund & Category Definitions ========================

// Fund columns in asset_table_final: [colIndex, fundName, categoryName]
const FUND_DEFS = [
  [3,  '中加货币E',                '货币类'],
  [6,  '长城短债债券A',            '固收类'],
  [7,  '鹏华纯债债券D',            '固收类'],
  [8,  '安信新价值灵活配置混合A',  '固收类'],
  [11, '国泰黄金ETF联接C',         '商品类'],
  [12, '国泰黄金ETF联接A',         '商品类'],
  [13, '华安黄金ETF联接C',         '商品类'],
  [16, '诺安中证A100指数A',        'A股权益类'],
  [17, '诺安中证A100指数C',        'A股权益类'],
  [18, '国泰海通中证500指数增强C', 'A股权益类'],
  [19, '广发价值回报混合C',        'A股权益类'],
  [20, '易方达机器人ETF联接C',     'A股权益类'],
  [23, '天弘纳斯达克100指数(QDII)A',  '海外权益类'],
  [24, '天弘纳斯达克100指数(QDII)C',  '海外权益类'],
  [25, '摩根纳斯达克100指数(QDII)A',  '海外权益类'],
  [26, '招商纳斯达克100ETF联接(QDII)C','海外权益类'],
  [29, '华安香港精选股票(QDII)',      '港股大中华类'],
  [30, '易方达恒生科技ETF联接(QDII)C','港股大中华类'],
  [31, '余额宝',                  '余额类'],
  [32, '余额',                    '余额类'],
];

// Category summary columns in asset_table_final
// { categoryName: { valueCol, ratioCol, fundCols: [colIndices] } }
const CATEGORY_DEFS = {
  '货币类':       { valueCol: 1,  ratioCol: 2,  fundCols: [3] },
  '固收类':       { valueCol: 4,  ratioCol: 5,  fundCols: [6, 7, 8] },
  '商品类':       { valueCol: 9,  ratioCol: 10, fundCols: [11, 12, 13] },
  'A股权益类':    { valueCol: 14, ratioCol: 15, fundCols: [16, 17, 18, 19, 20] },
  '海外权益类':   { valueCol: 21, ratioCol: 22, fundCols: [23, 24, 25, 26] },
  '港股大中华类': { valueCol: 27, ratioCol: 28, fundCols: [29, 30] },
  // 余额类 is special: valueCol=null, calculated as sum of 余额宝+余额
  '余额类':       { valueCol: null, ratioCol: null, fundCols: [31, 32] },
};

// ======================== SQL: Create Tables ========================

const CREATE_NAV_HISTORY = `
CREATE TABLE IF NOT EXISTS nav_history (
  id                BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id           BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  nav_date          DATE         NOT NULL COMMENT '净值日期',
  weekday           VARCHAR(10)  NULL COMMENT '星期',
  daily_return_pct  DECIMAL(12,8) NULL COMMENT '涨跌幅(小数, 如0.0001=0.01%)',
  actual_profit     DECIMAL(12,2) NULL COMMENT '实际收益(元)',
  cumulative_profit DECIMAL(12,2) NULL COMMENT '累加(元)',
  nav               DECIMAL(12,8) NULL COMMENT '净值',
  nav_pct           DECIMAL(12,8) NULL COMMENT '净值%(小数, 如0.01=0.01%)',
  total_asset       DECIMAL(14,2) NULL COMMENT '总资产(元)',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_user_date (user_id, nav_date),
  INDEX idx_user_date (user_id, nav_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日净值历史表（外部数据导入）'
`;

const CREATE_NAV_MILESTONE = `
CREATE TABLE IF NOT EXISTS nav_milestone (
  id                BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id           BIGINT       NOT NULL DEFAULT 1 COMMENT '用户ID',
  milestone_date    DATE         NOT NULL COMMENT '里程碑日期',
  category          VARCHAR(50)  NULL COMMENT '里程碑类别',
  cumulative_profit DECIMAL(12,2) NULL COMMENT '累加(元)',
  nav               DECIMAL(12,8) NULL COMMENT '净值',
  nav_pct           DECIMAL(12,8) NULL COMMENT '净值%(小数)',
  description       TEXT         NULL COMMENT '说明',
  sort_order        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_user_date (user_id, milestone_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='净值关键里程碑表'
`;

// ======================== Import Functions ========================

async function importAssetTable(conn) {
  const file = path.join(EXCEL_DIR, 'asset_table_total_20260302-20260924.xlsx');
  const wb = xlsx.readFile(file);
  const ws = wb.Sheets['asset_table_final'];
  const rows = xlsx.utils.sheet_to_json(ws, { header: 1, raw: true, defval: null });
  
  console.log(`[asset_table] ${rows.length} rows (1 header + ${rows.length - 1} data)`);
  
  // --- fund_category_map ---
  const fundMapValues = FUND_DEFS.map(([col, name, cat]) => 
    [USER_ID, name, cat, 'user_manual']
  );
  await conn.execute(
    'DELETE FROM fund_category_map WHERE user_id = ?',
    [USER_ID]
  );
  await conn.query(
    'INSERT INTO fund_category_map (user_id, fund_name, category, source) VALUES ?',
    [fundMapValues]
  );
  console.log(`[fund_category_map] Inserted ${fundMapValues.length} funds`);
  
  // --- Process data rows ---
  let rawCount = 0, snapCount = 0, metaCount = 0;
  const batchRaw = [];
  const batchSnap = [];
  const batchMeta = [];
  let latestDate = null;
  
  for (let i = 1; i < rows.length; i++) {
    const row = rows[i];
    if (!row || !isNum(row[0])) continue;
    
    const dateStr = excelDateToStr(row[0]);
    if (!dateStr) continue;
    
    // Track latest date
    if (!latestDate || dateStr > latestDate) latestDate = dateStr;
    
    // Check if this row has any fund data (not all "-")
    const hasFundData = FUND_DEFS.some(([col]) => isNum(row[col]));
    if (!hasFundData) continue; // skip weekend/holiday rows with no breakdown
    
    // --- asset_raw: one row per fund with a value ---
    for (const [col, fundName, cat] of FUND_DEFS) {
      const amount = toNum(row[col]);
      if (amount === null) continue;
      
      batchRaw.push([
        USER_ID, dateStr, fundName, null, cat,
        round(amount, 2), 0, null, null,
        'import_csv', 'top', true, new Date()
      ]);
      rawCount++;
    }
    
    // --- asset_snapshot: one row per category ---
    const totalAsset = toNum(row[33]); // 总资产 column
    const totalAssetSafe = totalAsset !== null ? totalAsset : 0;
    
    for (const [catName, def] of Object.entries(CATEGORY_DEFS)) {
      let totalAmount, actualRatio, balanceFund = 0;
      
      if (def.valueCol !== null) {
        // Regular category
        totalAmount = toNum(row[def.valueCol]);
        actualRatio = toNum(row[def.ratioCol]);
        if (actualRatio !== null) {
          actualRatio = round(actualRatio * 100, 2); // decimal → percentage
        }
      } else {
        // 余额类: sum of 余额宝 + 余额
        totalAmount = 0;
        for (const fc of def.fundCols) {
          const v = toNum(row[fc]);
          if (v !== null) totalAmount += v;
        }
        totalAmount = round(totalAmount, 2);
        balanceFund = totalAmount;
        // Calculate ratio
        if (totalAssetSafe > 0) {
          actualRatio = round(totalAmount / totalAssetSafe * 100, 2);
        } else {
          actualRatio = 0;
        }
      }
      
      if (totalAmount === null) totalAmount = 0;
      
      // Build sub_detail JSON
      const subDetail = {};
      for (const fc of def.fundCols) {
        const fd = FUND_DEFS.find(([c]) => c === fc);
        if (fd) {
          const v = toNum(row[fc]);
          if (v !== null) subDetail[fd[1]] = v;
        }
      }
      
      batchSnap.push([
        USER_ID, dateStr, catName,
        round(totalAmount, 2), 0, actualRatio || 0,
        round(balanceFund, 2),
        Object.keys(subDetail).length > 0 ? JSON.stringify(subDetail) : null,
        'top', true
      ]);
      snapCount++;
    }
    
    // --- snapshot_meta ---
    batchMeta.push([USER_ID, dateStr, true, false, new Date()]);
    metaCount++;
  }
  
  // Clear existing data
  await conn.execute('DELETE FROM asset_raw WHERE user_id = ?', [USER_ID]);
  await conn.execute('DELETE FROM asset_snapshot WHERE user_id = ?', [USER_ID]);
  await conn.execute('DELETE FROM snapshot_meta WHERE user_id = ?', [USER_ID]);
  
  // Batch insert asset_raw (chunk if too large)
  const CHUNK = 500;
  for (let i = 0; i < batchRaw.length; i += CHUNK) {
    const chunk = batchRaw.slice(i, i + CHUNK);
    const placeholders = chunk.map(() => '(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)').join(', ');
    const flat = chunk.flat();
    await conn.execute(
      `INSERT INTO asset_raw (user_id, snapshot_date, fund_name, fund_code, category, amount, profit, holding_profit, cumulative_profit, source, total_asset_source, is_latest, confirmed_at) VALUES ${placeholders}`,
      flat
    );
  }
  console.log(`[asset_raw] Inserted ${rawCount} rows`);
  
  // Batch insert asset_snapshot
  for (let i = 0; i < batchSnap.length; i += CHUNK) {
    const chunk = batchSnap.slice(i, i + CHUNK);
    const placeholders = chunk.map(() => '(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)').join(', ');
    const flat = chunk.flat();
    await conn.execute(
      `INSERT INTO asset_snapshot (user_id, snapshot_date, category, total_amount, target_ratio, actual_ratio, balance_fund, sub_detail, total_asset_source, is_latest) VALUES ${placeholders}`,
      flat
    );
  }
  console.log(`[asset_snapshot] Inserted ${snapCount} rows`);
  
  // Batch insert snapshot_meta
  for (let i = 0; i < batchMeta.length; i += CHUNK) {
    const chunk = batchMeta.slice(i, i + CHUNK);
    const placeholders = chunk.map(() => '(?, ?, ?, ?, ?)').join(', ');
    const flat = chunk.flat();
    await conn.execute(
      `INSERT INTO snapshot_meta (user_id, snapshot_date, is_latest, is_current, confirmed_at) VALUES ${placeholders}`,
      flat
    );
  }
  console.log(`[snapshot_meta] Inserted ${metaCount} rows`);
  
  // Set the latest date as current
  if (latestDate) {
    await conn.execute(
      'UPDATE snapshot_meta SET is_current = 1 WHERE user_id = ? AND snapshot_date = ?',
      [USER_ID, latestDate]
    );
    console.log(`[snapshot_meta] Set is_current for latest date: ${latestDate}`);
  }
  
  return latestDate;
}

async function importNavHistory(conn) {
  const file = path.join(EXCEL_DIR, 'portfolio_daily_complete_v3 (1).xlsx');
  const wb = xlsx.readFile(file);
  const ws = wb.Sheets['每日明细'];
  const rows = xlsx.utils.sheet_to_json(ws, { header: 1, raw: true, defval: null });
  
  console.log(`\n[nav_history] 每日明细 sheet: ${rows.length} rows`);
  
  // Header at row 4 (0-indexed), data from row 5
  const batch = [];
  let count = 0;
  
  for (let i = 5; i < rows.length; i++) {
    const row = rows[i];
    if (!row || !isNum(row[1])) continue;
    // 跳过空占位行（尾部的日期无数据行）
    if (!isNum(row[6]) && !isNum(row[5]) && !isNum(row[8])) continue;
    
    const dateStr = excelDateToStr(row[1]);
    if (!dateStr) continue;
    
    const weekday = row[2] ? String(row[2]) : null;
    const dailyReturnPct = toNum(row[3]);     // 涨跌幅(%)
    const actualProfit = toNum(row[4]);       // 实际收益(元)
    const cumulativeProfit = toNum(row[5]);   // 累加(元)
    const nav = toNum(row[6]);                // 净值
    const navPct = toNum(row[7]);             // 净值%(%)
    const totalAsset = toNum(row[8]);         // 总资产(元)
    
    batch.push([
      USER_ID, dateStr, weekday,
      dailyReturnPct !== null ? round(dailyReturnPct, 8) : null,
      actualProfit !== null ? round(actualProfit, 2) : null,
      cumulativeProfit !== null ? round(cumulativeProfit, 2) : null,
      nav !== null ? round(nav, 8) : null,
      navPct !== null ? round(navPct, 8) : null,
      totalAsset !== null ? round(totalAsset, 2) : null,
    ]);
    count++;
  }
  
  await conn.execute('DELETE FROM nav_history WHERE user_id = ?', [USER_ID]);
  
  const CHUNK = 500;
  for (let i = 0; i < batch.length; i += CHUNK) {
    const chunk = batch.slice(i, i + CHUNK);
    const placeholders = chunk.map(() => '(?, ?, ?, ?, ?, ?, ?, ?, ?)').join(', ');
    const flat = chunk.flat();
    await conn.execute(
      `INSERT INTO nav_history (user_id, nav_date, weekday, daily_return_pct, actual_profit, cumulative_profit, nav, nav_pct, total_asset) VALUES ${placeholders}`,
      flat
    );
  }
  console.log(`[nav_history] Inserted ${count} rows`);
  
  // Date range
  if (batch.length > 0) {
    console.log(`  Date range: ${batch[0][1]} ~ ${batch[batch.length - 1][1]}`);
  }
}

async function importNavMilestones(conn) {
  const file = path.join(EXCEL_DIR, 'portfolio_daily_complete_v3 (1).xlsx');
  const wb = xlsx.readFile(file);
  const ws = wb.Sheets['关键日期'];
  const rows = xlsx.utils.sheet_to_json(ws, { header: 1, raw: true, defval: null });
  
  console.log(`\n[nav_milestone] 关键日期 sheet: ${rows.length} rows`);
  
  // Header at row 3, data from row 4
  // Columns: #, 日期, 类别, 累加(元), 净值, 净值%, 说明
  const batch = [];
  let count = 0;
  
  for (let i = 4; i < rows.length; i++) {
    const row = rows[i];
    if (!row || !row[0]) continue;
    
    const dateStr = excelDateToStr(row[1]);
    if (!dateStr) continue;
    
    const category = row[2] ? String(row[2]) : null;
    const cumulativeProfit = toNum(row[3]);
    const nav = toNum(row[4]);
    const navPct = toNum(row[5]);
    const desc = row[6] ? String(row[6]) : null;
    
    batch.push([
      USER_ID, dateStr, category,
      cumulativeProfit !== null ? round(cumulativeProfit, 2) : null,
      nav !== null ? round(nav, 8) : null,
      navPct !== null ? round(navPct, 8) : null,
      desc,
      count + 1
    ]);
    count++;
  }
  
  await conn.execute('DELETE FROM nav_milestone WHERE user_id = ?', [USER_ID]);
  
  if (batch.length > 0) {
    const placeholders = batch.map(() => '(?, ?, ?, ?, ?, ?, ?, ?)').join(', ');
    const flat = batch.flat();
    await conn.execute(
      `INSERT INTO nav_milestone (user_id, milestone_date, category, cumulative_profit, nav, nav_pct, description, sort_order) VALUES ${placeholders}`,
      flat
    );
  }
  console.log(`[nav_milestone] Inserted ${count} milestones`);
}

// ======================== Main ========================

async function main() {
  console.log('=== FinControl Phase 3 Data Import ===\n');
  
  const conn = await mysql.createConnection(DB_CONFIG);
  
  try {
    // 1. Create new tables
    console.log('[1/5] Creating nav_history and nav_milestone tables...');
    await conn.execute(CREATE_NAV_HISTORY);
    await conn.execute(CREATE_NAV_MILESTONE);
    console.log('  Tables ready.\n');
    
    // 2. Import asset_table_total → asset_raw, asset_snapshot, fund_category_map, snapshot_meta
    console.log('[2/5] Importing asset_table_total...');
    const latestDate = await importAssetTable(conn);
    console.log('');
    
    // 3. Import nav_history
    console.log('[3/5] Importing nav_history (每日明细)...');
    await importNavHistory(conn);
    console.log('');
    
    // 4. Import nav_milestone
    console.log('[4/5] Importing nav_milestone (关键日期)...');
    await importNavMilestones(conn);
    console.log('');
    
    // 5. Summary
    console.log('[5/5] Summary:');
    const [tables] = await conn.execute(`
      SELECT 'asset_raw' AS t, COUNT(*) AS cnt FROM asset_raw WHERE user_id = ${USER_ID}
      UNION ALL SELECT 'asset_snapshot', COUNT(*) FROM asset_snapshot WHERE user_id = ${USER_ID}
      UNION ALL SELECT 'fund_category_map', COUNT(*) FROM fund_category_map WHERE user_id = ${USER_ID}
      UNION ALL SELECT 'snapshot_meta', COUNT(*) FROM snapshot_meta WHERE user_id = ${USER_ID}
      UNION ALL SELECT 'nav_history', COUNT(*) FROM nav_history WHERE user_id = ${USER_ID}
      UNION ALL SELECT 'nav_milestone', COUNT(*) FROM nav_milestone WHERE user_id = ${USER_ID}
    `);
    for (const r of tables) {
      console.log(`  ${r.t}: ${r.cnt} rows`);
    }
    
    console.log('\n=== Import complete! ===');
  } catch (err) {
    console.error('ERROR:', err.message);
    console.error(err.stack);
    process.exit(1);
  } finally {
    await conn.end();
  }
}

main();
