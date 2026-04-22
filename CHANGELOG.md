## Version 1.5.0

### Changed
- Weather votes (`/vote clear`, `/vote rain`, `/vote thunder`) now accept an optional duration of 1–7 Minecraft days. Omitting the duration preserves the previous behavior (roughly 6000 ticks).
- `/vote day` no longer forces clear weather when advancing time to morning — existing rain or storms are now left untouched.

### Fixed
- Fixed currency features (ATM, stock ticker integration, train crash payouts) crashing instead of failing gracefully when used in integrated singleplayer where the backend API is unavailable.
