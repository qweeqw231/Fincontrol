import React from 'react'

/**
 * ErrorBoundary — 顶层错误边界（1b.2 调试用）
 * <p>包住 App，捕获任意子组件渲染错误并在页面显示红字 + 堆栈。
 * <p>这样不用开 DevTools 就能看到根因（一片空白 = 子组件崩了）。
 */
export class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { error: null, info: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    this.setState({ error, info })
    // 同时输出到 console 方便 DevTools
    console.error('[ErrorBoundary] caught:', error, info)
  }

  render() {
    if (!this.state.error) return this.props.children
    const { error, info } = this.state
    return (
      <div
        style={{
          padding: '24px',
          margin: '24px',
          background: '#fff5f5',
          border: '2px solid #e53e3e',
          borderRadius: '8px',
          fontFamily: 'monospace',
          fontSize: '14px',
          color: '#c53030',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        <h1 style={{ color: '#c53030', margin: '0 0 16px' }}>
          ❌ 前端组件渲染错误
        </h1>
        <p style={{ fontWeight: 'bold', color: '#742a2a' }}>
          请把下面整段截图发给 Cline 即可定位。
        </p>
        <hr style={{ borderColor: '#feb2b2' }} />
        <p style={{ color: '#742a2a' }}>
          <strong>Error message:</strong>
        </p>
        <pre style={{ background: '#fed7d7', padding: '12px', borderRadius: '4px' }}>
          {String(error?.message || error)}
        </pre>
        {error?.stack && (
          <>
            <p style={{ color: '#742a2a' }}>
              <strong>Stack trace:</strong>
            </p>
            <pre
              style={{
                background: '#fed7d7',
                padding: '12px',
                borderRadius: '4px',
                fontSize: '12px',
                maxHeight: '300px',
                overflow: 'auto',
              }}
            >
              {error.stack}
            </pre>
          </>
        )}
        {info?.componentStack && (
          <>
            <p style={{ color: '#742a2a' }}>
              <strong>Component stack:</strong>
            </p>
            <pre
              style={{
                background: '#fed7d7',
                padding: '12px',
                borderRadius: '4px',
                fontSize: '12px',
                maxHeight: '200px',
                overflow: 'auto',
              }}
            >
              {info.componentStack}
            </pre>
          </>
        )}
      </div>
    )
  }
}
