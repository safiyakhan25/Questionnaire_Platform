# 06: Patient reaches a confirmed-complete end state

**What to build:** Once the patient finishes every section of their own questionnaire, they land on a completion screen that confirms a valid pedigree record was produced behind the scenes, or offers them a way to try that check again if it failed, without ever showing them the raw exported record.

**Blocked by:** 03 (Patient can answer questions and progress through sections)

**Status:** ready-for-agent

- [ ] Completing the final section of the questionnaire navigates the patient to a distinct completion screen
- [ ] On that screen, the app calls the existing pedigree-export check and shows a plain confirmation message on success
- [ ] If that check fails, the patient sees a clear message and a "try again" action, instead of a dead end
- [ ] Clicking "try again" re-runs the same check and shows success if it now succeeds
- [ ] The raw exported pedigree record itself is never displayed to the patient
