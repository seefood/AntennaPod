# Data Model: Smart Queues

**Feature**: Smart Queues
**Date**: 2025-11-08
**Phase**: 1 - Design & Contracts

## Entities

### QueueRuleset

A collection of rules associated with a specific queue that defines how episodes are selected during refill.

**Attributes**:
- `id` (Long, Primary Key): Unique identifier for the ruleset
- `queueId` (Long, Foreign Key → QueueMetadata.id): The queue this ruleset belongs to
- `createdAt` (Long, Timestamp): When the ruleset was created
- `updatedAt` (Long, Timestamp): When the ruleset was last modified

**Relationships**:
- One-to-one with QueueMetadata (one ruleset per queue)
- One-to-many with RefillRule (zero or more rules per ruleset)

**Validation Rules**:
- `queueId` must reference an existing queue (foreign key constraint)
- `queueId` must be unique (one ruleset per queue)

**State Transitions**:
- Created when first rule is added to a queue
- Updated when rules are added, removed, modified, or reordered
- Deleted when queue is deleted (cascade delete)

### RefillRule

A single rule that defines episode selection criteria.

**Attributes**:
- `id` (Long, Primary Key): Unique identifier for the rule
- `rulesetId` (Long, Foreign Key → QueueRuleset.id): The ruleset this rule belongs to
- `position` (Integer): Order of rule execution (0-based, lower numbers execute first)
- `ruleType` (String, Enum): Type of rule - "CLEAR_QUEUE" or "ADD_EPISODES"
- `selectionMethod` (String, Enum, nullable): How to select episodes - "OLDEST", "NEWEST", or "RANDOM" (null for CLEAR_QUEUE)
- `count` (Integer, nullable): Number of episodes to add (null for CLEAR_QUEUE)
- `sourceType` (String, Enum, nullable): Source of episodes - "FEED", "TAG", or "INBOX" (null for CLEAR_QUEUE)
- `sourceId` (String, nullable): Identifier for source:
  - For FEED: Feed ID (Long as String)
  - For TAG: Tag name (String)
  - For INBOX: null (inbox is a special source)
- `createdAt` (Long, Timestamp): When the rule was created
- `updatedAt` (Long, Timestamp): When the rule was last modified

**Relationships**:
- Many-to-one with QueueRuleset (rules belong to a ruleset)

**Validation Rules**:
- `rulesetId` must reference an existing ruleset (foreign key constraint)
- `position` must be unique within a ruleset (no duplicate positions)
- `ruleType` must be "CLEAR_QUEUE" or "ADD_EPISODES"
- For "CLEAR_QUEUE" rule:
  - `selectionMethod`, `count`, `sourceType`, `sourceId` must be null
  - Only one CLEAR_QUEUE rule allowed per ruleset (FR-030)
  - Must be at position 0 (first rule) (FR-031)
- For "ADD_EPISODES" rule:
  - `selectionMethod` must be "OLDEST", "NEWEST", or "RANDOM"
  - `count` must be > 0
  - `sourceType` must be "FEED", "TAG", or "INBOX"
  - `sourceId` must be non-null for FEED and TAG, null for INBOX

**State Transitions**:
- Created when user adds a rule
- Updated when user modifies rule parameters
- Deleted when user removes a rule
- Position updated when user reorders rules

## Database Schema

### QueueRuleset Table

```sql
CREATE TABLE QueueRuleset (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    queue_id INTEGER NOT NULL UNIQUE,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (queue_id) REFERENCES QueueMetadata(id) ON DELETE CASCADE
);

CREATE INDEX idx_queue_ruleset_queue_id ON QueueRuleset(queue_id);
```

### RefillRule Table

```sql
CREATE TABLE RefillRule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ruleset_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    rule_type TEXT NOT NULL CHECK (rule_type IN ('CLEAR_QUEUE', 'ADD_EPISODES')),
    selection_method TEXT CHECK (selection_method IN ('OLDEST', 'NEWEST', 'RANDOM')),
    count INTEGER CHECK (count > 0),
    source_type TEXT CHECK (source_type IN ('FEED', 'TAG', 'INBOX')),
    source_id TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (ruleset_id) REFERENCES QueueRuleset(id) ON DELETE CASCADE,
    UNIQUE (ruleset_id, position)
);

CREATE INDEX idx_refill_rule_ruleset_id ON RefillRule(ruleset_id);
CREATE INDEX idx_refill_rule_ruleset_position ON RefillRule(ruleset_id, position);
```

## Data Access Patterns

### Reading Rulesets

- Get ruleset for a queue: `DBReader.getQueueRuleset(queueId)`
- Get all rules for a ruleset: `DBReader.getRefillRules(rulesetId)`
- Get rules ordered by position: `DBReader.getRefillRules(rulesetId)` (ORDER BY position ASC)

### Writing Rulesets

- Create ruleset: `DBWriter.createQueueRuleset(queueId)`
- Update ruleset: `DBWriter.updateQueueRuleset(rulesetId, updatedAt)`
- Delete ruleset: `DBWriter.deleteQueueRuleset(rulesetId)` (cascade deletes rules)

### Reading Rules

- Get rule by ID: `DBReader.getRefillRule(ruleId)`
- Get rules for ruleset: `DBReader.getRefillRules(rulesetId)`
- Check if CLEAR_QUEUE rule exists: `DBReader.hasClearQueueRule(rulesetId)`

### Writing Rules

- Create rule: `DBWriter.createRefillRule(rulesetId, position, ruleType, ...)`
- Update rule: `DBWriter.updateRefillRule(ruleId, ...)`
- Delete rule: `DBWriter.deleteRefillRule(ruleId)`
- Reorder rules: `DBWriter.reorderRefillRules(rulesetId, newPositions)`

## Entity Lifecycle

### QueueRuleset Lifecycle

1. **Creation**: Created when first rule is added to a queue
2. **Update**: Updated whenever rules are modified (updatedAt timestamp)
3. **Deletion**: Deleted when queue is deleted (cascade) or when all rules are removed

### RefillRule Lifecycle

1. **Creation**: Created when user adds a rule via UI
2. **Update**: Updated when user modifies rule parameters
3. **Position Update**: Position updated when user reorders rules
4. **Deletion**: Deleted when user removes a rule

## Constraints

- One ruleset per queue (enforced by UNIQUE constraint on queue_id)
- One CLEAR_QUEUE rule per ruleset (enforced by application logic, FR-030)
- CLEAR_QUEUE rule must be at position 0 (enforced by application logic, FR-031)
- Unique position per ruleset (enforced by UNIQUE constraint on ruleset_id, position)
- Foreign key constraints ensure referential integrity
