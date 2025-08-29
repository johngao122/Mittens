'use client';

import { useEffect, useRef, useState } from 'react';
import { getD3NetworkData } from './chart-utils';
import D3Network from './d3-network';
import { D3Node } from '../../../../lib/knit-data-parser';

export default function DependencyNetwork() {
  const [selectedNode, setSelectedNode] = useState<D3Node | null>(null);
  const detailsRef = useRef<HTMLDivElement | null>(null);
  const [detailsHeight, setDetailsHeight] = useState<number | null>(null);

  const networkData = getD3NetworkData();

  const handleNodeClick = (node: D3Node) => {
    setSelectedNode(node);
  };

  // Keep the graph container height matched to the details panel when visible
  useEffect(() => {
    if (!detailsRef.current) {
      setDetailsHeight(null);
      return;
    }
    const el = detailsRef.current;
    const update = () => setDetailsHeight(Math.max(0, Math.floor(el.getBoundingClientRect().height)));
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    window.addEventListener('resize', update);
    return () => {
      ro.disconnect();
      window.removeEventListener('resize', update);
    };
  }, [selectedNode]);

  // Helper function to get status color for the inventory table
  const getStatusColor = (node: D3Node) => {
    if (node.errorInfo.isPartOfCycle) return 'text-purple-400 bg-purple-400/20';
    if (node.errorInfo.hasErrors) {
      switch (node.errorInfo.errorSeverity) {
        case "ERROR": return 'text-red-400 bg-red-400/20';
        case "WARNING": return 'text-yellow-400 bg-yellow-400/20';
        default: return 'text-green-400 bg-green-400/20';
      }
    }
    return 'text-green-400 bg-green-400/20';
  };

  // Helper function to get status icon
  const getStatusIcon = (node: D3Node) => {
    if (node.errorInfo.isPartOfCycle) return '🔄';
    if (node.errorInfo.hasErrors) {
      switch (node.errorInfo.errorSeverity) {
        case "ERROR": return '✗';
        case "WARNING": return '⚠';
        default: return '✓';
      }
    }
    return '✓';
  };

  // Helper function to get status text
  const getStatusText = (node: D3Node) => {
    if (node.errorInfo.isPartOfCycle) return 'cycle';
    if (node.errorInfo.hasErrors) {
      switch (node.errorInfo.errorSeverity) {
        case "ERROR": return 'error';
        case "WARNING": return 'warning';
        default: return 'healthy';
      }
    }
    return 'healthy';
  };

  // Calculate connection count for each node
  const getConnectionCount = (nodeId: string) => {
    return networkData.links.filter(
      link => 
        (typeof link.source === 'string' ? link.source : link.source.id) === nodeId ||
        (typeof link.target === 'string' ? link.target : link.target.id) === nodeId
    ).length;
  };

  return (
    <div className="w-full">
      <div className="mb-4 flex items-center justify-between">
        <div className="flex gap-4 items-center justify-end ml-auto">
          <div className="flex items-center gap-1">
            <div className="h-3 w-3 rounded-full bg-green-500"></div>
            <span className="text-sm text-gray-300">Healthy</span>
          </div>
          <div className="flex items-center gap-1">
            <div className="h-3 w-3 rounded-full bg-red-500"></div>
            <span className="text-sm text-gray-300">Error</span>
          </div>
          <div className="flex items-center gap-1">
            <div className="h-3 w-3 rounded-full bg-yellow-500"></div>
            <span className="text-sm text-gray-300">Warning</span>
          </div>
          <div className="flex items-center gap-1">
            <div className="h-3 w-3 rounded-full bg-purple-500"></div>
            <span className="text-sm text-gray-300">Cycle</span>
          </div>
        </div>
      </div>
      {/* Summary Statistics (moved to top) */}
      <div className="mb-4 grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-slate-700 rounded-lg p-4 text-center">
          <div className="text-2xl font-bold text-white">{networkData.nodes.length}</div>
          <div className="text-sm text-gray-400">Components</div>
        </div>
        <div className="bg-slate-700 rounded-lg p-4 text-center">
          <div className="text-2xl font-bold text-white">{networkData.links.length}</div>
          <div className="text-sm text-gray-400">Dependencies</div>
        </div>
        <div className="bg-slate-700 rounded-lg p-4 text-center">
          <div className="text-2xl font-bold text-red-400">
            {networkData.nodes.filter(n => n.errorInfo.hasErrors && n.errorInfo.errorSeverity === 'ERROR').length}
          </div>
          <div className="text-sm text-gray-400">Errors</div>
        </div>
        <div className="bg-slate-700 rounded-lg p-4 text-center">
          <div className="text-2xl font-bold text-purple-400">
            {networkData.nodes.filter(n => n.errorInfo.isPartOfCycle).length}
          </div>
          <div className="text-sm text-gray-400">Cycles</div>
        </div>
      </div>
      
      {/* Graph + Details side-by-side */}
      <div className="mb-6 flex flex-col lg:flex-row gap-4 items-start">
        <div
          className="flex-1 bg-slate-800 rounded-lg p-0"
          style={{ height: selectedNode && detailsHeight ? detailsHeight : undefined, minHeight: selectedNode ? 520 : 600 }}
        >
          <D3Network 
            data={networkData}
            width="100%"
            height="100%"
            onNodeClick={handleNodeClick}
          />
        </div>
        {selectedNode && (
          <div ref={detailsRef} className="lg:w-96 bg-slate-700 rounded-lg p-4 self-start">
            <h4 className="text-lg font-semibold text-white mb-2">Selected Component Details</h4>
            <div className="grid grid-cols-1 gap-4">
              <div>
                <p className="text-sm text-gray-400">Component Name</p>
                <p className="text-white font-medium">{selectedNode.label}</p>
              </div>
              <div>
                <p className="text-sm text-gray-400">Package</p>
                <p className="text-white font-medium">{selectedNode.packageName}</p>
              </div>
              <div>
                <p className="text-sm text-gray-400">Class</p>
                <p className="text-white font-medium">{selectedNode.className}</p>
              </div>
              <div>
                <p className="text-sm text-gray-400">Dependencies</p>
                <p className="text-white font-medium">{selectedNode.metadata.dependencyCount}</p>
              </div>
              <div>
                <p className="text-sm text-gray-400">Providers</p>
                <p className="text-white font-medium">{selectedNode.metadata.providerCount}</p>
              </div>
              <div>
                <p className="text-sm text-gray-400">Status</p>
                <span className={`px-2 py-1 rounded text-xs font-medium ${getStatusColor(selectedNode)}`}>
                  {getStatusText(selectedNode)}
                </span>
              </div>
              {selectedNode.errorInfo.hasErrors && (
                <div>
                  <p className="text-sm text-gray-400">Error Details</p>
                  <p className="text-red-400 text-sm">{selectedNode.errorInfo.errorTypes.join(", ")}</p>
                </div>
              )}
            </div>
            <button 
              onClick={() => setSelectedNode(null)}
              className="mt-4 px-3 py-1 bg-slate-600 text-white rounded hover:bg-slate-500 text-sm transition-colors"
            >
              Clear Selection
            </button>
          </div>
        )}
      </div>
      
      {/* Package Inventory Status Table */}
      <div className="bg-slate-700 rounded-lg p-4">
        <h4 className="text-lg font-semibold text-white mb-4">Component Inventory</h4>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-600">
                <th className="text-left py-3 px-2 text-gray-400 font-medium">COMPONENT</th>
                <th className="text-left py-3 px-2 text-gray-400 font-medium">PACKAGE</th>
                <th className="text-left py-3 px-2 text-gray-400 font-medium">STATUS</th>
                <th className="text-left py-3 px-2 text-gray-400 font-medium">DEPENDENCIES</th>
                <th className="text-left py-3 px-2 text-gray-400 font-medium">PROVIDERS</th>
                <th className="text-left py-3 px-2 text-gray-400 font-medium">CONNECTIONS</th>
              </tr>
            </thead>
            <tbody>
              {networkData.nodes.map((node) => {
                const connectionCount = getConnectionCount(node.id);
                const statusIcon = getStatusIcon(node);
                const statusColor = getStatusColor(node);
                const statusText = getStatusText(node);
                
                return (
                  <tr 
                    key={node.id} 
                    className={`border-b border-slate-600/50 hover:bg-slate-600/30 cursor-pointer transition-colors ${
                      selectedNode?.id === node.id ? 'bg-slate-600/50' : ''
                    }`}
                    onClick={() => setSelectedNode(node)}
                  >
                    <td className="py-3 px-2">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{statusIcon}</span>
                        <span className="text-white font-medium">{node.label}</span>
                      </div>
                    </td>
                    <td className="py-3 px-2 text-gray-300">{node.packageName}</td>
                    <td className="py-3 px-2">
                      <span className={`px-2 py-1 rounded text-xs font-medium ${statusColor}`}>
                        {statusText}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-gray-300">{node.metadata.dependencyCount}</td>
                    <td className="py-3 px-2 text-gray-300">{node.metadata.providerCount}</td>
                    <td className="py-3 px-2 text-gray-300">{connectionCount}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
      
      
    </div>
  );
}