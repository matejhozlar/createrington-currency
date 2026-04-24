## Version 1.6.0

### Added
- New `jwtSecret` config option (in `createringtoncurrency-common.toml`) for signing backend authentication tokens. Server operators upgrading from 1.5.0 must set this value to match the backend's configured secret; leaving it at the default will cause all currency API calls to be rejected with a 401 error.

### Changed
- Authentication with the currency backend no longer performs a separate login request. The mod now issues self-signed short-lived tokens locally, reducing network overhead and aligning with how other Createrington services authenticate.
