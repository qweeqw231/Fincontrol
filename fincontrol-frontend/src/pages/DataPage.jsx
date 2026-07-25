import { useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { friendlyError } from '../utils/formatters.js'
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
 *   <li>1b.4-pr7 (DATA-016)：入库成功后弹"设为当前吗?" prompt（蓝色默认改 current，白色取消保留）</li>
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
 * </ul>
 * <p>2026-07-25 PR7 (DATA-016) 修改：
 * <ul>
 *   <li>Fix 1：doConfirm 真正清理 preview modal 全 state（preview 才能自动关闭）</li>
 *   <li>Fix 2：删除原 PR3+ BUG-004 "confirm 后自动 setCurrent"（用户上传错日期会污染 current）</li>
 *   <li>Fix 3：入库成功后弹"设为当前吗?" prompt，蓝色默认改 current，白色取消保留</li>
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

  // 1b4pr6b 决策 33 v2 · 预览 modal 大类修正 UI（D1-D7）
  // canonical 类别（8 个 = 7 canonical + 余额类）
  const FUND_CATEGORIES = ['货币类', '固收类', '商品类', 'A股权益类', '海外权益类', '港股大中华类', '余额类']
  // D5：已确认映射表（从后端 match API 加载，key=fundName）
  const [categoryOverrides, setCategoryOverrides] = useState({})
  // D2：用户改 dropdown 但未点 ✓ 的草稿（key=fundName → categoryName）
  const [categoryDirty, setCategoryDirty] = useState({})
  // 行级 saving 状态（key=fundName → bool）
  const [overridesSaving, setOverridesSaving] = useState({})
  // D2：dirtyCount > 0 时弹二次确认 modal
  const [showSubmitDirtyModal, setShowSubmitDirtyModal] = useState(false)
  // D7：消失-重现事件缓存（key=fundName → { firstMissingSnapshotDate, lastSeenSnapshotDate }）
  const [pendingReConfirms, setPendingReConfirms] = useState({})

  // 1b4pr6b 批量确认 UX：top toggle + checkbox 列 + 6 大类选择 + 弹窗
  // batchMode：是否进入批量模式（true 时显示 checkbox 列 + 顶部 bar）
  const [batchMode, setBatchMode] = useState(false)
  // selectedFunds：当前勾选的 fundName Set
  const [selectedFunds, setSelectedFunds] = useState(new Set())
  // batchCategory：批量确认的目标大类，默认'固收类'
  const [batchCategory, setBatchCategory] = useState('固收类')
  // showBatchConfirmModal：批量确认二次弹窗
  const [showBatchConfirmModal, setShowBatchConfirmModal] = useState(false)
  // batchConfirming：批量提交中（避免重复点击）
  const [batchConfirming, setBatchConfirming] = useState(false)
  // batchToast：已确认 checkbox 点击后弹出"您已经确认 XXX 的 YYY 归属！"
  const [batchToast, setBatchToast] = useState(null) // {fundName, category} | null

  // PR3+ BUG-001：独立 confirmSuccess state（不耦合 step 状态机，让"✓ 入库成功"banner 必现）
  const [confirmSuccess, setConfirmSuccess] = useState(false)
  // 1b.4-pr7 (DATA-016) Fix 6：抢救 fundCount——doConfirm 成功路径会立刻 setParseInfo(null)
  // 清理 modal state（Fix 1），而 banner 要显示 5s。如果只读 parseInfo，5s 期间 parseInfo 是 null → fundCount 显示 0。
  // 存一个独立的 lastSuccessFundCount 让 banner 能读对。
  const [lastSuccessFundCount, setLastSuccessFundCount] = useState(0)

  // 1b.4-pr7 (DATA-016) Fix 3：入库成功后弹"设为当前吗?" prompt（蓝色默认改 current，白色取消保留）
  // - date：要设为 current 的快照日期
  // - fundCount：本次入库的基金数（用于标题显示 "已入库 N 只基金"）
  // - null：不弹
  const [setCurrentPrompt, setSetCurrentPrompt] = useState(null) // {date, fundCount} | null

  // PR6 (1b.4 后 PR)：预览弹窗快照日期可点击修改（2-step UX：点击 → 确认弹窗 → 日期选择器）
  // 1) confirmingDateEdit=true 弹确认问询（不破坏页面主结构）
  // 2) editingDate=true 弹年/月/日 3 select 滚轮
  // 3) 确定时调 setSnapshotDate + setShowConfirmModal 保持预览，AI 解析结果不变（只改提交时的日期）
  const [confirmingDateEdit, setConfirmingDateEdit] = useState(false)
  const [editingDate, setEditingDate] = useState(false)
  const [editYear, setEditYear] = useState(new Date().getFullYear())
  const [editMonth, setEditMonth] = useState(new Date().getMonth() + 1)
  const [editDay, setEditDay] = useState(new Date().getDate())
  function openDateEdit() {
    // 解析当前 snapshotDate 为年/月/日初值
    const parts = (snapshotDate || '').split('-')
    if (parts.length === 3) {
      setEditYear(parseInt(parts[0], 10))
      setEditMonth(parseInt(parts[1], 10))
      setEditDay(parseInt(parts[2], 10))
    }
    setConfirmingDateEdit(true)  // 先弹确认
  }
  function confirmDateEdit() {
    // 确认 → 进入日期选择
    setConfirmingDateEdit(false)
    setEditingDate(true)
  }
  function cancelDateEdit() {
    // 取消（无论在确认态还是编辑态）
    setConfirmingDateEdit(false)
    setEditingDate(false)
  }
  function applyDateEdit() {
    // 确定 → 应用新日期到 snapshotDate
    const yyyy = String(editYear).padStart(4, '0')
    const mm = String(editMonth).padStart(2, '0')
    const dd = String(editDay).padStart(2, '0')
    setSnapshotDate(`${yyyy}-${mm}-${dd}`)
    setEditingDate(false)
  }
  // PR4a DATA-012：editingDate/editYear/editMonth/editDay 编辑状态机已删除（日期移到 header 副标题）
  // PR6 恢复：editingDate + editYear/Month/Day 状态机在 2-step 弹窗内使用（点击日期 → 确认弹窗 → 日期选择器）
  // 注：confirm() 中使用 snapshotDate 提交，编辑时修改 snapshotDate 即可（不改 AI 解析结果）

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
  async function openConfirmModal() {
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

    // 决策 33 D5：调 match API 拿 user_correct 自动套用 + D7 拿消失-重现事件
    try {
      const fundNames = funds.map((f) => f.fundName).filter(Boolean)
      const matchResp = await apiClient.get(ENDPOINTS.CATEGORY_MAP_MATCH, {
        params: { funds: fundNames.join(',') },
      })
      const overrides = {}
      const reConfirms = {}
      for (const item of matchResp.matchedFunds || []) {
        overrides[item.fundName] = {
          category: item.category,
          source: item.source,
          confirmedAt: item.confirmedAt,
          lastSeenSnapshotDate: item.lastSeenSnapshotDate,
        }
        if (item.firstMissingSnapshotDate) {
          reConfirms[item.fundName] = {
            firstMissingSnapshotDate: item.firstMissingSnapshotDate,
            lastSeenSnapshotDate: item.lastSeenSnapshotDate,
          }
        }
      }
      setCategoryOverrides(overrides)
      setPendingReConfirms(reConfirms)
    } catch (e) {
      console.warn('[D5] match API 失败, 默认使用 AI 原猜分类:', e?.message)
    }

    setShowConfirmModal(true)
  }

  // 决策 33 v2 · 派生计算
  function getEffectiveCategory(fundName, originalCategory) {
    // 优先级: overrides (已 user_correct) > dirty (草稿) > original (AI 原猜)
    if (categoryOverrides[fundName]) return categoryOverrides[fundName].category
    if (categoryDirty[fundName]) return categoryDirty[fundName]
    return originalCategory
  }
  const pendingCount = useMemo(() => {
    if (!parsedSummary) return 0
    let n = 0
    for (const c of parsedSummary.categories) {
      for (const f of c.funds || []) {
        if (!categoryOverrides[f.fundName]) n++
      }
    }
    return n
  }, [parsedSummary, categoryOverrides])
  const dirtyCount = Object.keys(categoryDirty).length

  // 1b4pr6b Fix B：各类小计实时联动（derivedCategories）
  // 输入: parsedAsset (原始 AI 解析) + categoryOverrides (已确认) + categoryDirty (草稿)
  // 逻辑: 按 effective category (override > dirty > AI 原猜) 重新分桶到 8 个 canonical 类别
  // 效果: dropdown 改 1 个 fund → 小计表实时刷新 + 批量确认 → 小计一次刷新多行
  const derivedCategories = useMemo(() => {
    if (!parsedAsset) return []
    // 1. 初始化 8 个空桶（7 canonical + 余额类）
    const buckets = new Map()
    for (const cat of FUND_CATEGORIES) {
      buckets.set(cat, { categoryName: cat, categoryTotal: 0, fundCount: 0, funds: [] })
    }
    // 2. 遍历原始 funds，按 effective category 分桶
    for (const c of parsedAsset.categories || []) {
      for (const f of c.funds || []) {
        const override = categoryOverrides[f.fundName]
        const dirty = categoryDirty[f.fundName]
        const effective = override ? override.category : (dirty || c.categoryName)
        const bucket = buckets.get(effective)
        if (bucket) {
          bucket.categoryTotal += Number(f.amount || 0)
          bucket.fundCount += 1
          bucket.funds.push({ ...f, originalCategory: c.categoryName })
        }
      }
    }
    // 3. 返回非空桶（包含全部 8 类，即使 fundCount=0 也保留，让表格显示“空”行）
    return FUND_CATEGORIES.map((c) => buckets.get(c)).filter((b) => b.fundCount > 0 || c === '余额类' || buckets.get(c).categoryTotal > 0 || true)
  }, [parsedAsset, categoryOverrides, categoryDirty])

  // 决策 33 v2 · API 调用
  async function confirmOverride(fundName, category) {
    setOverridesSaving((s) => ({ ...s, [fundName]: true }))
    try {
      const data = await apiClient.post(ENDPOINTS.CATEGORY_MAP_UPDATE, { fundName, category })
      setCategoryOverrides((o) => ({
        ...o,
        [fundName]: {
          category: data.category,
          source: data.source,
          confirmedAt: data.confirmedAt,
          mappingId: data.mappingId,
        },
      }))
      setCategoryDirty((d) => {
        const n = { ...d }
        delete n[fundName]
        return n
      })
    } catch (e) {
      setError(friendlyError(e))
    } finally {
      setOverridesSaving((s) => ({ ...s, [fundName]: false }))
    }
  }
  async function resetOverride(fundName) {
    setOverridesSaving((s) => ({ ...s, [fundName]: true }))
    try {
      await apiClient.post(ENDPOINTS.CATEGORY_MAP_RESET, {}, { params: { fundName } })
      setCategoryOverrides((o) => {
        const n = { ...o }
        delete n[fundName]
        return n
      })
    } catch (e) {
      setError(friendlyError(e))
    } finally {
      setOverridesSaving((s) => ({ ...s, [fundName]: false }))
    }
  }
  function handleConfirm() {
    if (dirtyCount > 0) {
      setShowSubmitDirtyModal(true)
      return
    }
    doConfirm()
  }
  async function submitAllDirty() {
    // 逐行调 update
    for (const [fundName, cat] of Object.entries(categoryDirty)) {
      await confirmOverride(fundName, cat)
    }
    doConfirm()
  }
  function submitOnlyVerified() {
    setCategoryDirty({})
    doConfirm()
  }

  // 1b4pr6b 批量确认 UX · 助手函数
  // toggleBatchMode：进入/退出批量模式
  //   - 退出时清空 selectedFunds + batchCategory 重置 + batchToast 清掉
  //   - 进入时如果 selectedFunds > 0 ，保持选择；否则空
  function toggleBatchMode() {
    if (batchMode) {
      // 退出批量：重置所有状态
      setBatchMode(false)
      setSelectedFunds(new Set())
      setBatchCategory('固收类')
      setShowBatchConfirmModal(false)
      setBatchToast(null)
    } else {
      setBatchMode(true)
    }
  }
  // toggleSelectedFund：勾选/取消勾选某只基金
  function toggleSelectedFund(fundName) {
    // 1）如果该基金已被批量确认（override.category === batchCategory），则弹 toast 不加进 selected
    const o = categoryOverrides[fundName]
    if (o && o.category === batchCategory) {
      setBatchToast({ fundName, category: o.category })
      setTimeout(() => setBatchToast(null), 3000)
      return
    }
    // 2）正常 toggle
    setSelectedFunds((prev) => {
      const next = new Set(prev)
      if (next.has(fundName)) {
        next.delete(fundName)
      } else {
        next.add(fundName)
      }
      return next
    })
  }
  // doBatchConfirm：批量提交，逐个调 confirmOverride
  async function doBatchConfirm() {
    if (selectedFunds.size === 0) return
    setBatchConfirming(true)
    try {
      for (const fundName of selectedFunds) {
        // 只调 confirm（不调 reset，避免 audit log 噪声）
        await confirmOverride(fundName, batchCategory)
      }
      // 成功后退出批量模式（保留 overrides 以反映“已确认”状态）
      setBatchMode(false)
      setSelectedFunds(new Set())
      setShowBatchConfirmModal(false)
      // 1b4pr6b Fix D：刷新首页 store（next 用户返回首页时显示新分类）
      try {
        await fetchLatest(1)
      } catch (e) {
        console.warn('[Fix D] fetchLatest after doBatchConfirm failed:', e)
      }
    } catch (e) {
      setError(friendlyError(e))
    } finally {
      setBatchConfirming(false)
    }
  }

  // 决策 33 D1+D2：doConfirm（被 handleConfirm 调用，可能在二次确认 modal 后调用）
  // 1b.3.8 confirm（PR1 GLOBAL-015 + PR3 DATA-006 + PR3+ BUG-001/005 + PR3+hotfix BUG-006 + 1b.4-pr7 DATA-016 Fix 1/2/3）
  async function doConfirm() {
    if (!parsedAsset) return
    setStep('confirming')
    // ===== 主 confirm 流程（必须成功，否则失败回滚） =====
    try {
      // 1）入库到 snapshot_meta（confirmedOverwrite=true 让顶层 overwrite 子表）
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
      // 4）1b.4-pr7 DATA-016 Fix 1：彻底清理 preview modal 相关 state（modal 才能真正关掉）
      setShowConfirmModal(false)              // 关键：真正关 preview modal
      setParsedSummary(null)
      setCategoryDirty({})
      setCategoryOverrides({})
      setPendingReConfirms({})
      setShowSubmitDirtyModal(false)          // 保险：关二次确认 modal
      setShowBatchConfirmModal(false)         // 保险：关批量确认 modal
      setBatchMode(false)                     // 退批量模式
      setSelectedFunds(new Set())             // 清勾选
      setStep('idle')
      // 5）PR3+ BUG-001：独立 confirmSuccess state 显示入库成功 banner
      setConfirmSuccess(true)
      setTimeout(() => setConfirmSuccess(false), 5000)  // 5s 后消失
      // 6）1b.4-pr7 DATA-016 Fix 3：弹"设为当前吗?" prompt 让用户主动选择是否改 current
      // 注意：原 PR3+ BUG-004 (Fix 2 删除) 会在 confirm 后自动调 setCurrent，现改为用户主动。
      // 原因：用户上传错日期时也会被强制改 current，违反"未确认就不切 current"的设计。
      setSetCurrentPrompt({ date: snapshotDate, fundCount: parseInfo?.fundCount ?? 0 })
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
      // 1b.4-pr7 Fix 3 保险：失败路径不弹 prompt
      setSetCurrentPrompt(null)
    }
    // 1b.4-pr7 DATA-016 Fix 2：原"PR3+ BUG-004 自动 setCurrent"独立 try-catch 已删除。
    // 数据入库与"设为当前"解耦，由 Fix 3 prompt 让用户主动选。
  }

  // 1b.3.8 设为当前快照（主列表"设为当前"按钮 + Fix 3 prompt 蓝色按钮都复用）
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

  // 1b.4-pr7 DATA-016 Fix 3：prompt modal 两个回调
  // - 蓝色"改为当前日期"：先关 modal，再调 setCurrent（复用上面函数，含 fetchLatest + fetchMetaList）
  // - 白色"取消"：只关 modal，不动 current
  async function handleSetCurrentPromptConfirm() {
    if (!setCurrentPrompt) return
    const { date } = setCurrentPrompt
    setSetCurrentPrompt(null)  // 先关 modal（避免 setCurrent 过程中 modal 阻塞 UI）
    await setCurrent(date)
  }
  function handleSetCurrentPromptCancel() {
    setSetCurrentPrompt(null)  // 不动 current
  }

  return (
    <div className="data-page">
      {/* Fix 6：success banner 移出 section 到顶层（用 React Portal 到 document.body），
          并读 lastSuccessFundCount 避免 setParseInfo(null) 后的 0 显示问题 */}
      {confirmSuccess && createPortal(
        <div
          className="success-banner success-banner--top"
          role="status"
          aria-live="polite"
        >
          ✓ 入库成功！首页应已显示 {lastSuccessFundCount} 只基金
        </div>,
        document.body
      )}
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

      {/* PR3plus 决策 30/31：历史限制提示 + 修改按钮（从 store 读，不硬编码） ，当前历史截图限制：最近{7/14/30/180}天或者 不限制*/}
      <section className="section-card" style={{ background: '#f9fafb' }}>
        <h2>上传历史限制</h2>
        <p className="hint">
          当前历史截图限制：{' '}
          <strong data-testid="current-max-age">
            {maxSnapshotAgeDays === -1 ? '不限制' : `最近${maxSnapshotAgeDays} 天`}
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

      {/* 决策 33 D2：二次确认 modal（用户改 dropdown 但未点 ✓ 时拦截） */}
      {showSubmitDirtyModal && (
        <div className="modal-backdrop" onClick={() => setShowSubmitDirtyModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 480 }}>
            <div className="modal-header">
              <h2>⚠️ 还有 {dirtyCount} 行 dropdown 已改动但未 ✓</h2>
              <button className="modal-close" onClick={() => setShowSubmitDirtyModal(false)} aria-label="关闭">×</button>
            </div>
            <div className="modal-body">
              <p>你修改了 <strong>{dirtyCount}</strong> 条基金的类别，但还没点 ✓ 确认。</p>
              <p style={{ color: '#6b7280', fontSize: 13 }}>请选择如何处理：</p>
            </div>
            <div className="modal-footer">
              <button className="secondary-btn" onClick={() => setShowSubmitDirtyModal(false)} data-testid="cancel-dirty">返回修改</button>
              <button className="secondary-btn" onClick={submitOnlyVerified} data-testid="submit-only-verified">仅提交已 ✓ 的</button>
              <button className="primary-btn" onClick={submitAllDirty} data-testid="submit-all-dirty">全部提交</button>
            </div>
          </div>
        </div>
      )}

      {/* 1b4pr6b 批量确认二次弹窗 */}
      {showBatchConfirmModal && (
        <div className="modal-backdrop" onClick={() => !batchConfirming && setShowBatchConfirmModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 540 }}>
            <div className="modal-header">
              <h2>📦 批量确认归属</h2>
              <button className="modal-close" onClick={() => setShowBatchConfirmModal(false)} aria-label="关闭"
                disabled={batchConfirming}>×</button>
            </div>
            <div className="modal-body">
              <p>即将把以下 <strong data-testid="batch-modal-count">{selectedFunds.size}</strong> 只基金的归属修改为：
                <select
                  value={batchCategory}
                  onChange={(e) => setBatchCategory(e.target.value)}
                  data-testid="batch-modal-category"
                  disabled={batchConfirming}
                  style={{ marginLeft: 8, padding: '4px 8px', fontSize: 14 }}
                >
                  {FUND_CATEGORIES.filter((c) => c !== '余额类').map((cat) => (
                    <option key={cat} value={cat}>{cat}</option>
                  ))}
                </select>
              </p>
              <ul className="batch-fund-list" data-testid="batch-fund-list">
                {Array.from(selectedFunds).slice(0, 5).map((fn) => (
                  <li key={fn}>{fn}</li>
                ))}
                {selectedFunds.size > 5 && (
                  <li className="batch-fund-overflow">…等 {selectedFunds.size} 只</li>
                )}
              </ul>
              <p style={{ color: '#6b7280', fontSize: 13, marginTop: 8 }}>
                确认后这些基金的类别将被设为 <strong>{batchCategory}</strong>（user_correct）。
              </p>
            </div>
            <div className="modal-footer">
              <button className="secondary-btn" onClick={() => setShowBatchConfirmModal(false)}
                disabled={batchConfirming} data-testid="batch-modal-cancel">取消</button>
              <button className="primary-btn" onClick={doBatchConfirm}
                disabled={batchConfirming} data-testid="batch-modal-confirm">
                {batchConfirming ? '提交中…' : '确认'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 1b.3.10 确认入库弹窗（解析数据预览） */}
      {showConfirmModal && parsedSummary && parsedSummary.fundCount != null && (
        <div className="modal-backdrop" onClick={() => setShowConfirmModal(false)}>
          <div className="modal modal--wide" onClick={(e) => e.stopPropagation()}>
            {/* 1b.4 PR4a · DATA-012：快照日期移到 modal-header 副标题（PR6 增强：点击修改） */}
            <div className="modal-header">
              <h2>
                📊 今日资产预览 ·{' '}
                <span
                  className="modal-date modal-date-clickable"
                  data-testid="modal-snapshot-date"
                  onClick={openDateEdit}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') openDateEdit() }}
                  title="点击修改快照日期"
                >
                  {snapshotDate} <span className="modal-date-edit-hint">✎</span>
                </span>
              </h2>
              <button className="modal-close" onClick={() => setShowConfirmModal(false)} aria-label="关闭">×</button>
            </div>

            {/* PR6：点击日期 → 确认弹窗（"需要修改吗？"）→ 点击确认才出现 3 select 滚轮 */}
            {confirmingDateEdit && (
              <div className="modal-confirm-backdrop" onClick={cancelDateEdit}>
                <div className="modal-confirm-card" onClick={(e) => e.stopPropagation()}>
                  <div className="modal-confirm-title">需要修改快照日期吗？</div>
                  <div className="modal-confirm-sub">
                    当前入库的是 <strong>{snapshotDate}</strong> 的快照。
                    取消则返回预览；确认则进入日期选择。
                  </div>
                  <div className="modal-confirm-actions">
                    <button className="secondary-btn" onClick={cancelDateEdit}>取消</button>
                    <button className="primary-btn" onClick={confirmDateEdit}>确认修改</button>
                  </div>
                </div>
              </div>
            )}

            {/* PR6：日期选择弹窗（3 select 滚轮 + 确定/取消） */}
            {editingDate && (
              <div className="modal-confirm-backdrop" onClick={cancelDateEdit}>
                <div className="modal-confirm-card" onClick={(e) => e.stopPropagation()}>
                  <div className="modal-confirm-title">选择新的快照日期</div>
                  <div className="modal-confirm-sub">
                    改后预览中“今日资产预览 · X” 会立即更新，AI 解析结果不变，仅改提交日期。
                  </div>
                  <div className="date-edit-row">
                    <select
                      className="date-edit-select"
                      value={editYear}
                      onChange={(e) => setEditYear(parseInt(e.target.value, 10))}
                    >
                      {Array.from({ length: 5 }, (_, i) => 2023 + i).map((y) => (
                        <option key={y} value={y}>{y} 年</option>
                      ))}
                    </select>
                    <select
                      className="date-edit-select"
                      value={editMonth}
                      onChange={(e) => setEditMonth(parseInt(e.target.value, 10))}
                    >
                      {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                        <option key={m} value={m}>{m} 月</option>
                      ))}
                    </select>
                    <select
                      className="date-edit-select"
                      value={editDay}
                      onChange={(e) => setEditDay(parseInt(e.target.value, 10))}
                    >
                      {Array.from({ length: 31 }, (_, i) => i + 1).map((d) => (
                        <option key={d} value={d}>{d} 日</option>
                      ))}
                    </select>
                  </div>
                  <div className="modal-confirm-actions">
                    <button className="secondary-btn" onClick={cancelDateEdit}>取消</button>
                    <button className="primary-btn" onClick={applyDateEdit}>确定</button>
                  </div>
                </div>
              </div>
            )}
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

              {/* 各类小计 + 占比（1b4pr6b Fix B：使用 derivedCategories 实时联动 + dropdown/批量修改实时刷新） */}
              <div className="overview-detail">
                <h3>各类小计</h3>
                <table className="data-table" data-testid="subtotal-table">
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
                    {derivedCategories.map((c) => {
                      const isBalance = c.categoryName === '余额类'
                      const sixTotal = derivedCategories
                        .filter((x) => x.categoryName !== '余额类')
                        .reduce((s, x) => s + Number(x.categoryTotal || 0), 0)
                      const totalAll = derivedCategories
                        .reduce((s, x) => s + Number(x.categoryTotal || 0), 0)
                      const sixPct = isBalance
                        ? '—'
                        : (sixTotal > 0
                            ? (Number(c.categoryTotal || 0) / sixTotal * 100).toFixed(2) + '%'
                            : '0.00%')
                      const totalPct = totalAll > 0
                        ? (Number(c.categoryTotal || 0) / totalAll * 100).toFixed(2) + '%'
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

              {/* 决策 33 v2 · 预览 modal 表格（带 dropdown + 状态 + 操作 + D7 banner） */}
              <div className="overview-detail"
                style={{ marginTop: '16px' }}>
                {/* 1b4pr6b 批量确认：顶部 toggle 按钮（不进入批量模式时也可见，作为进入入口） */}
                <div className="batch-toolbar">
                  <h3 style={{ margin: 0 }}>基金明细（{parsedSummary.fundCount} 只）</h3>
                  <button
                    className={batchMode ? 'secondary-btn' : 'primary-btn'}
                    onClick={toggleBatchMode}
                    data-testid="batch-toggle-btn"
                  >
                    {batchMode ? '✓ 退出批量' : '📦 批量确认'}
                  </button>
                </div>

                {/* 批量模式顶部 bar：仅 batchMode=true 时可见，包含已选数 + 大类选择 + 提交按钮 */}
                {batchMode && (
                  <div className="batch-bar" data-testid="batch-bar">
                    <span className="batch-bar-label">已选 <strong>{selectedFunds.size}</strong> 只</span>
                    <label className="batch-bar-category">
                      目标大类：
                      <select
                        value={batchCategory}
                        onChange={(e) => setBatchCategory(e.target.value)}
                        data-testid="batch-category-select"
                      >
                        {FUND_CATEGORIES.filter((c) => c !== '余额类').map((cat) => (
                          <option key={cat} value={cat}>{cat}</option>
                        ))}
                      </select>
                    </label>
                    <button
                      className="primary-btn"
                      disabled={selectedFunds.size === 0 || batchConfirming}
                      onClick={() => setShowBatchConfirmModal(true)}
                      data-testid="batch-confirm-btn"
                    >
                      {batchConfirming ? '提交中…' : `✓ 批量确认 (${selectedFunds.size})`}
                    </button>
                  </div>
                )}

                {/* 1b4pr6b Fix C：表格 colgroup 条件渲染（5 列 vs 6 列与 th/td 数量一致） */}
                <table className="data-table">
                  <colgroup>
                    {batchMode && <col style={{ width: '40px' }} />}
                    <col style={{ width: 'auto' }} />
                    <col style={{ width: '160px' }} />
                    <col style={{ width: '100px' }} />
                    <col style={{ width: '100px' }} />
                    <col style={{ width: '120px' }} />
                  </colgroup>
                  <thead>
                    <tr>
                      {batchMode && <th></th>}
                      <th>基金名称</th>
                      <th>类别（dropdown）</th>
                      <th>金额（元）</th>
                      <th>状态</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {parsedSummary.categories.flatMap((c) => (c.funds || []).map((f) => {
                      const override = categoryOverrides[f.fundName]
                      const dirty = categoryDirty[f.fundName]
                      const dropdownVal = dirty || (override ? override.category : c.categoryName)
                      const isOverridden = !!override
                      const isDirty = !!dirty
                      const saving = !!overridesSaving[f.fundName]
                      const reConfirm = pendingReConfirms[f.fundName]
                      // Bug 2 fix：effectiveOriginal = override 优先，否则 AI 原猜 c.categoryName
                      const effectiveOriginal = override ? override.category : c.categoryName
                      // 批量模式：checkbox 三态（白方/红对勾/灰禁）
                      const isBatchConfirmed = batchMode && override && override.category === batchCategory
                      return (
                        <tr key={`${c.categoryName}-${f.fundName}`}
                            className={isOverridden ? 'fund-row-verified' : 'fund-row-pending'}>
                          {batchMode && (
                            <td className="batch-checkbox-cell">
                              {isBatchConfirmed ? (
                                <span
                                  className="batch-checkbox batch-checkbox--confirmed"
                                  title={`已确认 ${override.category} 归属`}
                                  data-testid={`batch-checkbox-confirmed-${f.fundName}`}
                                >✓</span>
                              ) : (
                                <label className="batch-checkbox-label">
                                  <input
                                    type="checkbox"
                                    checked={selectedFunds.has(f.fundName)}
                                    onChange={() => toggleSelectedFund(f.fundName)}
                                    data-testid={`batch-checkbox-${f.fundName}`}
                                    className="batch-checkbox-input"
                                  />
                                  {selectedFunds.has(f.fundName) && (
                                    <span className="batch-checkbox-tick" aria-hidden="true">✓</span>
                                  )}
                                </label>
                              )}
                            </td>
                          )}
                          <td>{f.fundName}</td>
                          <td>
                            {reConfirm && (
                              <div className="modal-warning-banner re-confirm" style={{ marginBottom: 4 }}>
                                ⚠ 上次确认 {reConfirm.lastSeenSnapshotDate}，可能于 {reConfirm.firstMissingSnapshotDate} 及之前清仓。本次确认后，日期会更新。
                              </div>
                            )}
                            <select
                              value={dropdownVal}
                              disabled={saving}
                              onChange={(e) => {
                                const v = e.target.value
                                // Bug 2 fix：比较 v 与 effectiveOriginal（不是 c.categoryName）
                                // 原因：如果用户之前已 override=A股权益类，现在 dropdown 显示 A股权益类
                                //   但 AI 原猜为商品类，c.categoryName 是商品类。
                                //   旧逻辑会把"未改动"误判成"回到商品类"，删除 dirty 是错的。
                                if (v === effectiveOriginal) {
                                  setCategoryDirty((d) => { const n = { ...d }; delete n[f.fundName]; return n })
                                } else {
                                  setCategoryDirty((d) => ({ ...d, [f.fundName]: v }))
                                }
                              }}
                              data-testid={`category-select-${f.fundName}`}
                            >
                              {FUND_CATEGORIES.map((cat) => (
                                <option key={cat} value={cat}>{cat}</option>
                              ))}
                            </select>
                          </td>
                          <td>¥{Number(f.amount || 0).toFixed(2)}</td>
                          <td>
                            {isOverridden ? (
                              <span className="badge badge-verified" data-testid="status-verified">✅ 已确认</span>
                            ) : (
                              <span className="badge badge-guess" data-testid="status-guess">🤖 ai_guess</span>
                            )}
                          </td>
                          <td>
                            {isOverridden ? (
                              <button className="link-btn" disabled={saving}
                                onClick={() => resetOverride(f.fundName)}
                                data-testid={`reset-btn-${f.fundName}`}>↺ 重置</button>
                            ) : (
                              <button className="primary-btn" disabled={!isDirty || saving}
                                onClick={() => confirmOverride(f.fundName, dirty)}
                                data-testid={`confirm-btn-${f.fundName}`}>
                                {saving ? '提交中…' : '✓ 确认'}
                              </button>
                            )}
                          </td>
                        </tr>
                      )
                    }))}
                  </tbody>
                </table>
              </div>

              <p style={{ fontSize: '12px', color: '#6b7280', marginTop: '16px' }}>
                提示：仅做资产概览。后续大类的精确确认（每个 asset_raw 行的目标比例/实际比例）会在 Phase 3 实施。
              </p>
            </div>
            <div className="modal-footer">
              <button className="secondary-btn" onClick={() => setShowConfirmModal(false)}>取消</button>
              {/* 决策 33 D1：严格阻塞（pendingCount > 0 时按钮 disabled）
                  + D2：dirtyCount > 0 弹二次确认 modal（handleConfirm 拦截） */}
              <button
                className="primary-btn"
                disabled={pendingCount > 0 || step === 'confirming'}
                title={pendingCount > 0 ? `还有 ${pendingCount} 条 AI 猜测未确认，请逐行核对` : ''}
                onClick={handleConfirm}
                data-testid="modal-confirm-btn"
              >
                {step === 'confirming' ? '入库中…' : '确认入库'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 1b.4-pr7 (DATA-016) Fix 3：入库成功后弹"设为当前吗?" prompt（蓝色默认改 current，白色取消保留） */}
      {setCurrentPrompt && (
        <div className="modal-confirm-backdrop" onClick={handleSetCurrentPromptCancel}>
          <div className="modal-confirm-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-confirm-title">
              ✅ 已入库 {setCurrentPrompt.fundCount} 只基金
            </div>
            <div className="modal-confirm-sub">
              是否将 <strong>{setCurrentPrompt.date}</strong> 设为当前显示日期？
            </div>
            <div className="modal-confirm-actions">
              <button
                className="secondary-btn"
                onClick={handleSetCurrentPromptCancel}
                data-testid="set-current-prompt-cancel"
              >取消</button>
              <button
                className="primary-btn"
                onClick={handleSetCurrentPromptConfirm}
                autoFocus
                data-testid="set-current-prompt-confirm"
              >改为当前日期</button>
            </div>
          </div>
        </div>
      )}

      {/* 1b4pr6b Fix A：batchToast 顶层（独立 stacking context，z-index 1300 超过所有 modal） */}
      {batchToast && (
        <div className="batch-toast-fixed" data-testid="batch-toast" role="alert">
          您已经确认 <strong>{batchToast.fundName}</strong> 的 <strong>{batchToast.category}</strong> 归属！
        </div>
      )}
    </div>
  )
}
