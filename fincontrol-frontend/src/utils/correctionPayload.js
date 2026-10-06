/**
 * 2a 校正页：把求解响应装配成确认入库请求（纯函数，便于单测）。
 *
 * <p>季度台把 calculate 的完整过程（α 表 / 逐资产 / 参数指标）转成
 * `POST /api/correction/quarterly/confirm` 的 4 表写入结构。
 */

const PARAM_KEYS = (calc) => {
  const p = calc?.params || {}
  const chosen = calc?.chosen || {}
  const list = []
  const num = (key, value) => {
    if (value != null) list.push({ key, numValue: value })
  }
  const text = (key, value) => {
    if (value != null) list.push({ key, textValue: String(value) })
  }
  num('surplus', p.surplus)
  num('mMax', p.mMax)
  text('mMaxSource', p.mMaxSource)
  num('alphaInit', p.alphaInit)
  num('alphaDecay', p.alphaDecay)
  num('alphaFinal', chosen.alphaFinal)
  num('zohThreshold', p.purchaseThreshold)
  num('zohStepPointEHigh', calc?.zohStep?.eHighAtStep)
  num('zohStepTotal', calc?.zohStep?.totalAfterStep)
  num('icDrrBefore', calc?.icDrr?.fBefore)
  num('icDrrAfter', calc?.icDrr?.fAfter)
  num('icDrrPct', calc?.icDrr?.ratioPct)
  text('anchor', calc?.anchor)
  text('kktNote', calc?.lqr?.kktNote)
  if (chosen.zohTriggered != null) {
    text('zohTriggered', chosen.zohTriggered ? 'true' : 'false')
  }
  return list
}

const assetRow = (phase, row, extra = {}) => ({
  phase,
  category: row.category,
  amount: row.amount ?? null,
  ratioActual: row.ratioActual ?? null,
  ratioTarget: row.ratioTarget ?? null,
  deviation: row.deviation ?? null,
  deltaRaw: extra.deltaRaw ?? row.deltaRaw ?? null,
  deltaAmount: extra.deltaAmount ?? row.deltaAmount ?? null,
  note: extra.note ?? row.note ?? null,
})

export function buildQuarterlyConfirmPayload(calc, defaults) {
  if (!calc) return null
  const preSix = calc.preSix || []
  const byCat = (rows) => {
    const m = {}
    for (const r of rows || []) m[r.category] = r
    return m
  }
  const six = byCat(preSix)
  const lqrDeltas = byCat(calc.lqr?.deltas)
  const params = calc.params || {}
  const chosen = calc.chosen || {}

  const iterations = (calc.alphaTable || []).map((row, idx) => ({
    sortOrder: idx,
    alpha: row.alpha,
    eHigh: row.eHigh,
    deltaM: row.deltaM,
    deltaB: row.deltaB,
    zohTriggered: row.zohTriggered === true,
    totalInvestment: row.totalInvestment,
    overLimit: row.overLimit ?? null,
    note: row.note ?? null,
  }))

  const assets = [
    ...preSix.map((r) => assetRow('pre_six', r)),
    ...(calc.highVolPre || []).map((r) =>
      assetRow('pre_high', r, {
        deltaRaw: lqrDeltas[r.category]?.deltaRaw,
        deltaAmount: lqrDeltas[r.category]?.deltaAmount,
        note: r.note ?? lqrDeltas[r.category]?.note,
      }),
    ),
    ...(calc.highVolPost || []).map((r) => assetRow('post_high', r)),
  ]

  return {
    snapshotDate: defaults?.snapshotDate ?? null,
    vCurr: calc.vCurr,
    vMonetary: six['货币类']?.amount ?? null,
    vBond: six['固收类']?.amount ?? null,
    vHighVol: calc.vHighVol,
    uHigh: params.uHigh,
    uMonetaryDca: params.uMonetaryDca,
    uBondDca: params.uBondDca,
    deltaMTheory: chosen.deltaMRaw,
    deltaBTheory: chosen.deltaBRaw,
    deltaMActual: chosen.deltaMActual,
    deltaBActual: chosen.deltaBActual,
    roundingStrategy: chosen.roundingStrategy,
    budgetLimitUsed: params.mMax,
    totalInvestment: chosen.totalInvestment,
    triggeredBoundary: chosen.zohTriggered ? '1' : null,
    notes: (calc.warnings || []).map((w) => w.message),
    correctionMode: 'lqr_zoh',
    iterations,
    assets,
    params: PARAM_KEYS(calc),
  }
}

export default buildQuarterlyConfirmPayload