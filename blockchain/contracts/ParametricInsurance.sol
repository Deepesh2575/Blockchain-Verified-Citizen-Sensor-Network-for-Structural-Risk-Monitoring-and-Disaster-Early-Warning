// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/**
 * @title ParametricInsurance (Epic 3)
 * @dev Smart Contract that listens for verified disaster events from the Hyperledger gateway.
 *      If an event is critical and mathematically verified, it automatically distributes
 *      relief funds (CBDC/Tokens) to citizens registered in that hex-grid without a claims adjuster.
 */
contract ParametricInsurance {
    
    struct DisasterEvent {
        string eventId;
        uint256 riskScore;
        uint256 timestamp;
        bool fundsDisbursed;
    }

    mapping(string => DisasterEvent) public verifiedEvents;
    address public immutable authorityGateway;

    event FundsDisbursed(string eventId, uint256 amount);

    constructor() {
        // Only the backend gateway (or Hyperledger oracle) can trigger payouts
        authorityGateway = msg.sender;
    }

    /**
     * @dev Called by the backend gateway when a CRITICAL event is anchored.
     */
    function triggerDisasterEvent(string memory _eventId, uint256 _riskScore) external {
        require(msg.sender == authorityGateway, "Only authorized gateway can trigger events");
        require(_riskScore > 90, "Risk score too low for parametric payout");
        
        verifiedEvents[_eventId] = DisasterEvent({
            eventId: _eventId,
            riskScore: _riskScore,
            timestamp: block.timestamp,
            fundsDisbursed: false
        });

        _executePayout(_eventId);
    }

    function _executePayout(string memory _eventId) internal {
        DisasterEvent storage disaster = verifiedEvents[_eventId];
        require(!disaster.fundsDisbursed, "Funds already disbursed for this event");

        // MOCK: Loop through registered citizen wallets in the affected GPS hex-grid
        // and transfer micro-relief tokens/funds instantly.
        
        disaster.fundsDisbursed = true;
        emit FundsDisbursed(_eventId, 1000000); // e.g. 1 Million tokens disbursed
    }
}
