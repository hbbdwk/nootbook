# Compress Image Reference

## Tinify API

- **Website**: https://tinify.com
- **Pricing**: 500 free compressions/month, then paid
- **API Key**: Get from https://tinify.com/dashboard/api

## Compression Limits

- Input: Max 5MB per image
- Output: Min 1KB per image
- Formats: PNG, JPEG, WebP, AVIF

## Common Issues

### "TINIFY_API_KEY environment variable is not set"
```bash
export TINIFY_API_KEY="your_key_here"
```

### "Failed to compress"
- Check if image format is supported
- Verify API key is valid
- Check network connection

## Performance Tips

- Compress images in batches (directory)
- Use lossless compression for icons
- Use lossy compression for photos
