## Version 1.6.1

### Fixed
- Fixed a server startup crash on fresh installs caused by the default `jwtSecret` config value being too short to meet the 256-bit minimum required for JWT signing. The default placeholder has been replaced with a longer value that satisfies this requirement.
