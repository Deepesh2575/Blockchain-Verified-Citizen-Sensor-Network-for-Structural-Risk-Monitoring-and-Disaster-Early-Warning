const hre = require("hardhat");

async function main() {
  console.log("Starting Live Deployment to Hardhat Local Node...");

  // Deploy ZKVerifier
  const ZKVerifier = await hre.ethers.getContractFactory("ZKVerifier");
  const zkVerifier = await ZKVerifier.deploy();
  await zkVerifier.waitForDeployment();
  const zkAddress = await zkVerifier.getAddress();
  console.log(`✅ ZKVerifier deployed to: ${zkAddress}`);

  // Deploy ParametricInsurance
  const ParametricInsurance = await hre.ethers.getContractFactory("ParametricInsurance");
  const insurance = await ParametricInsurance.deploy();
  await insurance.waitForDeployment();
  const insAddress = await insurance.getAddress();
  console.log(`✅ ParametricInsurance deployed to: ${insAddress}`);

  console.log("\nCopy these addresses into the backend-gateway environment configuration!");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
