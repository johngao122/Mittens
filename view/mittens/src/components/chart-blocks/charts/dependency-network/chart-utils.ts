import demoData from "@/data/knit-demo-dependency-graph.json";

interface DependencyNode {
  id: string;
  name: string;
  status: 'normal' | 'vulnerable' | 'deprecated' | 'conflict';
}

interface DependencyLink {
  source: string;
  target: string;
  type?: 'solid' | 'dashed';
}

const linkTypeToVisual: Record<string, "solid" | "dashed"> = {
  "COMPONENT":"solid", 
  "PROVIDER":"solid", 
  "PROVIDES":"solid", 
  "DEPENDENCY":"dashed"
}

type MapKey = keyof typeof linkTypeToVisual;

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

// should include fix -> under errorContext in demoData
export const dependencyData = (): { nodes: DependencyNode[], edges: DependencyNode[], links: DependencyLink[] } => {  
  const nodes : DependencyNode[] = demoData.graph.nodes.map(node => ({
    id: node.id,
    name: node.label,
    status: node.errorHighlight.hasErrors == null ? "normal" : "vulnerable"
  }));

  const edges : DependencyNode[] = demoData.graph.edges.map(edge => ({
    id: edge.id,
    name: edge.label,
    status: edge.errorHighlight.hasErrors == null ? "normal" : "conflict"
  }));

  const links : DependencyLink[] = demoData.graph.edges.map(edge => {
    const typeKey = edge.type as MapKey;
    return {
      source: edge.source,
      target: edge.target,
      type: linkTypeToVisual[typeKey]
    };
  });

  return {nodes, edges, links} ;
};


// // Simplified dependency data with fewer nodes
// export const dependencyData: { nodes: DependencyNode[], links: DependencyLink[] } = {
//   nodes: [
//     { id: 'MyApp', name: 'MyApp', status: 'normal' },
//     { id: 'React', name: 'React', status: 'normal' },
//     { id: 'Webpack', name: 'Webpack', status: 'normal' },
//     { id: 'TypeScript', name: 'TypeScript', status: 'normal' },
//     { id: 'Lodash', name: 'Lodash', status: 'vulnerable' },
//     { id: 'ESLint', name: 'ESLint', status: 'conflict' },
//   ],
//   links: [
//     { source: 'MyApp', target: 'React', type: 'solid' },
//     { source: 'MyApp', target: 'Lodash', type: 'solid' },
//     { source: 'React', target: 'Webpack', type: 'solid' },
//     { source: 'Webpack', target: 'TypeScript', type: 'solid' },
//     { source: 'ESLint', target: 'TypeScript', type: 'dashed' },
//   ]
// };


export const nodePositions = () : { [key: string]: { x: number; y: number } } => {
  // need unique nodes & edges
  // depending on no. of dependency count in node (no. of edges to one node), space them out
  const nodesLabel = demoData.graph.nodes.map(node => node.label );
  const edgesLabel = demoData.graph.nodes.map(edge => edge.label );
  const allLabels = [...nodesLabel, ...edgesLabel];

  const labelMap: { [key: string]: { x: number; y: number } } = {};
  allLabels.forEach((label, index) => {
    labelMap[label] = { x: index, y: index};
  });
  
  return labelMap;
};

// // Simplified node positions for 6 nodes
// export const nodePositions: { [key: string]: { x: number; y: number } } = {
//   'MyApp': { x: 20, y: 50 },       // Left side - main app
//   'React': { x: 45, y: 25 },       // Upper middle
//   'Webpack': { x: 70, y: 15 },     // Top right
//   'TypeScript': { x: 70, y: 50 },  // Middle right
//   'Lodash': { x: 45, y: 75 },      // Bottom middle - vulnerable (red)
//   'ESLint': { x: 70, y: 85 },      // Bottom right - conflict (yellow)
// };

export const createChartSpec = (nodeData: any[], linkData: any[]) => {
  return {
    type: 'common',
    data: [
      {
        id: 'links',
        values: linkData
      },
      {
        id: 'nodes', 
        values: nodeData
      }
    ],
    series: [
      {
        type: 'line',
        dataId: 'links',
        xField: ['x', 'x1'],
        yField: ['y', 'y1'],
        seriesField: 'name',
        line: {
          style: {
            stroke: '#ffffff',
            lineWidth: 2,
            opacity: 0.7
          }
        },
        point: {
          visible: false
        },
        tooltip: {
          visible: false
        }
      },
      {
        type: 'scatter',
        dataId: 'nodes',
        xField: 'x', 
        yField: 'y',
        point: {
          style: {
            size: 60,
            fill: (datum: any) => getNodeColor(datum.status),
            stroke: '#ffffff',
            lineWidth: 3
          }
        },
        label: {
          visible: true,
          style: {
            fontSize: 11,
            fill: '#ffffff',
            fontWeight: 'bold',
            textAlign: 'center',
            textShadow: '2px 2px 4px rgba(0,0,0,0.9)'
          },
          position: 'center',
          formatter: (params: any, datum: any) => {
            return datum.name;
          }
        },
        tooltip: {
          visible: true,
          mark: {
            title: {
              value: (datum: any) => `📦 ${datum.name}`
            },
            content: [
              {
                key: 'Status',
                value: (datum: any) => getStatusDescription(datum.status)
              }
            ]
          }
        }
      }
    ],
    axes: [
      {
        orient: 'bottom',
        visible: false,
        range: [0, 100]
      },
      {
        orient: 'left', 
        visible: false,
        range: [0, 100]
      }
    ],
    background: '#1e293b',
    padding: { top: 30, bottom: 30, left: 30, right: 30 }
  };
};
