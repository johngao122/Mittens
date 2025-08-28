'use client';

import { useState, useRef } from 'react';
import { dependencyData, nodePositions, getNodeColor, getStatusDescription } from './chart-utils';

export default function DependencyNetwork() {
  const [hoveredNode, setHoveredNode] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const svgRef = useRef<SVGSVGElement>(null);

  // Handle zoom
  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const delta = e.deltaY * -0.001;
    const newZoom = Math.min(Math.max(0.5, zoom + delta), 3);
    setZoom(newZoom);
  };

  // Handle pan start
  const handleMouseDown = (e: React.MouseEvent) => {
    setIsDragging(true);
    setDragStart({ x: e.clientX - pan.x, y: e.clientY - pan.y });
  };

  // Handle pan move
  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return;
    setPan({
      x: e.clientX - dragStart.x,
      y: e.clientY - dragStart.y
    });
  };

  // Handle pan end
  const handleMouseUp = () => {
    setIsDragging(false);
  };

  // Reset zoom and pan
  const resetView = () => {
    setZoom(1);
    setPan({ x: 0, y: 0 });
  };

  // Create SVG-based dependency network
  const renderDependencyNetwork = () => {
    const width = 800;
    const height = 400;
    
    return (
      <div className="w-full h-96 bg-slate-800 rounded-lg flex items-center justify-center relative">
        {/* Zoom controls */}
        <div className="absolute top-4 right-4 z-10 flex flex-col gap-2">
          <button
            onClick={() => setZoom(Math.min(3, zoom * 1.2))}
            className="px-3 py-1 bg-slate-700 text-white rounded hover:bg-slate-600 text-sm"
          >
            +
          </button>
          <button
            onClick={() => setZoom(Math.max(0.5, zoom / 1.2))}
            className="px-3 py-1 bg-slate-700 text-white rounded hover:bg-slate-600 text-sm"
          >
            -
          </button>
          <button
            onClick={resetView}
            className="px-2 py-1 bg-slate-700 text-white rounded hover:bg-slate-600 text-xs"
          >
            Reset
          </button>
        </div>
        
        <svg 
          ref={svgRef}
          width={width} 
          height={height} 
          className="overflow-hidden cursor-grab"
          onWheel={handleWheel}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
          style={{ cursor: isDragging ? 'grabbing' : 'grab' }}
        >
          <g transform={`translate(${pan.x}, ${pan.y}) scale(${zoom})`}>
          {/* Render connection lines */}
          {dependencyData.links.map((link, index) => {
            const sourcePos = nodePositions[link.source];
            const targetPos = nodePositions[link.target];
            
            if (!sourcePos || !targetPos) return null;
            
            const x1 = (sourcePos.x / 100) * width;
            const y1 = (sourcePos.y / 100) * height;
            const x2 = (targetPos.x / 100) * width;
            const y2 = (targetPos.y / 100) * height;
            
            return (
              <line
                key={`link-${index}`}
                x1={x1}
                y1={y1}
                x2={x2}
                y2={y2}
                stroke="#ffffff"
                strokeWidth="2"
                strokeOpacity="0.6"
                strokeDasharray={link.type === 'dashed' ? '5,5' : '0'}
              />
            );
          })}
          
          {/* Render nodes */}
          {dependencyData.nodes.map((node) => {
            const pos = nodePositions[node.id];
            if (!pos) return null;
            
            const x = (pos.x / 100) * width;
            const y = (pos.y / 100) * height;
            const radius = 35;
            
            return (
              <g key={node.id}>
                {/* Node circle */}
                <circle
                  cx={x}
                  cy={y}
                  r={radius}
                  fill={getNodeColor(node.status)}
                  stroke="#ffffff"
                  strokeWidth="3"
                  className="cursor-pointer"
                  onMouseEnter={() => setHoveredNode(node.id)}
                  onMouseLeave={() => setHoveredNode(null)}
                />
                
                {/* Node label */}
                <text
                  x={x}
                  y={y}
                  textAnchor="middle"
                  dominantBaseline="middle"
                  fill="#ffffff"
                  fontSize="12"
                  fontWeight="bold"
                  className="pointer-events-none select-none"
                  style={{ textShadow: '2px 2px 4px rgba(0,0,0,0.9)' }}
                >
                  {node.name}
                </text>
                
                {/* Hover tooltip */}
                {hoveredNode === node.id && (
                  <g>
                    <rect
                      x={x - 60}
                      y={y - 60}
                      width="120"
                      height="40"
                      fill="rgba(0, 0, 0, 0.9)"
                      stroke="#ffffff"
                      strokeWidth="1"
                      rx="4"
                      className="pointer-events-none"
                    />
                    <text
                      x={x}
                      y={y - 50}
                      textAnchor="middle"
                      fill="#ffffff"
                      fontSize="11"
                      fontWeight="bold"
                      className="pointer-events-none"
                    >
                      📦 {node.name}
                    </text>
                    <text
                      x={x}
                      y={y - 35}
                      textAnchor="middle"
                      fill="#cccccc"
                      fontSize="10"
                      className="pointer-events-none"
                    >
                      {getStatusDescription(node.status)}
                    </text>
                  </g>
                )}
              </g>
            );
          })}
          </g>
        </svg>
      </div>
    );
  };

  return (
    <div className="w-full">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-semibold text-white">Dependency Network</h3>
        <div className="flex gap-2">
          <div className="flex items-center gap-1">
            <div className="h-3 w-3 rounded-full bg-green-500"></div>
            <span className="text-sm text-gray-300">All</span>
          </div>
          <div className="flex items-center gap-1">
            <div className="h-3 w-3 rounded-full bg-red-500"></div>
            <span className="text-sm text-gray-300">Vulnerable</span>
          </div>
          <div className="flex items-center gap-1">
            <div className="h-3 w-3 rounded-full bg-orange-500"></div>
            <span className="text-sm text-gray-300">Deprecated</span>
          </div>
          <div className="flex items-center gap-1">
            <div className="h-3 w-3 rounded-full bg-yellow-500"></div>
            <span className="text-sm text-gray-300">Conflict</span>
          </div>
        </div>
      </div>
      
      {renderDependencyNetwork()}
      
      {/* Package Inventory Status Bar */}
      <div className="mt-6 bg-slate-700 rounded-lg p-4">
        <h4 className="text-lg font-semibold text-white mb-4">Package Inventory</h4>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-600">
                <th className="text-left py-3 px-2 text-gray-400 font-medium">PACKAGE</th>
                <th className="text-left py-3 px-2 text-gray-400 font-medium">VERSION</th>
                <th className="text-left py-3 px-2 text-gray-400 font-medium">STATUS</th>
                <th className="text-left py-3 px-2 text-gray-400 font-medium">SIZE</th>
                <th className="text-left py-3 px-2 text-gray-400 font-medium">RISK</th>
                <th className="text-left py-3 px-2 text-gray-400 font-medium">CONNECTIONS</th>
              </tr>
            </thead>
            <tbody>
              {dependencyData.nodes.map((node, index) => {
                const connectionCount = dependencyData.links.filter(
                  link => link.source === node.id || link.target === node.id
                ).length;
                
                // Mock data for demo - in real app this would come from actual package analysis
                const packageInfo = {
                  'MyApp': { version: '1.0.0', size: '2.1MB', risk: 'low' },
                  'React': { version: '18.2.0', size: '42.2KB', risk: 'low' },
                  'Webpack': { version: '5.89.0', size: '1.2MB', risk: 'low' },
                  'TypeScript': { version: '5.2.2', size: '34.1MB', risk: 'low' },
                  'Lodash': { version: '4.17.15', size: '528KB', risk: 'high' },
                  'ESLint': { version: '8.49.0', size: '2.8MB', risk: 'medium' }
                };
                
                const info = packageInfo[node.id as keyof typeof packageInfo];
                
                const statusIcon = {
                  'normal': '✓',
                  'vulnerable': '✗',
                  'deprecated': '⚠',
                  'conflict': '⚠'
                }[node.status];
                
                const statusColor = {
                  'normal': 'text-green-400',
                  'vulnerable': 'text-red-400',
                  'deprecated': 'text-orange-400',
                  'conflict': 'text-yellow-400'
                }[node.status];
                
                const riskColor = {
                  'low': 'text-green-400 bg-green-400/20',
                  'medium': 'text-yellow-400 bg-yellow-400/20',
                  'high': 'text-red-400 bg-red-400/20'
                }[info?.risk || 'low'];
                
                return (
                  <tr key={node.id} className="border-b border-slate-600/50 hover:bg-slate-600/30">
                    <td className="py-3 px-2">
                      <div className="flex items-center gap-2">
                        <span className={`${statusColor} font-medium`}>{statusIcon}</span>
                        <span className="text-white font-medium">{node.name}</span>
                      </div>
                    </td>
                    <td className="py-3 px-2 text-gray-300">{info?.version || 'N/A'}</td>
                    <td className="py-3 px-2">
                      <span className={`px-2 py-1 rounded text-xs font-medium ${
                        node.status === 'normal' ? 'text-green-400 bg-green-400/20' :
                        node.status === 'vulnerable' ? 'text-red-400 bg-red-400/20' :
                        node.status === 'deprecated' ? 'text-orange-400 bg-orange-400/20' :
                        'text-yellow-400 bg-yellow-400/20'
                      }`}>
                        {node.status === 'normal' ? 'healthy' : node.status}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-gray-300">{info?.size || 'N/A'}</td>
                    <td className="py-3 px-2">
                      <span className={`px-2 py-1 rounded text-xs font-medium ${riskColor}`}>
                        {info?.risk || 'low'}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-gray-300">{connectionCount}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
      
      <div className="mt-4">
        <div className="text-center">
          <h4 className="text-md font-medium text-white mb-2">Package Details</h4>
          <p className="text-sm text-gray-400">Hover over a package node to see details</p>
        </div>
      </div>
    </div>
  );
}
