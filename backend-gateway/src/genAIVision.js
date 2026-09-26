/**
 * Epic 7: Generative AI Damage Assessment (Vision)
 * Simulates passing a drone's live camera feed to a Vision-Language Model 
 * (like Gemini 1.5 Pro) to automatically categorize rubble and suggest heavy machinery.
 */

const { GoogleGenAI } = require('@google/genai');

module.exports.analyzeRubble = async (gpsCoordinates) => {
    console.log(`[GenAI Vision] Drone arrived at ${gpsCoordinates}. Taking high-res aerial photographs...`);
    console.log(`[GenAI Vision] Sending images to Multimodal LLM (Gemini 2.5 Pro) for structural analysis...`);
    
    // Initialize the API with fallback to mock if no key is provided
    if (!process.env.GEMINI_API_KEY) {
        console.warn(`[GenAI Vision] ⚠️ GEMINI_API_KEY not found in environment. Falling back to simulated analysis.`);
        return getMockAnalysis();
    }

    try {
        const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
        
        const systemPrompt = `You are an expert structural engineer and disaster response AI. 
Analyze the provided scene of a collapsed building and output a JSON object with the following fields:
- rubbleType (string): The primary construction materials visible (e.g. Reinforced Concrete).
- collapsePattern (string): The structural collapse type (e.g. Pancake, V-shape, Lean-to).
- survivabilityIndex (string): A short assessment of potential air pockets or survival zones.
- logisticsRecommendation (string): Specific heavy machinery needed for this exact rubble type.
- hazards (array of strings): Any immediate hazards visible (e.g. exposed rebar, fires, flooding, unstable pillars).`;

        // Since we don't have a real drone image, we'll prompt the model to simulate an assessment based on the location.
        // In a real system, you would pass `parts: [{inlineData: {data: base64img, mimeType: "image/jpeg"}}]`.
        const userPrompt = `A massive seismic event just occurred near coordinates ${gpsCoordinates}. We've dispatched a drone. Generate a realistic and highly detailed visual assessment of a severely collapsed modern highway overpass at this location.`;

        const response = await ai.models.generateContent({
            model: 'gemini-2.5-pro',
            contents: [
                { role: 'user', parts: [{ text: systemPrompt + "\\n\\n" + userPrompt }] }
            ],
            config: {
                responseMimeType: "application/json",
            }
        });

        const result = JSON.parse(response.text);

        console.log(`\n========================================================`);
        console.log(`🧠 [GenAI Vision] LIVE ANALYSIS COMPLETE (Powered by Gemini)`);
        console.log(`🧠 Rubble Type: ${result.rubbleType}`);
        console.log(`🧠 Collapse Pattern: ${result.collapsePattern}`);
        console.log(`🧠 Hazards: ${result.hazards?.join(', ') || 'None identified'}`);
        console.log(`🧠 LOGISTICS: ${result.logisticsRecommendation}`);
        console.log(`========================================================\n`);

        return result;
    } catch (e) {
        console.error(`[GenAI Vision] ❌ Error calling Gemini API:`, e.message);
        return getMockAnalysis();
    }
};

function getMockAnalysis() {
    return {
        rubbleType: "Reinforced Concrete with High Tensile Steel Rebar",
        collapsePattern: "Pancake Collapse (Severe)",
        survivabilityIndex: "Moderate (Air pockets likely in eastern quadrant)",
        logisticsRecommendation: "DISPATCH 50-Ton Crawler Crane AND Hydraulic Concrete Crushers.",
        hazards: ["Exposed high-voltage power lines", "Unstable load-bearing pillar on North face"]
    };
}
