import { useEffect } from '@lynx-js/react'
import NetworkGraph from './components/NetworkGraph.tsx'

import './App.css'

export function App(props: { onRender?: () => void }) {
  // Call onRender once after mount
  useEffect(() => {
    console.info('Hello, ReactLynx - Network Graph Demo')
    props.onRender?.()
  }, [])

  return (
    <view style={{ 
      minHeight: '100vh', 
      background: 'linear-gradient(135deg, #0a0a23 0%, #1a1a3a 50%, #2a1a4a 100%)',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '20px'
    }}>
      {/* Header */}
      <view style={{
        marginBottom: '20px',
        textAlign: 'center'
      }}>
        <text style={{
          fontSize: '28px',
          fontWeight: 'bold',
          color: '#ffffff',
          marginBottom: '8px',
          textShadow: '0 2px 4px rgba(0,0,0,0.5)'
        }}>
          Interactive Network Graph
        </text>
        <text style={{
          fontSize: '14px',
          color: 'rgba(255,255,255,0.7)',
          fontStyle: 'italic'
        }}>
          Click nodes to select • Touch and drag to move • Explore connections
        </text>
      </view>

      {/* Graph Container */}
      <view style={{
        background: 'rgba(0,0,0,0.3)',
        borderRadius: '12px',
        padding: '20px',
        boxShadow: '0 8px 32px rgba(0,0,0,0.3)',
        border: '1px solid rgba(255,255,255,0.1)',
        backdropFilter: 'blur(10px)'
      }}>
        <NetworkGraph />
      </view>

      {/* Footer info */}
      <view style={{
        marginTop: '20px',
        textAlign: 'center'
      }}>
        <text style={{
          fontSize: '12px',
          color: 'rgba(255,255,255,0.5)'
        }}>
          Built with Lynx + D3 Force Simulation
        </text>
      </view>
    </view>
  )
}
