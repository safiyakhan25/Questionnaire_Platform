# 02: Patient can sign up and log in

**What to build:** The React web app exists. A patient can switch between a sign-up form and a login form, submit either, and on success lands on an authenticated (placeholder) screen; an invalid attempt shows the backend's own error message in place.

**Blocked by:** 01 (Account registration & login API)

**Status:** ready-for-agent

- [ ] Visiting the app with no stored session shows the login screen with a link to sign up
- [ ] Submitting the sign-up form with a new email/phone/password creates the account and logs the patient straight in
- [ ] Submitting the sign-up form with an already-registered email shows a clear error and does not log the patient in
- [ ] Submitting the login form with valid credentials logs the patient in and reaches the authenticated area of the app
- [ ] Submitting the login form with invalid credentials shows a clear error and stays on the login screen
- [ ] Refreshing the browser after logging in keeps the patient logged in without re-entering credentials
- [ ] Attempting to open the authenticated area without being logged in redirects to the login screen
