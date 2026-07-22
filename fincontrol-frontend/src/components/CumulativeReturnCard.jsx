import { useState } from 'react'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'

/**
 * CumulativeReturnCard — 累计 + 持有 收益双列卡片（1b.2 / 决策 4 v2 / 决策 25 v2）
 * <p>左列：累计收益率 + 累计收益额
 * <p>右列：持有收益率 + 持有收益额
 * <p>每个数字旁 ℹ️ 按钮点击弹 InfoModal 显示定义 / 公式 / 算法
 * <p>口径 A = 全口径含余额类（决策 4 v2）
 */
export const CumulativeReturnCard = () => {
  const cum = useAssetSnapshotStore((s) => s.cumulativeReturn)
  const loading = useAssetSnapshotStore((s) => s.loading)
  const error = useAssetSnapshotStore((s) => s.error)
  const [openInfo, setOpenInfo] = useState(null)  // 'cum' | 'cumAmt' | 'hold' | 'holdAmt' | null

  if (loading) {
    return (
      <div className="card cumulative-return-card">
        <div className="card-title">累计 / 持有 收益</div>
        <div className="card-value">加载中...</div>
      </div>
    )
  }
  if (error) {
    return (
      <div className="card cumulative-return-card error">
        <div className="card-title">累计 / 持有 收益</div>
        <div className="card-value">错误</div>
      </div>
    )
  }
  if (!cum || !cum.available) {
    return (
      <div className="card cumulative-return-card disabled">
        <div className="card-title">累计 / 持有 收益</div>
        <div className="card-value">—</div>
        <div className="card-sub">{cum?.message || 'Phase 3 上线'}</div>
      </div>
    )
  }

  // 累计侧
  const cumRate = Number(cum.returnRate ?? 0)
  const cumAmt = Number(cum.totalCumulativeProfit ?? 0)
  // 持有侧
  const holdRate = Number(cum.holdingReturnRate ?? 0)
  const holdAmt = Number(cum.totalHoldingProfit ?? 0)
  const algo = cum.algorithm || 'phase1_simple'
  const snap = cum.snapshotDate || '—'

  const rateColor = (r) => r >= 0 ? 'var(--color-success)' : 'var(--color-error)'
  const formatRate = (r) => `${r >= 0 ? '+' : ''}${(r * 100).toFixed(2)}%`
  const formatAmt  = (a) => `${a >= 0 ? '+' : ''}${a.toFixed(2)} 元`

  return (
    <div className="card cumulative-return-card">
      <div className="card-title">累计 / 持有 收益</div>

      <div className="crc-grid">
        {/* 左列：累计 */}
        <div className="crc-col crc-col-cum">
          <div className="crc-label">累计</div>
          <div className="crc-row">
            <div className="crc-rate" style={{ color: rateColor(cumRate) }} title="点击查看累计收益率定义">
              {formatRate(cumRate)}
            </div>
            <button
              className="crc-info-btn"
              onClick={() => setOpenInfo('cum')}
              title="累计收益率定义"
              aria-label="累计收益率定义"
            >ℹ️</button>
          </div>
          <div className="crc-row crc-row-amt">
            <div className="crc-amt" style={{ color: rateColor(cumAmt) }} title="点击查看累计收益定义">
              {formatAmt(cumAmt)}
            </div>
            <button
              className="crc-info-btn"
              onClick={() => setOpenInfo('cumAmt')}
              title="累计收益定义"
              aria-label="累计收益定义"
            >ℹ️</button>
          </div>
        </div>

        {/* 右列：持有 */}
        <div className="crc-col crc-col-hold">
          <div className="crc-label">持有</div>
          <div className="crc-row">
            <div className="crc-rate" style={{ color: rateColor(holdRate) }} title="点击查看持有收益率定义">
              {formatRate(holdRate)}
            </div>
            <button
              className="crc-info-btn"
              onClick={() => setOpenInfo('hold')}
              title="持有收益率定义"
              aria-label="持有收益率定义"
            >ℹ️</button>
          </div>
          <div className="crc-row crc-row-amt">
            <div className="crc-amt" style={{ color: rateColor(holdAmt) }} title="点击查看持有收益定义">
              {formatAmt(holdAmt)}
            </div>
            <button
              className="crc-info-btn"
              onClick={() => setOpenInfo('holdAmt')}
              title="持有收益定义"
              aria-label="持有收益定义"
            >ℹ️</button>
          </div>
        </div>
      </div>

      <div className="card-sub">
        算法：{algo}｜快照：{snap}｜{cum.fundCount ?? 0} 只基金
      </div>

      <InfoModal openKey={openInfo} onClose={() => setOpenInfo(null)} />
    </div>
  )
}

/** 4 个 ℹ️ 弹窗：累计/持有 × 率/额 */
const InfoModal = ({ openKey, onClose }) => {
  if (!openKey) return null
  const titles = {
    cum: '累计收益率',
    cumAmt: '累计收益额',
    hold: '持有收益率',
    holdAmt: '持有收益额',
  }
  const contents = {
    cum: {
      formula: '累计收益率 = Σcumulative_profit / Σamount',
      def: '自该基金建仓以来所有盈亏的总收益率（含已实现盈亏，如部分卖出后）',
      algo: 'phase1_simple（Phase 1b 简化版，Phase 3 升级为 Modified Dietz / XIRR）',
    },
    cumAmt: {
      formula: '累计收益（元）= Σcumulative_profit',
      def: '所有持仓基金自建仓以来盈亏总和（含已实现）',
      algo: '累计口径 A = 全口径含余额类（与决策 7 / 8 / 13 一致）',
    },
    hold: {
      formula: '持有收益率 = Σholding_profit / Σamount',
      def: '当前仍持仓的基金浮盈率（不含已实现盈亏）',
      algo: 'Phase 1b 暂用 phase1_simple 近似（Phase 3 升级为 XIRR / Dietz）',
    },
    holdAmt: {
      formula: '持有收益（元）= Σholding_profit',
      def: '当前仍持仓的基金浮盈/亏总和（不含已实现盈亏）',
      algo: '口径 A = 全口径含余额类（余额宝等归余额类）',
    },
  }
  const c = contents[openKey]
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{titles[openKey]}</h3>
          <button className="modal-close" onClick={onClose} aria-label="关闭">×</button>
        </div>
        <div className="modal-body">
          <div className="modal-row">
            <span className="modal-label">公式：</span>
            <code className="modal-code">{c.formula}</code>
          </div>
          <div className="modal-row">
            <span className="modal-label">定义：</span>
            <span className="modal-text">{c.def}</span>
          </div>
          <div className="modal-row">
            <span className="modal-label">算法：</span>
            <span className="modal-text">{c.algo}</span>
          </div>
        </div>
        <div className="modal-footer">
          <button className="modal-btn" onClick={onClose}>关闭</button>
        </div>
      </div>
    </div>
  )
}
