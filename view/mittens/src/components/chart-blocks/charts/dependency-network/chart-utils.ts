import demoData from "@/data/knit-demo-dependency-graph.json";
import { parseKnitDataForD3, NetworkData, D3Node, D3Link } from "@/lib/knit-data-parser";

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

// D3.js-compatible data function
export const getD3NetworkData = (): NetworkData => {
  if (!cachedNetworkData) {
    cachedNetworkData = parseKnitDataForD3(demoData as any);
  }
  return cachedNetworkData;
};