## Version 1.10.0

### Added
- Status LED on the depositor terminal shows payment readiness: unlit when no price is set, steady when ready, and full-bright when the storage is too full for the next payment. Flashes brass while the redstone pulse runs.
- Bill split and merge recipes: combine bills into larger denominations or break them into smaller ones in any crafting grid, without visiting an ATM.
- Bank card right-click shows your current balance on the action bar; sneak right-click prints your last five transactions in chat.
- `/createringtoncurrency admin-mode` command lets operators toggle access to depositor terminal owner menus. When off (the default), operators interact as ordinary customers.

### Changed
- Mob drop rates are now fully configurable via the config file using a list of `<entity_id or #tag>=<denomination>:<chance>` entries. The four previous hardcoded config keys are replaced; defaults reproduce the previous drop behaviour. The daily earnings cap now correctly limits which bills drop (previously the last kill of the day could drop a full bill worth more than the remaining cap). The Capitalist Greed enchantment bonus now applies to all configured drop lines rather than only the $1 roll.
- Deposits and withdrawals now send idempotency keys to the bank API, so a timed-out request that the network retries is applied only once.

### Fixed
- Fixed depositor terminal payment notifications: the payer's action bar and the owner's chat message now show the total amount paid rather than a bill breakdown, and the owner's notice includes the terminal's coordinates.
- Fixed a race condition where a second deposit or withdrawal submitted while one was already in flight could charge or credit the same bills twice. Bills are now removed from inventory before the API request, and only one request per player runs at a time.
- Fixed bills being lost when a player disconnects during a transaction or when their inventory is full on delivery. Bills that cannot be handed over immediately are queued and delivered on next login.
- Fixed Stock Ticker payments blocking the server thread for the full API timeout per denomination on every purchase.
- Fixed ATM blocks not breaking as a unit: explosions, pistons, and `/fill` could leave one half standing and sometimes drop two items. Placing an ATM with no room for its upper half is now rejected at placement. ATMs can now be moved by Create contraptions.
- Fixed a crash when the Capitalist Greed enchantment is removed by a datapack.
- Fixed Blaze Burner clicks incorrectly triggering a Stock Ticker withdrawal when a player holding a Bank Card and a shopping list fueled a basin or mixer instead of paying a shop.
