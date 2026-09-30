/**
 * MCF（micro-control-finance）关键事件补充导入
 *
 * 背景：净值收益表（portfolio_daily_complete_v3）的关键事件只到 2026-08-12，
 *       8/12 之后的事件需从 mcf 静态站点 + 北极星实证文档补充。
 *
 * 数据来源：
 *   - c:\Work\Minimax_Work\micro-control-finance\chapter-3-empirical-backtest\sec30-keytime-table.html
 *   - c:\Work\Minimax_Work\micro-control-finance\timeline.html
 *   - c:\Work\投资组合研究\北极星\北极星主系统过程实证\8月末ZOH校正记录.docx
 *   - c:\Work\投资组合研究\北极星\北极星主系统过程实证\2026年9月末LQR-ZOH联合校正计算过程.docx
 *   - c:\Work\投资组合研究\北极星\北极星主系统过程实证\北极星主系统主动战术调仓记录-20260825.docx
 *
 * 交叉验证：所有净值/总资产数字已与 nav_history 表比对一致（2026-09-30 验证）
 *
 * 用法： node import-mcf-events.cjs
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
// 1. nav_milestone 补充（8/12 之后的净值里程碑）
// ============================================================
const NEW_MILESTONES = [
  [10, '2026-08-31', '总资产新高', 105.00, 1.109104, 10.9104,
   '★ 总资产历史新高 ¥9,776.97，净值 1.1091；同日北证50讨论熔断（成交额100.87亿）。净值未创新高＝定投入金摊薄净值涨幅'],
  [11, '2026-09-15', '净值阶段低点', -117.91, 1.083743, 8.3743,
   '净值回落至 1.0837，累加 -¥117.91；月内前哨黄金 1,000 元尚未回本'],
  [12, '2026-09-23', '净值反弹', 58.66, 1.103791, 10.3791,
   '净值反弹至 1.1038，累加 +¥58.66；9/24 累加再次转负 → 定投入金与浮盈的拉锯'],
];

// 修正 #9 的误导性标签（原为"最新 (8/12)"，但数据已延伸至 9/25）
const MILESTONE_FIX = {
  oldCategory: '最新 (8/12)',
  newCategory: '累加转正',
  newDesc: '★ 累加 +¥140.41，净值 1.1134 (+11.34%) —— 累加自 6/11 修正后首次转正',
};

// ============================================================
// 2. operation_log 校正与操作记录（ZOH / LQR / 战术调仓）
// ============================================================
// 字段顺序：
// operation_date, operation_type, snapshot_date, v_curr, v_monetary, v_bond, v_high_vol,
// u_high, u_monetary_dca, u_bond_dca, delta_m_theory, delta_b_theory,
// delta_m_actual, delta_b_actual, rounding_strategy, budget_limit_used, total_investment,
// triggered_boundary, warnings, source
const OPERATIONS = [
  // --- 2026-04-30 ZOH 校正（4月末）---
  ['2026-04-30 18:00:00', 'monthly_correction', '2026-04-30',
    null, null, null, null,
    560, null, null, 169.79, 212.65,
    null, null, null, null, 943.00,
    '1', JSON.stringify([
      'ZOH 触发：4月纳指大涨，货币/固收缺口均超阈值',
      '五一休市，采样时点由 5/1 前移至 4/30',
      '高波定投 560 元；当月总投入约 943 元'
    ])],

  // --- 2026-06-11 Delta 行动（黄金）---
  ['2026-06-11 18:00:00', 'manual_adjustment', '2026-06-11',
    null, null, null, null,
    null, null, null, null, null,
    null, null, null, null, 30.00,
    null, JSON.stringify([
      'Delta 首次触发：黄金占比 21.88%，偏差 -3.12%',
      '买入 30 元 国泰黄金ETF联接C，偏差收窄至 -3.01%',
      '6/12 黄金 +2.7%，策略日收益 +1.23%',
      '注：6/11 同日官方数据修正（+76.6 → -76.6），累加重算'
    ])],

  // --- 2026-06-30 第一次 LQR-ZOH 联合校正 ---
  ['2026-06-30 18:00:00', 'monthly_correction', '2026-06-30',
    6866.65, null, null, 5268.54,
    null, null, null, 68.30, 106.88,
    null, null, 'round_up_10', 1080.00, 1069.81,
    '1', JSON.stringify([
      'LQR 权重 α 经 7 次迭代由 20% 收敛至 4.19%',
      'Δm=68.30（货币类）、Δb=106.88（固收类）',
      'Δg 黄金 = 45.30（取整 46）',
      'IC-DRR ≈ 23.4%（f: 0.3341 → 0.2560 ×10⁻²）'
    ])],

  // --- 2026-07-30 ZOH 不触发（7月末）---
  ['2026-07-30 18:00:00', 'monthly_correction', '2026-07-30',
    7619.57, null, null, null,
    null, null, null, 40.83, 65.46,
    0, 0, 'manual', null, 0.00,
    null, JSON.stringify([
      '结论：不触发补仓（Δm 未达 100 阈值）',
      'v2.0 修正版：Δm≈40.83 / Δb≈65.46（旧经验法估算为 30.20）',
      '校正节奏规律：4月末 → 6月末 → 8月末 →（10月末）→ 12月末，约 2 个月一次'
    ])],

  // --- 2026-08-03 前哨-0803-黄金抄底 ---
  ['2026-08-03 14:00:00', 'manual_adjustment', '2026-08-03',
    null, null, null, null,
    null, null, null, null, null,
    null, null, null, null, 300.00,
    null, JSON.stringify([
      '前哨行动：黄金抄底 300 元（占个人总资产 1.52%）',
      '买入价 ≈ 4,040 美元/盎司，目标止盈 5,500'
    ])],

  // --- 2026-08-24 前哨-0824-北证50 定投建仓 ---
  ['2026-08-24 14:00:00', 'manual_adjustment', '2026-08-24',
    null, null, null, null,
    null, null, null, null, null,
    null, null, null, null, 25.00,
    null, JSON.stringify([
      '前哨行动：北证50 定投建仓（当天即暂停）',
      '原计划 25 元 × 49 日 = 1,225 元，仅保留 25 元桥头堡',
      '08-27 加仓 25 元（动用 ZOH 资金池剩余富余），持仓升至 50 元'
    ])],

  // --- 2026-08-25 A股内部仓位置换 ---
  ['2026-08-25 14:30:00', 'manual_adjustment', '2026-08-25',
    null, null, null, null,
    null, null, null, null, null,
    null, null, null, null, 116.31,
    null, JSON.stringify([
      '主动战术调仓：卖出 广发价值回报混合C(004853) ≈116.37 元 → 买入 上证50ETF联接A ≈116.31 元',
      '手续费 0.06 元；卖出端亏损 -3.63 元（系统首次实现亏损）',
      '真尾差 1.13 元 = 116.37 - 115.24',
      '前哨行动0号正式追认：机器人ETF(020973) 3/23 买入约 100 元 → 扩至 215.24 元',
      '另注：补充说明文档记为买入机器人ETF 115.24 元，两份文档口径存冲突，待核'
    ])],

  // --- 2026-08-31 第二次单独 ZOH 低波校正 ---
  ['2026-08-31 18:00:00', 'monthly_correction', '2026-08-31',
    8713.89, 797.38, 1194.11, null,
    659, null, null, 186.81, 282.20,
    190.00, 290.00, 'round_up_10', null, 1143.76,
    '1', JSON.stringify([
      '校正前六大类合计 8,713.89 元（货币 797.38 / 固收 1,194.11 / 商品 2,208.28 / A股 2,143.89 / 海外 1,936.47 / 港股 433.76）',
      'Δm≈186.81 → 取整 190；Δb≈282.20 → 取整 290（长城短债100 + 鹏华纯债100 + 安信新价值90）',
      '机器人尾差补足 4.76 元（215.24 → 220）',
      '9月高波定投 659 元；余额宝实际转入 1,139 元',
      '总投入 1,143.76 元'
    ])],

  // --- 2026-09-01 月度前哨-0901-黄金 ---
  ['2026-09-01 14:00:00', 'manual_adjustment', '2026-09-01',
    null, null, null, null,
    null, null, null, null, null,
    null, null, null, null, 1000.00,
    null, JSON.stringify([
      '月度前哨：黄金 1,000 元买入（区间 4,400 - 4,450）',
      '2026-09-24 前哨正式终止：美债 > 5.1%、黄金回落至 4,274 - 4,300，1,000 元未回本'
    ])],

  // --- 2026-09-30 第二次 LQR-ZOH 联合校正（9月末）---
  ['2026-09-30 18:00:00', 'monthly_correction', '2026-09-30',
    9525.87, 988.27, 1486.37, null,
    618, null, null, 85.22, null,
    null, null, 'manual', 1000.00, 1000.00,
    null, JSON.stringify([
      '校正前六大类 9,525.87 元（货币 988.27 / 固收 1,486.37 / 商品 2,207.71 / A股 2,239.95 / 海外 2,152.13 / 港股 451.44）',
      '10月高波定投 618 元；LQR 高波校正 382 元（商品 207 + A股 175 + 港股 0）',
      'ZOH 货币补仓 85.22 < 100 → 不触发',
      '总投入 1,000 元（人为上限 M_max=1000，默认 1,260）；实际转入余额宝 990 元',
      'IC-DRR ≈ 67.4%（f: 21.41 → 6.97 ×10⁻²）',
      '阶跃点参考：E_high=493 时 Δm=100 触发，总投入跃升至 1,357.08 元（+248 元）'
    ])],
];

async function main() {
  console.log('=== MCF 关键事件补充导入 ===\n');
  const conn = await mysql.createConnection(DB_CONFIG);

  try {
    // --- 1a. 修正 #9 标签 ---
    const [fixRes] = await conn.execute(
      `UPDATE nav_milestone SET category = ?, description = ?
       WHERE user_id = ? AND category = ?`,
      [MILESTONE_FIX.newCategory, MILESTONE_FIX.newDesc, USER_ID, MILESTONE_FIX.oldCategory]
    );
    console.log(`[nav_milestone] 修正 #9 标签：${fixRes.affectedRows} 行`);

    // --- 1b. 追加 8/12 后的净值里程碑 ---
    await conn.execute(
      'DELETE FROM nav_milestone WHERE user_id = ? AND sort_order > 9',
      [USER_ID]
    );
    let added = 0;
    for (const m of NEW_MILESTONES) {
      const [r] = await conn.execute(
        `INSERT INTO nav_milestone
           (user_id, sort_order, milestone_date, category, cumulative_profit, nav, nav_pct, description)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        [USER_ID, ...m]
      );
      added += r.affectedRows;
    }
    console.log(`[nav_milestone] 新增 ${added} 条（8/12 之后）`);

    // --- 2. operation_log 校正与操作记录 ---
    await conn.execute(
      `DELETE FROM operation_log WHERE user_id = ? AND source = 'import_mcf'`,
      [USER_ID]
    );
    const COLS = [
      'operation_date', 'operation_type', 'snapshot_date',
      'v_curr', 'v_monetary', 'v_bond', 'v_high_vol',
      'u_high', 'u_monetary_dca', 'u_bond_dca',
      'delta_m_theory', 'delta_b_theory', 'delta_m_actual', 'delta_b_actual',
      'rounding_strategy', 'budget_limit_used', 'total_investment',
      'triggered_boundary', 'warnings', 'source'
    ];
    let opAdded = 0;
    for (const op of OPERATIONS) {
      const row = [...op, 'import_mcf'];
      const placeholders = COLS.map(() => '?').join(', ');
      const [r] = await conn.execute(
        `INSERT INTO operation_log (user_id, ${COLS.join(', ')}) VALUES (?, ${placeholders})`,
        [USER_ID, ...row]
      );
      opAdded += r.affectedRows;
    }
    console.log(`[operation_log] 新增 ${opAdded} 条（ZOH/LQR 校正 + 战术调仓）`);

    // --- 3. 汇总 ---
    const [ms] = await conn.execute(
      'SELECT COUNT(*) AS c FROM nav_milestone WHERE user_id = ?', [USER_ID]
    );
    const [ops] = await conn.execute(
      'SELECT COUNT(*) AS c FROM operation_log WHERE user_id = ?', [USER_ID]
    );
    console.log(`\n汇总：nav_milestone ${ms[0].c} 条 · operation_log ${ops[0].c} 条`);
    console.log('\n=== 完成 ===');
  } catch (e) {
    console.error('ERROR:', e.message);
    process.exit(1);
  } finally {
    await conn.end();
  }
}

main();