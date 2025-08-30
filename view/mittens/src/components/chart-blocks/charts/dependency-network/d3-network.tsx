"use client";

/**
 * D3 NETWORK VISUALIZATION COMPONENT
 * 
 * Interactive force-directed graph visualization using D3.js featuring:
 * - Draggable nodes with smart positioning (nodes stay where placed)
 * - Dynamic force simulation with multiple force types
 * - Zoom/pan controls and smooth animations
 * - Node clustering by package type
 * - Elastic link behavior during drag operations
 * - Color-coded nodes based on error status
 * - Size-based nodes reflecting dependency count
 */

import React, { useRef, useEffect, useState } from "react";
import * as d3 from "d3";
import { NetworkData, D3Node, D3Link } from "../../../../lib/knit-data-parser";

// ========================================
// CONFIGURATION CONSTANTS
// ========================================
const FORCE_CONFIG = {
    LINK_DISTANCE: 170,           // Base distance between connected nodes
    LINK_STRENGTH: 0.5,           // Base strength of link forces
    REPEL_STRENGTH: -100,         // Node repulsion strength (negative = repel)
    COLLISION_PADDING: 5,         // Padding around nodes for collision detection
    CLUSTER_STRENGTH: 0.1,        // Strength of package clustering force
    SIDE_PANEL_WIDTH_RATIO: 1,    // Reserved space ratio for side panels
} as const;

const ANIMATION_DURATIONS = {
    ZOOM: 300,                    // Zoom transition duration (ms)
    RESET: 500,                   // Reset animation duration (ms)
    CENTER: 500,                  // Center animation duration (ms)
} as const;

// ========================================
// TYPE DEFINITIONS
// ========================================
interface D3NetworkProps {
    data: NetworkData;
    width?: number | string;
    height?: number | string;
    onNodeClick?: (node: D3Node | null) => void;
    onLinkClick?: (link: D3Link) => void;
    showZoomControls?: boolean;
    selectedNode?: D3Node | null;
}

interface TooltipData {
    node: D3Node;
    x: number;
    y: number;
}

// ========================================
// MAIN COMPONENT
// ========================================
export default function D3Network({
    data,
    width = 800,
    height = 400,
    onNodeClick,
    onLinkClick,
    showZoomControls = true,
    selectedNode = null,
}: D3NetworkProps) {
    // ========================================
    // STATE & REFS
    // ========================================
    const svgRef = useRef<SVGSVGElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const [tooltip, setTooltip] = useState<TooltipData | null>(null);
    const [mounted, setMounted] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(false);
    const zoomRef = useRef<d3.ZoomBehavior<SVGSVGElement, unknown> | null>(
        null
    );
    const [containerSize, setContainerSize] = useState<{ width: number; height: number }>({ width: 800, height: 400 });
    const labelsRef = useRef<any>(null); // Store reference to labels for theme updates
    const nodesRef = useRef<any>(null); // Store reference to nodes for theme updates
    const linksRef = useRef<any>(null); // Store reference to links for selection highlighting
    const hasSelectionRef = useRef<boolean>(false); // Track if a node is currently selected

    // ========================================
    // COMPONENT LIFECYCLE & SETUP
    // ========================================
    // Wait for component to mount before using browser APIs
    useEffect(() => {
        setMounted(true);
        // Set initial theme state
        setIsDarkMode(document.documentElement.classList.contains('dark'));
    }, []);

    // ========================================
    // THEME MANAGEMENT
    // ========================================
    // Listen for theme changes (Light and Dark mode)
    useEffect(() => {
        if (!mounted) return;
        
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
                    const newIsDarkMode = document.documentElement.classList.contains('dark');
                    setIsDarkMode(newIsDarkMode);
                }
            });
        });

        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['class']
        });

        return () => observer.disconnect();
    }, [mounted]);

    // Update label and Node border colors when theme changes
    useEffect(() => {
        if (labelsRef.current) {
            labelsRef.current
                .attr("fill", isDarkMode ? "#ffffff" : "#434850ff") // Dark gray for light mode
                .style("text-shadow", isDarkMode ? "2px 2px 4px rgba(0,0,0,0.9)" : "1px 1px 2px rgba(255,255,255,0.8)");
        }
        if (nodesRef.current) {
            nodesRef.current
                .attr("stroke", isDarkMode ? "#ffffff" : "#374151") // Dark gray border for light mode
                .attr("stroke-width", 2.5);
        }
    }, [isDarkMode]);

    // Track selection state for centering logic
    useEffect(() => {
        hasSelectionRef.current = selectedNode !== null;
    }, [selectedNode]);

    // Track container size so we can fill 100% of the parent
    useEffect(() => {
        if (!mounted || !containerRef.current) return;
        const el = containerRef.current;
        const ro = new ResizeObserver(entries => {
            for (const entry of entries) {
                const { width: w, height: h } = entry.contentRect;
                setContainerSize({ width: Math.max(1, Math.floor(w)), height: Math.max(1, Math.floor(h)) });
            }
        });
        ro.observe(el);
        return () => ro.disconnect();
    }, [mounted]);

    // ========================================
    // MAIN D3 VISUALIZATION EFFECT
    // ========================================
    useEffect(() => {
        if (!mounted || !svgRef.current || !data.nodes.length) return;

        const numericWidth = typeof width === "number" ? width : (containerSize.width || 800);
        const numericHeight = typeof height === "number" ? height : (containerSize.height || 400);

        const svg = d3.select(svgRef.current);
        svg.selectAll("*").remove();
        svg.attr("width", numericWidth).attr("height", numericHeight);

        // ========================================
        // SVG SETUP & ZOOM BEHAVIOR
        // ========================================
        // Create main container group
        const container = svg.append("g").attr("class", "network-container");

        // Initialize zoom behavior
        const zoom = d3
            .zoom<SVGSVGElement, unknown>()
            .scaleExtent([0.1, 10])
            .on("zoom", (event) => {
                container.attr("transform", event.transform);
            });

        svg.call(zoom);
        zoomRef.current = zoom;

        // Clicking on empty space (background) clears selection
        svg.on("click", () => {
            onNodeClick?.(null);
        });

        // ========================================
        // DATA PREPARATION
        // ========================================
        // Create copies of data for D3.js simulation
        const nodes = data.nodes.map((d) => ({ ...d }));
        const links = data.links.map((d) => ({ ...d }));

        // ========================================
        // STYLING HELPER FUNCTIONS
        // ========================================
        // Helper function to get node color based on error state
        const getNodeColor = (node: D3Node) => {
            if (node.errorInfo.isPartOfCycle) return "#8b5cf6"; // Purple for cycles
            if (node.errorInfo.hasErrors) {
                switch (node.errorInfo.errorSeverity) {
                    case "ERROR":
                        return "#ef4444"; // Red for errors
                    case "WARNING":
                        return "#eab308"; // Yellow for warnings
                    default:
                        return "#10b981"; // Green for healthy
                }
            }
            return "#10b981"; // Green for healthy
        };

        // Helper function to get node size based on dependency count
        const getNodeSize = (node: D3Node) => {
            const baseSize = 20;
            const maxSize = 50;
            const dependencyCount = node.metadata.dependencyCount || 0;
            // Scale node size based on dependency count (logarithmic scale for better visualization)
            return Math.min(
                baseSize + Math.log(dependencyCount + 1) * 8,
                maxSize
            );
        };

        const clusters: Record<string, { x: number; y: number }> = {};
        const clusterTags = Array.from(new Set(nodes.map(n => n.packageName)));
        const clusterRadius = Math.min(numericWidth, numericHeight) / 3;

        // Assign cluster centers in a circle
        clusterTags.forEach((tag, i) => {
            const angle = (2 * Math.PI * i) / clusterTags.length;
            clusters[tag] = {
                x: numericWidth / 2 + clusterRadius * Math.cos(angle),
                y: numericHeight / 2 + clusterRadius * Math.sin(angle),
            };
        });

        // Custom cluster force
        function forceCluster(alpha: number) {
            nodes.forEach((node: any) => {
                const cluster = clusters[node.packageName];
                // Only apply cluster force to non-manually positioned nodes
                if (!node._manuallyPositioned) {
                    node.vx += (cluster.x - node.x) * FORCE_CONFIG.CLUSTER_STRENGTH * alpha;
                    node.vy += (cluster.y - node.y) * FORCE_CONFIG.CLUSTER_STRENGTH * alpha;
                }
            });
        }

        // Custom center force that respects manually positioned nodes
        function forceCustomCenter(alpha: number) {
            const centerX = numericWidth / 2;
            const centerY = numericHeight / 2;
            
            nodes.forEach((node: any) => {
                // Only apply center force to non-manually positioned nodes
                if (!node._manuallyPositioned) {
                    const dx = centerX - node.x;
                    const dy = centerY - node.y;
                    const centerStrength = 0.02; // Weak center force
                    
                    node.vx += dx * centerStrength * alpha;
                    node.vy += dy * centerStrength * alpha;
                }
            });
        }

        // Custom anchor force to keep manually positioned nodes near their target
        function forceAnchor(alpha: number) {
            nodes.forEach((node: any) => {
                if (node._manuallyPositioned && node._targetX !== undefined && node._targetY !== undefined) {
                    // Calculate distance from target
                    const dx = node._targetX - node.x;
                    const dy = node._targetY - node.y;
                    const distance = Math.sqrt(dx * dx + dy * dy);
                    
                    // Check if this is a recently positioned node (within last 10 seconds)
                    const isRecentlyPositioned = node._positionedAt && (Date.now() - node._positionedAt < 10000);
                    
                    if (isRecentlyPositioned) {
                        // Extremely strong anchor for recently positioned nodes - acts like fixed positioning
                        const anchorStrength = 0.99; // Almost completely locked
                        node.vx += dx * anchorStrength;
                        node.vy += dy * anchorStrength;
                    } else {
                        // Normal anchor force for older positioned nodes
                        const maxDistance = 50;
                        const strengthMultiplier = Math.max(0, (distance - 5) / maxDistance);
                        const anchorStrength = 0.8 * strengthMultiplier;
                        
                        if (distance > 5) {
                            node.vx += dx * anchorStrength * alpha;
                            node.vy += dy * anchorStrength * alpha;
                        }
                    }
                }
            });
        }

        // ========================================
        // FORCE SIMULATION SETUP
        // ========================================
        // Create force simulation with multiple physics forces
        const simulation = d3
            .forceSimulation(nodes)
            .alphaDecay(0.0228) // Normal decay rate to prevent excessive motion
            .velocityDecay(0.4) // Normal velocity decay for stable motion
            .force(
                "link",
                d3
                    .forceLink(links)
                    .id((d: any) => d.id)
                    .distance((d: any) => {
                        // Dynamic distance based on node types and states
                        const sourceNode = typeof d.source === 'object' ? d.source : nodes.find(n => n.id === d.source);
                        const targetNode = typeof d.target === 'object' ? d.target : nodes.find(n => n.id === d.target);
                        
                        // Base distance
                        let baseDistance = FORCE_CONFIG.LINK_DISTANCE;
                        
                        // If either node is being dragged, make links much more elastic
                        const sourceBeingDragged = sourceNode && (sourceNode.fx !== null);
                        const targetBeingDragged = targetNode && (targetNode.fx !== null);
                        
                        if (sourceBeingDragged || targetBeingDragged) {
                            // Much shorter rest length for maximum stretch and smooth following
                            return baseDistance * 0.2;
                        }
                        
                        // If either node is manually positioned, slightly flexible
                        const sourceManuallyPositioned = sourceNode && sourceNode._manuallyPositioned;
                        const targetManuallyPositioned = targetNode && targetNode._manuallyPositioned;
                        
                        if (sourceManuallyPositioned || targetManuallyPositioned) {
                            return baseDistance * 0.7;
                        }
                        
                        return baseDistance;
                    })
                    .strength((d: any) => {
                        // Dynamic strength for smooth elastic behavior
                        const sourceNode = typeof d.source === 'object' ? d.source : nodes.find(n => n.id === d.source);
                        const targetNode = typeof d.target === 'object' ? d.target : nodes.find(n => n.id === d.target);
                        
                        // Base strength
                        let baseStrength = FORCE_CONFIG.LINK_STRENGTH;
                        
                        // If either node is being dragged, use higher strength for smoother following
                        const sourceBeingDragged = sourceNode && (sourceNode.fx !== null);
                        const targetBeingDragged = targetNode && (targetNode.fx !== null);
                        
                        if (sourceBeingDragged || targetBeingDragged) {
                            return baseStrength * 0.8; // Higher strength for connected nodes to follow
                        }
                        
                        // COMPLETELY disable link forces for click-locked nodes
                        const sourceClickLocked = sourceNode && (sourceNode as any)._clickLocked;
                        const targetClickLocked = targetNode && (targetNode as any)._clickLocked;
                        
                        if (sourceClickLocked || targetClickLocked) {
                            return 0; // No link force for click-locked nodes
                        }
                        
                        // Special handling for drag-positioned nodes (keep them more fluid)
                        const sourceDragPositioned = sourceNode && (sourceNode as any)._dragPositioned;
                        const targetDragPositioned = targetNode && (targetNode as any)._dragPositioned;
                        
                        if (sourceDragPositioned || targetDragPositioned) {
                            return baseStrength * 0.8; // High strength to maintain fluidity while preventing slingback
                        }
                        
                        // If either node is manually positioned, keep reasonable strength for fluid motion
                        const sourceManuallyPositioned = sourceNode && sourceNode._manuallyPositioned;
                        const targetManuallyPositioned = targetNode && targetNode._manuallyPositioned;
                        
                        if (sourceManuallyPositioned && targetManuallyPositioned) {
                            return baseStrength * 0.4; // Keep reasonable strength between positioned nodes
                        }
                        
                        if (sourceManuallyPositioned || targetManuallyPositioned) {
                            return baseStrength * 0.7; // Strong for mixed connections to maintain fluid motion
                        }
                        
                        return baseStrength;
                    })
            )
            .force("charge", d3.forceManyBody().strength(FORCE_CONFIG.REPEL_STRENGTH))
            .force("center", forceCustomCenter as any)
            .force(
                "collision",
                d3.forceCollide().radius((d) => getNodeSize(d as D3Node) + FORCE_CONFIG.COLLISION_PADDING)
            )
            .force("cluster", forceCluster as any)
            .force("anchor", forceAnchor as any);

        // ========================================
        // VISUAL ELEMENTS CREATION
        // ========================================
        // Create links (connections between nodes)
        const link = container
            .selectAll(".link")
            .data(links)
            .enter()
            .append("line")
            .attr("class", "link")
            .attr("stroke", (d: any) =>
                d.errorInfo.hasErrors ? "#ef4444" : "#64748b"
            )
            .attr("stroke-width", 3)
            .attr("stroke-opacity", 0.8)
            .attr("stroke-dasharray", (d: any) =>
                d.errorInfo.hasErrors ? "5,5" : "0"
            );

        // Store reference for later highlighting updates
        linksRef.current = link;

        // Create nodes (interactive circles)
        const node = container
            .selectAll(".node")
            .data(nodes)
            .enter()
            .append("circle")
            .attr("class", "node")
            .attr("r", (d: any) => getNodeSize(d as D3Node))
            .attr("fill", (d: any) => getNodeColor(d as D3Node))
            .attr("stroke", isDarkMode ? "#ffffff" : "#374151")
            .attr("stroke-width", 2.5)
            .style("cursor", "pointer")
            .call(
                // ========================================
                // DRAG BEHAVIOR IMPLEMENTATION
                // ========================================
                // Smart drag system: nodes stay where placed, elastic following during drag
                d3
                    .drag<SVGCircleElement, D3Node>()
                    .on("start", (event, d) => {
                        // Clear click-locked status when dragging starts
                        delete (d as any)._clickLocked;
                        
                        // Don't clear manually positioned status - keep it for connected nodes
                        // Only clear it for the dragged node to ensure it moves freely
                        delete (d as any)._manuallyPositioned;
                        delete (d as any)._targetX;
                        delete (d as any)._targetY;
                        delete (d as any)._dragPositioned;
                        
                        // Higher simulation activity for responsive following
                        if (!event.active) simulation.alphaTarget(0.5).restart();
                        
                        // Fix only the dragged node position during drag
                        d.fx = d.x;
                        d.fy = d.y;
                    })
                    .on("drag", (event, d) => {
                        // Update dragged node position
                        d.fx = event.x;
                        d.fy = event.y;
                        
                        // Active simulation during drag for responsive following
                        simulation.alphaTarget(0.5);
                    })
                    .on("end", (event, d) => {
                        // Mark as manually positioned to prevent slingback, but keep it draggable
                        (d as any)._manuallyPositioned = true;
                        (d as any)._targetX = event.x;
                        (d as any)._targetY = event.y;
                        (d as any)._dragPositioned = true; // Flag to distinguish from click-positioned
                        (d as any)._positionedAt = Date.now(); // Timestamp for recent positioning
                        
                        // Set final position and release from fixed positioning immediately
                        d.x = event.x;
                        d.y = event.y;
                        d.fx = null; // Release fixed positioning to prevent phase-through
                        d.fy = null; // Release fixed positioning to prevent phase-through
                        
                        // Use very low simulation activity and rely on strong anchor force
                        simulation.alphaTarget(0.02);
                    })
            );

        // Create node labels
        const labels = container
            .selectAll(".label")
            .data(nodes)
            .enter()
            .append("text")
            .attr("class", "label")
            .attr("text-anchor", "middle")
            .attr("dominant-baseline", "middle")
            .attr("fill", isDarkMode ? "#ffffff" : "#1f2937")
            .attr("font-size", "12px")
            .attr("font-weight", "bold")
            .attr("pointer-events", "none")
            .style("text-shadow", isDarkMode ? "2px 2px 4px rgba(0,0,0,0.9)" : "1px 1px 2px rgba(255,255,255,0.8)")
            .text((d: any) => d.label);

        // Store references for theme updates
        labelsRef.current = labels;
        nodesRef.current = node;

        // Add event listeners
        node.on("click", (event: any, d: any) => {
            event.stopPropagation();
            const nodeData = d as D3Node;
            
            // IMMEDIATELY fix the node position to prevent any movement
            nodeData.fx = nodeData.x;
            nodeData.fy = nodeData.y;
            
            // Mark as manually positioned and record current position
            (nodeData as any)._manuallyPositioned = true;
            (nodeData as any)._targetX = nodeData.x;
            (nodeData as any)._targetY = nodeData.y;
            (nodeData as any)._clickLocked = true; // Additional flag for click-locked nodes
            
            // Handle selection state
            if (!hasSelectionRef.current) {
                hasSelectionRef.current = true;
            }
            
            onNodeClick?.(nodeData);
        })
            .on("mouseenter", (event: any, d: any) => {
                const [x, y] = d3.pointer(event, svgRef.current);
                setTooltip({
                    node: d as D3Node,
                    x,
                    y,
                });
            })
            .on("mouseleave", () => {
                setTooltip(null);
            });

        // ========================================
        // ANIMATION & SIMULATION LOOP
        // ========================================
        // Update positions on each tick of the simulation
        simulation.on("tick", () => {
            link.attr("x1", (d: any) => d.source.x)
                .attr("y1", (d: any) => d.source.y)
                .attr("x2", (d: any) => d.target.x)
                .attr("y2", (d: any) => d.target.y)
                .attr("stroke-width", (d: any) => {
                    // Calculate distance between nodes
                    const dx = d.target.x - d.source.x;
                    const dy = d.target.y - d.source.y;
                    const distance = Math.sqrt(dx * dx + dy * dy);
                    
                    // Make links thicker when stretched beyond normal distance
                    const normalDistance = FORCE_CONFIG.LINK_DISTANCE;
                    const stretchFactor = Math.max(1, distance / normalDistance);
                    const baseWidth = 3; // Increased from 2 to 3 to match static width
                    const maxWidth = 6; // Increased from 4 to 6 for better prominence
                    
                    return Math.min(baseWidth * stretchFactor, maxWidth);
                })
                .attr("stroke-opacity", (d: any) => {
                    // Calculate distance between nodes
                    const dx = d.target.x - d.source.x;
                    const dy = d.target.y - d.source.y;
                    const distance = Math.sqrt(dx * dx + dy * dy);
                    
                    // Make links more opaque when stretched
                    const normalDistance = FORCE_CONFIG.LINK_DISTANCE;
                    const stretchFactor = distance / normalDistance;
                    const baseOpacity = 0.6;
                    const maxOpacity = 0.9;
                    
                    return Math.min(baseOpacity + (stretchFactor - 1) * 0.3, maxOpacity);
                });

            node.attr("cx", (d: any) => d.x).attr("cy", (d: any) => d.y);

            labels.attr("x", (d: any) => d.x).attr("y", (d: any) => d.y);
        });

        // Clean up simulation on unmount
        return () => {
            simulation.stop();
        };
    }, [mounted, data, width, height]);

    // Highlight selection: emphasize selected node and its links; dim others
    useEffect(() => {
        if (!mounted) return;
        const nodesSel = nodesRef.current;
        const labelsSel = labelsRef.current;
        const linksSel = linksRef.current;
        if (!nodesSel || !labelsSel) return;

        if (!selectedNode) {
            // Reset styling
            nodesSel
                .attr("opacity", 1)
                .attr("stroke-width", 2.5);
            labelsSel
                .attr("opacity", 1)
                .attr("font-size", "12px");
            if (linksSel) {
                linksSel
                    .attr("stroke-opacity", 0.8)
                    .attr("stroke-width", 3);
            }
            return;
        }

        // Dim non-selected nodes/labels and emphasize the selected one
        nodesSel
            .attr("opacity", (d: any) => (d.id === selectedNode.id ? 1 : 0.25))
            .attr("stroke-width", (d: any) => (d.id === selectedNode.id ? 4 : 2));
        labelsSel
            .attr("opacity", (d: any) => (d.id === selectedNode.id ? 1 : 0.35))
            .attr("font-size", (d: any) => (d.id === selectedNode.id ? "14px" : "12px"));

        if (linksSel) {
            linksSel
                .attr("stroke-opacity", (d: any) => {
                    const srcId = typeof d.source === 'object' ? d.source.id : d.source;
                    const tgtId = typeof d.target === 'object' ? d.target.id : d.target;
                    return srcId === selectedNode.id || tgtId === selectedNode.id ? 0.9 : 0.1;
                })
                .attr("stroke-width", (d: any) => {
                    const srcId = typeof d.source === 'object' ? d.source.id : d.source;
                    const tgtId = typeof d.target === 'object' ? d.target.id : d.target;
                    return srcId === selectedNode.id || tgtId === selectedNode.id ? 5 : 2;
                });
        }
    }, [mounted, selectedNode]);

    // Handle container resize without recreating the entire network
    useEffect(() => {
        if (!mounted || !svgRef.current) return;

        const numericWidth = typeof width === "number" ? width : (containerSize.width || 800);
        const numericHeight = typeof height === "number" ? height : (containerSize.height || 400);
        
        d3.select(svgRef.current).attr("width", numericWidth).attr("height", numericHeight);
    }, [mounted, containerSize.width, containerSize.height, width, height]);

    // Zoom control functions
    const handleZoomIn = () => {
        if (svgRef.current && zoomRef.current) {
            d3.select(svgRef.current)
                .transition()
                .duration(ANIMATION_DURATIONS.ZOOM)
                .call(zoomRef.current.scaleBy, 1.5);
        }
    };

    const handleZoomOut = () => {
        if (svgRef.current && zoomRef.current) {
            d3.select(svgRef.current)
                .transition()
                .duration(ANIMATION_DURATIONS.ZOOM)
                .call(zoomRef.current.scaleBy, 1 / 1.5);
        }
    };

    const handleResetZoom = () => {
        if (svgRef.current && zoomRef.current) {
            d3.select(svgRef.current)
                .transition()
                .duration(ANIMATION_DURATIONS.RESET)
                .call(zoomRef.current.transform, d3.zoomIdentity);
        }
    };

    const centerOnNode = (node: D3Node) => {
        if (svgRef.current && zoomRef.current && node.x !== undefined && node.y !== undefined) {
            const numericWidth = typeof width === "number" ? width : (containerSize.width || 800);
            const numericHeight = typeof height === "number" ? height : (containerSize.height || 400);
            
            // Get current scale to maintain zoom level
            const currentTransform = d3.zoomTransform(svgRef.current);
            const currentScale = currentTransform.k;
            
            // Calculate the transform to center the node
            // When centering from big border, use effective width for where the graph will be after side panel appears
            const effectiveWidth = numericWidth * FORCE_CONFIG.SIDE_PANEL_WIDTH_RATIO; // Space when side panel is open
            const x = effectiveWidth / 2 - node.x * currentScale;
            const y = numericHeight / 2 - node.y * currentScale;
            
            d3.select(svgRef.current)
                .transition()
                .duration(ANIMATION_DURATIONS.CENTER)
                .call(zoomRef.current.transform, d3.zoomIdentity.translate(x, y).scale(currentScale));
        }
    };

    return (
        <div ref={containerRef} className="relative w-full h-full">
            {showZoomControls && (
                <div className="absolute top-4 right-4 z-10 flex flex-col gap-2">
                    <button
                        onClick={handleZoomIn}
                        className="px-3 py-1 bg-gray-200 dark:bg-slate-700 text-gray-800 dark:text-white rounded hover:bg-gray-300 dark:hover:bg-slate-600 text-sm transition-colors shadow-md"
                        aria-label="Zoom in"
                    >
                        +
                    </button>
                    <button
                        onClick={handleZoomOut}
                        className="px-3 py-1 bg-gray-200 dark:bg-slate-700 text-gray-800 dark:text-white rounded hover:bg-gray-300 dark:hover:bg-slate-600 text-sm transition-colors shadow-md"
                        aria-label="Zoom out"
                    >
                        -
                    </button>
                    <button
                        onClick={handleResetZoom}
                        className="px-2 py-1 bg-gray-200 dark:bg-slate-700 text-gray-800 dark:text-white rounded hover:bg-gray-300 dark:hover:bg-slate-600 text-xs transition-colors shadow-md"
                        aria-label="Reset zoom"
                    >
                        Reset
                    </button>

                    {/* Status Legend - positioned below reset button */}
                    <div className="mt-4 p-3 bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-600 shadow-lg">
                        <div className="text-xs font-medium text-slate-700 dark:text-slate-300 mb-2">Status Legend</div>
                        <div className="space-y-1">
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 rounded-full bg-green-500"></div>
                                <span className="text-xs text-slate-600 dark:text-slate-400">Healthy</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 rounded-full bg-red-500"></div>
                                <span className="text-xs text-slate-600 dark:text-slate-400">Error</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 rounded-full bg-yellow-500"></div>
                                <span className="text-xs text-slate-600 dark:text-slate-400">Warning</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 rounded-full bg-purple-500"></div>
                                <span className="text-xs text-slate-600 dark:text-slate-400">Cycle</span>
                            </div>
                        </div>
                    </div>
                </div>
            )}
            <svg
                ref={svgRef}
                className="border border-gray-200 dark:border-slate-700 rounded-lg bg-gray-50 dark:bg-slate-800 w-full h-full"
            />

            {tooltip && (
                <div
                    className="absolute bg-gray-900 dark:bg-black bg-opacity-95 text-white p-3 rounded-lg shadow-lg pointer-events-none z-10 max-w-xs border border-gray-700"
                    style={{
                        left: tooltip.x + 10,
                        top: tooltip.y - 10,
                        transform:
                            tooltip.x > containerSize.width - 200
                                ? "translateX(-100%)"
                                : "none",
                    }}
                >
                    <div className="font-bold text-sm mb-1">
                        📦 {tooltip.node.label}
                    </div>
                    <div className="text-xs text-gray-300 mb-1">
                        {tooltip.node.packageName}
                    </div>
                    <div className="text-xs text-gray-300 mb-1">
                        Class: {tooltip.node.className}
                    </div>
                    <div className="text-xs mb-2">
                        Dependencies: {tooltip.node.metadata.dependencyCount} |
                        Providers: {tooltip.node.metadata.providerCount}
                    </div>
                    {tooltip.node.errorInfo.hasErrors && (
                        <div className="text-xs">
                            <span className="text-red-400">
                                {tooltip.node.errorInfo.errorSeverity}:{" "}
                                {tooltip.node.errorInfo.errorTypes.join(", ")}
                            </span>
                        </div>
                    )}
                    {tooltip.node.errorInfo.isPartOfCycle && (
                        <div className="text-xs text-purple-400">
                            ⚠️ Part of dependency cycle
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
