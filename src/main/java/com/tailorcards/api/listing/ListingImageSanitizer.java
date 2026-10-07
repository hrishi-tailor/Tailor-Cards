package com.tailorcards.api.listing;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Validates card photos by content (magic bytes and container structure, not the client's
 * declared type) and strips metadata such as EXIF GPS, XMP, IPTC and comments without
 * re-encoding the pixels.
 *
 * <p>The EXIF orientation tag is the one piece of metadata kept: it is rewritten into a minimal
 * EXIF block that contains nothing else, so rotated phone photos still display upright.
 */
@Component
public class ListingImageSanitizer {

    public enum ImageType {
        JPEG("image/jpeg"),
        PNG("image/png"),
        WEBP("image/webp");

        private final String mediaType;

        ImageType(String mediaType) {
            this.mediaType = mediaType;
        }

        public String mediaType() {
            return mediaType;
        }
    }

    /** Cleaned image bytes plus the EXIF orientation (1-8) that was preserved; 1 means upright. */
    public record SanitizedImage(ImageType type, byte[] bytes, int orientation) {}

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};
    private static final Set<String> PNG_METADATA_CHUNKS = Set.of("eXIf", "tEXt", "zTXt", "iTXt", "tIME");
    private static final int ORIENTATION_TAG = 0x0112;

    public SanitizedImage sanitize(byte[] data) {
        ImageType type = detect(data);
        if (type == null) {
            throw new IllegalArgumentException("Unsupported image. Upload a JPEG, PNG or WebP photo.");
        }
        try {
            return switch (type) {
                case JPEG -> stripJpeg(data);
                case PNG -> stripPng(data);
                case WEBP -> stripWebp(data);
            };
        } catch (IndexOutOfBoundsException e) {
            throw corrupt(type);
        }
    }

    public static ImageType detect(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (startsWith(data, 0, PNG_SIGNATURE)) {
            return ImageType.PNG;
        }
        if (startsWith(data, 0, ascii("RIFF")) && startsWith(data, 8, ascii("WEBP"))) {
            return ImageType.WEBP;
        }
        return null;
    }

    // ---------------------------------------------------------------- JPEG

    private SanitizedImage stripJpeg(byte[] in) {
        ByteArrayOutputStream head = new ByteArrayOutputStream(); // a leading JFIF APP0, if any
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int orientation = 1;
        boolean firstSegment = true;
        boolean sawScan = false;
        int pos = 2;

        while (true) {
            if (pos >= in.length || (in[pos] & 0xFF) != 0xFF) {
                throw corrupt(ImageType.JPEG);
            }
            while (pos < in.length && (in[pos] & 0xFF) == 0xFF) {
                pos++; // fill bytes
            }
            if (pos >= in.length) {
                throw corrupt(ImageType.JPEG);
            }
            int marker = in[pos++] & 0xFF;

            if (marker == 0xD9) { // EOI: anything after it (e.g. appended images with their own EXIF) is dropped
                if (!sawScan) {
                    throw corrupt(ImageType.JPEG);
                }
                body.write(0xFF);
                body.write(0xD9);
                break;
            }
            if ((marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
                body.write(0xFF);
                body.write(marker);
                continue;
            }

            int segLen = u16be(in, pos);
            if (segLen < 2 || pos + segLen > in.length) {
                throw corrupt(ImageType.JPEG);
            }
            int payloadStart = pos + 2;
            int segEnd = pos + segLen;

            if (marker == 0xE1 && startsWith(in, payloadStart, EXIF_HEADER)) {
                orientation = readTiffOrientation(in, payloadStart + EXIF_HEADER.length, segEnd);
            }
            if (keepJpegSegment(marker, in, payloadStart)) {
                ByteArrayOutputStream target = firstSegment && marker == 0xE0 ? head : body;
                target.write(0xFF);
                target.write(marker);
                target.write(in, pos, segLen);
            }
            firstSegment = false;
            pos = segEnd;

            if (marker == 0xDA) { // start of scan: copy entropy-coded data up to the next real marker
                sawScan = true;
                int dataStart = pos;
                while (true) {
                    if (pos + 1 >= in.length) {
                        throw corrupt(ImageType.JPEG);
                    }
                    if ((in[pos] & 0xFF) == 0xFF) {
                        int next = in[pos + 1] & 0xFF;
                        if (next == 0x00 || (next >= 0xD0 && next <= 0xD7)) {
                            pos += 2;
                            continue;
                        }
                        if (next != 0xFF) {
                            break;
                        }
                    }
                    pos++;
                }
                body.write(in, dataStart, pos - dataStart);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(0xFF);
        out.write(0xD8);
        out.writeBytes(head.toByteArray());
        if (orientation != 1) {
            byte[] tiff = orientationTiff(orientation);
            int len = 2 + EXIF_HEADER.length + tiff.length;
            out.write(0xFF);
            out.write(0xE1);
            out.write(len >> 8);
            out.write(len & 0xFF);
            out.writeBytes(EXIF_HEADER);
            out.writeBytes(tiff);
        }
        out.writeBytes(body.toByteArray());
        return new SanitizedImage(ImageType.JPEG, out.toByteArray(), orientation);
    }

    private static boolean keepJpegSegment(int marker, byte[] in, int payloadStart) {
        if (marker == 0xFE) {
            return false; // comment
        }
        if (marker >= 0xE0 && marker <= 0xEF) {
            return switch (marker) {
                case 0xE0 -> true; // JFIF / JFXX
                case 0xE2 -> startsWith(in, payloadStart, ascii("ICC_PROFILE\0")); // colour profile, not MPF
                case 0xEE -> startsWith(in, payloadStart, ascii("Adobe")); // needed to decode CMYK/YCCK
                default -> false; // EXIF, XMP, IPTC/Photoshop, maker data
            };
        }
        return true;
    }

    // ---------------------------------------------------------------- PNG

    private SanitizedImage stripPng(byte[] in) {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        head.writeBytes(PNG_SIGNATURE);
        int orientation = 1;
        boolean sawHeader = false;
        boolean sawEnd = false;
        int pos = PNG_SIGNATURE.length;

        while (pos + 12 <= in.length) {
            long chunkLen = u32be(in, pos);
            if (chunkLen > in.length - pos - 12L) {
                throw corrupt(ImageType.PNG);
            }
            String chunkType = new String(in, pos + 4, 4, StandardCharsets.US_ASCII);
            int dataStart = pos + 8;
            int chunkEnd = dataStart + (int) chunkLen + 4;

            if (!sawHeader && !"IHDR".equals(chunkType)) {
                throw corrupt(ImageType.PNG);
            }
            if ("eXIf".equals(chunkType)) {
                orientation = readTiffOrientation(in, dataStart, dataStart + (int) chunkLen);
            }
            if (!PNG_METADATA_CHUNKS.contains(chunkType)) {
                ("IHDR".equals(chunkType) ? head : body).write(in, pos, chunkEnd - pos);
            }
            sawHeader = true;
            pos = chunkEnd;
            if ("IEND".equals(chunkType)) {
                sawEnd = true;
                break; // drop trailing data
            }
        }
        if (!sawEnd) {
            throw corrupt(ImageType.PNG);
        }

        if (orientation != 1) { // eXIf must precede IDAT, so it goes straight after IHDR
            byte[] tiff = orientationTiff(orientation);
            byte[] typeAndData = concat(ascii("eXIf"), tiff);
            CRC32 crc = new CRC32();
            crc.update(typeAndData);
            writeU32be(head, tiff.length);
            head.writeBytes(typeAndData);
            writeU32be(head, crc.getValue());
        }
        head.writeBytes(body.toByteArray());
        return new SanitizedImage(ImageType.PNG, head.toByteArray(), orientation);
    }

    // ---------------------------------------------------------------- WebP

    private SanitizedImage stripWebp(byte[] in) {
        long riffSize = u32le(in, 4);
        int end = (int) Math.min(in.length, 8 + riffSize);
        List<byte[]> chunks = new ArrayList<>();
        int vp8xIndex = -1;
        boolean sawImage = false;
        int orientation = 1;
        int pos = 12;

        while (pos + 8 <= end) {
            String fourcc = new String(in, pos, 4, StandardCharsets.US_ASCII);
            long size = u32le(in, pos + 4);
            long padded = size + (size & 1);
            if (padded > end - pos - 8L) {
                throw corrupt(ImageType.WEBP);
            }
            int dataStart = pos + 8;
            int chunkEnd = dataStart + (int) padded;

            switch (fourcc) {
                case "EXIF" -> {
                    int tiffStart = startsWith(in, dataStart, EXIF_HEADER) ? dataStart + EXIF_HEADER.length : dataStart;
                    orientation = readTiffOrientation(in, tiffStart, dataStart + (int) size);
                }
                case "XMP " -> {
                    // dropped
                }
                default -> {
                    if ("VP8X".equals(fourcc)) {
                        if (size < 10) {
                            throw corrupt(ImageType.WEBP);
                        }
                        vp8xIndex = chunks.size();
                    }
                    if (fourcc.equals("VP8 ") || fourcc.equals("VP8L") || fourcc.equals("ANIM")) {
                        sawImage = true;
                    }
                    byte[] chunk = new byte[chunkEnd - pos];
                    System.arraycopy(in, pos, chunk, 0, chunk.length);
                    chunks.add(chunk);
                }
            }
            pos = chunkEnd;
        }
        if (!sawImage) {
            throw corrupt(ImageType.WEBP);
        }

        if (vp8xIndex < 0) {
            orientation = 1; // simple WebP cannot carry EXIF
        } else {
            byte[] vp8x = chunks.get(vp8xIndex);
            int flags = vp8x[8] & 0xFF;
            flags &= ~0x04; // XMP
            flags = orientation != 1 ? flags | 0x08 : flags & ~0x08; // EXIF
            vp8x[8] = (byte) flags;
            if (orientation != 1) {
                byte[] tiff = orientationTiff(orientation); // even length, no padding needed
                ByteArrayOutputStream exif = new ByteArrayOutputStream();
                exif.writeBytes(ascii("EXIF"));
                writeU32le(exif, tiff.length);
                exif.writeBytes(tiff);
                chunks.add(exif.toByteArray());
            }
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(ascii("WEBP"));
        chunks.forEach(body::writeBytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ascii("RIFF"));
        writeU32le(out, body.size());
        out.writeBytes(body.toByteArray());
        return new SanitizedImage(ImageType.WEBP, out.toByteArray(), orientation);
    }

    // ---------------------------------------------------------------- EXIF helpers

    /** Reads the IFD0 orientation tag from TIFF-structured EXIF data; 1 if absent or malformed. */
    static int readTiffOrientation(byte[] b, int start, int end) {
        end = Math.min(end, b.length);
        if (start < 0 || end - start < 8) {
            return 1;
        }
        boolean littleEndian;
        if (b[start] == 'I' && b[start + 1] == 'I') {
            littleEndian = true;
        } else if (b[start] == 'M' && b[start + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        long ifdOffset = littleEndian ? u32le(b, start + 4) : u32be(b, start + 4);
        if (ifdOffset > end - start - 2L) {
            return 1;
        }
        int ifd = start + (int) ifdOffset;
        int count = littleEndian ? u16le(b, ifd) : u16be(b, ifd);
        for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                break;
            }
            int tag = littleEndian ? u16le(b, entry) : u16be(b, entry);
            if (tag == ORIENTATION_TAG) {
                int value = littleEndian ? u16le(b, entry + 8) : u16be(b, entry + 8);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    /** Big-endian TIFF block whose IFD0 holds a single entry: Orientation. */
    static byte[] orientationTiff(int orientation) {
        return new byte[]{
                'M', 'M', 0, 42, 0, 0, 0, 8,        // header, IFD0 at offset 8
                0, 1,                               // one entry
                0x01, 0x12, 0, 3, 0, 0, 0, 1,       // tag 0x0112, SHORT, count 1
                0, (byte) orientation, 0, 0,        // value
                0, 0, 0, 0                          // no next IFD
        };
    }

    // ---------------------------------------------------------------- byte helpers

    private static IllegalArgumentException corrupt(ImageType type) {
        return new IllegalArgumentException("The " + type.name() + " image is corrupt or truncated.");
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (offset < 0 || offset + prefix.length > data.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static int u16be(byte[] b, int p) {
        return ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF);
    }

    private static int u16le(byte[] b, int p) {
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8);
    }

    private static long u32be(byte[] b, int p) {
        return ((long) (b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16) | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
    }

    private static long u32le(byte[] b, int p) {
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8) | ((b[p + 2] & 0xFF) << 16) | ((long) (b[p + 3] & 0xFF) << 24);
    }

    private static void writeU32be(ByteArrayOutputStream out, long v) {
        out.write((int) (v >>> 24) & 0xFF);
        out.write((int) (v >>> 16) & 0xFF);
        out.write((int) (v >>> 8) & 0xFF);
        out.write((int) v & 0xFF);
    }

    private static void writeU32le(ByteArrayOutputStream out, long v) {
        out.write((int) v & 0xFF);
        out.write((int) (v >>> 8) & 0xFF);
        out.write((int) (v >>> 16) & 0xFF);
        out.write((int) (v >>> 24) & 0xFF);
    }
}
