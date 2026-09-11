# Omega AI development instructions

Omega AI is an early work in progress.
Andrzej and the agent will jointly decide when it is ready for version 1.0.0.
This file overrides the workspace's automatic release workflow for this project until that decision.

## Before the agreed 1.0.0 release

- Build and run the relevant automated checks for requested changes.
- Provide local test ZIPs for Andrzej to install and evaluate.
- Keep source commits and pushes as part of normal development.
- Do not create new version tags, GitHub Releases, draft releases, or prereleases.
- Do not infer release approval from passing tests, completed features, or a version number.
- Keep the public version tracker on the last published release.
  Local build identifiers must not advertise an unavailable GitHub asset or make an update checker offer an unpublished build.
- Keep existing published releases and tags intact unless Andrzej asks to change them.
- Keep the forum post as a draft until publication is requested.

## Local test packaging

Write each ZIP directly in this mod project's root directory.
Stage only runtime files in `OmegaAI/`, then zip that entire folder.
Keep both `OmegaAI/` and all generated ZIPs gitignored.
Exclude source, tests, development documentation, build tools, and private reference material from the ZIP.
Include required mission resources, LunaLib icon registration, compiled code, and the appropriate local version metadata.
Never install or copy a build into the game's mods folder.

## Ship action labels

Show one short action above a ship only while Omega actively directs it.
Use behavior words such as Regrouping, Waiting for support, or Disengaging for the implemented actions.
Do not label behavior that Omega does not implement or an unissued proposal.
Hide the ship label when Omega is observing, blocked, excluded, or has no active order.
Keep controller names, eligibility reasons, assignments, and other technical diagnostics in the log, not above ships.

Instruction-only changes do not require a mod version bump, a new ZIP, or a release.
After the joint 1.0.0 decision, follow the workspace release workflow for authorized releases.
