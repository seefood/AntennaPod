<!--
Sync Impact Report:
- Version change: 1.0.0 → 1.0.1 (template clarification)
- Modified principles: None
- Added sections: None
- Removed sections: None
- Templates requiring updates:
  ✅ plan-template.md - Constitution Check section now includes specific principle references
  ✅ spec-template.md - Requirements section aligned (no changes needed)
  ✅ tasks-template.md - Test-first workflow aligned (no changes needed)
- Follow-up TODOs: None
-->

# AntennaPod Development Constitution

## Core Principles

### I. Code Quality First (NON-NEGOTIABLE)

All code contributions MUST pass the following quality gates before merge:

- **Checkstyle**: Zero violations using `config/checkstyle/checkstyle.xml`
- **SpotBugs**: Zero medium/high priority bugs (max effort analysis)
- **Android Lint**: Zero errors (warnings treated as errors, `abortOnError = true`)
- **XML Formatting**: All layout files formatted with android-xml-formatter

**Rationale**: AntennaPod serves millions of users across diverse Android devices. Code quality gates prevent subtle bugs that are difficult to reproduce and track down in production.

**Enforcement**: CI fails if any quality check fails. Run locally before pushing:
```bash
./gradlew checkstyle spotbugsPlayDebug spotbugsDebug :app:lintPlayDebug
```

### II. Test Coverage for New Features

New features MUST include unit tests demonstrating correct behavior.

- Unit tests required for new business logic, data transformations, and algorithms
- Use Robolectric for Android-dependent code
- Integration tests (Espresso) required for new user-facing workflows
- Database operations MUST call `DBWriter.tearDownTests()` in test teardown to prevent "Illegal connection pointer" errors

**Rationale**: AntennaPod's multi-module architecture and EventBus communication make manual testing insufficient. Automated tests prevent regressions across module boundaries.

**Exemptions**: Simple UI layout changes, string updates, and refactorings with existing test coverage.

### III. Modular Architecture Preservation

Code MUST respect the 37-module architecture and dependency hierarchy:

- **Model layer** (`model/`) - NO Android dependencies, pure Java domain objects
- **Storage layer** → depends on model only
- **UI layer** → consumes storage/playback, never modifies data directly
- New modules require architecture review and justification
- Cross-module communication via EventBus or explicit service interfaces

**Rationale**: Module boundaries enable parallel development, independent testing, and product flavor separation (Play vs Free builds).

**Violation Recovery**: If a module gains inappropriate dependencies, create a migration plan to extract the dependency or restructure the code.

### IV. Event-Driven Communication

Cross-component communication MUST use EventBus patterns correctly:

- **Posting**: Database writes automatically post events (via `DBWriter`), UI operations post explicit events
- **Subscription**: Always register in `onStart()`, unregister in `onStop()` to prevent memory leaks
- **Thread Mode**: Use `@Subscribe(threadMode = ThreadMode.MAIN)` for UI updates
- **Sticky Events**: Use for state that late subscribers need (e.g., `FeedUpdateRunningEvent`)

**Rationale**: EventBus decouples the 37 modules and enables reactive UI updates without direct coupling. Improper usage causes memory leaks and crashes.

**Pattern**:
```java
// Posting
EventBus.getDefault().post(new QueueEvent());

// Subscribing
@Override public void onStart() {
    EventBus.getDefault().register(this);
}
@Subscribe(threadMode = ThreadMode.MAIN)
public void onQueueEvent(QueueEvent event) { /* update UI */ }
@Override public void onStop() {
    EventBus.getDefault().unregister(this);
}
```

### V. Database Integrity

Database operations MUST follow the single-threaded executor pattern:

- **Reads**: Use `DBReader` static methods (synchronous, caller's thread)
- **Writes**: Use `DBWriter` static methods (asynchronous, single-threaded executor)
- All write operations automatically post EventBus events
- Never execute raw SQL writes outside `DBWriter` or `PodDBAdapter`
- Never modify database schema without migration in `DBUpgrader`

**Rationale**: SQLite database is accessed from multiple threads. The single-threaded write executor prevents race conditions and data corruption. All writes are serialized through the "DatabaseExecutor" thread at MIN_PRIORITY.

**Critical Tables**: Feeds, FeedItems, FeedMedia, Queue, Favorites, SimpleChapters, DownloadLog

## Localization & Translation

**MANDATORY RULE**: Only modify English string resources (`values/strings.xml`).

All translations are managed via Transifex and synchronized automatically. Editing translated strings in the repository will cause them to be overwritten.

**Process**:
1. Add/modify English strings only
2. Translations appear via Transifex integration
3. Never commit changes to `values-<lang>/strings.xml` files

## Code Style Compliance

**Java**: Use `config/checkstyle/checkstyle.xml` in Android Studio

**XML Layouts**: Use android-xml-formatter before committing:
```bash
curl -s -L https://github.com/ByteHamster/android-xml-formatter/releases/download/1.1.0/android-xml-formatter.jar > android-xml-formatter.jar
find . -wholename "*/res/layout/*.xml" | xargs java -jar android-xml-formatter.jar
```

**Rationale**: Consistent formatting reduces merge conflicts and improves code review efficiency across 100+ contributors.

## Product Flavor Awareness

**Play vs Free builds**:
- **Play flavor**: Includes Chromecast support (proprietary Google libraries)
- **Free flavor**: F-Droid compatible, no proprietary dependencies

**Rules**:
- Chromecast code MUST be isolated in `playback/cast/` module
- Check flavor before importing Google Play Services dependencies
- Both flavors share >99% of codebase via `common.gradle`

## Dependency Management

**Conservative upgrade policy**:
- Do NOT upgrade dependencies or build tools without explicit justification
- Upgrades can introduce subtle bugs difficult to track across Android versions/devices
- Discuss in issue or community call before major dependency changes

**Rationale**: AntennaPod supports Android API 21-35 (10+ years of Android versions). Dependency upgrades can break compatibility in unexpected ways.

## Governance

This constitution establishes the non-negotiable development standards for AntennaPod.

**Amendment Process**:
1. Propose change in GitHub issue or community call
2. Document rationale and migration plan for existing code
3. Update this constitution and increment version appropriately
4. Update dependent templates in `.specify/templates/`

**Compliance Review**:
- All PRs MUST pass CI quality gates (enforces Principles I, II)
- Code reviewers verify architectural compliance (Principles III, IV, V)
- Complexity violations require written justification in PR description

**Versioning Policy**:
- **MAJOR**: Backward-incompatible principle removals or redefinitions
- **MINOR**: New principles added or materially expanded guidance
- **PATCH**: Clarifications, wording fixes, non-semantic refinements

**Runtime Development Guidance**: See `CLAUDE.md` for build commands, architecture details, and implementation patterns.

**Version**: 1.0.1 | **Ratified**: 2025-10-31 | **Last Amended**: 2025-11-08
