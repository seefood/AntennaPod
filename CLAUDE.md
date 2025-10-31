# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build System & Commands

### Prerequisites
- JDK 21 (Temurin distribution)
- Android SDK with API level 35
- Min SDK: 21, Target SDK: 35
- Java 17 source/target compatibility

### Build Commands

**Build the app:**
```bash
# Play Store variant (default)
./gradlew assemblePlayDebug
./gradlew assemblePlayRelease

# F-Droid variant
./gradlew assembleFreeRelease
```

**Run code quality checks:**
```bash
# Run all static analysis (checkstyle, lint, spotbugs)
./gradlew checkstyle :app:lintPlayDebug spotbugsPlayDebug spotbugsDebug

# Individual checks
./gradlew checkstyle
./gradlew :app:lintPlayDebug
./gradlew spotbugsPlayDebug
```

**Run tests:**
```bash
# All unit tests for Play variant
./gradlew testPlayDebugUnitTest testDebugUnitTest

# All unit tests for Free variant
./gradlew testFreeReleaseUnitTest testReleaseUnitTest

# Single module tests
./gradlew :storage:database:testDebugUnitTest
./gradlew :model:testDebugUnitTest

# Integration tests (requires emulator/device)
./gradlew connectedPlayDebugAndroidTest
```

**XML formatting:**
```bash
# Download formatter and check XML layout files
curl -s -L https://github.com/ByteHamster/android-xml-formatter/releases/download/1.1.0/android-xml-formatter.jar > android-xml-formatter.jar
find . -wholename "*/res/layout/*.xml" | xargs java -jar android-xml-formatter.jar
```

### Product Flavors
- **play** - Google Play Store variant (includes Chromecast support)
- **free** - F-Droid variant (no proprietary dependencies)

Both flavors share the same codebase with dimension "market".

## Architecture Overview

### Module Structure (37 Gradle Modules)

**Core Data Modules:**
- `model/` - Domain objects (Feed, FeedItem, FeedMedia, etc.) - NO Android dependencies
- `event/` - EventBus events for cross-component communication

**Storage Layer:**
- `storage/database/` - SQLite database via PodDBAdapter (direct SQL, no ORM)
- `storage/preferences/` - SharedPreferences wrappers
- `storage/importexport/` - OPML import/export

**Networking Layer:**
- `net/common/` - Shared HTTP utilities
- `net/discovery/` - Podcast discovery
- `net/download/service/` - Download management via WorkManager
- `net/sync/` - gpodder.net synchronization

**Parsing Layer:**
- `parser/feed/` - RSS 2.0 and Atom 1.0 parsing
- `parser/media/` - Media metadata extraction
- `parser/transcript/` - Podcast transcript parsing

**Playback Layer:**
- `playback/base/` - Core playback interfaces
- `playback/service/` - PlaybackService (foreground service) + ExoPlayerWrapper
- `playback/cast/` - Chromecast support (play flavor only)

**UI Layer:**
- `app/` - Main application with MainActivity (single activity architecture)
- `ui/common/` - Shared UI components
- `ui/*` - Feature-specific UI modules (episodes, subscriptions, queue, etc.)

### Database Architecture

**Key Tables:**
- `Feeds` - Podcast subscriptions
- `FeedItems` - Episodes
- `FeedMedia` - Episode media files
- `Queue` - Playback queue (ordered list of episode IDs, associated with specific queue via queue_id)
- `QueueMetadata` - Queue metadata (name, color, sort order, currently playing episode) - added in v3080100
- `Favorites` - Favorited episodes
- `SimpleChapters` - Episode chapters
- `DownloadLog` - Download history

**Access Pattern:**
- **Reads:** Use `DBReader` static methods (runs on caller's thread)
- **Writes:** Use `DBWriter` static methods (runs on single-threaded ExecutorService)
- All database writes post EventBus events to notify UI

**Critical:** DBWriter uses a single-threaded executor named "DatabaseExecutor" with MIN_PRIORITY. All write operations are serialized through this executor to prevent race conditions.

**Multiple Queues Support (v3080100+):**
- `QueueMetadata` table stores queue information (name, color, sort_order, currently_playing_*)
- `Queue` table has `queue_id` column to associate episodes with specific queues
- Active queue tracked in SharedPreferences as `PREF_CURRENT_QUEUE_ID`
- Queue operations default to current active queue if queueId not specified
- Foreign keys enabled via `PRAGMA foreign_keys=ON` in PodDBAdapter.onConfigure()
- Composite index on (queue_id, id) for optimal query performance

### Event-Driven Communication

Uses GreenRobot EventBus with annotation processor (generates `ApEventBusIndex`).

**Key Events:**
- `QueueEvent` - Queue modifications (includes queue switching, queue metadata changes)
- `FeedEvent` / `FeedListUpdateEvent` - Feed updates
- `FeedItemEvent` - Episode changes
- `PlaybackHistoryEvent` - Playback history updates
- `DownloadLogEvent` - Download status changes
- `MessageEvent` - User-visible messages (shown as Snackbars)

**Pattern:** Database writes → EventBus post → UI subscribers update

**Queue Events (v3080100+):**
- Posted when queues are created, renamed, deleted, or reordered
- Posted when episodes are added/removed from queues
- Posted when active queue switches (QueueEvent.Action.SWITCHED)

### Single Activity Architecture

`MainActivity` hosts all screens as fragments:
- Uses `FragmentTransaction` for navigation
- Bottom sheet for audio player (collapsible/expandable)
- Optional navigation: Drawer (tablet/traditional) OR Bottom Navigation (mobile)
- Fragments tagged with constants (e.g., `QueueFragment.TAG`)

### Playback Architecture

**PlaybackService** (foreground service):
- Wraps ExoPlayer via `ExoPlayerWrapper`
- Manages MediaSession for external controls
- Handles sleep timer, volume adaptation, skip silence
- Notification via `PlaybackServiceNotificationBuilder`
- Survives activity lifecycle

**Control Flow:**
1. UI → `PlaybackController` → `PlaybackService` (via service binding)
2. Service → ExoPlayer → Media playback
3. State changes → EventBus → UI updates

## Development Guidelines

### Code Quality Requirements

**Before committing:**
1. All checkstyle violations must be fixed (uses `config/checkstyle/checkstyle.xml`)
2. All SpotBugs violations must be resolved (medium/max effort)
3. All lint errors must be fixed (warnings treated as errors)
4. XML layout files must be formatted with android-xml-formatter
5. Tests must pass for the variant you're modifying

**Lint Configuration:**
- `warningsAsErrors = true`
- `abortOnError = true`
- Specific rules disabled in `app/build.gradle` lines 50-54

### Testing Guidelines

**Unit Tests:**
- Use Robolectric for Android-dependent code
- Test coverage expected for new functionality
- Call `DBWriter.tearDownTests()` after database tests to avoid "Illegal connection pointer" errors

**Integration Tests:**
- Espresso-based UI tests
- Run on emulator with animations disabled
- See `.github/workflows/checks.yml` for CI setup

### String Resources

**CRITICAL:** Only modify English string resources (`values/strings.xml`). All translations are managed via Transifex and will be overwritten.

### EventBus Usage

**Subscription:**
```java
@Override
public void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);
}

@Override
public void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
}

@Subscribe(threadMode = ThreadMode.MAIN)
public void onEventMainThread(QueueEvent event) {
    // Handle event
}
```

**Posting Events:**
- Use `EventBus.getDefault().post(event)` for regular events
- Use `EventBus.getDefault().postSticky(event)` for state that should persist for late subscribers
- Database write operations automatically post relevant events

### Common Patterns

**Loading Data:**
1. Start background operation (usually on DBReader or via DBWriter)
2. On completion, post EventBus event
3. UI subscribes to event and updates

**Modifying Queue:**
- Never modify queue directly in SQLite
- Use `DBWriter.addQueueItem()`, `DBWriter.removeQueueItem()`, etc.
- Operations automatically trigger `QueueEvent`
- Queue methods accept optional `queueId` parameter (defaults to current active queue)
- Active queue: `QueuePreferences.getCurrentQueueId()` / `setCurrentQueueId()`

**Feed Updates:**
- Managed via `FeedUpdateManager` (singleton)
- Uses WorkManager for background execution
- Handles paging for feeds with multiple pages

**Downloads:**
- Managed via `DownloadServiceInterface` (uses WorkManager)
- Status tracked via `DownloadStatus` objects
- Episodes can be in: QUEUED, RUNNING, COMPLETED states

## Key Implementation Details

### ViewBinding
- Enabled for all modules (`buildFeatures.viewBinding = true`)
- Use generated binding classes instead of findViewById

### Parallel Gradle Builds
- CI uses `org.gradle.parallel=true` in local.properties
- Safe due to module isolation

### Feed Paging
- Feeds can have multiple pages (e.g., large archives)
- `Feed.pageNr` indicates page number (only pageNr=0 persisted)
- `Feed.nextPageLink` points to next page URL
- Temporary feed objects merged into persisted feed

### Custom Feed Titles
- Feeds have both `feedTitle` (from feed) and `customTitle` (user override)
- Display logic should prefer `customTitle` when set

### Queue vs Favorites
- **Queue:** Ordered playback list (temporary, consumed on play)
- **Favorites:** Permanent episode bookmarks
- Both are separate tables, episodes can be in both
- **Multiple Queues (v3080100+):** Each episode can be in at most one queue at a time

### Release Signing
For release builds, create a keystore or set properties:
```bash
# For development/testing only:
keytool -genkey -v -keystore "app/keystore" -alias alias \
  -storepass password -keypass password -keyalg RSA -validity 10 \
  -dname "CN=antennapod.org, OU=dummy, O=dummy, L=dummy, S=dummy, C=US"
```

Or set in `local.properties`:
```
releaseStoreFile=/path/to/keystore
releaseStorePassword=yourpassword
releaseKeyAlias=youralias
releaseKeyPassword=yourpassword
```

## Common Gotchas

1. **EventBus Memory Leaks:** Always unregister in onStop(). Never register in onCreate() without unregistering.

2. **Database Access on Main Thread:** DBReader methods run synchronously. Wrap in background thread for heavy queries.

3. **Testing Database Code:** Must call `DBWriter.tearDownTests()` to flush executor queue in tests.

4. **Feed Item Filters:** `FeedItemFilter` uses include/exclude string lists - check `FeedItemFilterQuery` for SQL generation.

5. **WorkManager Tags:** Downloads use specific tag patterns (`WORK_TAG_EPISODE_URL` prefix) for tracking.

6. **Flavor-Specific Code:** Chromecast code is play-flavor only. Check flavor before importing cast dependencies.

7. **XML Formatting:** CI will fail if XML layouts aren't formatted. Run formatter locally before pushing.

8. **Theme Changes:** MainActivity restarts on theme/navigation changes. Save state appropriately.

9. **Database Migrations (v3080100+):** SQLite ALTER TABLE with DEFAULT applies virtually (instant operation). Use composite indexes for query optimization. Enable foreign keys in `PodDBAdapter.onConfigure()`.

10. **Queue Operations (v3080100+):** Episode can only be in one queue at a time. Always check if episode already in queue before adding. Use `QueuePreferences.getCurrentQueueId()` for default queue parameter.
