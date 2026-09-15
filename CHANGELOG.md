## Version 1.13.3

### Fixed
- Fixed votes raising their required-yes count after the start message was announced. If a player joined or returned from AFK after a vote opened, the threshold could silently increase, causing votes to fail even after reaching the announced number. The required count is now locked at vote start.
- Fixed `voteApprovalPercent=50` requiring strictly more than half on server sizes where the eligible count divided evenly by 2. The threshold now uses ceiling division so 50 means at least half (e.g. 3 of 6, 3 of 5).
- Fixed tied votes passing when yes and no were equal. Yes votes now must also strictly outnumber no votes in addition to meeting the approval-percent quorum.
