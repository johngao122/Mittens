import knitDemoData from '@/data/knit-demo-dependency-graph.json';
import { parseKnitDataForD3, getNetworkStats, defaultParseConfig } from './knit-data-parser';

// Test the parser with the sample data
export function testKnitParser() {
  try {
    console.log('Testing knit data parser...');
    
    // Parse the data
    const networkData = parseKnitDataForD3(knitDemoData as any, defaultParseConfig);
    
    // Get statistics
    const stats = getNetworkStats(networkData);
    
    console.log('Parser test successful!');
    console.log('Network Statistics:', stats);
    console.log('Sample nodes:', networkData.nodes.slice(0, 2));
    console.log('Sample links:', networkData.links.slice(0, 2));
    console.log('Error context:', {
      totalErrors: networkData.errorContext.totalErrors,
      cycleCount: networkData.errorContext.cycles.length
    });
    
    return {
      success: true,
      networkData,
      stats
    };
  } catch (error) {
    console.error('Parser test failed:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

// Export for use in components
export { parseKnitDataForD3, getNetworkStats };