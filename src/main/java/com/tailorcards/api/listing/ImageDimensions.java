package com.tailorcards.api.listing;

/**
 * Reads an image's width and height from its header without decoding pixels, so a "pixel bomb" (a small file that
 * declares enormous dimensions and expands to gigabytes when decoded) can be refused before anything decodes it.
 */
public final class ImageDimensions {

    public record Size(int width, int height) {
        public long pixels() {
            return (long) width * height;
        }
    }

    private ImageDimensions() {
    }

    /** Width and height, or null when the header can't be read. */
    public static Size read(ListingImageSanitizer.ImageType type, byte[] data) {
        try {
            return switch (type) {
                case PNG -> png(data);
                case JPEG -> jpeg(data);
                case WEBP -> webp(data);
            };
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /** IHDR is always the first chunk: width and height are big-endian at bytes 16 and 20. */
    private static Size png(byte[] d) {
        if (d[12] != 'I' || d[13] != 'H' || d[14] != 'D' || d[15] != 'R') {
            return null;
        }
        return new Size(u32be(d, 16), u32be(d, 20));
    }

    /** The first start-of-frame segment (SOF0-SOF15, except DHT, JPG and DAC) holds height then width. */
    private static Size jpeg(byte[] d) {
        int pos = 2;
        while (pos + 9 < d.length) {
            if ((d[pos] & 0xFF) != 0xFF) {
                return null;
            }
            int marker = d[pos + 1] & 0xFF;
            if (marker == 0xFF) {
                pos++;
                continue;
            }
            if (marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
                pos += 2;
                continue;
            }
            int length = ((d[pos + 2] & 0xFF) << 8) | (d[pos + 3] & 0xFF);
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                int height = ((d[pos + 5] & 0xFF) << 8) | (d[pos + 6] & 0xFF);
                int width = ((d[pos + 7] & 0xFF) << 8) | (d[pos + 8] & 0xFF);
                return new Size(width, height);
            }
            if (marker == 0xDA || length < 2) {
                return null; // reached the image data without a frame header
            }
            pos += 2 + length;
        }
        return null;
    }

    /** RIFF....WEBP then VP8 (lossy), VP8L (lossless) or VP8X (extended) with the canvas size. */
    private static Size webp(byte[] d) {
        String chunk = new String(d, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return switch (chunk) {
            case "VP8 " -> new Size(u16le(d, 26) & 0x3FFF, u16le(d, 28) & 0x3FFF);
            case "VP8L" -> {
                long bits = (d[21] & 0xFFL) | (d[22] & 0xFFL) << 8 | (d[23] & 0xFFL) << 16 | (d[24] & 0xFFL) << 24;
                yield new Size((int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1);
            }
            case "VP8X" -> new Size(u24le(d, 24) + 1, u24le(d, 27) + 1);
            default -> null;
        };
    }

    private static int u32be(byte[] d, int i) {
        long v = ((d[i] & 0xFFL) << 24) | ((d[i + 1] & 0xFFL) << 16) | ((d[i + 2] & 0xFFL) << 8) | (d[i + 3] & 0xFFL);
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    }

    private static int u16le(byte[] d, int i) {
        return (d[i] & 0xFF) | (d[i + 1] & 0xFF) << 8;
    }

    private static int u24le(byte[] d, int i) {
        return (d[i] & 0xFF) | (d[i + 1] & 0xFF) << 8 | (d[i + 2] & 0xFF) << 16;
    }
}
