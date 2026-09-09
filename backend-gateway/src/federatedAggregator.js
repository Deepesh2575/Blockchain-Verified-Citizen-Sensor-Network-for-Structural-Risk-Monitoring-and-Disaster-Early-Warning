const express = require('express');
const router = express.Router();

// Mock store for federated model weights
let globalModelWeights = {
    version: 1,
    lastUpdated: new Date().toISOString(),
    nodeContributions: 0
};

/**
 * FEDERATED LEARNING - UPLOAD ENDPOINT
 * Edge devices upload their locally trained gradients here.
 */
router.post('/upload-gradients', (req, res) => {
    const { deviceId, signature, gradients } = req.body;

    if (!deviceId || !signature) {
        return res.status(400).json({ error: 'Missing security credentials.' });
    }

    // In a real system, we'd average the gradients (FedAvg) 
    // Here we just acknowledge the contribution.
    globalModelWeights.nodeContributions += 1;
    console.log(`[FEDERATED] Received gradients from ${deviceId}. Total contributions: ${globalModelWeights.nodeContributions}`);

    // Every 100 contributions, "publish" a new model version
    if (globalModelWeights.nodeContributions % 100 === 0) {
        globalModelWeights.version += 1;
        globalModelWeights.lastUpdated = new Date().toISOString();
        console.log(`[FEDERATED] Published new Global Model Version: v${globalModelWeights.version}`);
    }

    return res.status(200).json({ message: 'Gradients aggregated successfully.' });
});

/**
 * FEDERATED LEARNING - DOWNLOAD ENDPOINT
 * Edge devices poll this to download the latest global model.
 */
router.get('/download-model', (req, res) => {
    console.log(`[FEDERATED] Device requesting latest model. Serving v${globalModelWeights.version}`);
    
    // In a real scenario, this would stream the binary .tflite file
    res.json({
        modelVersion: globalModelWeights.version,
        lastUpdated: globalModelWeights.lastUpdated,
        downloadUrl: `https://storage.example.com/models/v${globalModelWeights.version}/anomaly_model.tflite`
    });
});

module.exports = router;
