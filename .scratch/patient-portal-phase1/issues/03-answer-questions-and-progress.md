# 03: Patient can answer questions and progress through sections

**What to build:** The logged-in patient's current section of the live, admin-configured questionnaire renders with the correct input for every supported question type, required-field validation blocks advancing with the backend's own message naming what's missing, conditionally-visible questions appear/disappear as answers change, and completing a section advances to the next one. Refreshing resumes the same point rather than starting over.

**Blocked by:** 02 (Patient can sign up and log in)

**Status:** ready-for-agent

- [ ] Each of the platform's question types (free text, number, date, single-choice, multi-choice, dropdown) renders with the correct input control sourced from the live API
- [ ] A multi-choice answer with more than one option selected is saved as a full list, not reduced to one value
- [ ] A question whose visibility depends on an earlier answer appears or disappears automatically as that earlier answer changes
- [ ] Trying to advance with a required question unanswered is blocked, and the patient sees the backend's own message naming the specific missing field(s)
- [ ] Answering every required question in a section and advancing moves the patient into the next section's live questions
- [ ] Refreshing the browser mid-section returns the patient to the same in-progress section with their answers intact, without starting a new session
- [ ] A question added to a template through the existing admin API appears on the patient's screen with no frontend code change
