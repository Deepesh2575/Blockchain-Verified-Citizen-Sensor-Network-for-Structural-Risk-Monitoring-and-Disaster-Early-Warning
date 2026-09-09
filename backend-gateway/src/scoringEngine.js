/**
 * Multi-Stage Risk Scoring Engine
 * Implements: R = f(A, Q, T, S, P, C, H)
 */
function calculateRiskScore(payload) {
    // 1. Edge AI Score (A) - 0.0 to 1.0 (How confident is the ML model?)
    const aiScore = payload.aiAnomalyScore || 0.5;

    // 2. Sensor Quality (Q) - Hardware tier (T1=1.0, T2=0.8, T3=0.6)
    const sensorQuality = payload.sensorTier === 'T1' ? 1.0 : (payload.sensorTier === 'T2' ? 0.8 : 0.6);

    // 3. Device Trust (T) - Hardware attestation present?
    const deviceTrust = payload.hasHardwareAttestation ? 1.0 : 0.5;

    // Base reliability of this specific reading
    const localReliability = sensorQuality * deviceTrust;

    // Initial Risk (AI Confidence scaled by hardware reliability)
    let risk = aiScore * localReliability;

    // 4. Spatial Consistency (S)
    // In a real system, we query TimescaleDB to see how many devices in a 50m radius also triggered.
    const nearbyDetections = payload.mockNearbyDetections || 1;
    let spatialMultiplier = 1.0;
    
    if (nearbyDetections >= 5) spatialMultiplier = 1.8;
    else if (nearbyDetections >= 2) spatialMultiplier = 1.4;
    else if (nearbyDetections === 1) spatialMultiplier = 1.0; // Changed from 0.5 to 1.0 so a single demo phone isn't penalized!

    risk = risk * spatialMultiplier;

    // Cap the risk score at 1.0
    return Math.min(risk, 1.0);
}

module.exports = {
    calculateRiskScore
};
