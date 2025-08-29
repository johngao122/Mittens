'use client';

import React, { useEffect, useRef, useState, useMemo } from 'react';
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

  // Build provider→providee tree
  const providerTree = useMemo(() => {
    // Map nodeId → node
    const nodeMap = Object.fromEntries(networkData.nodes.map(n => [n.id, n]));
    // Map providerId → array of providee nodes
    const providesLinks = networkData.links.filter(l => l.type === "PROVIDES");
    const providerToProvidees: Record<string, D3Node[]> = {};
    providesLinks.forEach(link => {
      const providerId = typeof link.source === "string" ? link.source : link.source.id;
      const provideeId = typeof link.target === "string" ? link.target : link.target.id;
      if (!providerToProvidees[providerId]) providerToProvidees[providerId] = [];
      if (nodeMap[provideeId]) providerToProvidees[providerId].push(nodeMap[provideeId]);
    });
    // Find root nodes (nodes that are not providees)
    const provideeIds = new Set(providesLinks.map(l => (typeof l.target === "string" ? l.target : l.target.id)));
    const roots = networkData.nodes.filter(n => !provideeIds.has(n.id));
    return { nodeMap, providerToProvidees, roots };
  }, [networkData]);

  // Track expanded providers
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const toggleExpand = (id: string) => setExpanded(e => ({ ...e, [id]: !e[id] }));

  // Recursive render function for tree rows
  const renderTreeRows: any = (node: D3Node, depth = 0) => {
    const connectionCount = getConnectionCount(node.id);
    const statusIcon = getStatusIcon(node);
    const statusColor = getStatusColor(node);
    const statusText = getStatusText(node);
    const hasProvidees = providerTree.providerToProvidees[node.id]?.length > 0;
    return (
      <React.Fragment key={node.id}>
        <tr
          key={node.id}
          className={`border-b border-gray-100 dark:border-slate-600/50 hover:bg-gray-100 dark:hover:bg-slate-600/30 cursor-pointer transition-colors ${
            selectedNode?.id === node.id ? 'bg-gray-200 dark:bg-slate-600/50' : ''
          }`}
          onClick={() => setSelectedNode(node)}
        >
          <td className="py-3 px-2">
            <div className="flex items-center gap-2" style={{ marginLeft: depth * 18 }}>
              {hasProvidees && (
                <button
                  onClick={e => { e.stopPropagation(); toggleExpand(node.id); }}
                  className="mr-1 text-xs"
                  aria-label={expanded[node.id] ? "Collapse" : "Expand"}
                >
                  {expanded[node.id] ? "▼" : "▶"}
                </button>
              )}
              <span className="font-medium">{statusIcon}</span>
              <span className="text-gray-900 dark:text-white font-medium">{node.label}</span>
            </div>
          </td>
          <td className="py-3 px-2 text-gray-700 dark:text-gray-300">{node.packageName}</td>
          <td className="py-3 px-2">
            <span className={`px-2 py-1 rounded text-xs font-medium ${statusColor}`}>
              {statusText}
            </span>
          </td>
          <td className="py-3 px-2 text-gray-700 dark:text-gray-300">{node.metadata.dependencyCount}</td>
          <td className="py-3 px-2 text-gray-700 dark:text-gray-300">{node.metadata.providerCount}</td>
          <td className="py-3 px-2 text-gray-700 dark:text-gray-300">{connectionCount}</td>
        </tr>
        {hasProvidees && expanded[node.id] &&
          providerTree.providerToProvidees[node.id].map(child =>
            renderTreeRows(child, depth + 1)
          )
        }
      </React.Fragment>
    );
  };

  return (
    <div className="w-full">
      
      {/* Summary Statistics (moved to top) */}
      <div className="mb-4 grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-gray-100 dark:bg-slate-700 border border-gray-200 dark:border-slate-600 rounded-lg p-4 text-center">
          <div className="text-2xl font-bold text-gray-900 dark:text-white">{networkData.nodes.length}</div>
          <div className="text-sm text-gray-600 dark:text-gray-400">Components</div>
        </div>
        <div className="bg-gray-100 dark:bg-slate-700 border border-gray-200 dark:border-slate-600 rounded-lg p-4 text-center">
          <div className="text-2xl font-bold text-gray-900 dark:text-white">{networkData.links.length}</div>
          <div className="text-sm text-gray-600 dark:text-gray-400">Dependencies</div>
        </div>
        <div className="bg-gray-100 dark:bg-slate-700 border border-gray-200 dark:border-slate-600 rounded-lg p-4 text-center">
          <div className="text-2xl font-bold text-red-500 dark:text-red-400">
            {networkData.nodes.filter(n => n.errorInfo.hasErrors && n.errorInfo.errorSeverity === 'ERROR').length}
          </div>
          <div className="text-sm text-gray-600 dark:text-gray-400">Errors</div>
        </div>
        <div className="bg-gray-100 dark:bg-slate-700 border border-gray-200 dark:border-slate-600 rounded-lg p-4 text-center">
          <div className="text-2xl font-bold text-purple-500 dark:text-purple-400">
            {networkData.nodes.filter(n => n.errorInfo.isPartOfCycle).length}
          </div>
          <div className="text-sm text-gray-600 dark:text-gray-400">Cycles</div>
        </div>
      </div>
      
      {/* Graph + Details side-by-side */}
      <div className="mb-6 flex flex-col lg:flex-row gap-4 items-stretch">
        <div
          className="flex-1 bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg p-0"
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
                <>
                  <div>
                    <p className="text-sm text-gray-400">Error Details</p>
                    <p className="text-red-400 text-sm">{selectedNode.errorInfo.errorTypes.join(", ")}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-400">Suggested Fix</p>
                    <div className="text-red-400 text-sm">{
                      networkData.errorContext.issueDetails
                                  .filter(issue => selectedNode.errorInfo.errorTypes.includes(issue.type))
                                  .map((issue, index) => (
                                    <p key={index}>{issue.suggestedFix}</p>))}
                    </div>
                  </div>
                </>
              )}

              {selectedNode.errorInfo.isPartOfCycle && (                
                <div>
                  <p className="text-sm text-gray-400">Suggested Fix</p>
                  <p className="text-purple-400 text-sm">{
                    networkData.errorContext.issueDetails
                                .filter(issue => issue.type == "CIRCULAR_DEPENDENCY")
                                .map(issue => issue.suggestedFix)}
                  </p>
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
      
      {/* Package Inventory Status Table */}
      <div className="bg-gray-50 dark:bg-slate-700 border border-gray-200 dark:border-slate-600 rounded-lg p-4">
        <h4 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Component Inventory</h4>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 dark:border-slate-600">
                <th className="text-left py-3 px-2 text-gray-700 dark:text-gray-400 font-medium">COMPONENT</th>
                <th className="text-left py-3 px-2 text-gray-700 dark:text-gray-400 font-medium">PACKAGE</th>
                <th className="text-left py-3 px-2 text-gray-700 dark:text-gray-400 font-medium">STATUS</th>
                <th className="text-left py-3 px-2 text-gray-700 dark:text-gray-400 font-medium">DEPENDENCIES</th>
                <th className="text-left py-3 px-2 text-gray-700 dark:text-gray-400 font-medium">PROVIDERS</th>
                <th className="text-left py-3 px-2 text-gray-700 dark:text-gray-400 font-medium">CONNECTIONS</th>
              </tr>
            </thead>
            <tbody>
              {providerTree.roots.map(node => renderTreeRows(node))}
              {/* {networkData.nodes.map((node) => {
                const connectionCount = getConnectionCount(node.id);
                const statusIcon = getStatusIcon(node);
                const statusColor = getStatusColor(node);
                const statusText = getStatusText(node);
                
                return (
                  <tr 
                    key={node.id} 
                    className={`border-b border-gray-100 dark:border-slate-600/50 hover:bg-gray-100 dark:hover:bg-slate-600/30 cursor-pointer transition-colors ${
                      selectedNode?.id === node.id ? 'bg-gray-200 dark:bg-slate-600/50' : ''
                    }`}
                    onClick={() => setSelectedNode(node)}
                  >
                    <td className="py-3 px-2">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{statusIcon}</span>
                        <span className="text-gray-900 dark:text-white font-medium">{node.label}</span>
                      </div>
                    </td>
                    <td className="py-3 px-2 text-gray-700 dark:text-gray-300">{node.packageName}</td>
                    <td className="py-3 px-2">
                      <span className={`px-2 py-1 rounded text-xs font-medium ${statusColor}`}>
                        {statusText}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-gray-700 dark:text-gray-300">{node.metadata.dependencyCount}</td>
                    <td className="py-3 px-2 text-gray-700 dark:text-gray-300">{node.metadata.providerCount}</td>
                    <td className="py-3 px-2 text-gray-700 dark:text-gray-300">{connectionCount}</td>
                  </tr>
                );
              })} */}
            </tbody>
          </table>
        </div>
      </div>
      
      
    </div>
  );
}