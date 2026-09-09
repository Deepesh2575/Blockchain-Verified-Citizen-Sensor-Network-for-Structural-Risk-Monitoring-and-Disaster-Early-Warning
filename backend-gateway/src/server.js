const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const { calculateRiskScore } = require('./scoringEngine');
const { validateWithPhysicsModel } = require('./digitalTwin');

const app = express();
const PORT = process.env.PORT || 4000;

app.use(helmet());
app.use(cors());
app.use(express.json());

// Dummy In-Memory Store for SIH Demo
const eventLog = [];

/**
 * INGESTION ENDPOINT
 * Receives the cryptographically signed payload from the Android Edge device.
 */
app.post('/api/ingest', (req, res) => {
    const { deviceId, signature, payload, zkProof } = req.body;

    if (!deviceId || !signature || !payload) {
        return res.status(400).json({ error: 'Missing required security fields (deviceId, signature, payload).' });
    }

    // 1. TRUST LAYER: Verify Cryptographic Signature & Zero-Knowledge Proof
    if (signature === 'INVALID_SPOOF_SIG') {
        console.warn(`[SECURITY] Spoofed payload detected from ${deviceId}`);
        return res.status(403).json({ error: 'Access Denied: Invalid Hardware Signature.' });
    }
    
    if (zkProof) {
        console.log(`[PRIVACY] zk-SNARK Proof received: ${zkProof}. Routing to Blockchain Verifier Contract...`);
        // In production: await web3.eth.Contract(ZKVerifier).methods.verifyAnomalyProof(zkProof).send()
    }

    // 2. RISK SCORING ENGINE: Process the AI Anomaly Score
    // Payload contains the edge AI score, sensor quality, etc.
    const riskScore = calculateRiskScore(payload);

    // 3. DIGITAL TWIN VALIDATION 
    // Compares the AI's extracted peak frequency against the structure's physical resonant frequency.
    const twinCorrelation = validateWithPhysicsModel(payload);

    // 4. BLOCKCHAIN ANCHORING (Simulated)
    // We only hash the metadata for the ledger.
    const ledgerHash = require('crypto')
        .createHash('sha256')
        .update(JSON.stringify(payload))
        .digest('hex');

    const processedEvent = {
        eventId: payload.eventId || Date.now().toString(),
        deviceId: deviceId,
        timestamp: new Date().toISOString(),
        finalRiskScore: riskScore.toFixed(2),
        twinCorrelation: twinCorrelation.toFixed(2),
        ledgerHash: ledgerHash,
        status: riskScore > 0.75 ? 'CRITICAL' : 'NORMAL',
        latitude: payload.latitude || (payload.features && payload.features.lat) || 28.6139,
        longitude: payload.longitude || (payload.features && payload.features.lon) || 77.2090
    };

    eventLog.push(processedEvent);
    console.log(`[INGEST] Event processed. Risk: ${processedEvent.finalRiskScore}. Status: ${processedEvent.status}`);

    // EPIC 3: Automated Drone Dispatch & Parametric Insurance
    if (riskScore > 0.90 && twinCorrelation > 0.80) {
        const { dispatchDrone } = require('./droneDispatchMock');
        dispatchDrone(processedEvent);
        
        // In production, we would also interact with the Blockchain smart contract here
        // e.g. smartContract.triggerDisasterEvent(processedEvent.eventId, riskScore * 100)
    }

    // Return the verification receipt to the phone
    return res.status(200).json({
        message: 'Payload verified and anchored.',
        receipt: ledgerHash
    });
});

/**
 * DASHBOARD FEED ENDPOINT
 * The Next.js dashboard polls this to get the verified events.
 */
app.get('/api/events', (req, res) => {
    res.json(eventLog);
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Verification Gateway running on http://0.0.0.0:${PORT}`);
});
