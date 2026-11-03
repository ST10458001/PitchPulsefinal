# How AI tools were used and cited

*(Maximum 500 words. This draft reflects how Claude was used on this project. Edit it so it matches exactly what you did, and add any other tools you used.)*

**Tool:** Claude (Anthropic), claude.ai mobile app, accessed between August and November 2026.

**1. Planning and documentation (Parts 1 and 3).** Claude helped structure and tighten our Section B Planning and Design document (requirements wording, API design, data listing, project plan). For Part 3 it helped draft the README, the lab run instructions, the demo video checklist, the release notes and this write-up. We checked the content against the assignment brief and our own project, and rewrote the parts that did not match what we built.

**2. Code snippets.** Claude produced starter code and examples that we adapted into the app, including the salted password hashing approach, the offline prediction queue pattern with Room, the notification helper, the Firebase Authentication, Firestore and Cloud Messaging integration (with an on-device fallback), the Node.js/Express REST API and its Android client, and the unit tests. Claude also added the Logcat logging lines and the referenced code comments to the existing project. We ran and tested all of it on a physical Android phone, fixed problems ourselves, and only kept code we understand and can explain.

**3. Debugging.** *(Describe specific errors you fixed with AI help, e.g. Gradle sync errors, Room migration errors, permission problems. Paste one example prompt if you can.)*

**4. CI.** Claude helped write the GitHub Actions workflow, based on the module's example `build.yml` and the GitHub Marketplace guide listed in the README references. We confirmed that the workflow runs green in our own repository.

**5. Images.** *(State whether the app icon or screenshots were AI-generated and with which tool. Delete this line if not applicable.)*

**What AI did not do.** Claude did not record our demo video, run the app on our devices, create our GitHub repository, sign our APK or submit anything on our behalf. Design decisions, feature choices and the final review were ours.

**Responsibility.** We take full responsibility for the final work. Where AI-generated code or text was used, we reviewed it, edited it and cited the underlying sources (Android Developers, Firebase, OWASP, TheSportsDB) in the README references.
