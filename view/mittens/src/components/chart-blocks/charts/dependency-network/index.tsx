'use client';

import React, { useEffect, useRef, useState, useMemo } from 'react';
import { getD3NetworkData } from './chart-utils';
import D3Network from './d3-network';
import { D3Node } from '../../../../lib/knit-data-parser';

export default function DependencyNetwork() {
  const [selectedNode, setSelectedNode] = useState<D3Node | null>(null);
  const detailsRef = useRef<HTMLDivElement | null>(null);
  const [detailsHeight, setDetailsHeight] = useState<number | null>(null);
  
  // Filter states
  const [componentTypeFilter, setComponentTypeFilter] = useState<string>('all');

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

  // Helper function to determine component type based on package name
  const getComponentType = (node: D3Node): string => {
    const packageName = node.packageName.toLowerCase();
    if (packageName.includes('models')) return 'models';
    if (packageName.includes('services')) return 'services';
    if (packageName.includes('repositories') || packageName.includes('repo')) return 'repo';
    if (packageName.includes('payment')) return 'payment';
    if (packageName.includes('main') || node.className.toLowerCase().includes('main')) return 'main';
    return 'other';
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

  // Helper function to check if a node matches component type filter
  const matchesFilters = (node: D3Node): boolean => {
    // Check component type filter
    const matchesType = componentTypeFilter === 'all' || getComponentType(node) === componentTypeFilter;
    return matchesType;
  };

  // Recursive function to check if any descendant matches filters
  const hasMatchingDescendant = (node: D3Node): boolean => {
    const providees = providerTree.providerToProvidees[node.id] || [];
    return providees.some(child => matchesFilters(child) || hasMatchingDescendant(child));
  };

  // Filter the tree roots to show only nodes that match or have matching descendants
  const filteredRoots = useMemo(() => {
    return providerTree.roots.filter(node => 
      matchesFilters(node) || hasMatchingDescendant(node)
    );
  }, [providerTree.roots, componentTypeFilter]);

  // Recursive render function for tree rows
  const renderTreeRows: any = (node: D3Node, depth = 0) => {
    // Check if this node should be visible
    const nodeMatches = matchesFilters(node);
    const hasMatchingChild = hasMatchingDescendant(node);
    
    // If neither this node nor its children match, don't render
    if (!nodeMatches && !hasMatchingChild) {
      return null;
    }
    const connectionCount = getConnectionCount(node.id);
    const statusIcon = getStatusIcon(node);
    const statusColor = getStatusColor(node);
    const statusText = getStatusText(node);
    const hasProvidees = providerTree.providerToProvidees[node.id]?.length > 0;
    return (
      <React.Fragment key={node.id}>
        <tr
          key={node.id}
          className={`group transition-all duration-200 hover:bg-gradient-to-r hover:from-gray-50 hover:to-blue-50 dark:hover:from-slate-700/50 dark:hover:to-slate-600/50 cursor-pointer ${
            selectedNode?.id === node.id 
              ? 'bg-gradient-to-r from-blue-100 to-indigo-100 dark:from-slate-600/70 dark:to-slate-500/70 shadow-sm' 
              : 'hover:shadow-sm'
          }`}
          onClick={() => setSelectedNode(node)}
        >
          {/* Simple Component Column */}
          <td className="py-4 px-6">
            <div className="flex items-center gap-3" style={{ marginLeft: depth * 20 }}>
              {/* Always reserve space for expand button to maintain alignment */}
              <div className="w-5 h-5 flex items-center justify-center">
                {hasProvidees ? (
                  <button
                    onClick={e => { e.stopPropagation(); toggleExpand(node.id); }}
                    className="flex items-center justify-center w-5 h-5 rounded-md hover:bg-gray-200 dark:hover:bg-slate-600 transition-colors text-gray-500 dark:text-gray-400"
                    aria-label={expanded[node.id] ? "Collapse" : "Expand"}
                  >
                    {expanded[node.id] ? (
                      <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
                      </svg>
                    ) : (
                      <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
                      </svg>
                    )}
                  </button>
                ) : (
                  // Empty space to maintain alignment
                  <div className="w-5 h-5"></div>
                )}
              </div>
              <span className="text-lg transition-transform group-hover:scale-110">{statusIcon}</span>
              <span className="text-gray-900 dark:text-white font-medium group-hover:text-gray-700 dark:group-hover:text-gray-200">{node.label}</span>
            </div>
          </td>
          
          {/* Enhanced Package Column */}
          <td className="py-4 px-6">
            <span className="inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-semibold bg-gradient-to-r from-gray-50 to-gray-100 dark:from-gray-800 dark:to-gray-700 text-gray-700 dark:text-gray-300 border border-white dark:border-gray-600 shadow-sm">
              {node.packageName}
            </span>
          </td>
          
          {/* Enhanced Status Column */}
          <td className="py-4 px-6 text-center">
            <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all duration-200 shadow-sm ${statusColor}`}>
              {statusText}
            </span>
          </td>
          
          {/* Enhanced Dependencies Column */}
          <td className="py-4 px-6 text-center">
            <div className="flex items-center justify-center">
              <span className="inline-flex items-center justify-center w-10 h-8 bg-gradient-to-r from-purple-100 to-purple-200 dark:from-purple-900/30 dark:to-purple-800/30 text-purple-700 dark:text-purple-300 rounded-lg text-sm font-bold border border-purple-200 dark:border-purple-700/50 shadow-sm">
                {node.metadata.dependencyCount}
              </span>
            </div>
          </td>
          
          {/* Enhanced Providers Column */}
          <td className="py-4 px-6 text-center">
            <div className="flex items-center justify-center">
              <span className="inline-flex items-center justify-center w-10 h-8 bg-gradient-to-r from-indigo-100 to-indigo-200 dark:from-indigo-900/30 dark:to-indigo-800/30 text-indigo-700 dark:text-indigo-300 rounded-lg text-sm font-bold border border-indigo-200 dark:border-indigo-700/50 shadow-sm">
                {node.metadata.providerCount}
              </span>
            </div>
          </td>
          
          {/* Enhanced Connections Column */}
          <td className="py-4 px-6 text-center">
            <div className="flex items-center justify-center">
              <span className="inline-flex items-center justify-center w-10 h-8 bg-gradient-to-r from-rose-100 to-rose-200 dark:from-rose-900/30 dark:to-rose-800/30 text-rose-700 dark:text-rose-300 rounded-lg text-sm font-bold border border-rose-200 dark:border-rose-700/50 shadow-sm">
                {connectionCount}
              </span>
            </div>
          </td>
        </tr>
        {hasProvidees && expanded[node.id] &&
          providerTree.providerToProvidees[node.id]
            .filter(child => matchesFilters(child) || hasMatchingDescendant(child))
            .map(child => renderTreeRows(child, depth + 1))
        }
      </React.Fragment>
    );
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
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
            {/* Left side - Title */}
            <div>
              <h4 className="text-xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
                <span className="text-blue-500 dark:text-blue-400">📊</span>
                Component Inventory
              </h4>
              <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">Complete overview of all components and their dependencies</p>
            </div>

            {/* Right side - Filters */}
            <div className="flex flex-col sm:flex-row gap-3 lg:items-center">
              {/* Beautiful Component Type Filter Buttons */}
              <div className="flex gap-1 flex-nowrap">
                <button
                  onClick={() => setComponentTypeFilter('all')}
                  className={`flex items-center gap-1.5 px-2.5 py-2 text-xs font-semibold rounded-lg transition-all duration-200 shadow-sm whitespace-nowrap ${
                    componentTypeFilter === 'all'
                      ? 'bg-blue-500 text-white shadow-lg shadow-blue-500/25 scale-105'
                      : 'bg-gray-100 dark:bg-slate-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-slate-600 hover:shadow-md'
                  }`}
                >
                  <span>All ({networkData.nodes.length})</span>
                </button>
                
                <button
                  onClick={() => setComponentTypeFilter('main')}
                  className={`flex items-center gap-1.5 px-2.5 py-2 text-xs font-semibold rounded-lg transition-all duration-200 shadow-sm whitespace-nowrap ${
                    componentTypeFilter === 'main'
                      ? 'bg-indigo-500 text-white shadow-lg shadow-indigo-500/25 scale-105'
                      : 'bg-indigo-50 dark:bg-indigo-900/20 text-indigo-700 dark:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900/30 hover:shadow-md border border-indigo-200 dark:border-indigo-800'
                  }`}
                >
                  <div className="w-3 h-3 rounded-full bg-indigo-500 flex items-center justify-center">
                    <svg className="w-2 h-2 text-white" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M3 3a1 1 0 000 2v8a2 2 0 002 2h2.586l-1.293 1.293a1 1 0 101.414 1.414L10 15.414l2.293 2.293a1 1 0 001.414-1.414L12.414 15H15a2 2 0 002-2V5a1 1 0 100-2H3zm11.707 4.707a1 1 0 00-1.414-1.414L10 9.586 8.707 8.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                    </svg>
                  </div>
                  <span>Main ({networkData.nodes.filter(n => getComponentType(n) === 'main').length})</span>
                </button>
                
                <button
                  onClick={() => setComponentTypeFilter('models')}
                  className={`flex items-center gap-1.5 px-2.5 py-2 text-xs font-semibold rounded-lg transition-all duration-200 shadow-sm whitespace-nowrap ${
                    componentTypeFilter === 'models'
                      ? 'bg-emerald-500 text-white shadow-lg shadow-emerald-500/25 scale-105'
                      : 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300 hover:bg-emerald-100 dark:hover:bg-emerald-900/30 hover:shadow-md border border-emerald-200 dark:border-emerald-800'
                  }`}
                >
                  <div className="w-3 h-3 rounded-full bg-emerald-500 flex items-center justify-center">
                    <svg className="w-2 h-2 text-white" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zM3 10a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H4a1 1 0 01-1-1v-6zM14 9a1 1 0 00-1 1v6a1 1 0 001 1h2a1 1 0 001-1v-6a1 1 0 00-1-1h-2z" clipRule="evenodd" />
                    </svg>
                  </div>
                  <span>Models ({networkData.nodes.filter(n => getComponentType(n) === 'models').length})</span>
                </button>
                
                <button
                  onClick={() => setComponentTypeFilter('services')}
                  className={`flex items-center gap-1.5 px-2.5 py-2 text-xs font-semibold rounded-lg transition-all duration-200 shadow-sm whitespace-nowrap ${
                    componentTypeFilter === 'services'
                      ? 'bg-amber-500 text-white shadow-lg shadow-amber-500/25 scale-105'
                      : 'bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-300 hover:bg-amber-100 dark:hover:bg-amber-900/30 hover:shadow-md border border-amber-200 dark:border-amber-800'
                  }`}
                >
                  <div className="w-3 h-3 rounded-full bg-amber-500 flex items-center justify-center">
                    <svg className="w-2 h-2 text-white" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M6 6V5a3 3 0 013-3h2a3 3 0 013 3v1h2a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V8a2 2 0 012-2h2zm2-1a1 1 0 011-1h2a1 1 0 011 1v1H8V5zm1 5a1 1 0 011-1h.01a1 1 0 010 2H10a1 1 0 01-1-1z" clipRule="evenodd" />
                    </svg>
                  </div>
                  <span>Services ({networkData.nodes.filter(n => getComponentType(n) === 'services').length})</span>
                </button>
                
                <button
                  onClick={() => setComponentTypeFilter('repo')}
                  className={`flex items-center gap-1.5 px-2.5 py-2 text-xs font-semibold rounded-lg transition-all duration-200 shadow-sm whitespace-nowrap ${
                    componentTypeFilter === 'repo'
                      ? 'bg-purple-500 text-white shadow-lg shadow-purple-500/25 scale-105'
                      : 'bg-purple-50 dark:bg-purple-900/20 text-purple-700 dark:text-purple-300 hover:bg-purple-100 dark:hover:bg-purple-900/30 hover:shadow-md border border-purple-200 dark:border-purple-800'
                  }`}
                >
                  <div className="w-3 h-3 rounded-full bg-purple-500 flex items-center justify-center">
                    <svg className="w-2 h-2 text-white" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M3 4a1 1 0 011-1h4a1 1 0 010 2H6.414l2.293 2.293a1 1 0 01-1.414 1.414L5 6.414V8a1 1 0 01-2 0V4zm9 1a1 1 0 010-2h4a1 1 0 011 1v4a1 1 0 01-2 0V6.414l-2.293 2.293a1 1 0 11-1.414-1.414L13.586 5H12zm-9 7a1 1 0 012 0v1.586l2.293-2.293a1 1 0 111.414 1.414L6.414 15H8a1 1 0 010 2H4a1 1 0 01-1-1v-4zm13-1a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 010-2h1.586l-2.293-2.293a1 1 0 111.414-1.414L15 13.586V12a1 1 0 011-1z" clipRule="evenodd" />
                    </svg>
                  </div>
                  <span>Repo ({networkData.nodes.filter(n => getComponentType(n) === 'repo').length})</span>
                </button>
                
                <button
                  onClick={() => setComponentTypeFilter('payment')}
                  className={`flex items-center gap-1.5 px-2.5 py-2 text-xs font-semibold rounded-lg transition-all duration-200 shadow-sm whitespace-nowrap ${
                    componentTypeFilter === 'payment'
                      ? 'bg-rose-500 text-white shadow-lg shadow-rose-500/25 scale-105'
                      : 'bg-rose-50 dark:bg-rose-900/20 text-rose-700 dark:text-rose-300 hover:bg-rose-100 dark:hover:bg-rose-900/30 hover:shadow-md border border-rose-200 dark:border-rose-800'
                  }`}
                >
                  <div className="w-3 h-3 rounded-full bg-rose-500 flex items-center justify-center">
                    <svg className="w-2 h-2 text-white" fill="currentColor" viewBox="0 0 20 20">
                      <path d="M4 4a2 2 0 00-2 2v1h16V6a2 2 0 00-2-2H4z" />
                      <path fillRule="evenodd" d="M18 9H2v5a2 2 0 002 2h12a2 2 0 002-2V9zM4 13a1 1 0 011-1h1a1 1 0 110 2H5a1 1 0 01-1-1zm5-1a1 1 0 100 2h1a1 1 0 100-2H9z" clipRule="evenodd" />
                    </svg>
                  </div>
                  <span>Payment ({networkData.nodes.filter(n => getComponentType(n) === 'payment').length})</span>
                </button>
              </div>
            </div>
          </div>
        </div>
        <div className="overflow-x-auto bg-white dark:bg-slate-800 rounded-b-lg border-t border-gray-200 dark:border-slate-600">
          <table className="w-full text-sm">
            {/* Beautiful Table Header */}
            <thead className="bg-gradient-to-r from-gray-50 to-gray-100 dark:from-slate-700 dark:to-slate-800">
              <tr className="border-b-2 border-gray-200 dark:border-slate-600">
                <th className="text-left py-5 px-6 text-gray-800 dark:text-gray-200 font-bold tracking-wide text-xs uppercase">
                  Component
                </th>
                <th className="text-left py-5 px-6 text-gray-800 dark:text-gray-200 font-bold tracking-wide text-xs uppercase">
                  Package
                </th>
                <th className="text-center py-5 px-6 text-gray-800 dark:text-gray-200 font-bold tracking-wide text-xs uppercase">
                  Status
                </th>
                <th className="text-center py-5 px-6 text-gray-800 dark:text-gray-200 font-bold tracking-wide text-xs uppercase">
                  Dependencies
                </th>
                <th className="text-center py-5 px-6 text-gray-800 dark:text-gray-200 font-bold tracking-wide text-xs uppercase">
                  Providers
                </th>
                <th className="text-center py-5 px-6 text-gray-800 dark:text-gray-200 font-bold tracking-wide text-xs uppercase">
                  Connections
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-slate-700/50">
              {filteredRoots.map(node => renderTreeRows(node))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}