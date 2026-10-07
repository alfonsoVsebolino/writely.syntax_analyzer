# 3. Multi-Modal Source Ingestion Architecture

- **Status**: Accepted
- **Date**: 2026-10-07

## Context

The analyzer supports three distinct source ingestion pathways: single line commands, code snippets, and local file uploads. Ingestion must enforce offline safety, validate source payloads before parser delegation, and handle edge cases gracefully.

## Decisions/usage

1. **Service Interface**:
   `SourceIngestionService` exposes:
   - `ingestSingleLine(String line, Language explicitLanguage)`
   - `ingestSnippet(String snippet, Language explicitLanguage)`
   - `ingestFile(Path filePath, Language explicitLanguage)`

2. **File Size Limit**:
   Maximum file size is **15 MB** (`MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024`). Files exceeding this throw `IngestionException(FILE_TOO_LARGE)`.

3. **Binary & Encoding Detection**:
   Inspect the initial 8 KB buffer for null bytes (`\0`) and verify UTF-8 decoding. Any binary or non-text content throws `IngestionException(BINARY_FILE_DETECTED)`.

4. **Line-Ending Normalization**:
   Raw text normalizes line breaks (`\r\n` and `\r` to `\n`) while preserving source integrity and accurately computing character spans.

5. **Language Resolution**:
   - Explicit selection overrides file extension inference.
   - For file ingestion with no explicit language, infer from file extension via `Language.fromFileName(name)`.
   - If inference fails or no language is specified for single line/snippet, throw `IngestionException(LANGUAGE_REQUIRED)`.

6. **Empty & Blank Validation**:
   Empty or whitespace-only inputs throw `IngestionException(EMPTY_SOURCE_INPUT)`.

7. **GUI Sidenote / Contract**:
   The desktop GUI layer (downstream Issue #7 / #15) must catch `IngestionException` and display user-friendly error dialogs/banners without crashing or throwing unhandled stack traces to the user.

## Error Codes

- `EMPTY_SOURCE_INPUT`: Input is null, empty, or whitespace-only.
- `LANGUAGE_REQUIRED`: Language could not be inferred and was not explicitly selected.
- `FILE_NOT_FOUND`: Specified path does not exist.
- `FILE_UNREADABLE`: File permissions prevent reading.
- `FILE_TOO_LARGE`: File exceeds 15 MB.
- `BINARY_FILE_DETECTED`: File contains binary null bytes or invalid UTF-8 sequences.
- `IO_ERROR`: Underlying I/O exception during read.
