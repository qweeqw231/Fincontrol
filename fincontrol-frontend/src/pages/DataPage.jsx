import { useEffect, useState } from 'react'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'
import { apiClient } from '../api/client.js'
import { ENDPOINTS } from '../api/endpoints.js'

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
 *   <li>列表自动刷新（fetchLatest + fetchMetaList）</li>
 * </ol>
 */
export default function DataPage() {
  const fetchLatest = useAssetSnapshotStore((s) => s.fetchLatest)
  const snapshot = useAssetSnapshotStore((s) => s.latestSnapshot)

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
  const [step, setStep] = useState('idle') // idle | uploading | parsing | parsed | confirming | confirmed | error
  const [error, setError] = useState(null)
  const [parsedAsset, setParsedAsset] = useState(null)
  const [parseInfo, setParseInfo] = useState(null)

  // 1b.3.9 快照管理列表
  const [metaList, setMetaList] = useState([])
  const [metaLoading, setMetaLoading] = useState(false)

  useEffect(() => {
    fetchLatest(1)
    fetchMetaList()
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

  // 1b.3.6 文件选择
  function handleFiles(e) {
    const list = Array.from(e.target.files || [])
    if (list.length > 4) {
      alert('最多 4 张图，已自动截取前 4 张')
    }
    const next = list.slice(0, 4)
    setFiles(next)
    setFilePreviews(next.map((f) => ({ name: f.name, url: URL.createObjectURL(f) })))
    setStep('idle')
    setError(null)
    setParsedAsset(null)
  }

  function removeFile(idx) {
    const next = files.filter((_, i) => i !== idx)
    setFiles(next)
    setFilePreviews(filePreviews.filter((_, i) => i !== idx))
  }

  // 1b.3.6 + 1b.3.7 一步：上传 + parse
  async function uploadAndParse() {
    if (files.length !== 4) {
      setError('需要 4 张截图')
      return
    }
    if (!snapshotDate) {
      setError('请选择 snapshot_date')
      return
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

  // 1b.3.8 confirm
  async function confirm() {
    if (!parsedAsset) return
    setStep('confirming')
    try {
      await apiClient.post(
        ENDPOINTS.SNAPSHOT_CONFIRM,
        { userId: 1, snapshotDate, confirmedOverwrite: true, parsedAssets: [parsedAsset] },
        { headers: { 'X-User-Id': '1' } }
      )
      setStep('confirmed')
      await fetchLatest(1)
      await fetchMetaList()
      setFiles([])
      setFilePreviews([])
      setParsedAsset(null)
    } catch (e) {
      setError(e?.message || 'confirm 失败')
      setStep('error')
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

      <section className="card">
        <h2>1. 上传 4 张图</h2>
        <input
          type="file"
          accept="image/*"
          multiple
          onChange={handleFiles}
          data-testid="upload-input"
        />
        <div className="preview-grid">
          {filePreviews.map((p, i) => (
            <div key={i} className="preview-item">
              <img src={p.url} alt={p.name} />
              <span>{p.name}</span>
              <button onClick={() => removeFile(i)} className="rm-btn">×</button>
            </div>
          ))}
          {filePreviews.length === 0 && (
            <div className="preview-empty">请选择 4 张图（建议 0716 数据）</div>
          )}
        </div>
      </section>

      <section className="card">
        <h2>2. 解析与日期</h2>
        <div className="form-row">
          <label>
            <span>解析模式</span>
            <select value={mode} onChange={(e) => setMode(e.target.value)} data-testid="mode-select">
              <option value="single">single（推荐，单图逐张）</option>
              <option value="multi">multi（4 图 batch）</option>
            </select>
          </label>
          <label>
            <span>截图数据日期</span>
            <input
              type="date"
              value={snapshotDate}
              onChange={(e) => setSnapshotDate(e.target.value)}
              data-testid="date-input"
            />
          </label>
          <button
            onClick={uploadAndParse}
            disabled={step === 'uploading' || step === 'parsing' || files.length !== 4}
            className="primary-btn"
            data-testid="parse-btn"
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

      <section className="card">
        <h2>3. 确认入库</h2>
        <p className="hint">确认将 19-fund 数据写入 asset_raw + asset_snapshot + snapshot_meta</p>
        <button
          onClick={confirm}
          disabled={step !== 'parsed' && step !== 'confirmed'}
          className="primary-btn"
          data-testid="confirm-btn"
        >
          {step === 'confirming' ? '入库中…' : '确认入库'}
        </button>
        {step === 'confirmed' && (
          <div className="success-banner">✓ 入库成功！首页应已显示 19 只基金</div>
        )}
      </section>

      <section className="card">
        <h2>4. 快照管理（决策 27）</h2>
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
              <div className="meta-actions">
                {!m.isCurrent && (
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
    </div>
  )
}
