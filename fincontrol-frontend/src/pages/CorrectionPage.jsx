import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import apiClient from '../api/client.js'
import {
  CORRECTION_DEFAULTS,
  CORRECTION_MONTHLY_CALCULATE,
  CORRECTION_MONTHLY_RECALCULATE,
  CORRECTION_MONTHLY_CONFIRM,
} from '../api/endpoints.js'
import { useCorrectionStore } from '../stores/correctionStore.js'
import { formatYuan, friendlyError } from '../utils/formatters.js'
import CorrectionDetailDrawer, {
  OP_TYPE_LABEL,
  MODE_LABEL,
} from '../components/correction/CorrectionDetailDrawer.jsx'
import './correction-page.css'

/**
 * 2a 月度校正操作台（/correction）。
 *
 * 流程（需求书 CORR-001~006）：默认值 → 输入 → 二元方程组求解 → 取整弹窗（实时重算）
 * → 二次确认写入 operation_log → 审计结果。
 * 历史区列出全部校正与操作记录（含两次 LQR-ZOH 联合校正，点击可回放）。
 */

function fmt(v, dp = 2) {
  return v == null ? '—' : Number(v).toFixed(dp)
}

const emptyForm = {
  vCurr: '',
  vMonetary: '',
  vBond: '',
  uHigh: '',
  uMonetaryDca: 0,
  uBondDca: 0,
  eHigh: 0,
}

export default function CorrectionPage() {
  const [defaults, setDefaults] = useState(null)
  const [defaultsLoading, setDefaultsLoading] = useState(true)
  const [defaultsError, setDefaultsError] = useState(null)

  const [form, setForm] = useState(emptyForm)
  const [calc, setCalc] = useState(null)
  const [calcLoading, setCalcLoading] = useState(false)
  const [calcError, setCalcError] = useState(null)

  const [modalOpen, setModalOpen] = useState(false)
  const [actuals, setActuals] = useState({ deltaMActual: 0, deltaBActual: 0 })
  const [recalc, setRecalc] = useState(null)
  const [recalcLoading, setRecalcLoading] = useState(false)

  const [confirming, setConfirming] = useState(false)
  const [confirmResult, setConfirmResult] = useState(null)
  const [confirmError, setConfirmError] = useState(null)

  const operations = useCorrectionStore((s) => s.operations)
  const operationsLoading = useCorrectionStore((s) => s.operationsLoading)
  const operationsError = useCorrectionStore((s) => s.operationsError)
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
      .get(CORRECTION_DEFAULTS)
      .then((res) => {
        if (!alive) return
        setDefaults(res)
        if (res) {
          setForm({
            vCurr: res.vTotalSixCategories ?? '',
            vMonetary: res.vMonetary ?? '',
            vBond: res.vBond ?? '',
            uHigh: res.uHigh ?? '',
            uMonetaryDca: 0,
            uBondDca: 0,
            eHigh: 0,
          })
        }
        setDefaultsError(null)
      })
      .catch((err) => {
        if (!alive) return
        setDefaultsError(friendlyError(err))
      })
      .finally(() => alive && setDefaultsLoading(false))
    fetchOperations()
    return () => {
      alive = false
    }
  }, [fetchOperations])

  const setField = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const runCalculate = async () => {
    setCalcLoading(true)
    setCalcError(null)
    setConfirmResult(null)
    try {
      const res = await apiClient.post(CORRECTION_MONTHLY_CALCULATE, {
        vCurr: Number(form.vCurr),
        vMonetary: Number(form.vMonetary),
        vBond: Number(form.vBond),
        uHigh: Number(form.uHigh),
        uMonetaryDca: Number(form.uMonetaryDca) || 0,
        uBondDca: Number(form.uBondDca) || 0,
        eHigh: Number(form.eHigh) || 0,
        pMonetary: defaults?.targetRatios?.['货币类'],
        pBond: defaults?.targetRatios?.['固收类'],
        budgetLimit: defaults?.budgetLimit,
        purchaseThreshold: defaults?.purchaseThreshold,
      })
      setCalc(res)
      setRecalc(null)
    } catch (err) {
      setCalc(null)
      setCalcError(friendlyError(err))
    } finally {
      setCalcLoading(false)
    }
  }

  const openRounding = () => {
    if (!calc) return
    const next = {
      deltaMActual: calc.roundingSuggestion?.deltaMSuggested ?? 0,
      deltaBActual: calc.roundingSuggestion?.deltaBSuggested ?? 0,
    }
    setActuals(next)
    setRecalc(null)
    setModalOpen(true)
    // CORR-004：打开弹窗即按建议值重算一次，展示取整后的新比例与偏差
    runRecalculate(next)
  }

  const runRecalculate = async (next = actuals) => {
    if (!calc) return
    setRecalcLoading(true)
    try {
      const res = await apiClient.post(CORRECTION_MONTHLY_RECALCULATE, {
        vCurr: Number(form.vCurr),
        vMonetary: Number(form.vMonetary),
        vBond: Number(form.vBond),
        uHigh: Number(form.uHigh),
        deltaMActual: Number(next.deltaMActual) || 0,
        deltaBActual: Number(next.deltaBActual) || 0,
        uMonetaryDcaActual: Number(form.uMonetaryDca) || 0,
        uBondDcaActual: Number(form.uBondDca) || 0,
        eHigh: Number(form.eHigh) || 0,
        budgetLimit: defaults?.budgetLimit,
        pMonetary: defaults?.targetRatios?.['货币类'],
        pBond: defaults?.targetRatios?.['固收类'],
      })
      setRecalc(res)
    } catch (err) {
      setRecalc({ error: friendlyError(err) })
    } finally {
      setRecalcLoading(false)
    }
  }

  const updateActual = (key) => (e) => {
    const next = { ...actuals, [key]: e.target.value }
    setActuals(next)
    runRecalculate(next)
  }

  const runConfirm = async () => {
    if (!calc) return
    setConfirming(true)
    setConfirmError(null)
    try {
      const res = await apiClient.post(CORRECTION_MONTHLY_CONFIRM, {
        snapshotDate: defaults?.snapshotDate,
        vCurr: Number(form.vCurr),
        vMonetary: Number(form.vMonetary),
        vBond: Number(form.vBond),
        vHighVol: defaults?.vHighVol ?? null,
        uHigh: Number(form.uHigh),
        uMonetaryDca: Number(form.uMonetaryDca) || 0,
        uBondDca: Number(form.uBondDca) || 0,
        deltaMTheory: calc.deltaMTheory,
        deltaBTheory: calc.deltaBTheory,
        deltaMActual: Number(actuals.deltaMActual) || 0,
        deltaBActual: Number(actuals.deltaBActual) || 0,
        roundingStrategy: calc.roundingSuggestion?.roundingStrategy,
        budgetLimitUsed: defaults?.budgetLimit,
        totalInvestment: recalc?.totalInvestmentActual ?? calc.totalInvestment,
        notes: (calc.warnings || []).map((w) => w.message),
        correctionMode: 'zoh_only',
      })
      setConfirmResult(res)
      setModalOpen(false)
      await fetchOperations()
    } catch (err) {
      setConfirmError(friendlyError(err))
    } finally {
      setConfirming(false)
    }
  }

  if (defaultsLoading) {
    return <div className="corr-state">⏳ 加载月度校正默认值...</div>
  }
  if (defaultsError) {
    return <div className="corr-state corr-state--error">⚠️ {defaultsError}</div>
  }
  if (!defaults) {
    return (
      <div className="corr-state">
        尚无当前快照，无法加载校正默认值。请先到 <Link to="/data">数据管理</Link> 上传并确认资产快照。
      </div>
    )
  }

  return (
    <div className="page-shell corr-page">
      <header className="corr-header">
        <div>
          <h1>月度校正操作台</h1>
          <p className="corr-sub">
            低波前馈校正（二元一次方程组精确解） · 快照 {defaults.snapshotDate} · {defaults.snapshotNote}
          </p>
        </div>
        <div className="corr-header-links">
          <Link to="/quarterly" className="corr-link">季度 LQR-ZOH 联合校正 →</Link>
        </div>
      </header>

      {/* CORR-001 默认值 */}
      <section className="corr-card">
        <div className="corr-card-title">当前状态（六大类，不含余额类）</div>
        <div className="corr-kv-grid">
          <div className="corr-kv"><span>六大类合计 V_curr</span><strong>¥ {formatYuan(defaults.vTotalSixCategories)}</strong></div>
          <div className="corr-kv"><span>货币类 M</span><strong>¥ {formatYuan(defaults.vMonetary)}</strong></div>
          <div className="corr-kv"><span>固收类 B</span><strong>¥ {formatYuan(defaults.vBond)}</strong></div>
          <div className="corr-kv"><span>高波合计</span><strong>¥ {formatYuan(defaults.vHighVol)}</strong></div>
          <div className="corr-kv"><span>高波定投 U_high</span><strong>¥ {formatYuan(defaults.uHigh)}</strong></div>
          <div className="corr-kv"><span>目标比例（货币/固收）</span>
            <strong>{fmt(defaults.targetRatios?.['货币类'])}% / {fmt(defaults.targetRatios?.['固收类'])}%</strong></div>
          <div className="corr-kv"><span>预算上限</span><strong>¥ {formatYuan(defaults.budgetLimit)}</strong></div>
          <div className="corr-kv"><span>ZOH 触发阈值</span><strong>¥ {formatYuan(defaults.purchaseThreshold)}</strong></div>
        </div>
      </section>

      {/* CORR-002 输入表单 */}
      <section className="corr-card">
        <div className="corr-card-title">输入参数（可覆盖默认值）</div>
        <div className="corr-form-grid">
          <label>六大类合计 V_curr<input value={form.vCurr} onChange={setField('vCurr')} inputMode="decimal" /></label>
          <label>货币类 M<input value={form.vMonetary} onChange={setField('vMonetary')} inputMode="decimal" /></label>
          <label>固收类 B<input value={form.vBond} onChange={setField('vBond')} inputMode="decimal" /></label>
          <label>高波定投 U_high<input value={form.uHigh} onChange={setField('uHigh')} inputMode="decimal" /></label>
          <label>
            低波货币定投份额 U_m,dca
            <input value={form.uMonetaryDca} onChange={setField('uMonetaryDca')} inputMode="decimal" />
          </label>
          <label>
            低波固收定投份额 U_b,dca
            <input value={form.uBondDca} onChange={setField('uBondDca')} inputMode="decimal" />
          </label>
          <label>
            高波校正预算 E_high（联合校正时）
            <input value={form.eHigh} onChange={setField('eHigh')} inputMode="decimal" />
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
            title="U_high × p/(1−p_m−p_b)，6/30 联合校正曾采用该口径"
          >
            按理论反推填入低波份额
          </button>
          <button type="button" className="corr-btn corr-btn--primary" onClick={runCalculate} disabled={calcLoading}>
            {calcLoading ? '求解中...' : '计算校正方案'}
          </button>
        </div>
        {calcError && <div className="corr-inline-error">⚠️ {calcError}</div>}
      </section>

      {/* CORR-003 计算结果 */}
      {calc && (
        <section className="corr-card corr-card--result">
          <div className="corr-card-title">
            求解结果
            {calc.zohTriggered
              ? <span className="corr-badge corr-badge--ok">ZOH 触发</span>
              : <span className="corr-badge corr-badge--warn">ZOH 不触发</span>}
          </div>
          <div className="corr-kv-grid">
            <div className="corr-kv"><span>Δm 理论（货币）</span><strong>¥ {formatYuan(calc.deltaMTheory)}</strong></div>
            <div className="corr-kv"><span>Δb 理论（固收）</span><strong>¥ {formatYuan(calc.deltaBTheory)}</strong></div>
            <div className="corr-kv"><span>取整建议</span>
              <strong>Δm → {formatYuan(calc.roundingSuggestion?.deltaMSuggested)}，Δb → {formatYuan(calc.roundingSuggestion?.deltaBSuggested)}</strong></div>
            <div className="corr-kv"><span>取整策略</span><strong>{calc.roundingSuggestion?.roundingStrategy || '—'}</strong></div>
            <div className="corr-kv"><span>取整后偏差</span>
              <strong>{fmt(calc.roundingSuggestion?.deviationMSuggested)}pp / {fmt(calc.roundingSuggestion?.deviationBSuggested)}pp</strong></div>
            <div className="corr-kv"><span>总投入（理论口径）</span><strong>¥ {formatYuan(calc.totalInvestment)}</strong></div>
          </div>
          {(calc.warnings || []).length > 0 && (
            <ul className="corr-warnings">
              {calc.warnings.map((w, i) => (
                <li key={i} className={`corr-warning corr-warning--${w.type}`}>{w.message}</li>
              ))}
            </ul>
          )}
          <div className="corr-form-actions">
            <button type="button" className="corr-btn corr-btn--primary" onClick={openRounding}>
              打开取整确认（CORR-004/005）
            </button>
          </div>
          {confirmResult && (
            <div className="corr-audit">
              ✅ 已写入 operation_log（ID {confirmResult.operationLogId}，操作时间 {confirmResult.operationDate}），
              未修改资产快照金额。
            </div>
          )}
        </section>
      )}

      {/* CORR-006 历史记录（全部校正与操作） */}
      <section className="corr-card">
        <div className="corr-card-title">
          校正与操作记录
          <span className="corr-card-sub">共 {operations.length} 条 · 点击「查看」回放过程</span>
        </div>
        {operationsLoading && <div className="corr-state-inline">⏳ 加载记录...</div>}
        {operationsError && <div className="corr-inline-error">⚠️ {operationsError}</div>}
        {!operationsLoading && operations.length === 0 && (
          <div className="corr-state-inline">暂无记录。</div>
        )}
        {operations.length > 0 && (
          <div className="corr-table-wrap">
            <table className="corr-table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>类型 / 模式</th>
                  <th>快照日</th>
                  <th>Δm</th>
                  <th>Δb</th>
                  <th>总投入</th>
                  <th>IC-DRR</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {operations.map((op) => (
                  <tr key={op.id}>
                    <td>{op.operationDate}</td>
                    <td>
                      {OP_TYPE_LABEL[op.operationType] || op.operationType}
                      {op.correctionMode && (
                        <span className="corr-mode-tag">{MODE_LABEL[op.correctionMode] || op.correctionMode}</span>
                      )}
                    </td>
                    <td>{op.snapshotDate || '—'}</td>
                    <td>{op.deltaMActual != null ? formatYuan(op.deltaMActual) : (op.deltaMTheory != null ? `${formatYuan(op.deltaMTheory)}（理论）` : '—')}</td>
                    <td>{op.deltaBActual != null ? formatYuan(op.deltaBActual) : (op.deltaBTheory != null ? `${formatYuan(op.deltaBTheory)}（理论）` : '—')}</td>
                    <td>{formatYuan(op.totalInvestment)}</td>
                    <td>{op.icDrrPct != null ? `${fmt(op.icDrrPct, 1)}%` : '—'}</td>
                    <td>
                      <button
                        type="button"
                        className="corr-btn corr-btn--mini"
                        onClick={() => openDetail(op.id)}
                        disabled={op.hasDetail === false && !op.icDrrPct}
                      >
                        查看
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* CORR-004/005 取整弹窗 */}
      {modalOpen && calc && (
        <div className="corr-modal-overlay" role="dialog" aria-modal="true">
          <div className="corr-modal">
            <header className="corr-modal-head">
              <h3>取整确认</h3>
              <button type="button" className="corr-modal-close" onClick={() => setModalOpen(false)}>✕</button>
            </header>
            <p className="corr-modal-note">
              系统建议：Δm 取整至 {formatYuan(calc.roundingSuggestion?.deltaMSuggested)}，
              Δb 取整至 {formatYuan(calc.roundingSuggestion?.deltaBSuggested)}（纯 ZOH 放宽到整十）。
              修改后将实时重算新比例与偏差。
            </p>
            <div className="corr-form-grid corr-form-grid--modal">
              <label>Δm 实际<input value={actuals.deltaMActual} onChange={updateActual('deltaMActual')} inputMode="decimal" /></label>
              <label>Δb 实际<input value={actuals.deltaBActual} onChange={updateActual('deltaBActual')} inputMode="decimal" /></label>
            </div>
            {recalcLoading && <div className="corr-state-inline">⏳ 重算中...</div>}
            {recalc && !recalc.error && (
              <div className="corr-kv-grid">
                <div className="corr-kv"><span>货币类新比例</span><strong>{fmt(recalc.newRatios?.['货币类'])}%（偏差 {fmt(recalc.deviations?.['货币类'])}pp）</strong></div>
                <div className="corr-kv"><span>固收类新比例</span><strong>{fmt(recalc.newRatios?.['固收类'])}%（偏差 {fmt(recalc.deviations?.['固收类'])}pp）</strong></div>
                <div className="corr-kv"><span>实际总投入</span><strong>¥ {formatYuan(recalc.totalInvestmentActual)}</strong></div>
                <div className="corr-kv"><span>预算</span><strong>{recalc.overBudgetLimit ? '超出上限' : '在范围内'}</strong></div>
              </div>
            )}
            {recalc?.error && <div className="corr-inline-error">⚠️ {recalc.error}</div>}
            {confirmError && <div className="corr-inline-error">⚠️ {confirmError}</div>}
            <footer className="corr-modal-foot">
              <button type="button" className="corr-btn corr-btn--ghost" onClick={() => setModalOpen(false)}>取消</button>
              <button type="button" className="corr-btn corr-btn--primary" onClick={runConfirm} disabled={confirming}>
                {confirming ? '入库中...' : '确认入库（写 operation_log）'}
              </button>
            </footer>
          </div>
        </div>
      )}

      <CorrectionDetailDrawer
        detail={detail}
        loading={detailLoading}
        error={detailError}
        onClose={closeDetail}
      />
    </div>
  )
}