import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import apiClient from '../api/client.js'
import {
  CORRECTION_QUARTERLY_DEFAULTS,
  CORRECTION_QUARTERLY_CALCULATE,
  CORRECTION_QUARTERLY_CONFIRM,
} from '../api/endpoints.js'
import { useCorrectionStore } from '../stores/correctionStore.js'
import { formatYuan, friendlyError } from '../utils/formatters.js'
import { buildQuarterlyConfirmPayload } from '../utils/correctionPayload.js'
import CorrectionDetailDrawer from '../components/correction/CorrectionDetailDrawer.jsx'
import './correction-page.css'

/**
 * 2a 季度操作台（/quarterly）：LQR-ZOH 联合校正。
 *
 * 流程（需求书 QTR-001~006 + mcf §2.10 五步算法）：
 * 输入（S/M_max/U_high/α）→ α 迭代压缩（6/30 场景）或人为上限倒推 + ZOH 阶跃点（9/30 场景）
 * → LQR 高波内部 KKT 求解 → IC-DRR → 执行方案 → 确认写入 4 张表。
 * 页面同时承载两次历史联合校正（6/30、9/30）的回放入口。
 */

function fmt(v, dp = 2) {
  return v == null ? '—' : Number(v).toFixed(dp)
}

const emptyForm = {
  surplus: '',
  mMax: '',
  uHigh: '',
  uMonetaryDca: 0,
  uBondDca: 0,
  alphaInit: 0.2,
  alphaDecay: 0.8,
  mode: 'auto',
  probeBaseMmax: '',
}

export default function QuarterlyPage() {
  const [defaults, setDefaults] = useState(null)
  const [defaultsLoading, setDefaultsLoading] = useState(true)
  const [defaultsError, setDefaultsError] = useState(null)

  const [form, setForm] = useState(emptyForm)
  const [calc, setCalc] = useState(null)
  const [calcLoading, setCalcLoading] = useState(false)
  const [calcError, setCalcError] = useState(null)

  const [confirming, setConfirming] = useState(false)
  const [confirmResult, setConfirmResult] = useState(null)
  const [confirmError, setConfirmError] = useState(null)

  const operations = useCorrectionStore((s) => s.operations)
  const fetchOperations = useCorrectionStore((s) => s.fetchOperations)
  const detail = useCorrectionStore((s) => s.detail)
  const detailLoading = useCorrectionStore((s) => s.detailLoading)
  const detailError = useCorrectionStore((s) => s.detailError)
  const openDetail = useCorrectionStore((s) => s.openDetail)
  const closeDetail = useCorrectionStore((s) => s.closeDetail)

  useEffect(() => {
    let alive = true
    setDefaultsLoading(true)
    apiClient
      .get(CORRECTION_QUARTERLY_DEFAULTS)
      .then((res) => {
        if (!alive) return
        setDefaults(res)
        if (res) {
          setForm((f) => ({ ...f, uHigh: res.uHighDefault ?? '' }))
        }
        setDefaultsError(null)
      })
      .catch((err) => alive && setDefaultsError(friendlyError(err)))
      .finally(() => alive && setDefaultsLoading(false))
    fetchOperations()
    return () => {
      alive = false
    }
  }, [fetchOperations])

  const jointOperations = useMemo(
    () => operations.filter((op) => op.correctionMode === 'lqr_zoh'),
    [operations],
  )

  const setField = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const effectiveMMax = useMemo(() => {
    if (form.mMax !== '' && form.mMax != null) return Number(form.mMax)
    if (form.surplus !== '' && form.surplus != null) return Number(form.surplus) * 1.8
    return null
  }, [form.mMax, form.surplus])

  const runCalculate = async () => {
    setCalcLoading(true)
    setCalcError(null)
    setConfirmResult(null)
    try {
      const res = await apiClient.post(CORRECTION_QUARTERLY_CALCULATE, {
        vCurr: defaults?.vCurr,
        categories: defaults?.categories,
        targetRatios: defaults?.targetRatios,
        surplus: form.surplus === '' ? null : Number(form.surplus),
        mMax: effectiveMMax,
        mMaxSource: form.mMax === '' ? 'default' : 'manual',
        uHigh: Number(form.uHigh),
        uMonetaryDca: Number(form.uMonetaryDca) || 0,
        uBondDca: Number(form.uBondDca) || 0,
        alphaInit: Number(form.alphaInit),
        alphaDecay: Number(form.alphaDecay),
        purchaseThreshold: defaults?.purchaseThreshold,
        mode: form.mode,
        probeBaseMmax: form.probeBaseMmax === '' ? null : Number(form.probeBaseMmax),
      })
      setCalc(res)
    } catch (err) {
      setCalc(null)
      setCalcError(friendlyError(err))
    } finally {
      setCalcLoading(false)
    }
  }

  const runConfirm = async () => {
    const payload = buildQuarterlyConfirmPayload(calc, defaults)
    if (!payload) return
    setConfirming(true)
    setConfirmError(null)
    try {
      const res = await apiClient.post(CORRECTION_QUARTERLY_CONFIRM, payload)
      setConfirmResult(res)
      await fetchOperations()
    } catch (err) {
      setConfirmError(friendlyError(err))
    } finally {
      setConfirming(false)
    }
  }

  if (defaultsLoading) return <div className="corr-state">⏳ 加载季度台默认值...</div>
  if (defaultsError) return <div className="corr-state corr-state--error">⚠️ {defaultsError}</div>
  if (!defaults) {
    return (
      <div className="corr-state">
        尚无当前快照。请先到 <Link to="/data">数据管理</Link> 上传并确认资产快照。
      </div>
    )
  }

  return (
    <div className="page-shell corr-page">
      <header className="corr-header">
        <div>
          <h1>季度操作台 · LQR-ZOH 联合校正</h1>
          <p className="corr-sub">
            归一化高波内部 LQR + 低波精确复位联动算法 · 快照 {defaults.snapshotDate} · {defaults.snapshotNote}
          </p>
        </div>
        <div className="corr-header-links">
          <Link to="/correction" className="corr-link">← 月度校正操作台</Link>
        </div>
      </header>

      {/* QTR-001 输入区 */}
      <section className="corr-card">
        <div className="corr-card-title">输入参数</div>
        <div className="corr-kv-grid">
          <div className="corr-kv"><span>六大类合计</span><strong>¥ {formatYuan(defaults.vCurr)}</strong></div>
          <div className="corr-kv"><span>高波合计</span><strong>¥ {formatYuan(defaults.vHighVol)}</strong></div>
          <div className="corr-kv"><span>目标比例（高波）</span>
            <strong>
              {fmt(defaults.targetRatios?.['商品类'])}/{fmt(defaults.targetRatios?.['A股权益类'])}/
              {fmt(defaults.targetRatios?.['海外权益类'])}/{fmt(defaults.targetRatios?.['港股大中华类'])}%
            </strong></div>
          <div className="corr-kv"><span>ZOH 触发阈值</span><strong>¥ {formatYuan(defaults.purchaseThreshold)}</strong></div>
        </div>
        <div className="corr-form-grid">
          <label>当月结余 S<input value={form.surplus} onChange={setField('surplus')} inputMode="decimal" placeholder="用于默认上限 1.8 × S" /></label>
          <label>总投入上限 M_max<input value={form.mMax} onChange={setField('mMax')} inputMode="decimal" placeholder={effectiveMMax ? `默认 ${fmt(effectiveMMax)}` : '缺省 = 1.8 × S'} /></label>
          <label>高波定投 U_high<input value={form.uHigh} onChange={setField('uHigh')} inputMode="decimal" /></label>
          <label>低波货币份额 U_m,dca<input value={form.uMonetaryDca} onChange={setField('uMonetaryDca')} inputMode="decimal" /></label>
          <label>低波固收份额 U_b,dca<input value={form.uBondDca} onChange={setField('uBondDca')} inputMode="decimal" /></label>
          <label>α 初始值<input value={form.alphaInit} onChange={setField('alphaInit')} inputMode="decimal" /></label>
          <label>α 递减因子<input value={form.alphaDecay} onChange={setField('alphaDecay')} inputMode="decimal" /></label>
          <label>α 探针基准上限<input value={form.probeBaseMmax} onChange={setField('probeBaseMmax')} inputMode="decimal" placeholder="缺省 = 1.8 × S" /></label>
        </div>
        <div className="corr-mode-radios">
          <label className={form.mode === 'auto' ? 'active' : ''}>
            <input type="radio" name="mode" checked={form.mode === 'auto'} onChange={() => setForm((f) => ({ ...f, mode: 'auto' }))} />
            自动压缩 α（默认 M_max，6/30 场景）
          </label>
          <label className={form.mode === 'mMaxCapped' ? 'active' : ''}>
            <input type="radio" name="mode" checked={form.mode === 'mMaxCapped'} onChange={() => setForm((f) => ({ ...f, mode: 'mMaxCapped' }))} />
            人为上限倒推 + ZOH 阶跃点（9/30 场景）
          </label>
        </div>
        <div className="corr-form-actions">
          <button
            type="button"
            className="corr-btn corr-btn--ghost"
            onClick={() =>
              setForm((f) => ({
                ...f,
                uMonetaryDca: defaults.uMonetaryDcaTheory ?? 0,
                uBondDca: defaults.uBondDcaTheory ?? 0,
              }))
            }
          >
            按理论反推填入低波份额
          </button>
          <button type="button" className="corr-btn corr-btn--primary" onClick={runCalculate} disabled={calcLoading}>
            {calcLoading ? '求解中...' : '求解联合校正方案'}
          </button>
        </div>
        {calcError && <div className="corr-inline-error">⚠️ {calcError}</div>}
      </section>

      {calc && (
        <>
          {/* QTR-002/003 结果：校正前状态 */}
          <section className="corr-card">
            <div className="corr-card-title">
              校正前状态
              <span className="corr-card-sub">锚定资产：{calc.anchor || '—'}（超配最严重，Δ=0）</span>
            </div>
            <div className="qtr-cols">
              <div>
                <div className="qtr-sub">六大类（占六大类口径）</div>
                <table className="corr-table qtr-table">
                  <thead><tr><th>大类</th><th>金额</th><th>实际</th><th>目标</th><th>偏差</th></tr></thead>
                  <tbody>
                    {(calc.preSix || []).map((r) => (
                      <tr key={r.category}>
                        <td>{r.category}</td>
                        <td>{formatYuan(r.amount)}</td>
                        <td>{fmt(r.ratioActual)}%</td>
                        <td>{fmt(r.ratioTarget)}%</td>
                        <td className={r.deviation >= 0 ? 'pos' : 'neg'}>{r.deviation >= 0 ? '+' : ''}{fmt(r.deviation)}pp</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div>
                <div className="qtr-sub">高波内部归一化</div>
                <table className="corr-table qtr-table">
                  <thead><tr><th>资产</th><th>金额</th><th>内部占比</th><th>归一化目标</th><th>偏差</th></tr></thead>
                  <tbody>
                    {(calc.highVolPre || []).map((r) => (
                      <tr key={r.category} className={r.category === calc.anchor ? 'qtr-anchor-row' : ''}>
                        <td>{r.category}{r.category === calc.anchor && <span className="corr-mode-tag">锚定</span>}</td>
                        <td>{formatYuan(r.amount)}</td>
                        <td>{fmt(r.ratioActual)}%</td>
                        <td>{fmt(r.ratioTarget)}%</td>
                        <td className={r.deviation >= 0 ? 'pos' : 'neg'}>{r.deviation >= 0 ? '+' : ''}{fmt(r.deviation)}pp</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </section>

          {/* 预算参数 + α 表 + 阶跃点 */}
          <section className="corr-card">
            <div className="corr-card-title">
              α 迭代 / 探针过程
              <span className="corr-card-sub">
                {calc.params?.mode === 'mMaxCapped' ? '人为上限倒推模式' : `自动压缩模式（迭代 ${calc.alphaIterations ?? 0} 轮）`}
              </span>
            </div>
            <div className="corr-kv-grid">
              <div className="corr-kv"><span>结余 S</span><strong>¥ {formatYuan(calc.params?.surplus)}</strong></div>
              <div className="corr-kv"><span>总投入上限 M_max</span><strong>¥ {formatYuan(calc.params?.mMax)}（{calc.params?.mMaxSource === 'manual' ? '人为设定' : '默认 1.8×S'}）</strong></div>
              <div className="corr-kv"><span>α 初始 / 最终</span><strong>{calc.params?.alphaInit} → {calc.chosen?.alphaFinal}</strong></div>
              <div className="corr-kv"><span>高波校正预算 E_high</span><strong>¥ {formatYuan(calc.chosen?.eHigh)}</strong></div>
            </div>
            <div className="corr-table-wrap">
              <table className="corr-table">
                <thead>
                  <tr><th>α</th><th>E_high</th><th>Δm</th><th>Δb</th><th>ZOH</th><th>总投入</th><th>超限</th><th>备注</th></tr>
                </thead>
                <tbody>
                  {(calc.alphaTable || []).map((row, i) => (
                    <tr key={i} className={row.zohTriggered ? 'qtr-zoh-row' : ''}>
                      <td>{(Number(row.alpha) * 100).toFixed(2)}%</td>
                      <td>{formatYuan(row.eHigh)}</td>
                      <td>{formatYuan(row.deltaM)}</td>
                      <td>{formatYuan(row.deltaB)}</td>
                      <td>{row.zohTriggered ? '触发' : '—'}</td>
                      <td>{formatYuan(row.totalInvestment)}</td>
                      <td className={row.overLimit != null ? 'neg' : ''}>{row.overLimit != null ? `+${formatYuan(row.overLimit)}` : '—'}</td>
                      <td className="note-cell">{row.note || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {calc.zohStep && (
              <div className="qtr-step-banner">
                ⚡ ZOH 阶跃点：E_high 达到 <strong>¥ {formatYuan(calc.zohStep.eHighAtStep)}</strong> 时
                Δm = {formatYuan(calc.zohStep.deltaMAtStep)} 触发补仓，总投入由
                ¥ {formatYuan(calc.zohStep.totalBeforeStep)} 阶跃至 <strong>¥ {formatYuan(calc.zohStep.totalAfterStep)}</strong>
                （多出 ¥ {formatYuan(calc.zohStep.jumpAmount)}）
              </div>
            )}
          </section>

          {/* LQR 求解 + 最终方案 + IC-DRR */}
          <section className="corr-card">
            <div className="corr-card-title">LQR 高波内部求解（KKT）</div>
            <p className="qtr-kkt-note">{calc.lqr?.kktNote || '—'}</p>
            <table className="corr-table qtr-table">
              <thead><tr><th>资产</th><th>校正前金额</th><th>补仓（求解）</th><th>补仓（取整）</th><th>备注</th></tr></thead>
              <tbody>
                {(calc.lqr?.deltas || []).map((r) => (
                  <tr key={r.category}>
                    <td>{r.category}</td>
                    <td>{formatYuan(r.amount)}</td>
                    <td>{formatYuan(r.deltaRaw)}</td>
                    <td>{formatYuan(r.deltaAmount)}</td>
                    <td className="note-cell">{r.note || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="qtr-cols">
              <div className="qtr-panel">
                <div className="qtr-sub">校正后高波内部</div>
                <table className="corr-table qtr-table">
                  <thead><tr><th>资产</th><th>新市值</th><th>内部占比</th><th>目标</th><th>偏差</th></tr></thead>
                  <tbody>
                    {(calc.highVolPost || []).map((r) => (
                      <tr key={r.category}>
                        <td>{r.category}</td>
                        <td>{formatYuan(r.amount)}</td>
                        <td>{fmt(r.ratioActual)}%</td>
                        <td>{fmt(r.ratioTarget)}%</td>
                        <td className={r.deviation >= 0 ? 'pos' : 'neg'}>{r.deviation >= 0 ? '+' : ''}{fmt(r.deviation)}pp</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="qtr-panel">
                <div className="qtr-sub">IC-DRR（偏差平方和，百分点²）</div>
                <div className="qtr-icdrr">
                  <div className="qtr-icdrr-row"><span>校正前 f_before</span><strong>{fmt(calc.icDrr?.fBefore)}</strong></div>
                  <div className="qtr-icdrr-row"><span>校正后 f_after</span><strong>{fmt(calc.icDrr?.fAfter)}</strong></div>
                  <div className="qtr-icdrr-row qtr-icdrr-ratio">
                    <span>IC-DRR</span>
                    <strong>{fmt(calc.icDrr?.ratioPct, 1)}%</strong>
                  </div>
                </div>
              </div>
            </div>
          </section>

          {/* 执行方案 + 确认 */}
          <section className="corr-card corr-card--result">
            <div className="corr-card-title">
              最终执行方案
              {calc.chosen?.zohTriggered
                ? <span className="corr-badge corr-badge--ok">ZOH 触发</span>
                : <span className="corr-badge corr-badge--warn">ZOH 不触发</span>}
            </div>
            <table className="corr-table qtr-table">
              <thead><tr><th>项目</th><th>金额</th><th>说明</th></tr></thead>
              <tbody>
                {(calc.plan || []).map((row, i) => (
                  <tr key={i} className={row.item === '总投入' ? 'qtr-total-row' : ''}>
                    <td>{row.item}</td>
                    <td>{formatYuan(row.amount)}</td>
                    <td className="note-cell">{row.note}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {(calc.warnings || []).length > 0 && (
              <ul className="corr-warnings">
                {calc.warnings.map((w, i) => (
                  <li key={i} className={`corr-warning corr-warning--${w.type}`}>{w.message}</li>
                ))}
              </ul>
            )}
            {confirmError && <div className="corr-inline-error">⚠️ {confirmError}</div>}
            <div className="corr-form-actions">
              <button type="button" className="corr-btn corr-btn--primary" onClick={runConfirm} disabled={confirming}>
                {confirming ? '入库中...' : '确认入库（主表 + 迭代/资产/参数明细）'}
              </button>
            </div>
            {confirmResult && (
              <div className="corr-audit">
                ✅ 已写入 operation_log（ID {confirmResult.operationLogId}，操作时间 {confirmResult.operationDate}）
                及 {confirmResult.detailRows} 行过程明细，未修改资产快照金额。
              </div>
            )}
          </section>
        </>
      )}

      {/* 历史联合校正（回放） */}
      <section className="corr-card">
        <div className="corr-card-title">
          历史 LQR-ZOH 联合校正
          <span className="corr-card-sub">共 {jointOperations.length} 次 · 点击「回放」查看完整过程</span>
        </div>
        {jointOperations.length === 0 && (
          <div className="corr-state-inline">暂无联合校正记录（6/30、9/30 两次记录回填后可在此回放）。</div>
        )}
        {jointOperations.length > 0 && (
          <div className="corr-table-wrap">
            <table className="corr-table">
              <thead>
                <tr><th>时间</th><th>快照日</th><th>Δm</th><th>Δb</th><th>总投入</th><th>IC-DRR</th><th>锚定</th><th></th></tr>
              </thead>
              <tbody>
                {jointOperations.map((op) => (
                  <tr key={op.id}>
                    <td>{op.operationDate}</td>
                    <td>{op.snapshotDate || '—'}</td>
                    <td>{op.deltaMActual != null ? formatYuan(op.deltaMActual) : (op.deltaMTheory != null ? `${formatYuan(op.deltaMTheory)}（理论）` : '—')}</td>
                    <td>{op.deltaBActual != null ? formatYuan(op.deltaBActual) : (op.deltaBTheory != null ? `${formatYuan(op.deltaBTheory)}（理论）` : '—')}</td>
                    <td>{formatYuan(op.totalInvestment)}</td>
                    <td>{op.icDrrPct != null ? `${fmt(op.icDrrPct, 1)}%` : '—'}</td>
                    <td>{op.anchor || '—'}</td>
                    <td>
                      <button type="button" className="corr-btn corr-btn--mini" onClick={() => openDetail(op.id)}>
                        回放
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <CorrectionDetailDrawer
        detail={detail}
        loading={detailLoading}
        error={detailError}
        onClose={closeDetail}
      />
    </div>
  )
}