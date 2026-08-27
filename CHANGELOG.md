## Version 1.9.0

### Added
- Added a Depositor Terminal block that lets players set up a player-run shop: the owner configures a price in bill items and a denomination, and customers pay by right-clicking the block. A successfully paid terminal emits a redstone pulse from its back face (like an observer) on each successful payment, making it easy to wire up dispensers, hoppers, or other contraptions.
- Added individual config toggles to disable any economy command. Disabled commands are not registered at all and will not appear in tab completion. The new toggles are `disableMoneyCommand`, `disablePayCommand`, `disableBaltopCommand`, `disableDailyCommand`, `disableLotteryCommands`, and `disableVoteCommand` (the existing `disableCashCommands` toggle for `/deposit` and `/withdraw` continues to work as before).
