## 2026-03-30 - AppLock Timing Side-Channel Mitigation
**Vulnerability:** Short-circuit logic in `AppLock.checkPin` returned early when `appLockPinHash` was blank, creating a timing side-channel that allowed inferring whether app lock was configured before attempting hash verification.
**Learning:** `MessageDigest.isEqual` provides constant-time comparison, but short-circuit boolean checks prior to `isEqual` or varying hash generation depending on state can leak state via execution time.
**Prevention:** Always perform candidate hashing and execute `MessageDigest.isEqual` against a constant dummy hash array when no stored PIN hash is configured.
