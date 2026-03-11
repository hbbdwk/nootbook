# Code Review Output Examples

## Example 1: Clean Code (No Issues)

```json
{
  "summary": {
    "total_issues": 0,
    "by_severity": {},
    "by_category": {}
  },
  "issues": [],
  "statistics": {
    "files_analyzed": 8,
    "lines_of_code": 892,
    "issues_per_100_lines": 0
  },
  "message": "No issues found. Great code quality!"
}
```

## Example 2: Issues Found

```json
{
  "summary": {
    "total_issues": 5,
    "by_severity": {
      "critical": 1,
      "warning": 3,
      "info": 1
    },
    "by_category": {
      "logic": 1,
      "architecture": 1,
      "thread_safety": 1,
      "performance": 2
    }
  },
  "issues": [
    {
      "id": 1,
      "severity": "critical",
      "category": "performance",
      "file": "NoteRepository.kt",
      "line": 45,
      "title": "Database query on main thread",
      "description": "Room query executed on main thread will cause ANR for large datasets",
      "suggestion": "Use withContext(Dispatchers.IO) or return Flow from suspend function",
      "confidence": "high",
      "code": "val notes = database.noteDao().getAllNotes()"
    },
    {
      "id": 2,
      "severity": "warning",
      "category": "architecture",
      "file": "MainActivity.kt",
      "line": 78,
      "title": "Activity directly accessing database",
      "description": "Activity should not directly access DAO, violates clean architecture",
      "suggestion": "Use ViewModel + Repository pattern",
      "confidence": "high",
      "code": "database.noteDao()..."
    },
    {
      "id": 3,
      "severity": "warning",
      "category": "thread_safety",
      "file": "NoteViewModel.kt",
      "line": 23,
      "title": "Handler without lifecycle awareness",
      "description": "Handler posted messages after Activity destruction may cause NPE",
      "suggestion": "Use lifecycle-aware components or remove callbacks in onDestroy",
      "confidence": "high"
    },
    {
      "id": 4,
      "severity": "info",
      "category": "logic",
      "file": "NoteEditScreen.kt",
      "line": 112,
      "title": "Empty catch block",
      "description": "Empty catch block silently swallows exceptions",
      "suggestion": "At minimum, log the exception",
      "confidence": "medium"
    },
    {
      "id": 5,
      "severity": "warning",
      "category": "performance",
      "file": "NoteListScreen.kt",
      "line": 67,
      "title": "Using notifyDataSetChanged()",
      "description": "Inefficient - causes full list redraw instead of targeted updates",
      "suggestion": "Use DiffUtil with ListAdapter for efficient updates",
      "confidence": "high"
    }
  ],
  "statistics": {
    "files_analyzed": 12,
    "lines_of_code": 1543,
    "issues_per_100_lines": 0.32
  }
}
```

## Severity Levels

| Level | Description | Action Required |
|-------|-------------|-----------------|
| critical | Will cause crash/ANR/data loss | Fix immediately |
| warning | May cause issues in production | Review and fix |
| info | Code smell or best practice | Consider improving |

## Categories

| Category | Description |
|----------|-------------|
| logic | Logical errors, bugs |
| architecture | Design pattern violations |
| thread_safety | Concurrency issues |
| performance | Performance anti-patterns |
