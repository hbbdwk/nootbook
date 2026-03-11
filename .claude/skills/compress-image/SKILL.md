---
name: compress-image
description: Compress images using Tinify API to reduce file size. Use when user wants to compress images, reduce resource size, or mentions TinyPNG/tinypng. Supports PNG, JPEG, WebP, AVIF.
argument-hint: <file_or_directory>
disable-model-invocation: true
allowed-tools: Bash
---

# Compress Image Skill

Compress images using Tinify API to reduce file size while maintaining quality.

## Prerequisites

Set your Tinify API key:
```bash
export TINIFY_API_KEY="your_api_key"
```
Get API key from: https://tinify.com/dashboard/api

## Usage

Run the compression script:
```bash
$CLAUDE/skills/compress-image/scripts/compress.sh $ARGUMENTS
```

## Supported Formats

- PNG
- JPEG
- WebP
- AVIF

## Example

```
/compress-image app/src/main/res/drawable
```

This will compress all images in the drawable directory and show compression ratios.
