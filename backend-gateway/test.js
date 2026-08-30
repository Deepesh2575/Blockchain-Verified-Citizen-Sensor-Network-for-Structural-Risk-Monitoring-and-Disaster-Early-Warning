const http = require('http');

const GATEWAY_URL = 'http://localhost:4000/api/ingest';

function sendPayload(name, payloadData) {
    return new Promise((resolve) => {
        const req = http.request(GATEWAY_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                console.log(`\n--- Test: ${name} ---`);
                console.log(`Status Code: ${res.statusCode}`);
                console.log(`Response: ${data}`);
                resolve();
            });
        });

        req.on('error', error => {
            console.error(`\n--- Test: ${name} ---`);
            console.error(`Error connecting to Gateway: ${error.message}`);
            resolve();
        });

        req.write(JSON.stringify(payloadData));
        req.end();
    });
}

async function runTests() {
    console.log("🚀 Starting E2E System Tests...");

    // Test 1: The Spoofing Attack (Should be rejected by Trust Layer)
    await sendPayload("Spoofed Payload Attack", {
        deviceId: "HACKER_DEVICE_001",
        signature: "INVALID_SPOOF_SIG",
        payload: {
            eventId: "fake-event-123",
            aiAnomalyScore: 0.99,
            sensorTier: "T1"
        }
    });

    // Test 2: Valid High-Risk Structural Anomaly
    // We pass peakFrequencyHz near 2.4 to match the 'downtown_bridge_01' resonant frequency.
    await sendPayload("Valid Structural Anomaly", {
        deviceId: "CITIZEN_DEVICE_402",
        signature: "VALID_ECDSA_SIGNATURE_0x8f2a99",
        payload: {
            eventId: "real-event-999",
            structureId: "downtown_bridge_01",
            peakFrequencyHz: 2.38, // Close to natural freq 2.4
            aiAnomalyScore: 0.92,  // High AI confidence
            sensorTier: "T1",      // Premium hardware
            hasHardwareAttestation: true,
            mockNearbyDetections: 6 // High spatial correlation
        }
    });

    console.log("\n✅ Testing Complete.");
}

runTests();
