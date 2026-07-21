import { describe, it, expect, beforeEach } from 'vitest'
import { useAssetSnapshotStore } from '../../stores/assetSnapshotStore.js'

describe('assetSnapshotStore', () => {
  beforeEach(() => {
    useAssetSnapshotStore.getState().reset()
  })

  it('初始 state 正确', () => {
    const state = useAssetSnapshotStore.getState()
    expect(state.latestSnapshot).toBeNull()
    expect(state.balance).toBeNull()
    expect(state.operationsRecent).toEqual([])
    expect(state.loading).toBe(false)
    expect(state.error).toBeNull()
  })

  it('setLatest / setBalance / setOperations 能更新 state', () => {
    const snap = { snapshotDate: '2026-07-21', totalAsset: 7884.68 }
    const bal  = { balanceFundTotal: 320.85 }
    const ops  = [{ operationType: 'screenshot_parse', operationDate: '2026-07-21T20:30:00' }]

    const store = useAssetSnapshotStore.getState()
    store.setLatest(snap)
    store.setBalance(bal)
    store.setOperations(ops)

    const s = useAssetSnapshotStore.getState()
    expect(s.latestSnapshot).toEqual(snap)
    expect(s.balance).toEqual(bal)
    expect(s.operationsRecent).toEqual(ops)
  })

  it('reset() 能重置全部 state', () => {
    const store = useAssetSnapshotStore.getState()
    store.setLatest({ x: 1 })
    store.setBalance({ y: 2 })
    store.setLoading(true)
    store.setError('err')
    store.reset()

    const s = useAssetSnapshotStore.getState()
    expect(s.latestSnapshot).toBeNull()
    expect(s.balance).toBeNull()
    expect(s.operationsRecent).toEqual([])
    expect(s.loading).toBe(false)
    expect(s.error).toBeNull()
  })
})