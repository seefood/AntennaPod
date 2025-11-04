# API Contracts: Queue Color Gradient

**Status**: N/A - No External API Contracts

## Summary

The Queue Color Gradient feature is UI-only and does not expose any external API contracts (REST, GraphQL, gRPC, etc.).

## Internal Java APIs

This feature introduces internal Java APIs for use within the AntennaPod codebase:

### QueueColorGradient Utility Class

**Package**: `de.danoeh.antennapod.ui.common`

```java
public class QueueColorGradient {
    /**
     * Create a gradient drawable from queue color to transparent
     * @param queueColor ARGB color of the queue (top of gradient)
     * @param endColor End color (typically Color.TRANSPARENT)
     * @return GradientDrawable for title bar background
     */
    public static GradientDrawable createGradientDrawable(int queueColor, int endColor);

    /**
     * Compute text color (black or white) based on background luminance
     * @param backgroundColor ARGB color to compute luminance for
     * @return Color.BLACK if luminance > 0.5, Color.WHITE otherwise
     */
    public static int computeTextColor(int backgroundColor);

    /**
     * Apply 20% black scrim to light colors for visibility
     * @param color Original ARGB color
     * @return Darkened color if luminance > 0.5, unchanged otherwise
     */
    public static int applyScrim(int color);
}
```

### QueueViewModel Enhancements

**Package**: `de.danoeh.antennapod.ui.common`

```java
public class QueueViewModel extends AndroidViewModel {
    /**
     * Observe current active queue color for gradient updates
     * @return LiveData emitting ARGB color when queue switches
     */
    public LiveData<Integer> getCurrentQueueColor();

    /**
     * Get cached gradient drawable for queue color
     * @param color ARGB color value
     * @return Cached or newly created GradientDrawable
     */
    public GradientDrawable getGradientForColor(int color);

    /**
     * Clear gradient drawable cache (called on theme change)
     */
    public void clearGradientCache();
}
```

## No External Contracts

This feature does NOT introduce:
- ❌ REST API endpoints
- ❌ GraphQL queries/mutations
- ❌ gRPC services
- ❌ WebSocket messages
- ❌ Background sync protocols
- ❌ Inter-process communication (IPC)

## Justification for N/A

Phase 7 is an **internal UI feature** with:
1. No network communication
2. No external service integration
3. Only internal Java APIs for gradient rendering
4. All functionality contained within app process

**Conclusion**: No external API contracts required. Internal APIs documented in JavaDoc during implementation.
