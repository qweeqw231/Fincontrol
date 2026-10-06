import { formatYuan } from '../../utils/formatters.js'

/**
 * 2a 校正记录详情抽屉（回放）：主表摘要 + 迭代/探针表 + 逐资产明细 + 参数指标。
 *
 * 数据来源：GET /api/correction/operations/{id}（correctionStore.openDetail）。
 */

export const OP_TYPE_LABEL = {
  monthly_correction: '月度校正',
  quarterly_correction: '季度校正',
  manual_adjustment: '战术调仓',
}

export const MODE_LABEL = {
  zoh_only: '纯 ZOH 低波校正',
  lqr_zoh: 'LQR-ZOH 联合校正',
  manual: '手工',
}

export const PHASE_LABEL = {
  pre_six: '校正前六大类',
  post_six: '校正后六大类',
  pre_high: '高波内部校正前',
  post_high: '高波内部校正后',
}

export const PARAM_LABEL = {
  surplus: '当月结余 S（元）',
  mMax: '总投入上限 M_max（元）',
  mMaxSource: 'M_max 来源',
  alphaInit: 'α 初始值',
  alphaDecay: 'α 递减因子',
  alphaFinal: 'α 最终值',
  zohThreshold: 'ZOH 触发阈值（元）',
  zohStepPointEHigh: 'ZOH 阶跃点 E_high（元）',
  zohStepTotal: '阶跃后总投入（元）',
  icDrrBefore: 'f_before（百分点²）',
  icDrrAfter: 'f_after（百分点²）',
  icDrrPct: 'IC-DRR（%）',
  anchor: '锚定资产',
  zohTriggered: 'ZOH 触发',
  actualTransfer: '实际转入余额宝（元）',
  kktNote: 'KKT 求解说明',
}

function fmtPct(v) {
  return v == null ? '—' : `${Number(v).toFixed(2)}%`
}

function fmtPp(v) {
  if (v == null) return '—'
  const n = Number(v)
  return `${n >= 0 ? '+' : ''}${n.toFixed(2)}pp`
}

function fmtAmount(v) {
  return v == null ? '—' : `¥ ${formatYuan(v)}`
}

function ParamValue({ param }) {
  if (!param) return '—'
  const raw = param.numValue != null ? param.numValue : param.textValue
  if (raw == null) return '—'
  if (param.textValue != null && param.numValue == null) return param.textValue
  const n = Number(raw)
  if (Number.isNaN(n)) return String(raw)
  return Number.isInteger(n) ? String(n) : n.toFixed(4).replace(/0+$/, '').replace(/\.$/, '')
}

export default function CorrectionDetailDrawer({ detail, loading, error, onClose }) {
  if (!loading && !error && !detail) return null

  const op = detail?.operation
  const iterations = detail?.iterations || []
  const assets = detail?.assets || []
  const params = detail?.params || {}

  const phases = ['pre_six', 'post_six', 'pre_high', 'post_high'].filter((p) =>
    assets.some((a) => a.phase === p),
  )

  return (
    <div className="cdd-overlay" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="cdd-panel" onClick={(e) => e.stopPropagation()}>
        <header className="cdd-head">
          <div>
            <h2>校正记录详情</h2>
            <p className="cdd-sub">过程回放 · 数据来源 operation_log + 明细子表</p>
          </div>
          <button type="button" className="cdd-close" onClick={onClose} aria-label="关闭">
            ✕
          </button>
        </header>

        {loading && <div className="cdd-state">⏳ 加载详情...</div>}
        {error && <div className="cdd-state cdd-state--error">⚠️ {error}</div>}

        {!loading && !error && op && (
          <div className="cdd-body">
            {/* 概要 */}
            <section className="cdd-section">
              <h3>概要</h3>
              <div className="cdd-kv-grid">
                <div className="cdd-kv"><span>操作时间</span><strong>{op.operationDate || '—'}</strong></div>
                <div className="cdd-kv"><span>类型</span><strong>{OP_TYPE_LABEL[op.operationType] || op.operationType || '—'}</strong></div>
                <div className="cdd-kv"><span>模式</span><strong>{MODE_LABEL[op.correctionMode] || op.correctionMode || '—'}</strong></div>
                <div className="cdd-kv"><span>来源快照</span><strong>{op.snapshotDate || '—'}</strong></div>
                <div className="cdd-kv"><span>来源</span><strong>{op.source || '—'}</strong></div>
                <div className="cdd-kv"><span>六大类合计</span><strong>{fmtAmount(op.vCurr)}</strong></div>
                <div className="cdd-kv"><span>Δm（货币）</span><strong>{op.deltaMActual != null ? fmtAmount(op.deltaMActual) : fmtAmount(op.deltaMTheory)}</strong></div>
                <div className="cdd-kv"><span>Δb（固收）</span><strong>{op.deltaBActual != null ? fmtAmount(op.deltaBActual) : fmtAmount(op.deltaBTheory)}</strong></div>
                <div className="cdd-kv"><span>总投入</span><strong>{fmtAmount(op.totalInvestment)}</strong></div>
                <div className="cdd-kv"><span>取整策略</span><strong>{op.roundingStrategy || '—'}</strong></div>
                <div className="cdd-kv"><span>ZOH 触发</span><strong>{op.triggered ? '是' : '否'}</strong></div>
                <div className="cdd-kv"><span>IC-DRR</span><strong>{op.icDrrPct != null ? `${Number(op.icDrrPct).toFixed(1)}%` : '—'}</strong></div>
              </div>
              {Array.isArray(op.notes) && op.notes.length > 0 && (
                <ul className="cdd-notes">
                  {op.notes.map((n, i) => (
                    <li key={i}>{n}</li>
                  ))}
                </ul>
              )}
            </section>

            {/* 迭代/探针表 */}
            {iterations.length > 0 && (
              <section className="cdd-section">
                <h3>α 迭代 / 探针过程</h3>
                <div className="cdd-table-wrap">
                  <table className="cdd-table">
                    <thead>
                      <tr>
                        <th>轮次</th>
                        <th>α</th>
                        <th>E_high</th>
                        <th>Δm</th>
                        <th>Δb</th>
                        <th>ZOH</th>
                        <th>总投入</th>
                        <th>超限</th>
                        <th>备注</th>
                      </tr>
                    </thead>
                    <tbody>
                      {iterations.map((it, i) => (
                        <tr key={i}>
                          <td>{it.sortOrder}</td>
                          <td>{it.alpha != null ? `${(Number(it.alpha) * 100).toFixed(2)}%` : '—'}</td>
                          <td>{formatYuan(it.eHigh)}</td>
                          <td>{formatYuan(it.deltaM)}</td>
                          <td>{formatYuan(it.deltaB)}</td>
                          <td>{it.zohTriggered ? '触发' : '—'}</td>
                          <td>{formatYuan(it.totalInvestment)}</td>
                          <td className={it.overLimit != null ? 'neg' : ''}>
                            {it.overLimit != null ? `+${formatYuan(it.overLimit)}` : '—'}
                          </td>
                          <td className="note-cell">{it.note || '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            )}

            {/* 逐资产明细 */}
            {phases.length > 0 && (
              <section className="cdd-section">
                <h3>逐资产明细</h3>
                {phases.map((phase) => (
                  <div key={phase} className="cdd-phase">
                    <div className="cdd-phase-title">{PHASE_LABEL[phase] || phase}</div>
                    <div className="cdd-table-wrap">
                      <table className="cdd-table">
                        <thead>
                          <tr>
                            <th>类别</th>
                            <th>金额</th>
                            <th>实际占比</th>
                            <th>目标</th>
                            <th>偏差</th>
                            <th>补仓(求)</th>
                            <th>补仓(执行)</th>
                            <th>备注</th>
                          </tr>
                        </thead>
                        <tbody>
                          {assets
                            .filter((a) => a.phase === phase)
                            .map((a, i) => (
                              <tr key={i}>
                                <td>{a.category}</td>
                                <td>{formatYuan(a.amount)}</td>
                                <td>{fmtPct(a.ratioActual)}</td>
                                <td>{fmtPct(a.ratioTarget)}</td>
                                <td className={a.deviation == null ? '' : a.deviation >= 0 ? 'pos' : 'neg'}>
                                  {fmtPp(a.deviation)}
                                </td>
                                <td>{a.deltaRaw == null ? '—' : formatYuan(a.deltaRaw)}</td>
                                <td>{a.deltaAmount == null ? '—' : formatYuan(a.deltaAmount)}</td>
                                <td className="note-cell">{a.note || '—'}</td>
                              </tr>
                            ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ))}
              </section>
            )}

            {/* 参数与指标 */}
            {Object.keys(params).length > 0 && (
              <section className="cdd-section">
                <h3>参数与指标</h3>
                <div className="cdd-kv-grid">
                  {Object.entries(params).map(([key, val]) => (
                    <div key={key} className="cdd-kv">
                      <span>{PARAM_LABEL[key] || key}</span>
                      <strong><ParamValue param={val} /></strong>
                    </div>
                  ))}
                </div>
              </section>
            )}
          </div>
        )}
      </div>
    </div>
  )
}