import { useState, useEffect } from '@lynx-js/react'

interface Node {
  id: string
  x: number
  y: number
  z: number
  label: string
}

interface Link {
  source: string
  target: string
}

export default function NetworkGraph() {
  const [selectedNode, setSelectedNode] = useState<string | null>(null)
  const [rotation, setRotation] = useState({ x: 0, y: 0, z: 0 })
  const [isDragging, setIsDragging] = useState(false)
  const [lastPos, setLastPos] = useState({ x: 0, y: 0 })

  // Generate static nodes positioned in a 3D sphere
  const generateStaticNodes = (): Node[] => {
    const nodes: Node[] = []
    const numNodes = 20
    const radius = 150

    for (let i = 0; i < numNodes; i++) {
      // Use sphere distribution for more even spacing
      const phi = Math.acos(1 - 2 * (i + 0.5) / numNodes) // inclination
      const theta = Math.PI * (1 + Math.sqrt(5)) * i // azimuth (golden angle)
      
      const x = radius * Math.sin(phi) * Math.cos(theta)
      const y = radius * Math.sin(phi) * Math.sin(theta)
      const z = radius * Math.cos(phi)

      nodes.push({
        id: `node-${i}`,
        x,
        y,
        z,
        label: `Node ${i + 1}`
      })
    }
    return nodes
  }

  // Generate random links between nodes
  const generateLinks = (nodes: Node[]): Link[] => {
    const links: Link[] = []
    const numLinks = Math.min(25, nodes.length * 2)
    
    for (let i = 0; i < numLinks; i++) {
      const source = nodes[Math.floor(Math.random() * nodes.length)].id
      const target = nodes[Math.floor(Math.random() * nodes.length)].id
      
      if (source !== target && !links.some(l => 
        (l.source === source && l.target === target) ||
        (l.source === target && l.target === source)
      )) {
        links.push({ source, target })
      }
    }
    return links
  }

  const [nodes] = useState<Node[]>(() => generateStaticNodes())
  const [links] = useState<Link[]>(() => generateLinks(nodes))

  // Safe event handler wrapper
  const safePreventDefault = (e: any) => {
    if (e && typeof e.preventDefault === 'function') {
      try {
        e.preventDefault()
      } catch (err) {
        // Ignore if preventDefault fails
      }
    }
  }

  const safeStopPropagation = (e: any) => {
    if (e && typeof e.stopPropagation === 'function') {
      try {
        e.stopPropagation()
      } catch (err) {
        // Ignore if stopPropagation fails
      }
    }
  }

  // Manual rotation controls with simplified touch/mouse handling
  const handleStart = (e: any) => {
    safePreventDefault(e)
    setIsDragging(true)
    const clientX = e?.touches?.[0]?.clientX ?? e?.clientX ?? 0
    const clientY = e?.touches?.[0]?.clientY ?? e?.clientY ?? 0
    setLastPos({ x: clientX, y: clientY })
  }

  const handleMove = (e: any) => {
    if (!isDragging) return
    safePreventDefault(e)
    
    const clientX = e?.touches?.[0]?.clientX ?? e?.clientX ?? 0
    const clientY = e?.touches?.[0]?.clientY ?? e?.clientY ?? 0
    
    const deltaX = clientX - lastPos.x
    const deltaY = clientY - lastPos.y
    
    setRotation(prev => ({
      x: prev.x + deltaY * 0.5,
      y: prev.y + deltaX * 0.5,
      z: prev.z
    }))
    
    setLastPos({ x: clientX, y: clientY })
  }

  const handleEnd = () => {
    setIsDragging(false)
  }

  // Keyboard controls for Z-axis rotation and reset
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      switch(e.key.toLowerCase()) {
        case 'q':
          setRotation(prev => ({ ...prev, z: prev.z - 10 }))
          break
        case 'e':
          setRotation(prev => ({ ...prev, z: prev.z + 10 }))
          break
        case 'r':
          setRotation({ x: 0, y: 0, z: 0 })
          break
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [])

  // Project 3D coordinates to 2D with rotation
  const project3D = (x: number, y: number, z: number) => {
    const perspective = 600
    const rotX = rotation.x * Math.PI / 180
    const rotY = rotation.y * Math.PI / 180
    const rotZ = rotation.z * Math.PI / 180
    
    // Apply rotations
    const cosX = Math.cos(rotX), sinX = Math.sin(rotX)
    const cosY = Math.cos(rotY), sinY = Math.sin(rotY)
    const cosZ = Math.cos(rotZ), sinZ = Math.sin(rotZ)
    
    // Rotate around Z axis first
    const x1 = x * cosZ - y * sinZ
    const y1 = x * sinZ + y * cosZ
    
    // Rotate around X axis
    const y2 = y1 * cosX - z * sinX
    const z2 = y1 * sinX + z * cosX
    
    // Rotate around Y axis
    const x3 = x1 * cosY + z2 * sinY
    const z3 = -x1 * sinY + z2 * cosY
    
    // Project to 2D
    const scale = perspective / (perspective + z3)
    return {
      x: x3 * scale + 400, // Center X
      y: y2 * scale + 300, // Center Y
      scale: scale
    }
  }

  // Node interaction handlers
  const handleNodeClick = (node: Node, e?: any) => {
    if (e) {
      safePreventDefault(e)
      safeStopPropagation(e)
    }
    setSelectedNode(selectedNode === node.id ? null : node.id)
    console.log('Clicked node', node.id)
  }

  // Get connected node IDs for highlighting
  const getConnectedNodeIds = (nodeId: string): string[] => {
    const connected: string[] = []
    links.forEach(link => {
      if (link.source === nodeId) connected.push(link.target)
      if (link.target === nodeId) connected.push(link.source)
    })
    return connected
  }

  const isNodeHighlighted = (nodeId: string): boolean => {
    if (!selectedNode) return false
    if (selectedNode === nodeId) return true
    return getConnectedNodeIds(selectedNode).includes(nodeId)
  }

  const isLinkHighlighted = (link: Link): boolean => {
    if (!selectedNode) return false
    return link.source === selectedNode || link.target === selectedNode
  }

  // Get node color based on state
  const getNodeColor = (node: Node): string => {
    if (selectedNode === node.id) return '#00ff88'
    if (isNodeHighlighted(node.id)) return '#66aaff'
    return '#ff4444'
  }

  // Get node size based on state
  const getNodeSize = (node: Node): number => {
    if (selectedNode === node.id) return 16
    if (isNodeHighlighted(node.id)) return 14
    return 12
  }

  return (
    <view style={{
      width: '800px',
      height: '600px',
      position: 'relative',
      border: '2px solid #333',
      borderRadius: '12px',
      backgroundColor: '#111111',
      marginBottom: '20px',
      opacity: 1,
      transition: 'opacity 0.5s ease-in-out',
      perspective: '1000px',
      transformStyle: 'preserve-3d',
      overflow: 'hidden',
      cursor: isDragging ? 'grabbing' : 'grab'
    }}
    bindtouchstart={handleStart}
    bindtouchmove={handleMove}
    bindtouchend={handleEnd}
    >
      {/* Links rendered as CSS elements */}
      {links.map((link, index) => {
        const sourceNode = nodes.find(n => n.id === link.source)
        const targetNode = nodes.find(n => n.id === link.target)
        
        if (!sourceNode || !targetNode) return null
        
        const sourceProj = project3D(sourceNode.x, sourceNode.y, sourceNode.z)
        const targetProj = project3D(targetNode.x, targetNode.y, targetNode.z)
        
        const length = Math.sqrt(
          Math.pow(targetProj.x - sourceProj.x, 2) + 
          Math.pow(targetProj.y - sourceProj.y, 2)
        )
        const angle = Math.atan2(
          targetProj.y - sourceProj.y, 
          targetProj.x - sourceProj.x
        ) * 180 / Math.PI
        
        return (
          <view
            key={`link-${index}`}
            style={{
              position: 'absolute',
              left: `${sourceProj.x}px`,
              top: `${sourceProj.y - 0.5}px`,
              width: `${length}px`,
              height: `${isLinkHighlighted(link) ? 2 : 1}px`,
              backgroundColor: isLinkHighlighted(link) ? '#00ff88' : '#333333',
              transformOrigin: '0 50%',
              transform: `rotate(${angle}deg)`,
              opacity: 0.6,
              zIndex: 1,
              pointerEvents: 'none'
            }}
          />
        )
      })}

      {/* Render nodes */}
      {nodes.map((node) => {
        const projected = project3D(node.x, node.y, node.z)
        const size = getNodeSize(node)
        const color = getNodeColor(node)
        
        return (
          <view
            key={node.id}
            style={{
              position: 'absolute',
              left: `${projected.x - size / 2}px`,
              top: `${projected.y - size / 2}px`,
              width: `${size}px`,
              height: `${size}px`,
              borderRadius: '50%',
              background: `radial-gradient(circle at 30% 30%, ${color}ff, ${color}aa, ${color}66)`,
              border: selectedNode === node.id ? '2px solid #00ff88' : '1px solid #666',
              cursor: 'pointer',
              zIndex: Math.floor(projected.scale * 100) + 10,
              boxShadow: selectedNode === node.id 
                ? `0 0 20px ${color}88` 
                : `0 0 10px ${color}44`,
              transform: `scale(${Math.max(0.5, projected.scale)})`,
              transition: 'all 0.2s ease',
              pointerEvents: 'auto'
            }}
            bindtouchstart={(e: any) => {
              safePreventDefault(e)
              safeStopPropagation(e)
              handleNodeClick(node, e)
            }}
          />
        )
      })}

      {/* Controls info */}
      <view style={{
        position: 'absolute',
        top: '10px',
        right: '10px',
        color: '#cccccc',
        fontSize: '11px',
        zIndex: 1000,
        background: 'rgba(0,0,0,0.5)',
        padding: '8px',
        borderRadius: '6px'
      }}>
        <text style={{ display: 'block', marginBottom: '3px' }}>🖱️ Drag to rotate X/Y</text>
        <text style={{ display: 'block', marginBottom: '3px' }}>Q/E: Rotate Z-axis</text>
        <text style={{ display: 'block' }}>R: Reset rotation</text>
      </view>

      {/* Info display */}
      <view style={{
        position: 'absolute',
        bottom: '10px',
        left: '10px',
        color: '#cccccc',
        fontSize: '12px',
        zIndex: 1000
      }}>
        <text>Manual 3D Navigation • {nodes.length} nodes • {links.length} links</text>
        <text style={{ display: 'block', marginTop: '3px', fontSize: '10px' }}>
          Rotation: X:{Math.round(rotation.x)}° Y:{Math.round(rotation.y)}° Z:{Math.round(rotation.z)}°
        </text>
        {selectedNode && (
          <text style={{ display: 'block', marginTop: '5px', color: '#00ff88' }}>
            Selected: {nodes.find(n => n.id === selectedNode)?.label}
          </text>
        )}
      </view>
    </view>
  )
}
