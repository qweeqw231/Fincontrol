/**
 * 2a 校正页：两次 LQR-ZOH 联合校正完整过程回填
 *
 * 数据来源：
 *   - 6/30 第一次联合校正：mcf 静态站 §3.6（sec06-jun30-correction.html）
 *   - 9/30 第二次联合校正：北极星主系统过程实证/2026年9月末LQR-ZOH联合校正计算过程.docx
 *
 * 写入：
 *   - operation_log.correction_mode = 'lqr_zoh'（按 snapshot_date + monthly_correction 定位已有行）
 *   - correction_iteration（α 迭代/探针轮次）
 *   - correction_asset_detail（pre_six / pre_high / post_high）
 *   - correction_param（IC-DRR / 参数 / 锚定 / KKT 说明）
 *
 * 幂等：先按 operation_log_id 删除三张子表旧行，再插入。
 * 用法：node import-correction-details.cjs
 */
const mysql = require('mysql2/promise');

const DB_CONFIG = {
  host: 'localhost',
  port: 3306,
  user: 'root',
  password: 'root',
  database: 'fincontrol',
  charset: 'utf8mb4',
  multipleStatements: true
};
const USER_ID = 1;

// ============================================================
// 6/30 第一次 LQR-ZOH 联合校正（mcf §3.6）
// ============================================================
// α 七轮压缩（§3.6 表 3）：初始 α=20%（E=216）→ 第 7 次 α=4.19%（E=45.30）
const JUN30_ITERATIONS = [
  [0, 0.2,    216.0,  1298.28, 218.28, '初始：总投入 1,298.28 > M_max=1,080，启动 α 削减'],
  [1, 0.16,   172.8,  1240.31, 160.31, '第 1 次 α × 0.8'],
  [2, 0.128,  138.24, 1194.06, 114.06, '第 2 次 α × 0.8'],
  [3, 0.1024, 110.59, 1157.16, 77.16,  '第 3 次 α × 0.8'],
  [4, 0.0819, 88.47,  1127.63, 47.63,  '第 4 次 α × 0.8'],
  [5, 0.0655, 70.78,  1103.98, 23.98,  '第 5 次 α × 0.8'],
  [6, 0.0524, 56.62,  1084.94, 4.94,   '第 6 次 α × 0.8'],
  [7, 0.0419, 45.30,  1069.81, null,   '第 7 次满足约束：高波校正预算 45.30（取整 46）'],
];

// 逐资产：校正前六大类 / 高波内部 / 校正后高波内部
const JUN30_ASSETS = [
  // phase, category, amount, ratio_actual, ratio_target, deviation, delta_raw, delta_amount, note
  ['pre_six', '货币类',      641.94,  9.35,  10, null, null, null, null],
  ['pre_six', '固收类',      956.17,  13.92, 15, null, null, null, null],
  ['pre_six', '商品类',      1510.25, 21.99, 25, null, null, null, null],
  ['pre_six', 'A股权益类',   1842.41, 26.83, 25, null, null, null, null],
  ['pre_six', '海外权益类',  1563.15, 22.76, 20, null, null, null, null],
  ['pre_six', '港股大中华类', 352.73, 5.14,  5,  null, null, null, '合计 6,866.65'],
  ['pre_high', '商品类',      1510.25, 28.67, 33.33, -4.66, 45.30, 46, '待补仓资产（低配最严重）'],
  ['pre_high', 'A股权益类',   1842.41, 34.97, 33.33, 1.64,  0, 0, '已超配，不参与补仓'],
  ['pre_high', '海外权益类',  1563.15, 29.67, 26.67, 3.00,  0, 0, '锚定资产 Δ=0（超配最严重）'],
  ['pre_high', '港股大中华类', 352.73, 6.69,  6.67,  0.02,  0, 0, '已超配，不参与补仓'],
  ['post_high', '商品类',      1556.25, 29.28, 33.33, -4.05, null, null, '补入 46 后仍未完全复位'],
  ['post_high', 'A股权益类',   1842.41, 34.66, 33.33, 1.33,  null, null, null],
  ['post_high', '海外权益类',  1563.15, 29.41, 26.67, 2.74,  null, null, null],
  ['post_high', '港股大中华类', 352.73, 6.64,  6.67,  -0.03, null, null, null],
];

const JUN30_PARAMS = [
  // key, num_value, text_value
  ['surplus', 600, null],
  ['mMax', 1080, null],
  ['mMaxSource', null, 'default'],
  ['alphaInit', 0.2, null],
  ['alphaDecay', 0.8, null],
  ['alphaFinal', 0.0419, null],
  ['zohThreshold', 100, null],
  ['icDrrBefore', 33.41, 'f_before（百分点²）：(−4.66)²+(1.64)²+(3.00)²+(0.02)²；原文 0.3341×10⁻²'],
  ['icDrrAfter', 25.60, 'f_after（百分点²）；原文 0.2560×10⁻²'],
  ['icDrrPct', 23.4, null],
  ['anchor', null, '海外权益类'],
  ['zohTriggered', null, 'true'],
  ['kktNote', null, '理论上 Δg≈247.52 可完全复位商品类，受 E_high=216 截断；α 七轮压缩后高波预算仅 45.30（取整 46）'],
];

// ============================================================
// 9/30 第二次 LQR-ZOH 联合校正（北极星计算过程 docx）
// ============================================================
const SEP30_ITERATIONS = [
  [0, 0.2,   252,   67.89,  97.84,  false, 870,     null,  '分布式 α 表：20%（默认上限 1,260 基准）'],
  [1, 0.3,   378,   84.69,  123.08, false, 996,     null,  '分布式 α 表：30%'],
  [2, 0.35,  441,   93.09,  135.68, false, 1059,    null,  '分布式 α 表：35%'],
  [3, 0.39,  491,   99.75,  145.68, false, 1109,    null,  '分布式 α 表：39%（Δm=99.75 逼近触发）'],
  [4, 0.3032, 382,  85.22,  123.88, false, 1000,    null,  '人为上限 M_max=1,000 倒推：E_high=382，总投入恰好用满；Δm<100 不触发'],
  [5, 0.3912, 493,  100.0,  146.08, true,  1357.08, 357.08, 'ZOH 阶跃点参考：E=493 时 Δm=100 触发，总投入阶跃至 1,357.08'],
];

const SEP30_ASSETS = [
  ['pre_six', '货币类',      988.27,  10.37, 10, null, null, null, null],
  ['pre_six', '固收类',      1486.37, 15.60, 15, null, null, null, null],
  ['pre_six', '商品类',      2207.71, 23.18, 25, null, null, null, null],
  ['pre_six', 'A股权益类',   2239.95, 23.51, 25, null, null, null, null],
  ['pre_six', '海外权益类',  2152.13, 22.59, 20, null, null, null, null],
  ['pre_six', '港股大中华类', 451.44, 4.74,  5,  null, null, null, '合计 9,525.87（不变价推导至 9/30）'],
  ['pre_high', '商品类',      2207.71, 31.31, 33.33, -2.02, 207.12, 207, '待补仓（KKT）'],
  ['pre_high', 'A股权益类',   2239.95, 31.77, 33.33, -1.56, 174.88, 175, '待补仓（KKT）'],
  ['pre_high', '海外权益类',  2152.13, 30.52, 26.67, 3.85,  0, 0, '锚定资产 Δ=0（超配最严重）'],
  ['pre_high', '港股大中华类', 451.44, 6.40,  6.67,  -0.27, -12.53, 0, '内点解为负 → 边界解置 0'],
  ['post_high', '商品类',      2414.71, 32.49, 33.33, -0.84, null, null, '新市值 = 2,207.71 + 207'],
  ['post_high', 'A股权益类',   2414.95, 32.49, 33.33, -0.84, null, null, '新市值 = 2,239.95 + 175'],
  ['post_high', '海外权益类',  2152.13, 28.95, 26.67, 2.28,  null, null, '仍为唯一显著超配'],
  ['post_high', '港股大中华类', 451.44, 6.07,  6.67,  -0.60, null, null, null],
];

const SEP30_PARAMS = [
  ['surplus', 700, null],
  ['mMax', 1000, null],
  ['mMaxSource', null, 'manual'],
  ['alphaInit', 0.2, null],
  ['alphaDecay', 0.8, null],
  ['alphaFinal', 0.3032, 'E_high=382 / 默认上限 1,260'],
  ['zohThreshold', 100, null],
  ['zohStepPointEHigh', 493, null],
  ['zohStepTotal', 1357.08, '触发后总投入（阶跃 +246.08）'],
  ['icDrrBefore', 21.41, null],
  ['icDrrAfter', 6.97, null],
  ['icDrrPct', 67.4, null],
  ['anchor', null, '海外权益类'],
  ['zohTriggered', null, 'false'],
  ['actualTransfer', 990, '实际转入余额宝 990（余额宝已有 10）'],
  ['kktNote', null, '内点解不成立（港股大中华类 Δ=-12.53<0）→ 边界解 Δh=0；Δg=207.12、Δa=174.88'],
];

async function backfill(conn, snapshotDate, iterations, assets, params) {
  const [rows] = await conn.execute(
    `SELECT id FROM operation_log
     WHERE user_id = ? AND snapshot_date = ? AND operation_type = 'monthly_correction'
     ORDER BY id ASC LIMIT 1`,
    [USER_ID, snapshotDate]
  );
  if (rows.length === 0) {
    console.log(`[skip] ${snapshotDate} 未找到 operation_log 行`);
    return;
  }
  const opId = rows[0].id;

  await conn.execute(
    `UPDATE operation_log SET correction_mode = 'lqr_zoh' WHERE id = ?`,
    [opId]
  );
  await conn.execute('DELETE FROM correction_iteration WHERE operation_log_id = ?', [opId]);
  await conn.execute('DELETE FROM correction_asset_detail WHERE operation_log_id = ?', [opId]);
  await conn.execute('DELETE FROM correction_param WHERE operation_log_id = ?', [opId]);

  let rowsAdded = 0;
  if (iterations === JUN30_ITERATIONS) {
    for (const [sortOrder, alpha, eHigh, total, overLimit, note] of JUN30_ITERATIONS) {
      await conn.execute(
        `INSERT INTO correction_iteration
           (user_id, operation_log_id, sort_order, alpha, e_high, zoh_triggered, total_investment, over_limit, note)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [USER_ID, opId, sortOrder, alpha, eHigh, true, total, overLimit, note]
      );
      rowsAdded++;
    }
  } else {
    for (const [sortOrder, alpha, eHigh, deltaM, deltaB, zoh, total, overLimit, note] of SEP30_ITERATIONS) {
      await conn.execute(
        `INSERT INTO correction_iteration
           (user_id, operation_log_id, sort_order, alpha, e_high, delta_m, delta_b, zoh_triggered, total_investment, over_limit, note)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [USER_ID, opId, sortOrder, alpha, eHigh, deltaM, deltaB, zoh ? 1 : 0, total, overLimit, note]
      );
      rowsAdded++;
    }
  }

  let sortOrder = 0;
  for (const [phase, category, amount, ratioActual, ratioTarget, deviation, deltaRaw, deltaAmount, note] of assets) {
    await conn.execute(
      `INSERT INTO correction_asset_detail
         (user_id, operation_log_id, sort_order, phase, category, amount, ratio_actual, ratio_target, deviation, delta_raw, delta_amount, note)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [USER_ID, opId, sortOrder++, phase, category, amount, ratioActual, ratioTarget, deviation, deltaRaw, deltaAmount, note]
    );
    rowsAdded++;
  }

  for (const [key, numValue, textValue] of params) {
    await conn.execute(
      `INSERT INTO correction_param
         (user_id, operation_log_id, param_key, num_value, text_value)
       VALUES (?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE num_value = VALUES(num_value), text_value = VALUES(text_value)`,
      [USER_ID, opId, key, numValue, textValue]
    );
    rowsAdded++;
  }

  console.log(`[ok] ${snapshotDate} → operation_log.id=${opId}，明细 ${rowsAdded} 行`);
}

async function main() {
  console.log('=== 2a 校正过程回填（6/30 + 9/30 LQR-ZOH 联合校正） ===\n');
  const conn = await mysql.createConnection(DB_CONFIG);
  try {
    await backfill(conn, '2026-06-30', JUN30_ITERATIONS, JUN30_ASSETS, JUN30_PARAMS);
    await backfill(conn, '2026-09-30', SEP30_ITERATIONS, SEP30_ASSETS, SEP30_PARAMS);

    const [check] = await conn.execute(
      `SELECT o.id, o.snapshot_date, o.correction_mode,
              (SELECT COUNT(*) FROM correction_iteration i WHERE i.operation_log_id = o.id) AS iterations,
              (SELECT COUNT(*) FROM correction_asset_detail a WHERE a.operation_log_id = o.id) AS assets,
              (SELECT COUNT(*) FROM correction_param p WHERE p.operation_log_id = o.id) AS params
       FROM operation_log o
       WHERE o.user_id = ? AND o.snapshot_date IN ('2026-06-30', '2026-09-30')
       ORDER BY o.snapshot_date`,
      [USER_ID]
    );
    console.log('\n汇总：');
    for (const r of check) {
      console.log(`  id=${r.id} ${r.snapshot_date.toISOString().slice(0, 10)} mode=${r.correction_mode} ` +
        `迭代 ${r.iterations} / 资产 ${r.assets} / 参数 ${r.params}`);
    }
    console.log('\n=== 完成 ===');
  } catch (e) {
    console.error('ERROR:', e.message);
    process.exit(1);
  } finally {
    await conn.end();
  }
}

main();