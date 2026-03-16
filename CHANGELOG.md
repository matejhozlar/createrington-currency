## New version 1.2.0
- Reworked `/vote` command to run fully in-memory, no longer requires backend API
- Vote types: day, night, clear, rain, thunder with 30-second voting window
- Clickable [YES] / [NO] buttons in chat for voting
- Solo player votes pass instantly
- Vote cooldowns: ~9.6 minutes after pass, 3 minutes after fail
- Added Vote Yes / Vote No key bindings (unbound by default, configurable in Controls)
- Removed `apiStartVoteUrl` config option

## New version 1.1.9
- Added driver, passenger, owner, and backwards driver data to train crash reports

## New version 1.1.8
- Added train crash reporting to backend API via mixin into Create's Train.crash() (train name, speed, position, dimension, carriage count)
- Conditional mixin plugin: only applies when Create mod is loaded
- Added `trainCrashReportingEnabled` and `apiTrainCrashUrl` config options
- Updated NeoForge 21.1.172 -> 21.1.217
- Updated Create 6.0.7 -> 6.0.9
- Added explicit Flywheel 1.0.6 dependency (required by Create 6.0.9)