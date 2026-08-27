## Version 1.9.0

### Added
- Added a Depositor Terminal block that lets players set up a player-run shop: the owner configures a price in bill items and a denomination, and customers pay by right-clicking the block. A successfully paid terminal emits a redstone pulse from its back face, making it easy to wire up dispensers, hoppers, or other contraptions.
- Added individual config toggles to disable any economy command. Disabled commands are not registered at all and will not appear in tab completion. The new toggles are `disableMoneyCommand`, `disablePayCommand`, `disableBaltopCommand`, `disableDailyCommand`, `disableLotteryCommands`, and `disableVoteCommand` (the existing `disableCashCommands` toggle for `/deposit` and `/withdraw` continues to work as before).

### Fixed
- Fixed the Depositor Terminal's redstone signal to emit only from the back face of the block, consistent with observer-style behaviour. Previously the signal leaked to all adjacent blocks.
- Fixed rapid consecutive payments on the same Depositor Terminal sometimes causing a redstone pulse to be skipped. Each payment now always produces its own distinct rising edge.
- Fixed the owner GUI allowing a configured price count higher than the terminal's storage capacity.
- Fixed an exploit where breaking a Depositor Terminal while a player's menu was still open could let that player retrieve the dropped bills a second time.
