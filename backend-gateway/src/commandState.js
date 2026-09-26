const { geoFences, structureModels, resolveStructureByLocation } = require("./digitalTwin");
const { classifyRiskStage } = require("./scoringEngine");

const STRUCTURE_CATALOG = [
    {
        id: "downtown_bridge_01",
        name: "Downtown Bridge 01",
        lat: 28.6139,
        lon: 77.209,
        heightM: 42,
        radiusM: 220,
        kind: "bridge",
    },
    {
        id: "residential_tower_7b",
        name: "Sector 7 Residential",
        lat: 19.076,
        lon: 72.8777,
        heightM: 96,
        radiusM: 90,
        kind: "tower",
    },
    {
        id: "metro_line_b",
        name: "Metro Line B Tunnel",
        lat: 28.6304,
        lon: 77.2177,
        heightM: 16,
        radiusM: 140,
        kind: "tunnel",
    },
    {
        id: "city_hall",
        name: "City Hall Foundation",
        lat: 28.6328,
        lon: 77.2197,
        heightM: 58,
        radiusM: 80,
        kind: "building",
    },
];

const CAMERA_SITES = [
    { id: "cam-rajpath-01", name: "Rajpath West Cam", lat: 28.6129, lon: 77.2195 },
    { id: "cam-ig-02", name: "India Gate Perimeter", lat: 28.6127, lon: 77.2295 },
    { id: "cam-bridge-03", name: "Bridge 01 Underside", lat: 28.6146, lon: 77.2082 },
];

function haversineKm(lat1, lon1, lat2, lon2) {
    const R = 6371;
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
        0.5 -
        Math.cos(dLat) / 2 +
        (Math.cos((lat1 * Math.PI) / 180) *
            Math.cos((lat2 * Math.PI) / 180) *
            (1 - Math.cos(dLon))) /
            2;
    return R * 2 * Math.asin(Math.sqrt(a));
}

function attachStructure(event) {
    if (event.structureId) return event.structureId;
    return resolveStructureByLocation(event.latitude, event.longitude) || null;
}

function buildCommandState(eventLog) {
    const events = (eventLog || []).slice(-250);
    const sensorsByDevice = new Map();

    for (const evt of events) {
        const lat = Number(evt.latitude ?? 28.6139);
        const lon = Number(evt.longitude ?? 77.209);
        const score = Number(evt.finalRiskScore ?? 0);
        const stage = evt.status || classifyRiskStage(score);
        sensorsByDevice.set(evt.deviceId, {
            deviceId: evt.deviceId,
            lat,
            lon,
            alt: 18,
            trust: Number(evt.factors?.T ?? 0.7),
            anomaly: score,
            signature: evt.ledgerHash ? "valid" : "queued",
            lastSeen: evt.timestamp,
            structureId: attachStructure(evt),
            stage,
        });
    }

    const structures = STRUCTURE_CATALOG.map((asset) => {
        const related = events.filter((evt) => {
            const sid = attachStructure(evt);
            if (sid === asset.id) return true;
            const d = haversineKm(asset.lat, asset.lon, Number(evt.latitude), Number(evt.longitude));
            return d <= asset.radiusM / 1000;
        });
        const maxScore = related.reduce((m, evt) => Math.max(m, Number(evt.finalRiskScore ?? 0)), 0);
        const latest = related[related.length - 1];
        const devices = new Set(related.map((e) => e.deviceId));
        const twin = latest ? Number(latest.twinCorrelation ?? 0.5) : Number(structureModels[asset.id] ? 0.82 : 0.5);
        return {
            ...asset,
            stage: classifyRiskStage(maxScore),
            score: maxScore,
            twinConfidence: twin,
            verifiedSensorCount: devices.size,
            lastUpdate: latest?.timestamp || null,
            factors: latest?.factors || null,
        };
    });

    const correlations = [];
    for (const evt of events) {
        const count = Number(evt.localDeviceCount || 0);
        if (count < 2 && !Array.isArray(evt.localDevices)) continue;
        const neighbors = Array.isArray(evt.localDevices) ? evt.localDevices : [];
        neighbors.forEach((peer, idx) => {
            const peerId = typeof peer === "string" ? peer : peer.deviceId || `peer-${idx}`;
            correlations.push({
                eventId: evt.eventId,
                fromDeviceId: evt.deviceId,
                toDeviceId: peerId,
                from: { lat: Number(evt.latitude), lon: Number(evt.longitude) },
                to: {
                    lat: Number(peer.lat ?? evt.latitude) + Math.sin(idx + 1) * 0.004,
                    lon: Number(peer.lon ?? evt.longitude) + Math.cos(idx + 1) * 0.004,
                },
            });
        });
        if (neighbors.length === 0 && count >= 2) {
            for (let i = 0; i < Math.min(count, 4); i++) {
                correlations.push({
                    eventId: evt.eventId,
                    fromDeviceId: evt.deviceId,
                    toDeviceId: `${evt.deviceId}-mesh-${i}`,
                    from: { lat: Number(evt.latitude), lon: Number(evt.longitude) },
                    to: {
                        lat: Number(evt.latitude) + Math.sin(i * 1.7) * 0.005,
                        lon: Number(evt.longitude) + Math.cos(i * 1.3) * 0.005,
                    },
                });
            }
        }
    }

    return {
        generatedAt: new Date().toISOString(),
        sensors: Array.from(sensorsByDevice.values()),
        structures,
        events: events.map((evt) => ({
            ...evt,
            structureId: attachStructure(evt),
            latitude: Number(evt.latitude ?? 28.6139),
            longitude: Number(evt.longitude ?? 77.209),
        })),
        correlations,
        cameras: CAMERA_SITES,
        geofences: geoFences,
        stats: {
            eventCount: events.length,
            sensorCount: sensorsByDevice.size,
            criticalCount: events.filter((e) => (e.status || "") === "CRITICAL").length,
        },
    };
}

module.exports = {
    buildCommandState,
    STRUCTURE_CATALOG,
    CAMERA_SITES,
};
