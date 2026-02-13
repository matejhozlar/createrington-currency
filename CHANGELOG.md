## New version 1.1.7
- Local mob daily earnings limit (configurable `mobDailyLimit`, persistent across restarts, no backend ping needed)
- Fixed HTTP resource leaks across all API call sites (proper connection disconnect and stream closing)
- Fixed token cache race condition with per-player synchronization
- Periodic save for mob daily earnings using dirty flag (every 5 minutes instead of per-kill)
- Extracted shared `DENOMINATIONS` constant to eliminate duplication
- Replaced hardcoded HTTP timeouts with configurable `apiTimeoutMs`
- Added integer overflow and negative value validation on withdraw commands and ATM packets
- Added audit logging for all successful transactions (pay, deposit, withdraw, ATM)
- Expired token eviction to prevent memory leak from crashed players
- All token caches cleared on server stop

