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

const FORCE_CONFIG = {
    LINK_DISTANCE: 300,
    LINK_STRENGTH: 0.5,
    REPEL_STRENGTH: -450,
    COLLISION_PADDING: 18,
    CLUSTER_STRENGTH: 0.05,
    SIDE_PANEL_WIDTH_RATIO: 1,
} as const;

const ANIMATION_DURATIONS = {
    ZOOM: 300,
    RESET: 500,
    CENTER: 500,
} as const;

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

export default function D3Network({
    data,
    width = 800,
    height = 400,
    onNodeClick,
    onLinkClick,
    showZoomControls = true,
    selectedNode = null,
}: D3NetworkProps) {
    const svgRef = useRef<SVGSVGElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const [tooltip, setTooltip] = useState<TooltipData | null>(null);
    const [mounted, setMounted] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(false);
    const zoomRef = useRef<d3.ZoomBehavior<SVGSVGElement, unknown> | null>(
        null
    );
    const [containerSize, setContainerSize] = useState<{
        width: number;
        height: number;
    }>({ width: 800, height: 400 });
    const labelsRef = useRef<any>(null);
    const nodesRef = useRef<any>(null);
    const linksRef = useRef<any>(null);
    const hasSelectionRef = useRef<boolean>(false);

    useEffect(() => {
        setMounted(true);

        setIsDarkMode(document.documentElement.classList.contains("dark"));
    }, []);

    useEffect(() => {
        if (!mounted) return;

        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                if (
                    mutation.type === "attributes" &&
                    mutation.attributeName === "class"
                ) {
                    const newIsDarkMode =
                        document.documentElement.classList.contains("dark");
                    setIsDarkMode(newIsDarkMode);
                }
            });
        });

        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ["class"],
        });

        return () => observer.disconnect();
    }, [mounted]);

    useEffect(() => {
        if (labelsRef.current) {
            labelsRef.current
                .attr("fill", isDarkMode ? "#ffffff" : "#434850ff")
                .style(
                    "text-shadow",
                    isDarkMode
                        ? "2px 2px 4px rgba(0,0,0,0.9)"
                        : "1px 1px 2px rgba(255,255,255,0.8)"
                );
        }
        if (nodesRef.current) {
            nodesRef.current
                .attr("stroke", isDarkMode ? "#ffffff" : "#374151")
                .attr("stroke-width", 2.5);
        }
    }, [isDarkMode]);

    useEffect(() => {
        hasSelectionRef.current = selectedNode !== null;
    }, [selectedNode]);

    useEffect(() => {
        if (!mounted || !containerRef.current) return;
        const el = containerRef.current;
        const ro = new ResizeObserver((entries) => {
            for (const entry of entries) {
                const { width: w, height: h } = entry.contentRect;
                setContainerSize({
                    width: Math.max(1, Math.floor(w)),
                    height: Math.max(1, Math.floor(h)),
                });
            }
        });
        ro.observe(el);
        return () => ro.disconnect();
    }, [mounted]);

    useEffect(() => {
        if (!mounted || !svgRef.current || !data.nodes.length) return;

        const numericWidth =
            typeof width === "number" ? width : containerSize.width || 800;
        const numericHeight =
            typeof height === "number" ? height : containerSize.height || 400;

        const svg = d3.select(svgRef.current);
        svg.selectAll("*").remove();
        svg.attr("width", numericWidth).attr("height", numericHeight);

        const container = svg.append("g").attr("class", "network-container");

        const zoom = d3
            .zoom<SVGSVGElement, unknown>()
            .scaleExtent([0.1, 10])
            .on("zoom", (event) => {
                container.attr("transform", event.transform);
            });

        svg.call(zoom);
        zoomRef.current = zoom;

        svg.on("click", () => {
            onNodeClick?.(null);
        });

        const nodes = data.nodes.map((d) => ({ ...d }));
        const links = data.links.map((d) => ({ ...d }));

        const getNodeColor = (node: D3Node) => {
            if (node.errorInfo.isPartOfCycle) return "#8b5cf6";
            if (node.errorInfo.hasErrors) {
                switch (node.errorInfo.errorSeverity) {
                    case "ERROR":
                        return "#ef4444";
                    case "WARNING":
                        return "#eab308";
                    default:
                        return "#10b981";
                }
            }
            return "#10b981";
        };

        const getNodeDims = (node: D3Node) => {
            const dep = node.metadata.dependencyCount || 0;
            const baseW = 120;
            const baseH = 44;
            const maxW = 260;
            const maxH = 96;
            const scale = Math.log(dep + 1);
            const width = Math.min(baseW + scale * 28, maxW);
            const height = Math.min(baseH + scale * 8, maxH);
            return { width, height };
        };

        const getCollisionRadius = (node: D3Node) => {
            const { width, height } = getNodeDims(node);
            return 0.5 * Math.sqrt(width * width + height * height);
        };

        const clusters: Record<string, { x: number; y: number }> = {};
        const clusterTags = Array.from(
            new Set(nodes.map((n) => n.packageName))
        );
        const clusterRadius = Math.min(numericWidth, numericHeight) / 3;

        clusterTags.forEach((tag, i) => {
            const angle = (2 * Math.PI * i) / clusterTags.length;
            clusters[tag] = {
                x: numericWidth / 2 + clusterRadius * Math.cos(angle),
                y: numericHeight / 2 + clusterRadius * Math.sin(angle),
            };
        });

        function forceCluster(alpha: number) {
            nodes.forEach((node: any) => {
                const cluster = clusters[node.packageName];

                if (!node._manuallyPositioned) {
                    node.vx +=
                        (cluster.x - node.x) *
                        FORCE_CONFIG.CLUSTER_STRENGTH *
                        alpha;
                    node.vy +=
                        (cluster.y - node.y) *
                        FORCE_CONFIG.CLUSTER_STRENGTH *
                        alpha;
                }
            });
        }

        function forceCustomCenter(alpha: number) {
            const centerX = numericWidth / 2;
            const centerY = numericHeight / 2;

            nodes.forEach((node: any) => {
                if (!node._manuallyPositioned) {
                    const dx = centerX - node.x;
                    const dy = centerY - node.y;
                    const centerStrength = 0.02;

                    node.vx += dx * centerStrength * alpha;
                    node.vy += dy * centerStrength * alpha;
                }
            });
        }

        function forceAnchor(alpha: number) {
            nodes.forEach((node: any) => {
                if (
                    node._manuallyPositioned &&
                    node._targetX !== undefined &&
                    node._targetY !== undefined
                ) {
                    const dx = node._targetX - node.x;
                    const dy = node._targetY - node.y;
                    const distance = Math.sqrt(dx * dx + dy * dy);

                    const isRecentlyPositioned =
                        node._positionedAt &&
                        Date.now() - node._positionedAt < 10000;

                    if (isRecentlyPositioned) {
                        const anchorStrength = 0.99;
                        node.vx += dx * anchorStrength;
                        node.vy += dy * anchorStrength;
                    } else {
                        const maxDistance = 50;
                        const strengthMultiplier = Math.max(
                            0,
                            (distance - 5) / maxDistance
                        );
                        const anchorStrength = 0.8 * strengthMultiplier;

                        if (distance > 5) {
                            node.vx += dx * anchorStrength * alpha;
                            node.vy += dy * anchorStrength * alpha;
                        }
                    }
                }
            });
        }

        const simulation = d3
            .forceSimulation(nodes)
            .alphaDecay(0.0228)
            .velocityDecay(0.4)
            .force(
                "link",
                d3
                    .forceLink(links)
                    .id((d: any) => d.id)
                    .distance((d: any) => {
                        const sourceNode =
                            typeof d.source === "object"
                                ? d.source
                                : nodes.find((n) => n.id === d.source);
                        const targetNode =
                            typeof d.target === "object"
                                ? d.target
                                : nodes.find((n) => n.id === d.target);

                        let baseDistance = FORCE_CONFIG.LINK_DISTANCE;

                        const sourceBeingDragged =
                            sourceNode && sourceNode.fx !== null;
                        const targetBeingDragged =
                            targetNode && targetNode.fx !== null;

                        if (sourceBeingDragged || targetBeingDragged) {
                            return baseDistance * 0.2;
                        }

                        const sourceManuallyPositioned =
                            sourceNode && sourceNode._manuallyPositioned;
                        const targetManuallyPositioned =
                            targetNode && targetNode._manuallyPositioned;

                        if (
                            sourceManuallyPositioned ||
                            targetManuallyPositioned
                        ) {
                            return baseDistance * 0.7;
                        }

                        return baseDistance;
                    })
                    .strength((d: any) => {
                        const sourceNode =
                            typeof d.source === "object"
                                ? d.source
                                : nodes.find((n) => n.id === d.source);
                        const targetNode =
                            typeof d.target === "object"
                                ? d.target
                                : nodes.find((n) => n.id === d.target);

                        let baseStrength = FORCE_CONFIG.LINK_STRENGTH;

                        const sourceBeingDragged =
                            sourceNode && sourceNode.fx !== null;
                        const targetBeingDragged =
                            targetNode && targetNode.fx !== null;

                        if (sourceBeingDragged || targetBeingDragged) {
                            return baseStrength * 0.8;
                        }

                        const sourceClickLocked =
                            sourceNode && (sourceNode as any)._clickLocked;
                        const targetClickLocked =
                            targetNode && (targetNode as any)._clickLocked;

                        if (sourceClickLocked || targetClickLocked) {
                            return 0;
                        }

                        const sourceDragPositioned =
                            sourceNode && (sourceNode as any)._dragPositioned;
                        const targetDragPositioned =
                            targetNode && (targetNode as any)._dragPositioned;

                        if (sourceDragPositioned || targetDragPositioned) {
                            return baseStrength * 0.8;
                        }

                        const sourceManuallyPositioned =
                            sourceNode && sourceNode._manuallyPositioned;
                        const targetManuallyPositioned =
                            targetNode && targetNode._manuallyPositioned;

                        if (
                            sourceManuallyPositioned &&
                            targetManuallyPositioned
                        ) {
                            return baseStrength * 0.4;
                        }

                        if (
                            sourceManuallyPositioned ||
                            targetManuallyPositioned
                        ) {
                            return baseStrength * 0.7;
                        }

                        return baseStrength;
                    })
            )
            .force(
                "charge",
                d3
                    .forceManyBody()
                    .strength(FORCE_CONFIG.REPEL_STRENGTH)
                    .distanceMin(40)
                    .distanceMax(Math.max(numericWidth, numericHeight))
            )
            .force("center", forceCustomCenter as any)
            .force(
                "collision",
                d3
                    .forceCollide()
                    .radius(
                        (d) =>
                            getCollisionRadius(d as D3Node) +
                            FORCE_CONFIG.COLLISION_PADDING
                    )
            )
            .force("cluster", forceCluster as any)
            .force("anchor", forceAnchor as any);

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

        linksRef.current = link;

        const node = container
            .selectAll(".node")
            .data(nodes)
            .enter()
            .append("rect")
            .attr("class", "node")
            .attr("width", (d: any) => getNodeDims(d as D3Node).width)
            .attr("height", (d: any) => getNodeDims(d as D3Node).height)
            .attr("x", (d: any) => -getNodeDims(d as D3Node).width / 2)
            .attr("y", (d: any) => -getNodeDims(d as D3Node).height / 2)
            .attr("rx", (d: any) =>
                Math.min(10, getNodeDims(d as D3Node).height / 2)
            )
            .attr("ry", (d: any) =>
                Math.min(10, getNodeDims(d as D3Node).height / 2)
            )
            .attr("fill", (d: any) => getNodeColor(d as D3Node))
            .attr("stroke", isDarkMode ? "#ffffff" : "#374151")
            .attr("stroke-width", 2.5)
            .style("cursor", "pointer")
            .call(
                d3
                    .drag<SVGRectElement, D3Node>()
                    .on("start", (event, d) => {
                        delete (d as any)._clickLocked;

                        delete (d as any)._manuallyPositioned;
                        delete (d as any)._targetX;
                        delete (d as any)._targetY;
                        delete (d as any)._dragPositioned;

                        if (!event.active)
                            simulation.alphaTarget(0.5).restart();

                        d.fx = d.x;
                        d.fy = d.y;
                    })
                    .on("drag", (event, d) => {
                        d.fx = event.x;
                        d.fy = event.y;

                        simulation.alphaTarget(0.5);
                    })
                    .on("end", (event, d) => {
                        (d as any)._manuallyPositioned = true;
                        (d as any)._targetX = event.x;
                        (d as any)._targetY = event.y;
                        (d as any)._dragPositioned = true;
                        (d as any)._positionedAt = Date.now();

                        d.x = event.x;
                        d.y = event.y;
                        d.fx = null;
                        d.fy = null;

                        simulation.alphaTarget(0.02);
                    })
            );

        const labels = container
            .selectAll(".label")
            .data(nodes)
            .enter()
            .append("text")
            .attr("class", "label")
            .attr("text-anchor", "middle")
            .attr("dominant-baseline", "middle")
            .attr("fill", isDarkMode ? "#ffffff" : "#1f2937")
            .attr("font-size", "15px")
            .attr(
                "font-family",
                "Inter, ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans'"
            )
            .attr("font-weight", "bold")
            .attr("pointer-events", "none")
            .style(
                "text-shadow",
                isDarkMode
                    ? "2px 2px 4px rgba(0,0,0,0.9)"
                    : "1px 1px 2px rgba(255,255,255,0.8)"
            )
            .text((d: any) => d.label)
            .each(function (d: any) {
                const el = this as SVGTextElement;
                (d as any)._origLabel = d.label;
                const dims = getNodeDims(d as D3Node);
                const maxWidth = Math.max(24, dims.width - 16);
                const baseSize = 15;

                const measure = () => el.getComputedTextLength();

                el.setAttribute("font-size", `${baseSize}px`);
                let width = measure();
                if (width > 0) {
                    const scale = Math.min(1, maxWidth / width);
                    const size = Math.max(12, Math.floor(baseSize * scale));
                    el.setAttribute("font-size", `${size}px`);
                }

                width = measure();
                if (width > maxWidth) {
                    const text = (d as any)._origLabel as string;
                    const ratio = maxWidth / width;
                    const keep = Math.max(
                        3,
                        Math.floor(text.length * ratio) - 1
                    );
                    const truncated =
                        text.length > keep ? `${text.slice(0, keep)}…` : text;
                    el.textContent = truncated;

                    const current = parseInt(
                        el.getAttribute("font-size") || "15",
                        10
                    );
                    el.setAttribute("font-size", `${Math.max(12, current)}px`);
                }
            });

        labelsRef.current = labels;
        nodesRef.current = node;

        node.on("click", (event: any, d: any) => {
            event.stopPropagation();
            const nodeData = d as D3Node;

            nodeData.fx = nodeData.x;
            nodeData.fy = nodeData.y;

            (nodeData as any)._manuallyPositioned = true;
            (nodeData as any)._targetX = nodeData.x;
            (nodeData as any)._targetY = nodeData.y;
            (nodeData as any)._clickLocked = true;

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

        simulation.on("tick", () => {
            link.attr("x1", (d: any) => d.source.x)
                .attr("y1", (d: any) => d.source.y)
                .attr("x2", (d: any) => d.target.x)
                .attr("y2", (d: any) => d.target.y)
                .attr("stroke-width", (d: any) => {
                    const dx = d.target.x - d.source.x;
                    const dy = d.target.y - d.source.y;
                    const distance = Math.sqrt(dx * dx + dy * dy);

                    const normalDistance = FORCE_CONFIG.LINK_DISTANCE;
                    const stretchFactor = Math.max(
                        1,
                        distance / normalDistance
                    );
                    const baseWidth = 3;
                    const maxWidth = 6;

                    return Math.min(baseWidth * stretchFactor, maxWidth);
                })
                .attr("stroke-opacity", (d: any) => {
                    const dx = d.target.x - d.source.x;
                    const dy = d.target.y - d.source.y;
                    const distance = Math.sqrt(dx * dx + dy * dy);

                    const normalDistance = FORCE_CONFIG.LINK_DISTANCE;
                    const stretchFactor = distance / normalDistance;
                    const baseOpacity = 0.6;
                    const maxOpacity = 0.9;

                    return Math.min(
                        baseOpacity + (stretchFactor - 1) * 0.3,
                        maxOpacity
                    );
                });

            node.attr("x", (d: any) => d.x - getNodeDims(d as D3Node).width / 2)
                .attr(
                    "y",
                    (d: any) => d.y - getNodeDims(d as D3Node).height / 2
                )
                .attr("width", (d: any) => getNodeDims(d as D3Node).width)
                .attr("height", (d: any) => getNodeDims(d as D3Node).height);

            labels.attr("x", (d: any) => d.x).attr("y", (d: any) => d.y);
        });

        return () => {
            simulation.stop();
        };
    }, [mounted, data, width, height]);

    useEffect(() => {
        if (!mounted) return;
        const nodesSel = nodesRef.current;
        const labelsSel = labelsRef.current;
        const linksSel = linksRef.current;
        if (!nodesSel || !labelsSel) return;

        if (!selectedNode) {
            nodesSel.attr("opacity", 1).attr("stroke-width", 2.5);
            labelsSel.attr("opacity", 1).attr("font-size", "15px");
            if (linksSel) {
                linksSel.attr("stroke-opacity", 0.8).attr("stroke-width", 3);
            }
            return;
        }

        nodesSel
            .attr("opacity", (d: any) => (d.id === selectedNode.id ? 1 : 0.25))
            .attr("stroke-width", (d: any) =>
                d.id === selectedNode.id ? 4 : 2
            );
        labelsSel
            .attr("opacity", (d: any) => (d.id === selectedNode.id ? 1 : 0.35))
            .attr("font-size", (d: any) =>
                d.id === selectedNode.id ? "18px" : "15px"
            );

        if (linksSel) {
            linksSel
                .attr("stroke-opacity", (d: any) => {
                    const srcId =
                        typeof d.source === "object" ? d.source.id : d.source;
                    const tgtId =
                        typeof d.target === "object" ? d.target.id : d.target;
                    return srcId === selectedNode.id ||
                        tgtId === selectedNode.id
                        ? 0.9
                        : 0.1;
                })
                .attr("stroke-width", (d: any) => {
                    const srcId =
                        typeof d.source === "object" ? d.source.id : d.source;
                    const tgtId =
                        typeof d.target === "object" ? d.target.id : d.target;
                    return srcId === selectedNode.id ||
                        tgtId === selectedNode.id
                        ? 5
                        : 2;
                });
        }
    }, [mounted, selectedNode]);

    useEffect(() => {
        if (!mounted || !svgRef.current) return;

        const numericWidth =
            typeof width === "number" ? width : containerSize.width || 800;
        const numericHeight =
            typeof height === "number" ? height : containerSize.height || 400;

        d3.select(svgRef.current)
            .attr("width", numericWidth)
            .attr("height", numericHeight);
    }, [mounted, containerSize.width, containerSize.height, width, height]);

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
        if (
            svgRef.current &&
            zoomRef.current &&
            node.x !== undefined &&
            node.y !== undefined
        ) {
            const numericWidth =
                typeof width === "number" ? width : containerSize.width || 800;
            const numericHeight =
                typeof height === "number"
                    ? height
                    : containerSize.height || 400;

            const currentTransform = d3.zoomTransform(svgRef.current);
            const currentScale = currentTransform.k;

            const effectiveWidth =
                numericWidth * FORCE_CONFIG.SIDE_PANEL_WIDTH_RATIO;
            const x = effectiveWidth / 2 - node.x * currentScale;
            const y = numericHeight / 2 - node.y * currentScale;

            d3.select(svgRef.current)
                .transition()
                .duration(ANIMATION_DURATIONS.CENTER)
                .call(
                    zoomRef.current.transform,
                    d3.zoomIdentity.translate(x, y).scale(currentScale)
                );
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
                        <div className="text-xs font-medium text-slate-700 dark:text-slate-300 mb-2">
                            Status Legend
                        </div>
                        <div className="space-y-1">
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 rounded-full bg-green-500"></div>
                                <span className="text-xs text-slate-600 dark:text-slate-400">
                                    Healthy
                                </span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 rounded-full bg-red-500"></div>
                                <span className="text-xs text-slate-600 dark:text-slate-400">
                                    Error
                                </span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 rounded-full bg-yellow-500"></div>
                                <span className="text-xs text-slate-600 dark:text-slate-400">
                                    Warning
                                </span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 rounded-full bg-purple-500"></div>
                                <span className="text-xs text-slate-600 dark:text-slate-400">
                                    Cycle
                                </span>
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
