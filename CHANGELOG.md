## New version 1.2.0
- Reworked `/vote` command to run fully in-memory, no longer requires backend API
- Vote types: day, night, clear, rain, thunder with 30-second voting window
- Clickable [YES] / [NO] buttons in chat for voting
- Solo player votes pass instantly
- Vote cooldowns: ~9.6 minutes after pass, 3 minutes after fail
- Added Vote Yes / Vote No key bindings (unbound by default, configurable in Controls)
- Removed `apiStartVoteUrl` config option
