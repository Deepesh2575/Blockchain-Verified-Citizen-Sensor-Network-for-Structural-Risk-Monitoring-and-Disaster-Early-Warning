/**
 * Civil Engineering Digital Twin (Simplified)
 * 
 * In a production environment, this queries a dedicated Python/Go microservice running
 * a Newmark-Beta numerical integration over a Finite Element Model (FEM) of the specific structure.
 * 
 * For the SIH presentation, this validates the anomaly by ensuring the measured vibration
 * frequency aligns with the natural resonant frequency of the structure.
 */

// A simple dictionary mapping structure IDs to their natural frequencies (Hz)
const structureModels = {
    'downtown_bridge_01': {
        naturalFrequencyHz: 2.4, 
        massKg: 5000000,
        dampingRatio: 0.05
    },
    'residential_tower_7b': {
        naturalFrequencyHz: 1.1,
        massKg: 12000000,
        dampingRatio: 0.02
    }
};

/**
 * Validates the measured sensor data against the structural physics model.
 * 
 * @param {Object} payload The sensor payload containing measured frequencies.
 * @returns {Number} Correlation score from 0.0 (No Match) to 1.0 (Perfect Match).
 */
function validateWithPhysicsModel(payload) {
    const structureId = payload.structureId || 'downtown_bridge_01'; // Default for demo
    const model = structureModels[structureId];

    if (!model) {
        console.warn(`[TWIN] No digital twin found for structure: ${structureId}`);
        return 0.5; // Neutral confidence
    }

    // The AI extracted the peak frequency of the vibration from the phone's accelerometer
    const measuredFrequency = payload.peakFrequencyHz || 2.3; 

    // Calculate how close the measured frequency is to the structure's natural resonant frequency.
    // An earthquake that matches the natural frequency causes dangerous resonance.
    const difference = Math.abs(model.naturalFrequencyHz - measuredFrequency);
    
    // Convert difference to a correlation score (0 to 1)
    // If difference is 0, correlation is 1.0. If difference is > 1.0Hz, correlation drops significantly.
    let correlation = 1.0 - (difference * 0.5); 
    
    // Clamp between 0.1 and 0.99
    correlation = Math.max(0.1, Math.min(correlation, 0.99));

    console.log(`[TWIN] Physics Validation - Measured: ${measuredFrequency}Hz, Predicted: ${model.naturalFrequencyHz}Hz -> Match: ${(correlation*100).toFixed(1)}%`);

    return correlation;
}

module.exports = {
    validateWithPhysicsModel
};
