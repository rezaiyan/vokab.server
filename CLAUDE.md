# Project Guidelines

## Jetpack Compose Conventions

### Composable Function Parameter Ordering

When defining composable functions, follow this parameter order for consistency and best practices:

1. **Mandatory parameters** (required, no defaults) - Place all required parameters first
2. **Modifier parameter** - Should have priority placement, typically `modifier: Modifier = Modifier`
3. **Optional parameters with defaults** - All other parameters with default values
4. **Functions/Callbacks** - Always place last, including EventSink-based callbacks

#### Example

```kotlin
@Composable
fun VocabularyCard(
    // 1. Mandatory parameters
    word: String,
    definition: String,

    // 2. Modifier (prioritized)
    modifier: Modifier = Modifier,

    // 3. Optional parameters with defaults
    isExpanded: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,

    // 4. Functions/callbacks (last)
    onExpand: () -> Unit = {},
    onEvent: (VocabularyEvent) -> Unit = {}
)
```

This ordering:
- Makes required parameters obvious and easy to find
- Follows Material Design and Compose conventions for Modifier placement
- Improves readability by grouping similar parameter types
- Enables clean trailing lambda syntax for callbacks
