import { SimulationNodeDatum, SimulationLinkDatum } from "d3";

export interface KnitNode {
    id: string;
    label: string;
    type: string;
    packageName: string;
    className: string;
    metadata: {
        sourceFile: string;
        dependencyCount: number;
        providerCount: number;
        issueCount: number;
    };
    errorHighlight: {
        hasErrors: boolean;
        errorSeverity: string | null;
        errorTypes: string[];
        isPartOfCycle: boolean;
        cycleId: string | null;
        visualHints: {
            borderColor: string;
            backgroundColor: string;
            borderWidth: number;
            classes: string[];
        };
    };
}

export interface KnitEdge {
    id: string;
    source: string;
    target: string;
    type: "DEPENDENCY" | "PROVIDES";
    label: string;
    metadata: {
        isNamed: boolean;
        namedQualifier: string | null;
        isSingleton: boolean;
        isFactory: boolean;
    };
    errorHighlight: {
        hasErrors: boolean;
        errorSeverity: string | null;
        errorTypes: string[];
        isPartOfCycle: boolean;
        cycleId: string | null;
        visualHints: {
            borderColor: string;
            color: string;
            width: number;
            style: string;
            classes: string[];
        };
    };
}

export interface KnitCycle {
    id: string;
    path: string[];
    nodeIds: string[];
    edgeIds: string[];
    severity: string;
}

export interface KnitIssueDetail {
    id: string;
    type: string;
    severity: string;
    message: string;
    affectedNodes: string[];
    affectedEdges: string[];
    suggestedFix: string;
    confidenceScore: number;
}

export interface KnitErrorContext {
    totalErrors: number;
    totalWarnings: number;
    cycles: KnitCycle[];
    unresolvedDependencies: Array<{
        fromNodeId: string;
        targetType: string;
        issue: string;
        namedQualifier: string;
    }>;
    issueDetails: KnitIssueDetail[];
}

export interface KnitMetadata {
    projectName: string;
    analysisTimestamp: number;
    totalComponents: number;
    totalDependencies: number;
    componentsWithErrors: number;
    healthyComponents: number;
    knitVersion?: string;
    pluginVersion?: string;
}

export interface KnitGraphData {
    graph: {
        nodes: KnitNode[];
        edges: KnitEdge[];
    };
    errorContext: KnitErrorContext;
    metadata: KnitMetadata;
}

export interface D3Node extends SimulationNodeDatum {
    id: string;
    group: number;
    label: string;
    type: string;
    packageName: string;
    className: string;

    size: number;
    color: string;
    borderColor: string;
    borderWidth: number;

    metadata: {
        sourceFile: string;
        dependencyCount: number;
        providerCount: number;
        issueCount: number;
    };

    errorInfo: {
        hasErrors: boolean;
        errorSeverity: "ERROR" | "WARNING" | null;
        errorTypes: string[];
        isPartOfCycle: boolean;
        cycleId: string | null;
        classes: string[];
    };
}

export interface D3Link extends SimulationLinkDatum<D3Node> {
    id: string;

    value: number;
    type: "DEPENDENCY" | "PROVIDES";
    label: string;

    color: string;
    width: number;
    style: "solid" | "dashed";

    metadata: {
        isNamed: boolean;
        namedQualifier: string | null;
        isSingleton: boolean;
        isFactory: boolean;
    };

    errorInfo: {
        hasErrors: boolean;
        errorSeverity: "ERROR" | "WARNING" | null;
        errorTypes: string[];
        isPartOfCycle: boolean;
        cycleId: string | null;
        classes: string[];
    };
}

export interface ProcessedErrorContext {
    totalErrors: number;
    totalWarnings: number;
    cycles: KnitCycle[];
    unresolvedDependencies: Array<{
        fromNodeId: string;
        targetType: string;
        issue: string;
        namedQualifier: string;
    }>;
    issueDetails: KnitIssueDetail[];

    cycleMap: Map<string, KnitCycle>;
    nodeErrorMap: Map<string, string[]>;
    edgeErrorMap: Map<string, string[]>;
}

export interface NetworkData {
    nodes: D3Node[];
    links: D3Link[];
    errorContext: ProcessedErrorContext;
    metadata: KnitMetadata;
}

export interface ParseConfig {
    minNodeSize?: number;
    maxNodeSize?: number;
    sizeByDependencyCount?: boolean;

    dependencyLinkWeight?: number;
    providesLinkWeight?: number;
    errorLinkWeightMultiplier?: number;

    nodeColors?: {
        healthy: string;
        warning: string;
        error: string;
        cycle: string;
    };

    linkColors?: {
        healthy: string;
        warning: string;
        error: string;
        cycle: string;
    };

    includeErrorDetails?: boolean;
    highlightCycles?: boolean;

    usePackageHierarchy?: boolean;
    clusterByPackage?: boolean;
}

export const defaultParseConfig: ParseConfig = {
    minNodeSize: 30,
    maxNodeSize: 80,
    sizeByDependencyCount: true,
    dependencyLinkWeight: 1,
    providesLinkWeight: 2,
    errorLinkWeightMultiplier: 1.5,
    nodeColors: {
        healthy: "#10b981",
        warning: "#eab308",
        error: "#ef4444",
        cycle: "#8b5cf6",
    },
    linkColors: {
        healthy: "#10b981",
        warning: "#eab308",
        error: "#ef4444",
        cycle: "#8b5cf6",
    },
    includeErrorDetails: true,
    highlightCycles: true,
    usePackageHierarchy: true,
    clusterByPackage: false,
};

function getNodeTypeGroup(type: string): number {
    const typeGroups: Record<string, number> = {
        COMPONENT: 1,
        SERVICE: 2,
        REPOSITORY: 3,
        CONTROLLER: 4,
        CONFIGURATION: 5,
        MODEL: 6,
        UTIL: 7,
    };

    return typeGroups[type.toUpperCase()] || 0;
}

function calculateNodeSize(
    node: KnitNode,
    config: ParseConfig,
    maxDependencies: number
): number {
    if (!config.sizeByDependencyCount) {
        return config.minNodeSize || 30;
    }

    const dependencyRatio =
        maxDependencies > 0
            ? node.metadata.dependencyCount / maxDependencies
            : 0;

    const minSize = config.minNodeSize || 30;
    const maxSize = config.maxNodeSize || 80;

    return minSize + dependencyRatio * (maxSize - minSize);
}

function getNodeColor(node: KnitNode, config: ParseConfig): string {
    const colors = config.nodeColors || defaultParseConfig.nodeColors!;

    if (node.errorHighlight.isPartOfCycle) {
        return colors.cycle;
    }

    if (node.errorHighlight.hasErrors) {
        return node.errorHighlight.errorSeverity === "ERROR"
            ? colors.error
            : colors.warning;
    }

    return colors.healthy;
}

function getLinkColor(edge: KnitEdge, config: ParseConfig): string {
    const colors = config.linkColors || defaultParseConfig.linkColors!;

    if (edge.errorHighlight.isPartOfCycle) {
        return colors.cycle;
    }

    if (edge.errorHighlight.hasErrors) {
        return edge.errorHighlight.errorSeverity === "ERROR"
            ? colors.error
            : colors.warning;
    }

    return colors.healthy;
}

function getLinkWeight(edge: KnitEdge, config: ParseConfig): number {
    let baseWeight =
        edge.type === "DEPENDENCY"
            ? config.dependencyLinkWeight || 1
            : config.providesLinkWeight || 2;

    if (edge.errorHighlight.hasErrors) {
        baseWeight *= config.errorLinkWeightMultiplier || 1.5;
    }

    return baseWeight;
}

function processErrorContext(
    errorContext: KnitErrorContext
): ProcessedErrorContext {
    const cycleMap = new Map<string, KnitCycle>();
    const nodeErrorMap = new Map<string, string[]>();
    const edgeErrorMap = new Map<string, string[]>();

    errorContext.cycles.forEach((cycle) => {
        cycleMap.set(cycle.id, cycle);

        cycle.nodeIds.forEach((nodeId) => {
            if (!nodeErrorMap.has(nodeId)) {
                nodeErrorMap.set(nodeId, []);
            }
            nodeErrorMap.get(nodeId)!.push("CIRCULAR_DEPENDENCY");
        });

        cycle.edgeIds.forEach((edgeId) => {
            if (!edgeErrorMap.has(edgeId)) {
                edgeErrorMap.set(edgeId, []);
            }
            edgeErrorMap.get(edgeId)!.push("CIRCULAR_DEPENDENCY");
        });
    });

    errorContext.issueDetails.forEach((issue) => {
        issue.affectedNodes.forEach((nodeId) => {
            if (!nodeErrorMap.has(nodeId)) {
                nodeErrorMap.set(nodeId, []);
            }
            if (!nodeErrorMap.get(nodeId)!.includes(issue.type)) {
                nodeErrorMap.get(nodeId)!.push(issue.type);
            }
        });

        issue.affectedEdges.forEach((edgeId) => {
            if (!edgeErrorMap.has(edgeId)) {
                edgeErrorMap.set(edgeId, []);
            }
            if (!edgeErrorMap.get(edgeId)!.includes(issue.type)) {
                edgeErrorMap.get(edgeId)!.push(issue.type);
            }
        });
    });

    return {
        ...errorContext,
        cycleMap,
        nodeErrorMap,
        edgeErrorMap,
    };
}

export function transformNodes(
    knitNodes: KnitNode[],
    config: ParseConfig = defaultParseConfig
): D3Node[] {
    const maxDependencies = Math.max(
        ...knitNodes.map((node) => node.metadata.dependencyCount)
    );

    return knitNodes.map((node) => {
        const d3Node: D3Node = {
            id: node.id,
            group: getNodeTypeGroup(node.type),
            label: node.label,
            type: node.type,
            packageName: node.packageName,
            className: node.className,

            size: calculateNodeSize(node, config, maxDependencies),
            color: getNodeColor(node, config),
            borderColor: node.errorHighlight.visualHints.borderColor,
            borderWidth: node.errorHighlight.visualHints.borderWidth,

            metadata: {
                sourceFile: node.metadata.sourceFile,
                dependencyCount: node.metadata.dependencyCount,
                providerCount: node.metadata.providerCount,
                issueCount: node.metadata.issueCount,
            },

            errorInfo: {
                hasErrors: node.errorHighlight.hasErrors,
                errorSeverity: node.errorHighlight.errorSeverity as
                    | "ERROR"
                    | "WARNING"
                    | null,
                errorTypes: [...node.errorHighlight.errorTypes],
                isPartOfCycle: node.errorHighlight.isPartOfCycle,
                cycleId: node.errorHighlight.cycleId,
                classes: [...node.errorHighlight.visualHints.classes],
            },
        };

        if (config.usePackageHierarchy) {
            const packageParts = node.packageName.split(".");
            const depth = packageParts.length;

            d3Node.fx = undefined;
            d3Node.fy = undefined;

            (d3Node as any).hierarchy = {
                depth,
                packageParts,
                rootPackage: packageParts[0] || "",
                leafPackage: packageParts[packageParts.length - 1] || "",
            };
        }

        return d3Node;
    });
}

export function transformEdges(
    knitEdges: KnitEdge[],
    config: ParseConfig = defaultParseConfig
): D3Link[] {
    return knitEdges.map((edge) => {
        const d3Link: D3Link = {
            id: edge.id,
            source: edge.source,
            target: edge.target,
            value: getLinkWeight(edge, config),
            type: edge.type,
            label: edge.label,

            color: getLinkColor(edge, config),
            width: edge.errorHighlight.visualHints.width,
            style:
                edge.errorHighlight.visualHints.style === "dashed"
                    ? "dashed"
                    : "solid",

            metadata: {
                isNamed: edge.metadata.isNamed,
                namedQualifier: edge.metadata.namedQualifier,
                isSingleton: edge.metadata.isSingleton,
                isFactory: edge.metadata.isFactory,
            },

            errorInfo: {
                hasErrors: edge.errorHighlight.hasErrors,
                errorSeverity: edge.errorHighlight.errorSeverity as
                    | "ERROR"
                    | "WARNING"
                    | null,
                errorTypes: [...edge.errorHighlight.errorTypes],
                isPartOfCycle: edge.errorHighlight.isPartOfCycle,
                cycleId: edge.errorHighlight.cycleId,
                classes: [...edge.errorHighlight.visualHints.classes],
            },
        };

        return d3Link;
    });
}

export function parseKnitDataForD3(
    knitData: KnitGraphData,
    config: ParseConfig = defaultParseConfig
): NetworkData {
    try {
        const processedErrorContext = processErrorContext(
            knitData.errorContext
        );

        const nodes = transformNodes(knitData.graph.nodes, config);
        const links = transformEdges(knitData.graph.edges, config);

        const nodeIds = new Set(nodes.map((node) => node.id));
        const validLinks = links.filter((link) => {
            const sourceId =
                typeof link.source === "string"
                    ? link.source
                    : typeof link.source === "object" &&
                      link.source !== null &&
                      "id" in link.source
                    ? (link.source as D3Node).id
                    : "";
            const targetId =
                typeof link.target === "string"
                    ? link.target
                    : typeof link.target === "object" &&
                      link.target !== null &&
                      "id" in link.target
                    ? (link.target as D3Node).id
                    : "";
            return nodeIds.has(sourceId) && nodeIds.has(targetId);
        });

        if (validLinks.length !== links.length) {
            console.warn(
                `Filtered out ${
                    links.length - validLinks.length
                } links with invalid node references`
            );
        }

        return {
            nodes,
            links: validLinks,
            errorContext: processedErrorContext,
            metadata: knitData.metadata,
        };
    } catch (error) {
        console.error("Error parsing knit data for D3:", error);
        throw new Error(
            `Failed to parse knit data: ${
                error instanceof Error ? error.message : "Unknown error"
            }`
        );
    }
}

export function parseKnitJsonForD3(
    jsonString: string,
    config: ParseConfig = defaultParseConfig
): NetworkData {
    try {
        const knitData: KnitGraphData = JSON.parse(jsonString);
        return parseKnitDataForD3(knitData, config);
    } catch (error) {
        console.error("Error parsing knit JSON:", error);
        throw new Error(
            `Failed to parse knit JSON: ${
                error instanceof Error ? error.message : "Invalid JSON"
            }`
        );
    }
}

export function getNodeById(
    networkData: NetworkData,
    nodeId: string
): D3Node | undefined {
    return networkData.nodes.find((node) => node.id === nodeId);
}

export function getLinksForNode(
    networkData: NetworkData,
    nodeId: string
): D3Link[] {
    return networkData.links.filter((link) => {
        const sourceId =
            typeof link.source === "string"
                ? link.source
                : typeof link.source === "object" &&
                  link.source !== null &&
                  "id" in link.source
                ? (link.source as D3Node).id
                : "";
        const targetId =
            typeof link.target === "string"
                ? link.target
                : typeof link.target === "object" &&
                  link.target !== null &&
                  "id" in link.target
                ? (link.target as D3Node).id
                : "";
        return sourceId === nodeId || targetId === nodeId;
    });
}

export function getNodesInCycle(
    networkData: NetworkData,
    cycleId: string
): D3Node[] {
    const cycle = networkData.errorContext.cycleMap.get(cycleId);
    if (!cycle) return [];

    return networkData.nodes.filter((node) => cycle.nodeIds.includes(node.id));
}

export function getLinksInCycle(
    networkData: NetworkData,
    cycleId: string
): D3Link[] {
    const cycle = networkData.errorContext.cycleMap.get(cycleId);
    if (!cycle) return [];

    return networkData.links.filter((link) => cycle.edgeIds.includes(link.id));
}

export function getNetworkStats(networkData: NetworkData) {
    const totalNodes = networkData.nodes.length;
    const totalLinks = networkData.links.length;
    const nodesWithErrors = networkData.nodes.filter(
        (node) => node.errorInfo.hasErrors
    ).length;
    const linksWithErrors = networkData.links.filter(
        (link) => link.errorInfo.hasErrors
    ).length;
    const cycleCount = networkData.errorContext.cycles.length;

    const nodeTypes = new Map<string, number>();
    networkData.nodes.forEach((node) => {
        nodeTypes.set(node.type, (nodeTypes.get(node.type) || 0) + 1);
    });

    return {
        totalNodes,
        totalLinks,
        nodesWithErrors,
        linksWithErrors,
        cycleCount,
        nodeTypes: Object.fromEntries(nodeTypes),
        healthPercentage: (
            ((totalNodes - nodesWithErrors) / totalNodes) *
            100
        ).toFixed(1),
    };
}
