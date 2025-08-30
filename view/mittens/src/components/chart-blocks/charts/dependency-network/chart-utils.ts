import demoData from "@/data/knit-demo-dependency-graph.json";
import { parseKnitDataForD3, NetworkData } from "@/lib/knit-data-parser";

// D3.js network data cache
let cachedNetworkData: NetworkData | null = null;

export const getNodeColor = (status: string) => {
  switch (status) {
    case 'vulnerable':
      return '#ef4444'; // red
    case 'deprecated':
      return '#f97316'; // orange
    case 'conflict':
      return '#eab308'; // yellow
    default:
      return '#10b981'; // green
  }
};

export const getStatusDescription = (status: string) => {
  switch(status) {
    case 'vulnerable': 
      return 'Vulnerable - Security issues detected';
    case 'deprecated': 
      return 'Deprecated - Consider updating';
    case 'conflict': 
      return 'Conflict - Version mismatch';
    default: 
      return 'Up to date';
  }
};

// Empty network data for rendering an empty graph when no data is provided
export const getEmptyNetworkData = (): NetworkData => {
  return {
    nodes: [],
    links: [],
    errorContext: {
      totalErrors: 0,
      totalWarnings: 0,
      cycles: [],
      unresolvedDependencies: [],
      issueDetails: [],
      cycleMap: new Map(),
      nodeErrorMap: new Map(),
      edgeErrorMap: new Map(),
    },
    metadata: {
      projectName: "",
      analysisTimestamp: Date.now(),
      totalComponents: 0,
      totalDependencies: 0,
      componentsWithErrors: 0,
      healthyComponents: 0,
    },
  };
};

// D3.js-compatible data function
export const getD3NetworkData = (): NetworkData => {
  if (!cachedNetworkData) {
    cachedNetworkData = parseKnitDataForD3(demoData as any);
  }
  return cachedNetworkData;
};

// Parse imported Knit data to D3 network format
export const parseKnitDataToD3Network = (knitData: any): NetworkData => {
  try {
    return parseKnitDataForD3(knitData);
  } catch (error) {
    console.error('Error parsing Knit data:', error);
    // Return empty graph if parsing fails
    return getEmptyNetworkData();
  }
};
