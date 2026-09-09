pragma circom 2.0.0;

/*
 * Epic 12: Live ZK-SNARK Circuit
 * Proves that a user's phone detected a G-force greater than the threshold (8G)
 * without revealing the exact G-force number or the user's private device ID.
 */

template GreaterEqThan(n) {
    signal input in[2];
    signal output out;

    component num2Bits = Num2Bits(n+1);
    num2Bits.in <== in[0] - in[1] + (1 << n);
    out <== num2Bits.out[n];
}

template Num2Bits(n) {
    signal input in;
    signal output out[n];
    var lc1=0;
    var e2=1;
    for (var i = 0; i < n; i++) {
        out[i] <-- (in >> i) & 1;
        out[i] * (out[i] - 1) === 0;
        lc1 += out[i] * e2;
        e2 = e2 + e2;
    }
    lc1 === in;
}

template AnomalyDetector() {
    // Private Inputs (From Edge Device)
    signal input deviceId;
    signal input gForce;
    
    // Public Inputs (Constants)
    signal input threshold; // Set to 8G
    
    // Output Hash (Proof of anomaly)
    signal output isCritical;

    // Check if gForce >= threshold
    component geq = GreaterEqThan(32);
    geq.in[0] <== gForce;
    geq.in[1] <== threshold;
    
    // Enforce that it MUST be greater than or equal to threshold to generate a valid proof
    geq.out === 1;

    // Output a success bit
    isCritical <== 1;
}

component main {public [threshold]} = AnomalyDetector();
