'use client';

import { useState } from 'react';
import { dependencyData, nodePositions, getNodeColor, getStatusDescription } from './chart-utils';

export default function DependencyNetwork() {
  const [hoveredNode, setHoveredNode] = useState<string | null>(null);

  // Create SVG-based dependency network
  const renderDependencyNetwork = () => {
    const width = 800;
    const height = 400;

    const { nodes, edges, links } = dependencyData();
    
    return (
      <div className="w-full h-96 bg-slate-800 rounded-lg flex items-center justify-center">
        <svg width={width} height={height} className="overflow-visible">
          {/* Render connection lines */}
          {links.map((link, index) => {
            const sourcePos = nodePositions()[link.source];
            const targetPos = nodePositions()[link.target];
            
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
          {nodes.map((node) => {
            const pos = nodePositions()[node.id];
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
                  className="cursor-pointer transition-all duration-200 hover:scale-110"
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

          {/* Render edges */}
          {edges.map((edge) => {
            const pos = nodePositions()[edge.id];
            if (!pos) return null;
            
            const x = (pos.x / 100) * width;
            const y = (pos.y / 100) * height;
            const radius = 35;
            
            return (
              <g key={edge.id}>
                {/* Node circle */}
                <circle
                  cx={x}
                  cy={y}
                  r={radius}
                  fill={getNodeColor(edge.status)}
                  stroke="#ffffff"
                  strokeWidth="3"
                  className="cursor-pointer transition-all duration-200 hover:scale-110"
                  onMouseEnter={() => setHoveredNode(edge.id)}
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
                  {edge.name}
                </text>
                
                {/* Hover tooltip */}
                {hoveredNode === edge.id && (
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
                      📦 {edge.name}
                    </text>
                    <text
                      x={x}
                      y={y - 35}
                      textAnchor="middle"
                      fill="#cccccc"
                      fontSize="10"
                      className="pointer-events-none"
                    >
                      {getStatusDescription(edge.status)}
                    </text>
                  </g>
                )}
              </g>
            );
          })}
        </svg>
      </div>
    );
  };

  return (
    <div className="w-full">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-semibold text-white">dependency nav</h3>
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
      
      <div className="mt-4">
        <div className="text-center">
          <h4 className="text-md font-medium text-white mb-2">Package Details</h4>
          <p className="text-sm text-gray-400">Hover over a package node to see details</p>
        </div>
      </div>
    </div>
  );
}
