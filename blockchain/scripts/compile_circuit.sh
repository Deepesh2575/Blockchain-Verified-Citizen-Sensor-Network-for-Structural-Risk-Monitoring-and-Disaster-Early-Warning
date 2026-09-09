#!/bin/bash
# Compiles the ZKP circuit and generates the Solidity Verifier

echo "Installing snarkjs and circom..."
npm install -g snarkjs

echo "Compiling circuit..."
circom circuits/anomaly.circom --r1cs --wasm --sym -o circuits/

echo "Generating trusted setup (Powers of Tau)..."
snarkjs powersoftau new bn128 12 pot12_0000.ptau -v
snarkjs powersoftau contribute pot12_0000.ptau pot12_0001.ptau --name="First contribution" -v -e="some random text"

echo "Phase 2..."
snarkjs powersoftau prepare phase2 pot12_0001.ptau pot12_final.ptau -v
snarkjs groth16 setup circuits/anomaly.r1cs pot12_final.ptau anomaly_0000.zkey
snarkjs zkey contribute anomaly_0000.zkey anomaly_0001.zkey --name="Second contribution" -v -e="Another random text"
snarkjs zkey export verificationkey anomaly_0001.zkey verification_key.json

echo "Generating Live Solidity Verifier..."
snarkjs zkey export solidityverifier anomaly_0001.zkey contracts/LiveZKVerifier.sol

echo "DONE! Live ZK-SNARK verifier contract generated."
