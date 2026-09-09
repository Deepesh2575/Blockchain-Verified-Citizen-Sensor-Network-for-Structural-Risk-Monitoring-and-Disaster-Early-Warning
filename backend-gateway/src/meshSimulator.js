/**
 * Epic 12: Virtual Node Cluster (BLE Mesh Hack)
 * Run this during the hackathon pitch to visually simulate a 5-node BLE mesh 
 * routing an SOS payload hop-by-hop when the main network is down.
 */

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

async function runMeshSimulation() {
    console.log(`\n========================================================`);
    console.log(`🌐 [BLE MESH] Initializing Virtual Node Cluster (5 Nodes)...`);
    
    const payload = "SOS|GFORCE:12.4|ID:DEVICE-A19";
    
    await sleep(1000);
    console.log(`\n🚨 [NODE 1] Subterranean Victim (No Internet) Broadcasting: ${payload}`);
    console.log(`   └─ Scanning BLE Advertising Channels...`);
    
    await sleep(800);
    console.log(`\n🔗 [NODE 2] Handshake successful! RSSI: -45dBm`);
    console.log(`   ├─ Routing payload deeper into mesh...`);
    console.log(`   └─ Scanning for stronger perimeter signal...`);
    
    await sleep(1200);
    console.log(`\n🔗 [NODE 3] Handshake successful! RSSI: -55dBm`);
    console.log(`   ├─ Routing payload...`);
    console.log(`   └─ Scanning...`);
    
    await sleep(900);
    console.log(`\n🔗 [NODE 5] PERIMETER DEVICE FOUND! Internet Connection: ACTIVE (5G)`);
    console.log(`   ├─ Offloading BLE payload to Macro Network...`);
    console.log(`   └─ POST /api/ingest`);
    
    await sleep(1500);
    console.log(`\n✅ [MESH SUCCESS] Payload delivered to Authority Gateway via Ad-Hoc Routing!`);
    console.log(`========================================================\n`);
}

runMeshSimulation();
