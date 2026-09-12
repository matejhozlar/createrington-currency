## Version 1.11.0

### Added
- Added a `/createringtoncurrency audit` command for server operators that scans every player inventory, container, entity, and unloaded chunk to tally all physical bills on the server. Results appear in chat as a ranked list of locations with teleport buttons and are also written to a timestamped file. Each report includes the change in total cash since the previous census.

### Changed
- Time and weather votes now require a majority of non-AFK, non-spectator players to pass instead of a simple yes-vs-no count. Spectators can no longer start or cast votes. Votes that fail due to low turnout (rather than active rejection) now use a 60-second cooldown instead of the 3-minute one.
