# FALVTP

Paper 26.3 plugin: villager trading without villagers. A vanilla job-site block *is* the trader — same levels,
XP, restocks, demand and trade tables as a vanilla villager, no entity.

Full design notes: `.claude/docs/design.md` (read it before non-trivial work).
Paper docs mirror for offline reference: `.claude/extern/papermc-docs/src/content/docs/`.

## Build / run
- `./gradlew compileJava --offline` — fast compile check (deps are cached; works offline).
- `./gradlew runServer` — dev server in `run/` (JetBrains Runtime, `-XX:+AllowEnhancedClassRedefinition`, debug on).
- Java 25 toolchain, paperweight userdev (Mojang-mapped NMS is on the classpath and used directly).
- Not a git repo.

## Don't flag these (decided by the owner)
- The name / README ("Fast Async Lagless", "EXTREMELY FAST AYNSC") is satire. Do not point out that nothing is async.
- `TraderChunk.breakNaturally` removing the trader before checking the `Workstation`, and blocks changed without
  an event (`/setblock`, WorldEdit, etc.) leaving or losing trader data — intentional / out of scope, not bugs.

## Code conventions
- `Option` (sealed, `utils/Option`) instead of null/Optional for lookups; branch with record patterns
  (`if (x instanceof Option.Some<T>(T v))`).
- `final` locals, Guava `Preconditions`, jspecify `@Nullable`.
- Comments are short, lowercase, and say *why* or name the vanilla source being mirrored
  (e.g. `// AbstractVillager#processTrade`). Don't add javadoc-heavy boilerplate.
- Vanilla behaviour is copied faithfully; when changing trading rules, check the vanilla/NMS source first.
