# Data Model: Queue Color Gradient

**Status**: N/A - No New Data Models

## Summary

The Queue Color Gradient feature is UI-only and does not introduce any new data models, database tables, or schema changes.

## Existing Data Model Usage

This feature **reads** from existing data structures:

### QueueMetadata (Phase 1 - Multiple Queues Feature)

**Table**: `QueueMetadata`

| Field | Type | Description | Usage in Phase 7 |
|-------|------|-------------|------------------|
| `id` | LONG | Primary key | Identify active queue |
| `name` | TEXT | Queue display name | Not used (display only) |
| `color` | INTEGER | ARGB color value | **READ** - Source for gradient |
| `sort_order` | INTEGER | Creation sequence | Not used |
| `currently_playing_item_id` | LONG | Active episode ID | Not used |

**Access Pattern**:
```java
// Read queue color via DBReader
QueueMetadata queue = DBReader.getQueueById(queueId);
int queueColor = queue.getColor(); // Used for gradient creation
```

## No New Entities

This feature does not create, modify, or delete:
- ❌ Database tables
- ❌ Entity classes
- ❌ Database migrations
- ❌ EventBus events (uses existing QueueEvent)
- ❌ Preferences (uses existing current queue ID)

## Data Flow

```text
User switches queue
    ↓
UserPreferences.setCurrentQueueId(newQueueId)
    ↓
QueueEvent posted (SWITCHED)
    ↓
QueueViewModel receives event
    ↓
QueueViewModel queries DBReader.getQueueById(queueId)
    ↓
QueueViewModel emits queue.getColor() via LiveData
    ↓
Fragments observe LiveData
    ↓
QueueColorGradient.createGradientDrawable(color)
    ↓
Apply gradient to fragment title bar
```

**All data operations**: READ-ONLY

## Justification for N/A

Phase 7 is a **visual enhancement** that:
1. Uses existing `QueueMetadata.color` field (Phase 1)
2. No writes to database or preferences
3. All state managed in ViewModel (memory only)
4. Gradient cache is transient (cleared on theme change)

**Conclusion**: No data-model.md content required. Feature implementation proceeds with existing data structures.
