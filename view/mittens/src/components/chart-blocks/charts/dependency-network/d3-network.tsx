"use client";

import React, { useRef, useEffect, useState } from "react";
import * as d3 from "d3";
import { NetworkData, D3Node, D3Link } from "../../../../lib/knit-data-parser";

interface D3NetworkProps {
    data: NetworkData;
    width?: number;
    height?: number;
    onNodeClick?: (node: D3Node) => void;
    onLinkClick?: (link: D3Link) => void;
    showZoomControls?: boolean;
}

interface TooltipData {
    node: D3Node;
    x: number;
    y: number;
}

export default function D3Network({
    data,
    width = 800,
    height = 600,
    onNodeClick,
    onLinkClick,
    showZoomControls = true,
}: D3NetworkProps) {
    const svgRef = useRef<SVGSVGElement>(null);
    const [tooltip, setTooltip] = useState<TooltipData | null>(null);
    const zoomRef = useRef<d3.ZoomBehavior<SVGSVGElement, unknown> | null>(
        null
    );

    useEffect(() => {
        if (!svgRef.current || !data.nodes.length) return;

        const svg = d3.select(svgRef.current);
        svg.selectAll("*").remove();

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

        // Create copies of data for D3.js simulation
        const nodes = data.nodes.map((d) => ({ ...d }));
        const links = data.links.map((d) => ({ ...d }));

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
        const clusterRadius = Math.min(width, height) / 3;

        // Assign cluster centers in a circle
        clusterTags.forEach((tag, i) => {
            const angle = (2 * Math.PI * i) / clusterTags.length;
            clusters[tag] = {
                x: width / 2 + clusterRadius * Math.cos(angle),
                y: height / 2 + clusterRadius * Math.sin(angle),
            };
        });

        // Custom cluster force
        function forceCluster(alpha: number) {
            nodes.forEach((node: any) => {
                const cluster = clusters[node.packageName];
                node.vx += (cluster.x - node.x) * 0.1 * alpha;
                node.vy += (cluster.y - node.y) * 0.1 * alpha;
            });
        }

        // Create force simulation
        const simulation = d3
            .forceSimulation(nodes)
            .force(
                "link",
                d3
                    .forceLink(links)
                    .id((d: any) => d.id)
                    .distance(170)
                    .strength(0.5)
            )
            .force("charge", d3.forceManyBody().strength(-100))
            .force("center", d3.forceCenter(width / 2, height / 2))
            .force(
                "collision",
                d3.forceCollide().radius((d) => getNodeSize(d as D3Node) + 5)
            )
            .force("cluster", forceCluster as any);

        // Create links
        const link = container
            .selectAll(".link")
            .data(links)
            .enter()
            .append("line")
            .attr("class", "link")
            .attr("stroke", (d) =>
                d.errorInfo.hasErrors ? "#ef4444" : "#64748b"
            )
            .attr("stroke-width", 2)
            .attr("stroke-opacity", 0.6)
            .attr("stroke-dasharray", (d) =>
                d.errorInfo.hasErrors ? "5,5" : "0"
            );

        // Create nodes
        const node = container
            .selectAll(".node")
            .data(nodes)
            .enter()
            .append("circle")
            .attr("class", "node")
            .attr("r", (d) => getNodeSize(d as D3Node))
            .attr("fill", (d) => getNodeColor(d as D3Node))
            .attr("stroke", "#fff")
            .attr("stroke-width", 3)
            .style("cursor", "pointer")
            .call(
                d3
                    .drag<SVGCircleElement, D3Node>()
                    .on("start", (event, d) => {
                        if (!event.active)
                            simulation.alphaTarget(0.3).restart();
                        d.fx = d.x;
                        d.fy = d.y;
                    })
                    .on("drag", (event, d) => {
                        d.fx = event.x;
                        d.fy = event.y;
                    })
                    .on("end", (event, d) => {
                        if (!event.active) simulation.alphaTarget(0);
                        d.fx = null;
                        d.fy = null;
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
            .attr("fill", "#ffffff")
            .attr("font-size", "12px")
            .attr("font-weight", "bold")
            .attr("pointer-events", "none")
            .style("text-shadow", "2px 2px 4px rgba(0,0,0,0.9)")
            .text((d) => d.label);

        // Add event listeners
        node.on("click", (event, d) => {
            event.stopPropagation();
            onNodeClick?.(d as D3Node);
        })
            .on("mouseenter", (event, d) => {
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

        // Update positions on each tick of the simulation
        simulation.on("tick", () => {
            link.attr("x1", (d: any) => d.source.x)
                .attr("y1", (d: any) => d.source.y)
                .attr("x2", (d: any) => d.target.x)
                .attr("y2", (d: any) => d.target.y);

            node.attr("cx", (d: any) => d.x).attr("cy", (d: any) => d.y);

            labels.attr("x", (d: any) => d.x).attr("y", (d: any) => d.y);
        });

        // Clean up simulation on unmount
        return () => {
            simulation.stop();
        };
    }, [data, width, height]);

    // Zoom control functions
    const handleZoomIn = () => {
        if (svgRef.current && zoomRef.current) {
            d3.select(svgRef.current)
                .transition()
                .duration(300)
                .call(zoomRef.current.scaleBy, 1.5);
        }
    };

    const handleZoomOut = () => {
        if (svgRef.current && zoomRef.current) {
            d3.select(svgRef.current)
                .transition()
                .duration(300)
                .call(zoomRef.current.scaleBy, 1 / 1.5);
        }
    };

    const handleResetZoom = () => {
        if (svgRef.current && zoomRef.current) {
            d3.select(svgRef.current)
                .transition()
                .duration(500)
                .call(zoomRef.current.transform, d3.zoomIdentity);
        }
    };

    return (
        <div className="relative">
            {showZoomControls && (
                <div className="absolute top-4 right-4 z-10 flex flex-col gap-2">
                    <button
                        onClick={handleZoomIn}
                        className="px-3 py-1 bg-slate-700 text-white rounded hover:bg-slate-600 text-sm transition-colors"
                        aria-label="Zoom in"
                    >
                        +
                    </button>
                    <button
                        onClick={handleZoomOut}
                        className="px-3 py-1 bg-slate-700 text-white rounded hover:bg-slate-600 text-sm transition-colors"
                        aria-label="Zoom out"
                    >
                        -
                    </button>
                    <button
                        onClick={handleResetZoom}
                        className="px-2 py-1 bg-slate-700 text-white rounded hover:bg-slate-600 text-xs transition-colors"
                        aria-label="Reset zoom"
                    >
                        Reset
                    </button>
                </div>
            )}
            <svg
                ref={svgRef}
                width={width}
                height={height}
                className="border rounded-lg bg-slate-800"
            />

            {tooltip && (
                <div
                    className="absolute bg-black bg-opacity-90 text-white p-3 rounded-lg shadow-lg pointer-events-none z-10 max-w-xs"
                    style={{
                        left: tooltip.x + 10,
                        top: tooltip.y - 10,
                        transform:
                            tooltip.x > width - 200
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
