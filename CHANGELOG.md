## Version 1.4.0

### Changed
- All backend communication now goes through the CRNet client library, improving reliability, token management, and error handling across all economy commands, ATM operations, and train crash reports.
- Player-facing messages (such as balance responses and daily cooldown notices) now use the formatted text provided by the backend server when available, giving server operators more control over display strings.
- Configuration file restructured into sections (`general`, `api`, `mobDrops`, `lottery`, `integrations`). Existing `createringtoncurrency-common.toml` files will be regenerated with defaults on first launch — review and re-apply any customized values after upgrading.
- Individual per-endpoint API URL settings have been consolidated into a single `apiBaseUrl` option. Update your config to use the new field instead of the old per-endpoint URLs.
