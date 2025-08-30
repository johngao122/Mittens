// Quick test to verify the parser works with our knit data
import knitDemoData from '@/data/knit-demo-dependency-graph.json';
import { parseKnitDataForD3, getNetworkStats } from './knit-data-parser';

export function testParserOutput() {
  console.log('🔍 Testing knit data parser...');
  
  try {
    const networkData = parseKnitDataForD3(knitDemoData as any);
    const stats = getNetworkStats(networkData);
    
    console.log('✅ Parser test successful!');
    console.log('📊 Network Statistics:');
    console.log(`   - Nodes: ${stats.totalNodes}`);
    console.log(`   - Links: ${stats.totalLinks}`);
    console.log(`   - Nodes with errors: ${stats.nodesWithErrors}`);
    console.log(`   - Links with errors: ${stats.linksWithErrors}`);
    console.log(`   - Cycles detected: ${stats.cycleCount}`);
    console.log(`   - Health percentage: ${stats.healthPercentage}%`);
    console.log(`   - Node types:`, stats.nodeTypes);
    
    // Sample output
    console.log('🔍 Sample node:', networkData.nodes[0]);
    console.log('🔍 Sample link:', networkData.links[0]);
    
    return { success: true, networkData, stats };
  } catch (error) {
    console.error('❌ Parser test failed:', error);
    return { success: false, error };
  }
}