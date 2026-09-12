## Version 1.11.1

### Added
- New `audit.auditThreads` config option to control how many threads the auditor uses when scanning region files (0 = automatic, defaults to half the available cores, capped at 4).

### Changed
- Cash audits now run significantly faster: chunks that cannot contain bills are skipped before full parsing, and region files are scanned in parallel.

### Fixed
- Unreadable or corrupt chunks are no longer silently excluded from audit results; each now produces a warning and is included in the chunk count.
- Interrupted or failed audit sweeps are no longer recorded as the new baseline, keeping the "change since last audit" figure accurate.
