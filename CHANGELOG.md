## Version 1.13.2

### Fixed
- Fixed the ATM and `/withdraw` paying out partial bills when asked for more than the available balance instead of refusing the whole request upfront with "Insufficient funds".
- Fixed the ATM displaying a stale balance after a partial withdrawal or a refunded deposit.
- Fixed a floating-point rounding edge case that could cause a full-balance ATM withdrawal to be incorrectly rejected as "Insufficient funds".
- Fixed players being permanently stuck with "A withdrawal is already in progress" after an error occurred during the post-withdrawal balance refresh.
