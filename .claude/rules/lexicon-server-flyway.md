---
name: Flyway Migrations & Schema
description: Schema evolution rules, migration conventions, and intentional design decisions
type: convention
paths:
  - "src/main/resources/db/migration/**"
  - "src/main/kotlin/com/alirezaiyan/vokab/server/domain/entity/**"
---

# Flyway Migrations & Schema

## Ground Rules

- **Flyway manages everything.** `spring.jpa.hibernate.ddl-auto = validate`. Never `create`, `update`, or `create-drop`.
- **Never edit a committed migration** — Flyway validates checksums, the app will refuse to boot.
- New file for every schema change: `V{N}__short_snake_description.sql`. Use the next available N (current highest: see `db/migration/`).
- Migrations must run cleanly on **both PostgreSQL and H2** (dev/test use H2 in-memory).

## File Naming

```
V32__add_foo_to_bar.sql           ✅
V32_add_foo.sql                   ❌ (missing double underscore)
V31__retry_email_tables.sql       ❌ (can't reuse version; never edit V31)
```

## Column Conventions

- `id BIGSERIAL PRIMARY KEY` for all tables.
- `TIMESTAMP` for time columns with `DEFAULT CURRENT_TIMESTAMP` where the write path expects it.
- `NOT NULL` unless null carries explicit meaning. Document nullability.
- `UNIQUE` constraints for business keys (email, external IDs).
- Foreign keys with `ON DELETE CASCADE` when the child is meaningless without the parent.

## Indexes

Add indexes for columns used in `WHERE` / `JOIN` / `ORDER BY` on hot paths. Existing examples live in V29 (`V29__add_performance_indexes.sql`).

```sql
CREATE INDEX idx_words_user_id ON words(user_id);
CREATE INDEX idx_app_events_user_created ON app_events(user_id, created_at);
```

## Intentional Design — Do Not "Fix"

| Table          | Non-obvious rule                                                          |
| -------------- | ------------------------------------------------------------------------- |
| `app_events`   | `user_id` has **no FK** — analytics must survive account deletion (GDPR) |
| `audit_log`    | `user_id` has **no FK** — audit trail must survive account deletion      |
| sync endpoints | `UNIQUE(user_id, client_session_id)` prevents duplicate sync writes      |

If you see a missing FK on `user_id` in these tables, it is deliberate. Do not add one.

## JSONB Columns

`app_events.properties` is JSONB (PostgreSQL) / TEXT (H2). Serialize with Jackson in the service layer before persisting. Never hand-build JSON strings.

## H2 Compatibility

H2 behaves differently from PostgreSQL for: `gen_random_uuid()`, `JSONB` ops, window function quirks, interval arithmetic. When using PG-specific syntax, check `H2CompatFunctions` or the test harness for shims. If a feature can't be made H2-compatible, gate the test with the integration profile.

## Entity ↔ Migration Sync

When adding a migration that changes a table:

1. Write `V{N}__...sql`
2. Update the matching `@Entity` in `domain/entity/`
3. Update/add repository methods if needed
4. Start the app (`./gradlew bootRun`) — `ddl-auto=validate` will fail loudly if the entity and schema disagree
