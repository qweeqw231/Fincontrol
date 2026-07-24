import { useState } from 'react'
import { useUserConfigStore } from '../../stores/userConfigStore.js'

/**
 * HistoryLimitDialog — PR3plus 决策 30/31：修改历史截图限制的 4 步 modal
 *
 * <p>UX 流程（用户需求 2026-07-24）：
 * <ol>
 *   <li>点击 [修改] → 弹 prompt 询问是否修改</li>
 *   <li>选择"取消" → 弹"OK 您可继续上传 X 天内" → 确认 → 关弹窗</li>
 *   <li>选择"是" → 弹选择框（7/14/30/180/-1，当前标注）</li>
 *   <li>选某项：
 *     <ul>
 *       <li>old == new → 弹"当前限制仍为 X" → 确认 → 回选择框</li>
 *       <li>old != new → 弹"确认将限制从 OLD 改为 NEW 吗" → 确认 → 关弹窗 + store 更新</li>
 *       <li>取消 → 回选择框（值不变）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * @param {Object} props
 * @param {boolean} props.open
 * @param {Function} props.onClose
 * @param {number} props.currentDays 当前限制（来自 store，-1/7/14/30/180）
 * @param {number} [props.userId=1]
 */
export default function HistoryLimitDialog({ open, onClose, currentDays, userId = 1 }) {
  const [step, setStep] = useState('prompt')  // prompt | confirm-no-change | select | confirm-change
  const [selectedDays, setSelectedDays] = useState(null)  // 选中的目标值
  const [saving, setSaving] = useState(false)
  const setMaxSnapshotAgeDays = useUserConfigStore((s) => s.setMaxSnapshotAgeDays)

  if (!open) return null

  const daysLabel = (d) => d === -1 ? '不限制' : `${d} 天`

  // 5 个可选项
  const OPTIONS = [
    { value: 7, label: '7 天' },
    { value: 14, label: '14 天' },
    { value: 30, label: '30 天' },
    { value: 180, label: '180 天' },
    { value: -1, label: '不限制' },
  ]

  // 重置到 prompt 步骤
  const resetToPrompt = () => {
    setStep('prompt')
    setSelectedDays(null)
  }

  // 选某项后决定走哪一步
  const handleSelect = (days) => {
    setSelectedDays(days)
    if (days === currentDays) {
      setStep('confirm-no-change')
    } else {
      setStep('confirm-change')
    }
  }

  // 确认保存
  const handleConfirmSave = async () => {
    if (selectedDays === null) return
    setSaving(true)
    try {
      await setMaxSnapshotAgeDays(userId, selectedDays)
      // 成功：关弹窗
      onClose()
      // 下次再开时回到 prompt
      setTimeout(() => resetToPrompt(), 0)
    } catch (e) {
      // 失败：留在原步，错误 message 由 store 设
      console.error('PR3plus save max age failed', e)
    } finally {
      setSaving(false)
    }
  }

  // 关闭弹窗
  const handleClose = () => {
    onClose()
    setTimeout(() => resetToPrompt(), 0)
  }

  return (
    <div className="modal-backdrop" onClick={handleClose} data-testid="history-limit-dialog">
      <div className="modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 480 }}>
        <div className="modal-header">
          <h2>📅 历史截图限制</h2>
          <button className="modal-close" onClick={handleClose} aria-label="关闭">×</button>
        </div>
        <div className="modal-body">
          {/* Step 1: prompt 询问是否修改 */}
          {step === 'prompt' && (
            <div>
              <p style={{ fontSize: 14, lineHeight: 1.6 }}>
                为了防止误传，目前限制上传与当前系统时间相差 <strong>{daysLabel(currentDays)}</strong> 及以上的截图。
              </p>
              <p style={{ fontSize: 14, lineHeight: 1.6 }}>是否修改限制？</p>
            </div>
          )}

          {/* Step 2a: 取消 → "OK 您可继续上传 X 天内" */}
          {step === 'confirm-no-change' && (
            <div>
              <p style={{ fontSize: 14, lineHeight: 1.6 }}>
                OK，您可以继续上传 <strong>{daysLabel(currentDays)}</strong> 以内的截图。
              </p>
            </div>
          )}

          {/* Step 3: 选择框 */}
          {step === 'select' && (
            <div>
              <p style={{ fontSize: 13, color: '#6b7280', marginBottom: 12 }}>
                选择新的历史限制（当前：{daysLabel(currentDays)}）
              </p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    onClick={() => handleSelect(opt.value)}
                    className="secondary-btn"
                    style={{ textAlign: 'left', padding: '10px 14px' }}
                    data-testid={`history-option-${opt.value}`}
                  >
                    {opt.label}
                    {opt.value === currentDays && (
                      <span style={{ marginLeft: 8, fontSize: 12, color: '#6b7280' }}>（当前）</span>
                    )}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Step 4a: 选了当前值 → "当前限制仍为 X" */}
          {step === 'confirm-no-change' && selectedDays !== null && selectedDays === currentDays && (
            <div>
              <p style={{ fontSize: 14, lineHeight: 1.6 }}>
                当前限制仍为 <strong>{daysLabel(currentDays)}</strong>。
              </p>
            </div>
          )}

          {/* Step 4b: 选了不同值 → "确认从 OLD 改为 NEW" */}
          {step === 'confirm-change' && selectedDays !== null && selectedDays !== currentDays && (
            <div>
              <p style={{ fontSize: 14, lineHeight: 1.6 }}>
                确认将上传截图历史限制从最近 <strong>{daysLabel(currentDays)}</strong> 改为 <strong>{daysLabel(selectedDays)}</strong> 吗？
              </p>
            </div>
          )}
        </div>
        <div className="modal-footer">
          {step === 'prompt' && (
            <>
              <button
                className="secondary-btn"
                onClick={() => setStep('confirm-no-change')}
                data-testid="history-prompt-cancel"
              >
                取消
              </button>
              <button
                className="primary-btn"
                onClick={() => setStep('select')}
                data-testid="history-prompt-yes"
              >
                是
              </button>
            </>
          )}

          {step === 'confirm-no-change' && (
            <button
              className="primary-btn"
              onClick={handleClose}
              data-testid="history-ok"
            >
              确认
            </button>
          )}

          {step === 'select' && (
            <button
              className="secondary-btn"
              onClick={() => setStep('prompt')}
              data-testid="history-select-back"
            >
              返回
            </button>
          )}

          {step === 'confirm-change' && (
            <>
              <button
                className="secondary-btn"
                onClick={() => setStep('select')}
                disabled={saving}
                data-testid="history-confirm-back"
              >
                取消
              </button>
              <button
                className="primary-btn"
                onClick={handleConfirmSave}
                disabled={saving}
                data-testid="history-confirm-save"
              >
                {saving ? '保存中…' : '确认'}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
