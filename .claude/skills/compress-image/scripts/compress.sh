#!/bin/bash
# Compress images using Tinify API
# Usage: compress.sh <image_path_or_directory>

API_KEY="${TINIFY_API_KEY}"

if [ -z "$API_KEY" ]; then
    echo "Error: TINIFY_API_KEY environment variable is not set"
    echo "Please set it with: export TINIFY_API_KEY='your_api_key'"
    echo "Get API key from: https://tinify.com/dashboard/api"
    exit 1
fi

if [ -z "$1" ]; then
    echo "Usage: compress.sh <image_path_or_directory>"
    echo "Example: compress.sh app/src/main/res/drawable"
    echo "Example: compress.sh app/src/main/res/drawable/icon.png"
    exit 1
fi

TINIFY_URL="https://api.tinify.com/output"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

compress_file() {
    local input_file="$1"
    local original_size=$(stat -f%z "$input_file" 2>/dev/null || stat -c%s "$input_file" 2>/dev/null)

    echo "Compressing: $input_file"
    echo "  Original size: $original_size bytes"

    # Compress using Tinify
    local temp_output="${input_file}.tmp"
    curl -s --user api:$API_KEY \
        --data-binary @"$input_file" \
        -o "$temp_output" \
        "$TINIFY_URL"

    if [ $? -ne 0 ] || [ ! -f "$temp_output" ]; then
        echo "  Error: Failed to compress $input_file"
        rm -f "$temp_output"
        return 1
    fi

    local new_size=$(stat -f%z "$temp_output" 2>/dev/null || stat -c%s "$temp_output" 2>/dev/null)

    # Calculate compression ratio
    local ratio=$((100 * (original_size - new_size) / original_size))

    if [ $new_size -lt $original_size ]; then
        mv "$temp_output" "$input_file"
        echo "  New size: $new_size bytes"
        echo "  Compression: ${ratio}% smaller"
    else
        echo "  New size: $new_size bytes"
        echo "  Compression: No improvement (file kept original)"
        rm -f "$temp_output"
    fi
}

# Check if input is file or directory
if [ -f "$1" ]; then
    compress_file "$1"
elif [ -d "$1" ]; then
    find "$1" -type f \( -iname "*.png" -o -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.webp" -o -iname "*.avif" \) | while read -r file; do
        compress_file "$file"
    done
else
    echo "Error: $1 is not a valid file or directory"
    exit 1
fi

echo ""
echo "Compression complete!"
