# 05: Patient can add a family member and fill in their data

**What to build:** From within the questionnaire, a patient can add a family member by relationship type, is placed directly into that relative's own questionnaire pass, sees a persistent on-screen indicator naming whose information they're currently entering, fills in that relative's answers using the same question-rendering behavior as their own pass, and is returned automatically to their own pass when that relative's pass completes.

**Blocked by:** 03 (Patient can answer questions and progress through sections)

**Status:** ready-for-agent

- [ ] The patient can add a family member by choosing a relationship type (and maternal/paternal side where applicable) from a fixed list
- [ ] Immediately after adding a family member, the patient is placed into that relative's own first section, not their own
- [ ] A persistent banner names whose information is currently being entered whenever the patient is inside a family member's pass, and is absent while answering for themselves
- [ ] The relative's questionnaire pass supports the same question types, required-field blocking, and section navigation as the patient's own pass
- [ ] Completing the relative's pass returns the patient automatically to their own current section, and the banner disappears
- [ ] Previously added family members are visible in a list the patient can return to
