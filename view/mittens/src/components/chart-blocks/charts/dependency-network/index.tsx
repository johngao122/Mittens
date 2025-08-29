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
    if (node.errorInfo.isPartOfCycle) return 'text-purple-600 dark:text-purple-400 bg-purple-100 dark:bg-purple-400/20';
    if (node.errorInfo.hasErrors) {
      switch (node.errorInfo.errorSeverity) {
        case "ERROR": return 'text-red-600 dark:text-red-400 bg-red-100 dark:bg-red-400/20';
        case "WARNING": return 'text-yellow-600 dark:text-yellow-400 bg-yellow-100 dark:bg-yellow-400/20';
        default: return 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-400/20';
      }
    }
    return 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-400/20';
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
      
      {/* Enhanced Summary Statistics */}
      <div className="mb-6 grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-gradient-to-br from-blue-50 to-blue-100 dark:from-slate-800 dark:to-slate-700 border border-blue-200 dark:border-slate-600 rounded-xl p-6 text-center shadow-sm hover:shadow-md transition-all duration-200">
          <div className="text-3xl font-bold text-blue-600 dark:text-blue-400 mb-1">{networkData.nodes.length}</div>
          <div className="text-sm font-medium text-blue-700 dark:text-blue-300">Components</div>
          <div className="mt-1 text-xs text-blue-600/70 dark:text-blue-400/70">Total Nodes</div>
        </div>
        <div className="bg-gradient-to-br from-emerald-50 to-emerald-100 dark:from-slate-800 dark:to-slate-700 border border-emerald-200 dark:border-slate-600 rounded-xl p-6 text-center shadow-sm hover:shadow-md transition-all duration-200">
          <div className="text-3xl font-bold text-emerald-600 dark:text-emerald-400 mb-1">{networkData.links.length}</div>
          <div className="text-sm font-medium text-emerald-700 dark:text-emerald-300">Dependencies</div>
          <div className="mt-1 text-xs text-emerald-600/70 dark:text-emerald-400/70">Total Connections</div>
        </div>
        <div className="bg-gradient-to-br from-red-50 to-red-100 dark:from-slate-800 dark:to-slate-700 border border-red-200 dark:border-slate-600 rounded-xl p-6 text-center shadow-sm hover:shadow-md transition-all duration-200">
          <div className="text-3xl font-bold text-red-600 dark:text-red-400 mb-1">
            {networkData.nodes.filter(n => n.errorInfo.hasErrors && n.errorInfo.errorSeverity === 'ERROR').length}
          </div>
          <div className="text-sm font-medium text-red-700 dark:text-red-300">Errors</div>
          <div className="mt-1 text-xs text-red-600/70 dark:text-red-400/70">Critical Issues</div>
        </div>
        <div className="bg-gradient-to-br from-purple-50 to-purple-100 dark:from-slate-800 dark:to-slate-700 border border-purple-200 dark:border-slate-600 rounded-xl p-6 text-center shadow-sm hover:shadow-md transition-all duration-200">
          <div className="text-3xl font-bold text-purple-600 dark:text-purple-400 mb-1">
            {networkData.nodes.filter(n => n.errorInfo.isPartOfCycle).length}
          </div>
          <div className="text-sm font-medium text-purple-700 dark:text-purple-300">Cycles</div>
          <div className="mt-1 text-xs text-purple-600/70 dark:text-purple-400/70">Circular Dependencies</div>
        </div>
      </div>
      
      {/* Graph + Details side-by-side */}
      <div className="mb-6 flex flex-col lg:flex-row gap-4 items-stretch">
        <div
          className="flex-1 bg-gradient-to-br from-white to-gray-50 dark:from-slate-800 dark:to-slate-900 border border-gray-200 dark:border-slate-700 rounded-xl shadow-lg overflow-hidden"
          style={{ height: selectedNode && detailsHeight ? detailsHeight : 650, minHeight: 650 }}
        >
          <D3Network 
            data={networkData}
            width="100%"
            height="100%"
            onNodeClick={handleNodeClick}
            selectedNode={selectedNode}
          />
        </div>
        {selectedNode && (
          <div ref={detailsRef} className="lg:w-96 bg-gray-50 dark:bg-slate-700 border border-gray-200 dark:border-slate-600 rounded-lg p-4 self-start">
            <h4 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">Selected Component Details</h4>
            <div className="grid grid-cols-1 gap-4">
              <div>
                <p className="text-sm text-gray-600 dark:text-gray-400">Component Name</p>
                <p className="text-gray-900 dark:text-white font-medium">{selectedNode.label}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600 dark:text-gray-400">Package</p>
                <p className="text-gray-900 dark:text-white font-medium">{selectedNode.packageName}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600 dark:text-gray-400">Class</p>
                <p className="text-gray-900 dark:text-white font-medium">{selectedNode.className}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600 dark:text-gray-400">Dependencies</p>
                <p className="text-gray-900 dark:text-white font-medium">{selectedNode.metadata.dependencyCount}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600 dark:text-gray-400">Providers</p>
                <p className="text-gray-900 dark:text-white font-medium">{selectedNode.metadata.providerCount}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600 dark:text-gray-400">Status</p>
                <span className={`px-2 py-1 rounded text-xs font-medium ${getStatusColor(selectedNode)}`}>
                  {getStatusText(selectedNode)}
                </span>
              </div>
              {selectedNode.errorInfo.hasErrors && (
                <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                  <div className="flex items-center gap-2 mb-3">
                    <span className="text-red-500">⚠️</span>
                    <p className="text-sm font-semibold text-red-700 dark:text-red-300">Error Details</p>
                  </div>
                  <p className="text-red-600 dark:text-red-400 text-sm mb-4 font-medium">
                    {selectedNode.errorInfo.errorTypes.join(", ")}
                  </p>
                  
                  <div className="border-t border-red-200 dark:border-red-800 pt-3">
                    <p className="text-xs uppercase tracking-wide text-red-500 dark:text-red-400 font-semibold mb-2">
                      Suggested Fix
                    </p>
                    <div className="bg-red-100 dark:bg-red-900/30 rounded-md p-3 border-l-4 border-red-400">
                      {networkData.errorContext.issueDetails
                        .filter(issue => selectedNode.errorInfo.errorTypes.includes(issue.type))
                        .map((issue, index) => (
                          <p key={index} className="text-red-700 dark:text-red-300 text-sm leading-relaxed mb-2 last:mb-0">
                            {issue.suggestedFix}
                          </p>
                        ))}
                    </div>
                  </div>
                </div>
              )}

              {selectedNode.errorInfo.isPartOfCycle && (                
                <div className="bg-purple-50 dark:bg-purple-900/20 border border-purple-200 dark:border-purple-800 rounded-lg p-4">
                  <div className="flex items-center gap-2 mb-3">
                    <span className="text-purple-500">🔄</span>
                    <p className="text-sm font-semibold text-purple-700 dark:text-purple-300">Circular Dependency</p>
                  </div>
                  
                  <div className="border-t border-purple-200 dark:border-purple-800 pt-3">
                    <p className="text-xs uppercase tracking-wide text-purple-500 dark:text-purple-400 font-semibold mb-2">
                      Suggested Fix
                    </p>
                    <div className="bg-purple-100 dark:bg-purple-900/30 rounded-md p-3 border-l-4 border-purple-400">
                      <p className="text-purple-700 dark:text-purple-300 text-sm leading-relaxed">
                        {networkData.errorContext.issueDetails
                          .filter(issue => issue.type == "CIRCULAR_DEPENDENCY")
                          .map(issue => {
                            // Format the suggested fix with proper line breaks
                            const formattedFix = issue.suggestedFix
                              .replace(/(\d\))/g, '\n$1')
                              .split('\n')
                              .filter(line => line.trim())
                              .map((line, index) => line.trim());
                            
                            return (
                              <div key="circular-fix">
                                {formattedFix.map((line, lineIndex) => (
                                  <div key={lineIndex} className={lineIndex === 0 ? "mb-2" : "ml-0 mb-1"}>
                                    {line}
                                  </div>
                                ))}
                              </div>
                            );
                          })}
                      </p>
                    </div>
                  </div>
                </div>
              )}
            </div>
            <button 
              onClick={() => setSelectedNode(null)}
              className="mt-4 px-3 py-1 bg-gray-200 dark:bg-slate-600 text-gray-800 dark:text-white rounded hover:bg-gray-300 dark:hover:bg-slate-500 text-sm transition-colors"
            >
              Clear Selection
            </button>
          </div>
        )}
      </div>
      
      {/* Enhanced Component Inventory Table */}
      <div className="bg-gradient-to-br from-white to-gray-50 dark:from-slate-800 dark:to-slate-900 border border-gray-200 dark:border-slate-700 rounded-xl shadow-lg overflow-hidden">
        <div className="bg-gradient-to-r from-gray-100 to-gray-50 dark:from-slate-700 dark:to-slate-800 px-6 py-4 border-b border-gray-200 dark:border-slate-600">
          <h4 className="text-xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
            <span className="text-blue-500 dark:text-blue-400">📊</span>
            Component Inventory
          </h4>
          <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">Complete overview of all components and their dependencies</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 dark:bg-slate-800/50">
              <tr className="border-b border-gray-200 dark:border-slate-600">
                <th className="text-left py-4 px-4 text-gray-700 dark:text-gray-300 font-semibold tracking-wide">COMPONENT</th>
                <th className="text-left py-4 px-4 text-gray-700 dark:text-gray-300 font-semibold tracking-wide">PACKAGE</th>
                <th className="text-center py-4 px-4 text-gray-700 dark:text-gray-300 font-semibold tracking-wide">STATUS</th>
                <th className="text-right py-4 px-4 text-gray-700 dark:text-gray-300 font-semibold tracking-wide">DEPS</th>
                <th className="text-right py-4 px-4 text-gray-700 dark:text-gray-300 font-semibold tracking-wide">PROVIDERS</th>
                <th className="text-right py-4 px-4 text-gray-700 dark:text-gray-300 font-semibold tracking-wide">CONNECTIONS</th>
              </tr>
            </thead>
            <tbody>
              {networkData.nodes.map((node, index) => {
                const connectionCount = getConnectionCount(node.id);
                const statusIcon = getStatusIcon(node);
                const statusColor = getStatusColor(node);
                const statusText = getStatusText(node);
                
                return (
                  <tr 
                    key={node.id} 
                    className={`group border-b border-gray-100 dark:border-slate-700/50 hover:bg-gradient-to-r hover:from-blue-50 hover:to-indigo-50 dark:hover:from-slate-700/30 dark:hover:to-slate-600/30 cursor-pointer transition-all duration-200 ${
                      selectedNode?.id === node.id ? 'bg-gradient-to-r from-blue-100 to-indigo-100 dark:from-slate-600/50 dark:to-slate-500/50' : index % 2 === 0 ? 'bg-white dark:bg-slate-800' : 'bg-gray-50/50 dark:bg-slate-800/50'
                    }`}
                    onClick={() => setSelectedNode(node)}
                  >
                    <td className="py-4 px-4">
                      <div className="flex items-center gap-3">
                        <span className="text-lg transition-transform group-hover:scale-110">{statusIcon}</span>
                        <div>
                          <span className="text-gray-900 dark:text-white font-semibold block">{node.label}</span>
                          <span className="text-xs text-gray-500 dark:text-gray-400 block mt-0.5">{node.className}</span>
                        </div>
                      </div>
                    </td>
                    <td className="py-4 px-4">
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-gray-100 dark:bg-slate-700 text-gray-700 dark:text-gray-300">
                        {node.packageName}
                      </span>
                    </td>
                    <td className="py-4 px-4 text-center">
                      <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold transition-all duration-200 ${statusColor}`}>
                        {statusText}
                      </span>
                    </td>
                    <td className="py-4 px-4 text-right">
                      <span className="inline-flex items-center justify-center w-8 h-8 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded-full text-sm font-bold">
                        {node.metadata.dependencyCount}
                      </span>
                    </td>
                    <td className="py-4 px-4 text-right">
                      <span className="inline-flex items-center justify-center w-8 h-8 bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300 rounded-full text-sm font-bold">
                        {node.metadata.providerCount}
                      </span>
                    </td>
                    <td className="py-4 px-4 text-right">
                      <span className="inline-flex items-center justify-center w-8 h-8 bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300 rounded-full text-sm font-bold">
                        {connectionCount}
                      </span>
                    </td>
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