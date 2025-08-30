# D3.js Network Graph Data Parser Implementation Plan

## Overview

Build a data parser that converts the knit-demo-dependency-graph.json format into a D3.js-compatible network graph, replacing the current VChart implementation with pure D3.js force-directed visualization.

## Current State Analysis

### Existing Data Structure (knit-demo-dependency-graph.json)

```json
{
    "graph": {
        "nodes": [
            {
                "id": "com.example.knit.demo.core.models.User",
                "label": "User",
                "type": "COMPONENT",
                "packageName": "com.example.knit.demo.core.models",
                "className": "User",
                "metadata": {
                    "sourceFile": "...",
                    "dependencyCount": 0,
                    "providerCount": 0,
                    "issueCount": 0
                },
                "errorHighlight": {
                    "hasErrors": false,
                    "errorSeverity": null,
                    "errorTypes": [],
                    "isPartOfCycle": false,
                    "cycleId": null,
                    "visualHints": {
                        "borderColor": "#28a745",
                        "backgroundColor": "#f8fff9",
                        "borderWidth": 1,
                        "classes": ["healthy-node"]
                    }
                }
            }
        ],
        "edges": [
            {
                "id": "edge_id",
                "source": "source_node_id",
                "target": "target_node_id",
                "type": "DEPENDENCY|PROVIDES",
                "label": "relationship_name",
                "metadata": {
                    "isNamed": false,
                    "namedQualifier": null,
                    "isSingleton": false,
                    "isFactory": false
                },
                "errorHighlight": {
                    "hasErrors": false,
                    "errorSeverity": null,
                    "errorTypes": [],
                    "isPartOfCycle": false,
                    "cycleId": null,
                    "visualHints": {
                        "borderColor": "#28a745",
                        "color": "#28a745",
                        "width": 1,
                        "style": "solid",
                        "classes": ["healthy-edge"]
                    }
                }
            }
        ]
    },
    "errorContext": {
        "totalErrors": 2,
        "totalWarnings": 0,
        "cycles": [
            {
                "id": "cycle_1",
                "path": ["node1", "node2"],
                "nodeIds": ["node1", "node2"],
                "edgeIds": ["edge1", "edge2"],
                "severity": "ERROR"
            }
        ],
        "unresolvedDependencies": [],
        "issueDetails": []
    },
    "metadata": {
        "projectName": "knit-demo",
        "analysisTimestamp": 1756345392456,
        "totalComponents": 24,
        "totalDependencies": 12,
        "componentsWithErrors": 4,
        "healthyComponents": 20
    }
}
```

## Implementation Tasks

### 1. Data Parser Foundation (DONE)

#### Task 1.1: Create Specialized Knit Format Parser

**File**: `view/mittens/src/lib/knit-data-parser.ts`

**Purpose**: Parse knit-demo JSON format specifically for D3.js network graphs

**Features**:

-   Transform knit nodes → D3.js node format with preserved metadata
-   Convert knit edges → D3.js link format with relationship types
-   Extract error context and cycle information
-   Include default values for missing fields
-   Type-safe interfaces matching D3.js requirements

#### Task 1.2: Define D3.js Network Interfaces

**Interfaces to create**:

```typescript
interface D3Node {
    id: string;
    group: number; // for coloring based on type
    label: string;
    type: string;
    packageName: string;
    className: string;
    metadata: NodeMetadata;
    errorInfo: ErrorInfo;
    // D3.js force simulation properties
    x?: number;
    y?: number;
    fx?: number; // fixed position
    fy?: number;
    vx?: number; // velocity
    vy?: number;
    index?: number;
}

interface D3Link {
    source: string | D3Node;
    target: string | D3Node;
    value: number; // link strength/weight
    type: string;
    label: string;
    metadata: EdgeMetadata;
    errorInfo: ErrorInfo;
    // D3.js force simulation properties
    index?: number;
}

interface NetworkData {
    nodes: D3Node[];
    links: D3Link[];
    errorContext: ErrorContext;
    metadata: ProjectMetadata;
}
```

#### Task 1.3: Implement Node Transformation

**Features**:

-   Map knit node types to D3.js groups for coloring
-   Preserve error highlights and cycle information
-   Include dependency/provider counts for sizing
-   Add positioning hints based on package structure
-   Handle missing/default fields gracefully

#### Task 1.4: Implement Edge Transformation

**Features**:

-   Convert knit edge types (PROVIDES, DEPENDENCY) to D3.js link weights
-   Preserve error information for styling
-   Map relationship metadata to link properties
-   Handle circular dependencies and cycles

### 2. D3.js Network Component

#### Task 2.1: Create D3.js Network Component

**File**: `view/mittens/src/components/chart-blocks/charts/dependency-network/d3-network.tsx`

**Base Structure**:

```typescript
interface D3NetworkProps {
    data: NetworkData;
    width?: number;
    height?: number;
    onNodeClick?: (node: D3Node) => void;
    onLinkClick?: (link: D3Link) => void;
}

export default function D3Network({
    data,
    width = 800,
    height = 600,
    onNodeClick,
    onLinkClick,
}: D3NetworkProps) {
    // D3.js implementation
}
```

#### Task 2.2: Implement D3.js Force-Directed Layout

**Features**:

-   Force simulation with collision detection
-   Link forces based on relationship types
-   Center force to keep nodes in view
-   Many-body force for repulsion
-   Interactive features (drag, hover, click)

**D3.js Forces to implement**:

```typescript
const simulation = d3
    .forceSimulation(nodes)
    .force(
        "link",
        d3.forceLink(links).id((d) => d.id)
    )
    .force("charge", d3.forceManyBody().strength(-300))
    .force("center", d3.forceCenter(width / 2, height / 2))
    .force("collision", d3.forceCollide().radius(30));
```

#### Task 2.3: Add Error Visualization Features

**Features**:

-   Node colors based on error states
-   Border styles for different severities
-   Link dashing for problematic relationships
-   Visual indicators for cycles

**Color Scheme**:

-   Healthy: Green (#10b981)
-   Warning: Yellow (#eab308)
-   Error: Red (#ef4444)
-   Cycle: Purple (#8b5cf6)

#### Task 2.4: Implement Cycle Highlighting

**Features**:

-   Distinct colors for nodes in cycles
-   Animated borders or pulsing effects
-   Cycle path tracing
-   Tooltip information about cycle details

#### Task 2.5: Add Zoom and Pan Capabilities

**Features**:

-   Mouse wheel zooming
-   Pan with mouse drag on background
-   Zoom controls (buttons)
-   Reset view functionality
-   Responsive behavior

```typescript
const zoom = d3
    .zoom()
    .scaleExtent([0.1, 10])
    .on("zoom", (event) => {
        svg.select("g").attr("transform", event.transform);
    });
```

### 3. Integration & Migration

#### Task 3.1: Update chart-utils.ts

**Changes**:

-   Replace current `dependencyData()` function
-   Use new knit data parser
-   Maintain backward compatibility during transition
-   Update interfaces and type definitions



#### Task 3.3: Testing and Integration

**Test scenarios**:

-   Parse various knit JSON formats
-   Handle malformed or incomplete data
-   Verify error visualization accuracy
-   Test interactive features
-   Performance with large graphs
-   Responsive behavior

## Key Requirements Met

### 1. Optimized for Knit Format

-   Specialized parser for knit-demo-dependency-graph.json
-   Preserves all rich metadata and error information
-   Handles knit-specific features like cycles and dependency types

### 2. Metadata Preservation

-   Full error context including severity levels
-   Cycle detection and highlighting
-   Provider/dependency relationship information
-   Visual styling hints from original data

### 3. Default Field Handling

-   Graceful handling of missing fields
-   Sensible defaults for optional properties
-   Error reporting for critical missing data
-   Validation of data integrity

### 4. Pure D3.js Implementation

-   Follows D3.js network graph patterns from d3-graph-gallery.com
-   Force-directed layout with customizable forces
-   Interactive features (drag, zoom, hover)
-   SVG-based rendering for scalability

## Dependencies to Add

```json
{
    "d3": "^7.8.5",
    "@types/d3": "^7.4.0"
}
```

## File Structure

```
view/mittens/src/
├── lib/
│   └── knit-data-parser.ts                    # New parser
├── components/chart-blocks/charts/dependency-network/
│   ├── d3-network.tsx                         # New D3.js component
│   ├── chart-utils.ts                         # Updated utilities
│   └── index.tsx                              # Updated main component
└── types/
    └── d3-network.ts                          # New type definitions
```

## Success Criteria

-   [x] Parse knit JSON format into D3.js-compatible structure
-   [x] Preserve all metadata and error information
-   [x] Handle missing fields with defaults
-   [x] Implement interactive D3.js network visualization
-   [x] Replace VChart with pure D3.js implementation
-   [x] Maintain existing UI/UX functionality
-   [x] Support error visualization and cycle highlighting
-   [x] Provide zoom, pan, and interactive capabilities
