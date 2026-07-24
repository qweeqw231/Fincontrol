import { useEffect, useState } from 'react'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'
import { useUserConfigStore } from '../stores/userConfigStore.js'
import { apiClient } from '../api/client.js'
import { ENDPOINTS } from '../api/endpoints.js'
import { revokeAll, revokeOne } from '../utils/blob.js'
import HistoryLimitDialog from '../components/data/HistoryLimitDialog.jsx'

/**
 * 1b.3 数据管理页
 * <p>功能：
 * <ul>
 *   <li>1b.3.6：4 张图批量上传 dropzone</li>
 *   <li>1b.3.7：single/multi toggle + date picker 选 snapshot_date</li>
 *   <li>1b.3.8：is_latest 按钮 + 设为当前快照按钮（按日期）</li>
 *   <li>1b.3.9：snapshot_meta 列表（所有 is_latest=true 行）</li>
 * </ul>
 * <p>提交流程：
 * <ol>
 *   <li>选 4 张图 → /screenshot/upload 得 4 fileId</li>
 *   <li>选 single/multi + snapshot_date → /screenshot/parse-batch?mode={single|multi} 得 19-fund parsedAsset</li>
 *   <li>触发 /snapshot/confirm 写入 DB → 1b.3.2 自动写 snapshot_meta</li>
 *   <li>1b.4+ 修复：自动 setCurrent 把新日期设为 ★ CURRENT（PR3+ BUG-004 修复）</li>
 *   <li>列表自动刷新（fetchLatest + fetchMetaList）</li>
 * </ol>
 * <p>2026-07-24 PR3+ 修改：
 * <ul>
 *   <li>PR3 HOME-013：删 v1.0-DRAFT + 简化技术术语（HomePage 改动）</li>
 *   <li>PR3 HOME-016：删 section 序号硬编码</li>
 *   <li>PR3 DATA-006：confirm 后 step 回 idle（避免下次上传闪'入库中…'）</li>
 *   <li>PR3+ BUG-001：confirmSuccess 独立 state 显示"✓ 入库成功"banner</li>
 *   <li>PR3+ BUG-002：is_current 卡片右侧加 ✓ 当前 badge（视觉等高）</li>
 *   <li>PR3+ BUG-003：预览弹窗快照日期可点击编辑（3 select 滚轮）— PR4a DATA-012 后改为 modal-header 副标题</li>
 *   <li>PR3+ BUG-004：confirm 后自动调 setCurrent（无需手动点"设为当前"）</li>
 * </ul>
 * <p>2026-07-24 PR4a 修改：
 * <ul>
 *   <li>GLOBAL-016：handleFiles > 4 张改 setError，移除静默截断</li>
 *   <li>DATA-002：占位符文案去 0716 残留</li>
 *   <li>DATA-005：上传按钮加 title tooltip</li>
 *   <li>DATA-010：解析模式下方加 form-hint</li>
 *   <li>DATA-011：去掉 asset_raw/snapshot_meta 技术术语</li>
 *   <li>DATA-012：modal 中快照日期卡移到 header 副标题，overview-summary 5 列 → 4 列</li>
 * </ul>
 */
export default function DataPage() {
  const fetchLatest = useAssetSnapshotStore((s) => s.fetchLatest)
  const snapshot = useAssetSnapshotStore((s) => s.latestSnapshot)

  // PR3plus 决策 30/31：maxSnapshotAgeDays 状态（从 store 读）
  const maxSnapshotAgeDays = useUserConfigStore((s) => s.maxSnapshotAgeDays)
  const fetchMaxSnapshotAgeDays = useUserConfigStore((s) => s.fetchMaxSnapshotAgeDays)
  // PR3plus：HistoryLimitDialog 控制
  const [showHistoryLimitDialog, setShowHistoryLimitDialog] = useState(false)

  // 1b.3.7 toggle + date picker
  const [mode, setMode] = useState('single') // 'single' | 'multi'
  const [snapshotDate, setSnapshotDate] = useState(() => {
    const d = new Date()
    return d.toISOString().slice(0, 10)
  })

  // 1b.3.6 dropzone
  const [files, setFiles] = useState([])
  const [filePreviews, setFilePreviews] = useState([])

  // 1b.3.6/7/8 流状态
  const [step, setStep] = useState('idle') // idle | uploading | parsing | parsed | confirming | error
  const [error, setError] = useState(null)
  const [parsedAsset, setParsedAsset] = useState(null)
  const [parseInfo, setParseInfo] = useState(null)

  // 1b.3.9 快照管理列表
  const [metaList, setMetaList] = useState([])
  const [metaLoading, setMetaLoading] = useState(false)

  // 1b.3.10 确认入库弹窗
  const [showConfirmModal, setShowConfirmModal] = useState(false)
  const [parsedSummary, setParsedSummary] = useState(null)

  // PR3+ BUG-001：独立 confirmSuccess state（不耦合 step 状态机，让"✓ 入库成功"banner 必现）
  const [confirmSuccess, setConfirmSuccess] = useState(false)
  // PR4a DATA-012：editingDate/editYear/editMonth/editDay 编辑状态机已删除（日期移到 header 副标题）

  useEffect(() => {
    fetchLatest(1)
    fetchMetaList()
    // PR3plus 决策 30/31：拉取 maxSnapshotAgeDays
    fetchMaxSnapshotAgeDays(1).catch((e) => {
      console.warn('PR3plus fetchMaxSnapshotAgeDays failed, use default 7', e)
    })
    // PR1 修复 GLOBAL-015：组件卸载时释放所有 blob URL，避免内存泄漏
    return () => revokeAll(filePreviews)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function fetchMetaList() {
    setMetaLoading(true)
    try {
      const data = await apiClient.get(ENDPOINTS.SNAPSHOT_META_LIST, {
        headers: { 'X-User-Id': '1' },
      })
      setMetaList(data || [])
    } catch (e) {
      console.error('fetchMetaList failed', e)
    } finally {
      setMetaLoading(false)
    }
  }

  // 1b.3.6 文件选择（PR1 修复 GLOBAL-015：先释放旧的 blob URL，再创建新的）
  // 1b.4 PR4a GLOBAL-016：> 4 张改 setError，不再静默截断
  function handleFiles(e) {
    const list = Array.from(e.target.files || [])
    if (list.length === 0) return
    if (list.length > 4) {
      setError(`最多 4 张图，当前选了 ${list.length} 张，请重新选择`)
      return
    }

    // PR1：释放旧的 blob，避免泄漏
    revokeAll(filePreviews)

    setFiles(list)
    setFilePreviews(list.map((f) => ({ name: f.name, url: URL.createObjectURL(f) })))
    setStep('idle')
    setError(null)
    setParsedAsset(null)
    setConfirmSuccess(false)  // PR3+ BUG-001：上传新图时重置 success banner
  }

  // 1b.3.6 单张删除（PR1 修复 GLOBAL-015：释放被删的 blob URL）
  function removeFile(idx) {
    const removed = filePreviews[idx]
    if (removed) revokeOne(removed)
    setFiles(files.filter((_, i) => i !== idx))
    setFilePreviews(filePreviews.filter((_, i) => i !== idx))
  }

  // 1b.3.6 + 1b.3.7 一步：上传 + parse（PR3plus 决策 30/31：加预校验）
  async function uploadAndParse() {
    if (files.length !== 4) {
      setError('需要 4 张截图')
      return
    }
    if (!snapshotDate) {
      setError('请选择 snapshot_date')
      return
    }
    // PR3plus 决策 30/31：前端预校验（maxSnapshotAgeDays 从 store 读，-1 表示不限制）
    if (maxSnapshotAgeDays !== -1) {
      const today = new Date()
      const todayStr = today.toISOString().slice(0, 10)
      const todayMs = Date.parse(todayStr + 'T00:00:00Z')
      const dateMs = Date.parse(snapshotDate + 'T00:00:00Z')
      if (!isNaN(dateMs) && !isNaN(todayMs)) {
        const daysDiff = Math.abs(Math.round((todayMs - dateMs) / 86400000))
        if (daysDiff > maxSnapshotAgeDays) {
          setError(
            `截图日期 ${snapshotDate} 与当前日相差 ${daysDiff} 天，超过当前限制 ${maxSnapshotAgeDays === -1 ? '不限制' : maxSnapshotAgeDays + ' 天'}。请先点击下方 [修改历史限制] 调整。`
          )
          setStep('error')
          return
        }
      }
    }
    setError(null)
    setStep('uploading')
    try {
      // 1b.3.6 修复：上传端点单文件，多文件循环调用
      const fileIds = []
      for (const f of files) {
        const fd = new FormData()
        fd.append('file', f)
        const r = await apiClient.post(ENDPOINTS.SCREENSHOT_UPLOAD, fd, {
          headers: { 'Content-Type': 'multipart/form-data' },
        })
        if (!r.fileId) {
          throw new Error('upload 返回无 fileId: ' + JSON.stringify(r));
        }
        fileIds.push(r.fileId);
      }
      if (fileIds.length !== 4) {
        throw new Error('upload 部分失败: ' + fileIds.length + '/4');
      }
      setStep('parsing')
      const parseResp = await apiClient.post(
        `${ENDPOINTS.SCREENSHOT_PARSE_BATCH}?mode=${mode}`,
        { userId: 1, fileIds, dataTime: snapshotDate },
        { headers: { 'X-User-Id': '1' } }
      )
      setParsedAsset(parseResp.parsedAsset)
      setParseInfo({
        imageCount: parseResp.imageCount,
        fundCount: parseResp.parsedAsset?.categories?.reduce((s, c) => s + (c.funds?.length || 0), 0) || 0,
        usedProvider: parseResp.usedProvider,
        fallbackTriggered: parseResp.fallbackTriggered,
      })
      setStep('parsed')
    } catch (e) {
      setError(e?.message || '上传或解析失败')
      setStep('error')
    }
  }

  // 1b.3.10 打开确认入库弹窗（数据预览）
  function openConfirmModal() {
    if (!parsedAsset) return
    const cats = parsedAsset.categories || []
    const funds = cats.flatMap((c) => (c.funds || []))
    const totalSix = cats.filter((c) => c.categoryName !== '余额类').reduce((s, c) => s + Number(c.categoryTotal || 0), 0)
    const balance = cats.find((c) => c.categoryName === '余额类')
    const totalWithBalance = totalSix + Number(balance?.categoryTotal || 0)
    setParsedSummary({
      sixCategoriesTotal: totalSix,
      balanceFundTotal: Number(balance?.categoryTotal || 0),
      totalWithBalance,
      fundCount: funds.length,
      categories: cats,
    })
    setShowConfirmModal(true)
  }

  // 1b.3.8 confirm（PR1 修复 GLOBAL-015 + PR3 DATA-006 + PR3+ BUG-001/004/005 + PR3+hotfix BUG-006）
  async function confirm() {
    if (!parsedAsset) return
    setStep('confirming')
    // ===== 主 confirm 流程（必须成功，否则失败回滚） =====
    try {
      // 1）入库到 snapshot_meta
      await apiClient.post(
        ENDPOINTS.SNAPSHOT_CONFIRM,
        { userId: 1, snapshotDate, confirmedOverwrite: true, parsedAssets: [parsedAsset] },
        { headers: { 'X-User-Id': '1' } }
      )
      // 2）PR3 DATA-006：成功后直接 setStep('idle')，由空态分支接管
      await fetchLatest(1)
      await fetchMetaList()  // 刷新列表
      // 3）PR1：释放所有 blob URL
      revokeAll(filePreviews)
      setFiles([])
      setFilePreviews([])
      setParsedAsset(null)
      setParseInfo(null)  // 清理 parseInfo（banner 已用 optional chaining 安全访问 fundCount）
      setStep('idle')
      // 4）PR3+ BUG-001：独立 confirmSuccess state 显示入库成功 banner
      setConfirmSuccess(true)
      setTimeout(() => setConfirmSuccess(false), 5000)  // 5s 后消失
    } catch (confirmErr) {
      // PR3+ BUG-005：错误 message 优先取后端业务 message（e.response.data.message），其次 axios 默认
      const msg = confirmErr?.response?.data?.message
        || confirmErr?.response?.data?.msg
        || confirmErr?.message
        || 'confirm 失败'
      console.error('[confirm 失败]', confirmErr)
      setError(`入库失败: ${msg}`)
      setStep('error')
      setConfirmSuccess(false)
      // PR3+hotfix BUG-006：失败路径也关闭 modal（按钮已先关，但保险），并清理 parseInfo
      setShowConfirmModal(false)
      setParsedSummary(null)
      setParseInfo(null)
    }

    // ===== PR3+ BUG-004：独立 try-catch 自动设 is_current =====
    // setCurrent 失败不影响主 confirm 成功状态（用户可手动点'设为当前'补救）
    try {
      await apiClient.post(
        ENDPOINTS.SNAPSHOT_SET_CURRENT,
        { userId: 1, snapshotDate },
        { headers: { 'X-User-Id': '1' } }
      )
      // 成功后 list 再刷一次确保 is_current 状态正确显示
      await fetchMetaList()
    } catch (setCurrentErr) {
      // 静默失败（不影响 confirm 成功状态），仅 console 记录
      console.warn('[confirm] setCurrent 失败，可手动设为 current:', setCurrentErr?.response?.data || setCurrentErr?.message)
    }
  }

  // 1b.3.8 设为当前快照
  async function setCurrent(snapshotDate) {
    try {
      await apiClient.post(
        ENDPOINTS.SNAPSHOT_SET_CURRENT,
        { userId: 1, snapshotDate },
        { headers: { 'X-User-Id': '1' } }
      )
      await fetchLatest(1)
      await fetchMetaList()
    } catch (e) {
      setError(e?.message || 'set-current 失败')
    }
  }

  return (
    <div className="data-page">
      <header className="data-header">
        <h1>数据管理</h1>
        <p className="sub">上传 4 张支付宝基金截图 → parse → confirm → 决策 27 snapshot_meta 同步</p>
      </header>

      {error && <div className="error-banner">⚠ {error}</div>}

      <section className="section-card">
        <h2>上传 4 张图</h2>
        <p className="hint">选 4 张支付宝基金截图 → 自动截取前 4 张。已选 {files.length}/4。</p>
        <div className="upload-bar">
          <input
            type="file"
            accept="image/*"
            multiple
            onChange={handleFiles}
            data-testid="upload-input"
          />
          {filePreviews.length > 0 && (
            <div className="preview-strip">
              {filePreviews.map((p, i) => (
                <div key={i} className="preview-item" title={p.name}>
                  <img src={p.url} alt={p.name} />
                  <span>{p.name}</span>
                  <button
                    onClick={() => removeFile(i)}
                    className="rm-btn"
                    aria-label="删除"
                    type="button"
                  >×</button>
                </div>
              ))}
            </div>
          )}
        </div>
        {/* 1b.4 PR4a · DATA-002：去掉'建议 0716 数据'残留 */}
        {filePreviews.length === 0 && (
          <div className="preview-empty">请选择 4 张支付宝基金截图</div>
        )}
      </section>

      <section className="section-card">
        <h2>解析与日期</h2>
        <p className="hint">选择解析模式与截图数据日期，再点 "上传并解析"。</p>
        {/* 1b.4 PR4a 收尾：form-group 包裹使两个 label 结构一致，
            避免解析模式的 form-hint 推高 select 而日期输入被压低 */}
        <div className="form-row">
          <div className="form-group">
            <label>
              <span>解析模式</span>
              <select value={mode} onChange={(e) => setMode(e.target.value)} data-testid="mode-select">
                <option value="single">single（推荐，单图逐张）</option>
                <option value="multi">multi（4 图 batch）</option>
              </select>
            </label>
            {/* 1b.4 PR4a · DATA-010：解析模式说明 */}
            <small className="form-hint">
              <strong>single</strong>：逐张上传，失败可单独重试；<br />
              <strong>multi</strong>：4 张一次性发给 AI，速度快但失败需全部重试。
            </small>
          </div>
          <div className="form-group">
            <label>
              <span>截图数据日期</span>
              <input
                type="date"
                value={snapshotDate}
                onChange={(e) => setSnapshotDate(e.target.value)}
                data-testid="date-input"
              />
            </label>
            {/* 1b.4 PR4a 收尾 v2：占位元素内容与解析模式 form-hint 完全一致，
                保证两个 form-group 高度严格相同（&nbsp; 单行占位会导致日期模块偏矮） */}
            <small
              className="form-hint"
              aria-hidden="true"
              style={{ visibility: 'hidden' }}
            >
              <strong>single</strong>：逐张上传，失败可单独重试；<br />
              <strong>multi</strong>：4 张一次性发给 AI，速度快但失败需全部重试。
            </small>
          </div>
        </div>
        <div style={{ marginTop: 8 }}>
          {/* 1b.4 PR4a · DATA-005：上传并解析按钮加 tooltip */}
          <button
            onClick={uploadAndParse}
            disabled={step === 'uploading' || step === 'parsing' || files.length !== 4}
            className="primary-btn"
            data-testid="parse-btn"
            title="先上传图片，再调用 AI 解析 19 只基金数据"
          >
            {step === 'uploading' ? '上传中…' : step === 'parsing' ? '解析中…' : '上传并解析'}
          </button>
        </div>
        {parseInfo && (
          <div className="parse-info">
            ✓ 解析成功：{parseInfo.imageCount} 张图 / {parseInfo.fundCount} 只基金
            （provider={parseInfo.usedProvider}，fallback={String(parseInfo.fallbackTriggered)}）
          </div>
        )}
      </section>

      {/* PR3plus 决策 30/31：历史限制提示 + 修改按钮（从 store 读，不硬编码） */}
      <section className="section-card" style={{ background: '#f9fafb' }}>
        <h2>上传历史限制</h2>
        <p className="hint">
          当前历史截图限制：最近{' '}
          <strong data-testid="current-max-age">
            {maxSnapshotAgeDays === -1 ? '不限制' : `${maxSnapshotAgeDays} 天`}
          </strong>
          <button
            className="secondary-btn"
            style={{ marginLeft: 12, padding: '4px 10px', fontSize: 12 }}
            onClick={() => setShowHistoryLimitDialog(true)}
            data-testid="open-history-limit-dialog"
          >
            修改
          </button>
        </p>
        <p className="hint" style={{ fontSize: 12, color: '#6b7280' }}>
          为防止误传，超过该限制的截图无法 confirm 入库。
        </p>
      </section>

      <section className="section-card">
        <h2>确认入库</h2>
        {/* 1b.4 PR4a · DATA-011：去除技术术语 asset_raw/snapshot_meta */}
        <p className="hint">确认将今日资产数据写入历史记录</p>
        <button
          onClick={openConfirmModal}
          disabled={step !== 'parsed' && step !== 'confirming'}
          className="primary-btn"
          data-testid="confirm-btn"
        >
          {step === 'confirming' ? '入库中…' : '确认入库（请先预览）'}
        </button>
        {/* PR3+ BUG-001：独立 confirmSuccess state 控制 banner（不耦合 step 状态机） */}
        {confirmSuccess && (
          <div className="success-banner">✓ 入库成功！首页应已显示 {parseInfo?.fundCount ?? 0} 只基金</div>
        )}
      </section>

      <section className="section-card">
        <h2>快照管理（决策 27）</h2>
        <div className="meta-list">
          {metaLoading && <div className="preview-empty">加载中…</div>}
          {!metaLoading && metaList.length === 0 && (
            <div className="preview-empty">暂无 snapshot_meta 记录</div>
          )}
          {metaList.map((m) => (
            <div key={m.id} className={`meta-item ${m.isCurrent ? 'current' : ''}`}>
              <div className="meta-info">
                <strong>{m.snapshotDate}</strong>
                <span className="badge is-latest">{m.isLatest ? 'is_latest=true' : 'is_latest=false'}</span>
                <span className={`badge is-current ${m.isCurrent ? 'active' : ''}`}>
                  {m.isCurrent ? '★ CURRENT' : 'is_current=false'}
                </span>
                <span className="confirmed-at">{m.confirmedAt}</span>
              </div>
              {/* PR3+ BUG-002：is_current 卡片右侧加 ✓ 当前 badge 占位，与其他卡片等高 */}
              <div className="meta-actions">
                {m.isCurrent ? (
                  <span
                    className="badge is-current active"
                    style={{ padding: '6px 12px' }}
                    data-testid={`current-badge-${m.snapshotDate}`}
                  >
                    ✓ 当前
                  </span>
                ) : (
                  <button
                    onClick={() => setCurrent(m.snapshotDate)}
                    className="secondary-btn"
                    data-testid={`set-current-${m.snapshotDate}`}
                  >
                    设为当前
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* PR3plus 决策 30/31：历史限制修改 4 步 modal */}
      <HistoryLimitDialog
        open={showHistoryLimitDialog}
        onClose={() => setShowHistoryLimitDialog(false)}
        currentDays={maxSnapshotAgeDays}
        userId={1}
      />

      {/* 1b.3.10 确认入库弹窗（解析数据预览） */}
      {showConfirmModal && parsedSummary && parsedSummary.fundCount != null && (
        <div className="modal-backdrop" onClick={() => setShowConfirmModal(false)}>
          <div className="modal modal--wide" onClick={(e) => e.stopPropagation()}>
            {/* 1b.4 PR4a · DATA-012：快照日期移到 modal-header 副标题 */}
            <div className="modal-header">
              <h2>
                📊 今日资产预览 ·{' '}
                <span className="modal-date" data-testid="modal-snapshot-date">
                  {snapshotDate}
                </span>
              </h2>
              <button className="modal-close" onClick={() => setShowConfirmModal(false)} aria-label="关闭">×</button>
            </div>
            <div className="modal-body">
              {/* 概览卡片（PR4a DATA-012：5 列 → 4 列） */}
              <div className="overview-summary">
                <div className="overview-card highlight">
                  <div className="label">总资产（含余额类）</div>
                  <div className="value">¥{Number(parsedSummary.totalWithBalance).toFixed(2)}</div>
                </div>
                <div className="overview-card">
                  <div className="label">六大类</div>
                  <div className="value">¥{Number(parsedSummary.sixCategoriesTotal).toFixed(2)}</div>
                </div>
                <div className="overview-card">
                  <div className="label">余额类</div>
                  <div className="value">¥{Number(parsedSummary.balanceFundTotal).toFixed(2)}</div>
                </div>
                <div className="overview-card">
                  <div className="label">基金数</div>
                  <div className="value">{parsedSummary.fundCount}</div>
                </div>
              </div>

              {/* 各类小计 + 占比（1b.4-pr2：余额类"占六大类 %"显示—，新增"占总资产的比例"列，精确到小数点后两位） */}
              <div className="overview-detail">
                <h3>各类小计</h3>
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>类别</th>
                      <th>金额（元）</th>
                      <th>占六大类 %</th>
                      <th>占总资产的比例</th>
                      <th>基金数</th>
                    </tr>
                  </thead>
                  <tbody>
                    {parsedSummary.categories.map((c) => {
                      const isBalance = c.categoryName === '余额类'
                      const sixPct = isBalance
                        ? '—'
                        : (parsedSummary.sixCategoriesTotal > 0
                            ? (Number(c.categoryTotal || 0) / parsedSummary.sixCategoriesTotal * 100).toFixed(2) + '%'
                            : '0.00%')
                      const totalPct = parsedSummary.totalWithBalance > 0
                        ? (Number(c.categoryTotal || 0) / parsedSummary.totalWithBalance * 100).toFixed(2) + '%'
                        : '0.00%'
                      return (
                        <tr key={c.categoryName}>
                          <td>{c.categoryName}</td>
                          <td>¥{Number(c.categoryTotal || 0).toFixed(2)}</td>
                          <td>{sixPct}</td>
                          <td>{totalPct}</td>
                          <td>{c.fundCount || 0}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>

              {/* 基金明细 - P6-2: 加 持有收益/累计收益 列 */}
              <div className="overview-detail"
                style={{ marginTop: '16px' }}>
                <h3>基金明细（{parsedSummary.fundCount} 只）</h3>
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>基金名称</th>
                      <th>类别</th>
                      <th>金额（元）</th>
                      <th>持有收益（元）</th>
                      <th>累计收益（元）</th>
                    </tr>
                  </thead>
                  <tbody>
                    {parsedSummary.categories.flatMap((c) => (c.funds || []).map((f) => (
                      <tr key={`${c.categoryName}-${f.fundName}`}>
                        <td>{f.fundName}</td>
                        <td>{c.categoryName}</td>
                        <td>¥{Number(f.amount || 0).toFixed(2)}</td>
                        <td>{f.holdingProfit == null ? '—' : `¥${Number(f.holdingProfit).toFixed(2)}`}</td>
                        <td>{f.cumulativeProfit == null ? '—' : `¥${Number(f.cumulativeProfit).toFixed(2)}`}</td>
                      </tr>
                    )))}
                  </tbody>
                </table>
              </div>

              <p style={{ fontSize: '12px', color: '#6b7280', marginTop: '16px' }}>
                提示：仅做资产概览。后续大类的精确确认（每个 asset_raw 行的目标比例/实际比例）会在 Phase 3 实施。
              </p>
            </div>
            <div className="modal-footer">
              <button className="secondary-btn" onClick={() => setShowConfirmModal(false)}>取消</button>
              <button className="primary-btn" onClick={() => { setShowConfirmModal(false); confirm(); }}>
                {step === 'confirming' ? '入库中…' : '确认入库'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}