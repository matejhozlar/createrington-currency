## New version 1.1.8
- Added train crash reporting to backend API via mixin into Create's Train.crash() (train name, speed, position, dimension, carriage count)
- Conditional mixin plugin: only applies when Create mod is loaded
- Added `trainCrashReportingEnabled` and `apiTrainCrashUrl` config options
- Updated NeoForge 21.1.172 -> 21.1.217
- Updated Create 6.0.7 -> 6.0.9
- Added explicit Flywheel 1.0.6 dependency (required by Create 6.0.9)