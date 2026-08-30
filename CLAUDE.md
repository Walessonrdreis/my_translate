# Project conventions

## Implementation plans

- Plans live in `docs/superpowers/plans/YYYY-MM-DD-<feature-name>/` as a folder of numbered Markdown files (`00-overview.md`, `01-...md`, etc.), never as a single plan file.
- No plan file may exceed 200 lines. If a task's content would push a file over that limit, split it into an additional numbered file rather than trimming content.
- `00-overview.md` always holds the plan header (goal, architecture, tech stack, global constraints) plus a task index table pointing to the other files.

## Debug APK builds

- Every debug build must produce a uniquely named APK (`app-debug-<versionName>-<timestamp>.apk`), configured in `app/build.gradle.kts`'s `applicationVariants.all` block. Never let a debug build overwrite a previous one — each build is kept for later testing/comparison.
- Old debug APKs in `app/build/outputs/apk/debug/` are not cleaned up automatically; delete them manually if disk space becomes a concern.
