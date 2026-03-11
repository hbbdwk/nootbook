---
name: code-review
description: Perform comprehensive code review for Kotlin/Android projects. Detects logic vulnerabilities, architecture violations, thread safety issues, and performance risks with structured output. Use when user asks for code review, code analysis, or /review.
argument-hint: [file_pattern]
context: fork
agent: general-purpose
allowed-tools: Read, Grep, Glob, Bash
---

# Code Review Skill

Perform a comprehensive code review with focus on accuracy and low false positives.

## Review Scope

Analyze the provided code for:

1. **逻辑漏洞检测 (Logic Vulnerabilities)**
2. **架构违规识别 (Architecture Violations)**
3. **线程安全分析 (Thread Safety)**
4. **性能风险提示 (Performance Risks)**

## Detection Rules (Low False Positive Focus)

### 1. 逻辑漏洞检测

Only flag these high-confidence issues:

| Pattern | Issue | Confidence |
|---------|-------|------------|
| `throw Exception()` in catch without message | Empty exception message | High |
| `==` for String comparison | Should use `.equals()` | High |
| `== null` on nullable after smart cast | Unnecessary null check | High |
| `if (condition) return; else return` | Redundant if-else | High |
| `Intent.getExtra()` without null check | Potential NPE | High |
| `finish()` called before async operation | Activity may be destroyed | High |
| `ConcurrentHashMap` with synchronized methods | Redundant synchronization | Medium |

### 2. 架构违规识别

| Pattern | Issue | Confidence |
|---------|-------|------------|
| Activity/Fragment doing DB operations directly | Should use Repository | High |
| ViewModel holding Android context reference | Memory leak risk | High |
| Public mutable `var` in data class | Should be immutable | Medium |
| Large (>200 lines) ViewModel without separation | Needs splitting | Medium |
| Hardcoded strings outside strings.xml | Should be extracted | Medium |
| Magic numbers without constants | Should use named constants | Medium |

### 3. 线程安全分析

| Pattern | Issue | Confidence |
|---------|-------|------------|
| `@Volatile` on object reference | Only guarantees visibility | High |
| `suspend` function with `runBlocking` | Anti-pattern | High |
| `Handler` without lifecycle awareness | Memory leak | High |
| `LiveData.postValue` in init block | May miss updates | Medium |
| Coroutine scope without structured concurrency | Resource leak | Medium |
| `SharedPreferences` in background thread | Performance issue | Medium |

### 4. 性能风险提示

| Pattern | Issue | Confidence |
|---------|-------|------------|
| `ListAdapter` not using DiffUtil | Inefficient updates | High |
| `notifyDataSetChanged()` | Should use specific notify | High |
| Image loading without size constraints | Memory waste | High |
| String concatenation in loop | Use StringBuilder | Medium |
| Creating new objects in `getView()`/`compose()` | Allocation pressure | Medium |
| Database query on Main thread | ANR risk | High |
| `collect` without `flowOn(Dispatchers.IO)` | UI thread blocking | High |

## Output Format

Provide structured JSON output:

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
      "description": "Room query executed on main thread will cause ANR",
      "suggestion": "Use withContext(Dispatchers.IO) or switch to Flow",
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

## Review Process

1. **Gather files**: Use Glob to find all `.kt` files
2. **Analyze each file**: Use Grep to find problematic patterns
3. **Verify issues**: Read relevant code to confirm
4. **Categorize**: Assign severity and category
5. **Generate report**: Output structured JSON

## Output Guidelines

- Only report high-confidence issues (>80% accuracy)
- Skip style/formatting issues (use ktlint for that)
- Skip obvious IDE warnings that IDE catches
- Group similar issues
- Provide actionable suggestions

## Response Language

Respond in the same language as the code being reviewed (Chinese for Chinese code, English otherwise).
