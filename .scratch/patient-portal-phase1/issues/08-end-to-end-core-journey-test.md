# 08: End-to-end test of the core patient journey

**What to build:** A new automated, browser-driven test suite runs the real built frontend against a real running backend and proves the core journey works end-to-end with no mocks: register, answer through to completion, add and fill in one family member, and confirm the pedigree-export check succeeds.

**Blocked by:** 01, 02, 03, 05, 06

**Status:** ready-for-agent

- [ ] A new end-to-end test harness exists and can launch the real frontend against a real running backend
- [ ] An automated end-to-end test registers a new patient account and reaches the first section of the questionnaire
- [ ] The same test answers through every section of the questionnaire to completion
- [ ] The same test adds one family member, fills in their data, and confirms control returns to the patient
- [ ] The same test confirms the completion screen reports a successful pedigree-export check
- [ ] This end-to-end suite runs as a distinct, separately invokable test command from the backend and frontend unit/integration suites
