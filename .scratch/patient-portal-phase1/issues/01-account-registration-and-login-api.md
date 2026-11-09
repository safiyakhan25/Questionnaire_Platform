# 01: Account registration & login API

**What to build:** A new, additive-only backend auth module. A patient (or an automated test) can register a new account with email/phone/password, log in with email/password, fetch their own profile including any linked assessment session, and link a freshly created session to their account — all over HTTP, with passwords hashed and tokens opaque. The backend also now accepts cross-origin requests from the frontend's dev origin. No existing assessment/family-member/relationship endpoint is touched.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] POST register creates an account with a securely hashed password and returns a bearer token
- [ ] Registering a second time with the same email is rejected with a clear conflict response
- [ ] POST login with correct credentials returns a valid bearer token; with a wrong password or an unregistered email, both fail identically with no hint which case it was
- [ ] GET my-profile (bearer token) returns the account's email/phone and linked session id, or null if none yet
- [ ] POST link-session (bearer token) records a session id against the account, and my-profile reflects it afterward
- [ ] A cross-origin request from the frontend's dev origin to any `/api/v1/**` route succeeds instead of being blocked
- [ ] The full existing backend test suite still passes unchanged
