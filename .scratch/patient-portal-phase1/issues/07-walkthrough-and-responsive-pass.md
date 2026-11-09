# 07: First-time walkthrough + responsive pass

**What to build:** A patient opening the app for the very first time sees a short, dismissible walkthrough of the core actions (answering questions, adding a family member, going back to edit) that never reappears afterward; and every screen built across tickets 02-06 is comfortably usable on both a typical phone-width screen and a desktop-width screen.

**Blocked by:** 02, 03, 04, 05, 06 (every ticket that produces a screen this one polishes)

**Status:** ready-for-agent

- [ ] A patient's very first visit shows a dismissible walkthrough covering answering questions, adding a family member, and going back to edit
- [ ] Dismissing the walkthrough once means it never shows again on later visits, even after a refresh or a new login session on the same device
- [ ] Every screen from tickets 02-06 (login/signup, questionnaire, visited-sections list, family-member panel, completion) is usable with no horizontal scrolling and no illegibly small tap targets at a typical phone width
- [ ] The same screens remain well laid out at a typical desktop width
