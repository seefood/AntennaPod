# Quickstart: Queue Color Gradient

**Feature**: Queue Color Gradient - Title Bar Styling
**Phase**: 1 (Quick Reference)
**Prerequisites**: AntennaPod development environment setup, JDK 21, Android SDK API 35

## Overview

This feature applies the active queue's color as a gradient background on title bars across 6 major screens. Gradients fade from queue color (top) to transparent (bottom) with automatic text color selection for WCAG AA compliance.

## Quick Start

### 1. Build and Run

```bash
# Build Play variant (default)
./gradlew assemblePlayDebug

# Install on device/emulator
adb install app/build/outputs/apk/play/debug/app-play-debug.apk

# Or use Android Studio: Run → Debug 'app'
```

### 2. Create Test Queues with Different Colors

```bash
# Launch app, navigate to Queue Management:
# Main screen → Queue tab → ... (overflow menu) → Manage Queues → + (FAB)

# Create 3 queues for testing:
1. "Work" - Blue (#2196F3)
2. "Leisure" - Orange (#FF9800)
3. "Light Test" - Light Yellow (#FFEB3B) - tests scrim application
4. "Dark Test" - Dark Purple (#4A148C) - tests white text
```

### 3. Verify Gradient on All 6 Screens

Navigate to each screen and verify gradient appears:

1. **Player Screen** (AudioPlayerFragment)
   - Play any episode
   - Observe gradient on title bar with playback controls
   - ✅ Gradient visible, text readable

2. **Queue Pane** (QueueFragment)
   - Main screen → Queue tab
   - ✅ Gradient on queue list title bar

3. **Episodes Pane** (AllEpisodesFragment)
   - Main screen → Episodes tab
   - ✅ Gradient on all episodes title bar

4. **Home Screen** (HomeFragment)
   - Main screen → Home tab
   - ✅ Gradient on home dashboard title bar

5. **Subscriptions** (SubscriptionFragment)
   - Main screen → Subscriptions tab
   - ✅ Gradient on subscriptions grid title bar

6. **Queue Management** (QueueManagementFragment)
   - Queue tab → ... → Manage Queues
   - ✅ Gradient on queue management title bar

### 4. Test Queue Switching

```bash
# Switch between queues and observe immediate gradient update:
1. Queue tab → ... → Switch Queue → Select "Work" (blue)
   ✅ All screens update to blue gradient

2. Switch to "Leisure" (orange)
   ✅ All screens update to orange gradient

3. Switch to "Light Test" (yellow)
   ✅ Scrim applied, text remains readable (should be darker yellow)

4. Switch to "Dark Test" (purple)
   ✅ White text on dark gradient
```

### 5. Test Theme Switching

```bash
# Settings → Appearance → Theme:
1. Switch from Light to Dark theme
   ✅ Gradients update, text remains readable

2. Switch back to Light theme
   ✅ Gradients with scrim applied for light colors

3. Navigate between screens in each theme
   ✅ Consistent gradient appearance
```

## Testing Checklist

### Visual Verification

- [ ] Gradient visible on all 6 target screens
- [ ] Gradient colors match queue management display exactly
- [ ] Gradient fades smoothly from queue color to transparent
- [ ] No visual glitches or artifacts
- [ ] Title bar height consistent across all screens

### Text Readability (WCAG AA)

- [ ] Black text on light gradients (yellow, white, light blue)
- [ ] White text on dark gradients (dark blue, purple, black)
- [ ] Text readable at all times (minimum 4.5:1 contrast ratio)
- [ ] Icon tint matches text color (black or white)

### Behavior

- [ ] Gradient updates immediately when switching queues (<16ms ideal)
- [ ] No lag or frame drops during queue switch
- [ ] Gradient persists when navigating between screens
- [ ] Gradient unchanged when viewing individual episodes
- [ ] Default theme color shown if no queue selected (edge case)

### Theme Support

- [ ] Light theme: Scrim applied to light queue colors
- [ ] Dark theme: Gradients visible without scrim for most colors
- [ ] Theme change triggers gradient cache clear and redraw
- [ ] Consistent appearance across both themes

### Performance

- [ ] No noticeable performance degradation
- [ ] Queue switch feels instant (<100ms perceived)
- [ ] Scrolling smooth on all screens with gradient
- [ ] No memory leaks (use LeakCanary if available)

### Edge Cases

- [ ] Very light color (white queue): Scrim darkens it, text readable
- [ ] Very dark color (black queue): White text, no scrim
- [ ] Rapid queue switching: No visual glitches or stale gradients
- [ ] Orientation change: Gradient reapplied correctly
- [ ] App backgrounded/foregrounded: Gradient persists
- [ ] Queue deleted while active: Falls back to default or next queue

## Development Commands

### Run Quality Checks

```bash
# All quality gates (run before commit)
./gradlew checkstyle spotbugsPlayDebug spotbugsDebug :app:lintPlayDebug

# Individual checks
./gradlew checkstyle
./gradlew spotbugsPlayDebug
./gradlew :app:lintPlayDebug
```

### Run Tests

```bash
# Unit tests for gradient utility
./gradlew :ui:common:testDebugUnitTest --tests "*QueueColorGradient*"

# Unit tests for ViewModel caching
./gradlew :ui:common:testDebugUnitTest --tests "*QueueViewModel*"

# All unit tests
./gradlew testPlayDebugUnitTest testDebugUnitTest

# UI tests (requires emulator)
./gradlew connectedPlayDebugAndroidTest
```

### Format XML Layouts

```bash
# Download formatter
curl -s -L https://github.com/ByteHamster/android-xml-formatter/releases/download/1.1.0/android-xml-formatter.jar > android-xml-formatter.jar

# Format new/modified layout files
find ui/common/src/main/res/layout -name "*.xml" | xargs java -jar android-xml-formatter.jar
```

## Implementation Files

### Created

- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueColorGradient.java`
  - Utility for gradient creation, luminance calculation, scrim application
- `ui/common/src/test/java/de/danoeh/antennapod/ui/common/QueueColorGradientTest.java`
  - Unit tests for gradient utility

### Modified

- `ui/common/src/main/java/de/danoeh/antennapod/ui/common/QueueViewModel.java`
  - Added: `getCurrentQueueColor()` LiveData
  - Added: `getGradientForColor(int)` with caching
  - Added: Theme change EventBus subscription
- `app/src/main/java/de/danoeh/antennapod/ui/screen/playback/audio/AudioPlayerFragment.java`
  - Applied gradient to title bar
  - Added LiveData observation for queue color
- `app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueFragment.java`
  - Applied gradient to queue pane title bar
- `app/src/main/java/de/danoeh/antennapod/ui/screen/AllEpisodesFragment.java`
  - Applied gradient to episodes pane title bar
- `app/src/main/java/de/danoeh/antennapod/ui/screen/home/HomeFragment.java`
  - Applied gradient to home screen title bar
- `app/src/main/java/de/danoeh/antennapod/ui/screen/subscriptions/SubscriptionFragment.java`
  - Applied gradient to subscriptions title bar
- `app/src/main/java/de/danoeh/antennapod/ui/screen/queue/QueueManagementFragment.java`
  - Applied gradient to queue management title bar

## Debugging Tips

### Gradient Not Showing

```bash
# Check logs for errors:
adb logcat | grep -i "gradient\|queue"

# Verify queue color is not null:
# Add log in QueueViewModel.getCurrentQueueColor() observer

# Check ViewBinding:
# Ensure titleBar reference is not null in fragment
```

### Text Unreadable

```bash
# Verify luminance calculation:
# Add assertion in QueueColorGradient.computeTextColor()
// double luminance = ColorUtils.calculateLuminance(color);
// Log.d("QueueGradient", "Luminance: " + luminance + " → " + (luminance > 0.5 ? "BLACK" : "WHITE"));

# Check contrast ratio:
// double contrast = ColorUtils.calculateContrast(textColor, backgroundColor);
// Log.d("QueueGradient", "Contrast: " + contrast + (contrast >= 4.5 ? " ✓" : " ✗"));
```

### Performance Issues

```bash
# Enable StrictMode for main thread detection:
# In MainActivity.onCreate():
// StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
//     .detectAll()
//     .penaltyLog()
//     .build());

# Profile gradient creation time:
// long start = System.nanoTime();
// GradientDrawable gradient = QueueColorGradient.createGradientDrawable(color);
// long elapsed = (System.nanoTime() - start) / 1_000_000; // Convert to ms
// Log.d("QueueGradient", "Creation time: " + elapsed + "ms");
```

### Cache Not Working

```bash
# Verify cache hits/misses:
// In QueueViewModel.getGradientForColor():
// if (gradientCache.containsKey(color)) {
//     Log.d("QueueViewModel", "Cache HIT for color: " + Integer.toHexString(color));
// } else {
//     Log.d("QueueViewModel", "Cache MISS for color: " + Integer.toHexString(color));
// }

# Monitor cache size:
// Log.d("QueueViewModel", "Cache size: " + gradientCache.size());
```

## Success Criteria

✅ **Visual**: Gradient visible on all 6 screens, colors accurate
✅ **Behavior**: Immediate update on queue switch, persistent across navigation
✅ **Accessibility**: Text readable on all gradients (WCAG AA 4.5:1 contrast)
✅ **Performance**: No lag, frame drops, or memory leaks
✅ **Quality**: Zero checkstyle, SpotBugs, Lint violations
✅ **Tests**: All unit tests pass, UI tests verify gradient appearance
✅ **Theme**: Works correctly in light and dark themes

## Known Limitations

- **Mini Player**: Not included in initial implementation (6 screens only)
- **Tablet Layout**: Gradient may need adjustment for large screens (verify in testing)
- **Landscape Mode**: Title bar height may differ, but gradient scales automatically

## Next Steps

After verifying all success criteria:

1. Run full quality check suite (checkstyle, SpotBugs, Lint)
2. Run all unit and UI tests
3. Create pull request with screenshots of gradients on all 6 screens
4. Update CLAUDE.md with queue gradient feature documentation

## Troubleshooting

**Problem**: Gradient not updating on queue switch

**Solution**: Verify QueueViewModel is activity-scoped (not fragment-scoped):
```java
QueueViewModel viewModel = new ViewModelProvider(requireActivity())
    .get(QueueViewModel.class);
```

**Problem**: Text color inconsistent across fragments

**Solution**: Ensure all fragments use `QueueColorGradient.computeTextColor()`:
```java
int textColor = QueueColorGradient.computeTextColor(queueColor);
titleBarText.setTextColor(textColor);
```

**Problem**: Scrim not applied to light colors

**Solution**: Verify luminance threshold in `applyScrim()`:
```java
if (ColorUtils.calculateLuminance(color) > 0.5) {
    // Apply 20% darkening
}
```

## Contact

For questions or issues:
- Check AntennaPod documentation: `CLAUDE.md`
- Review specification: `specs/007-queue-color-gradient/spec.md`
- Research findings: `specs/007-queue-color-gradient/research.md`
