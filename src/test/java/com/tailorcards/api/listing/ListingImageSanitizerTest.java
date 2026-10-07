package com.tailorcards.api.listing;

import com.tailorcards.api.listing.ListingImageSanitizer.ImageType;
import com.tailorcards.api.listing.ListingImageSanitizer.SanitizedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ListingImageSanitizer Tests")
class ListingImageSanitizerTest {

    static final String SECRET = "LOCATION-SECRET-43.6532N";

    private final ListingImageSanitizer sanitizer = new ListingImageSanitizer();

    @Test
    @DisplayName("Rotated JPEG: GPS, description, XMP, comments and trailing data removed; orientation 6 preserved")
    void stripsJpegMetadataAndPreservesOrientation() throws IOException {
        byte[] original = jpegWithMetadata(6);
        assertThat(contains(original, SECRET)).isTrue();

        SanitizedImage result = sanitizer.sanitize(original);

        assertThat(result.type()).isEqualTo(ImageType.JPEG);
        assertThat(result.orientation()).isEqualTo(6);
        assertThat(contains(result.bytes(), SECRET)).isFalse();
        assertThat(contains(result.bytes(), "xmpmeta")).isFalse();
        assertThat(contains(result.bytes(), "secret comment")).isFalse();
        assertThat(contains(result.bytes(), "TRAILING")).isFalse();

        // Exactly one EXIF block remains, holding only the orientation entry
        int exifAt = indexOf(result.bytes(), "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(exifAt).isPositive();
        int tiff = exifAt + 6;
        assertThat(ListingImageSanitizer.readTiffOrientation(result.bytes(), tiff, result.bytes().length)).isEqualTo(6);
        assertThat(((result.bytes()[tiff + 8] & 0xFF) << 8) | (result.bytes()[tiff + 9] & 0xFF))
                .as("IFD0 entry count").isEqualTo(1);
        assertThat(indexOf(Arrays.copyOfRange(result.bytes(), exifAt + 1, result.bytes().length),
                "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1))).isNegative();

        // Pixels untouched and still decodable, JFIF APP0 still first
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(32);
        assertThat(decoded.getHeight()).isEqualTo(16);
        assertThat(result.bytes()[2] & 0xFF).isEqualTo(0xFF);
        assertThat(result.bytes()[3] & 0xFF).isEqualTo(0xE0);
    }

    @Test
    @DisplayName("Upright JPEG: no EXIF block is written at all")
    void uprightJpegHasNoExif() throws IOException {
        SanitizedImage result = sanitizer.sanitize(jpegWithMetadata(1));

        assertThat(result.orientation()).isEqualTo(1);
        assertThat(indexOf(result.bytes(), "Exif".getBytes(StandardCharsets.ISO_8859_1))).isNegative();
        assertThat(ImageIO.read(new ByteArrayInputStream(result.bytes()))).isNotNull();
    }

    @Test
    @DisplayName("PNG: text chunks removed; eXIf rewritten with orientation only and a valid CRC")
    void stripsPngMetadata() throws IOException {
        byte[] png = pngWithMetadata(8);

        SanitizedImage result = sanitizer.sanitize(png);

        assertThat(result.type()).isEqualTo(ImageType.PNG);
        assertThat(result.orientation()).isEqualTo(8);
        assertThat(contains(result.bytes(), SECRET)).isFalse();
        assertThat(contains(result.bytes(), "tEXt")).isFalse();
        assertThat(ImageIO.read(new ByteArrayInputStream(result.bytes()))).isNotNull();

        // eXIf chunk directly after IHDR, CRC correct
        int ihdrLen = ByteBuffer.wrap(result.bytes(), 8, 4).getInt();
        int exifChunk = 8 + 12 + ihdrLen;
        int len = ByteBuffer.wrap(result.bytes(), exifChunk, 4).getInt();
        assertThat(new String(result.bytes(), exifChunk + 4, 4, StandardCharsets.US_ASCII)).isEqualTo("eXIf");
        CRC32 crc = new CRC32();
        crc.update(result.bytes(), exifChunk + 4, 4 + len);
        long stored = ByteBuffer.wrap(result.bytes(), exifChunk + 8 + len, 4).getInt() & 0xFFFFFFFFL;
        assertThat(stored).isEqualTo(crc.getValue());
    }

    @Test
    @DisplayName("WebP: XMP removed, EXIF rewritten with orientation only, VP8X flags and RIFF size updated")
    void stripsWebpMetadata() {
        byte[] webp = webpWithMetadata(3);

        SanitizedImage result = sanitizer.sanitize(webp);
        byte[] out = result.bytes();

        assertThat(result.type()).isEqualTo(ImageType.WEBP);
        assertThat(result.orientation()).isEqualTo(3);
        assertThat(contains(out, SECRET)).isFalse();
        assertThat(contains(out, "XMP ")).isFalse();
        assertThat(ByteBuffer.wrap(out, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()).isEqualTo(out.length - 8);
        int flags = out[12 + 8] & 0xFF; // first VP8X payload byte
        assertThat(flags & 0x08).as("EXIF flag").isEqualTo(0x08);
        assertThat(flags & 0x04).as("XMP flag").isZero();
        int exif = indexOf(out, "EXIF".getBytes(StandardCharsets.US_ASCII));
        assertThat(ListingImageSanitizer.readTiffOrientation(out, exif + 8, out.length)).isEqualTo(3);
    }

    @Test
    @DisplayName("Type is detected from content: GIF, PDF and text are rejected whatever their name")
    void rejectsUnsupportedContent() {
        assertThatThrownBy(() -> sanitizer.sanitize("GIF89a-not-a-supported-type".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPEG, PNG or WebP");
        assertThatThrownBy(() -> sanitizer.sanitize("%PDF-1.7 pretending to be photo.jpg".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Truncated JPEG is rejected as corrupt")
    void rejectsTruncatedJpeg() throws IOException {
        byte[] jpeg = jpegWithMetadata(1);
        byte[] truncated = Arrays.copyOf(jpeg, jpeg.length / 3);

        assertThatThrownBy(() -> sanitizer.sanitize(truncated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("corrupt");
    }

    // ------------------------------------------------------------------ fixtures

    static BufferedImage sample() {
        BufferedImage image = new BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 16; y++) {
                image.setRGB(x, y, x < 16 ? Color.RED.getRGB() : Color.BLUE.getRGB());
            }
        }
        return image;
    }

    /** JPEG with JFIF, EXIF (orientation, GPS IFD, description), XMP, a comment and data after EOI. */
    static byte[] jpegWithMetadata(int orientation) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(sample(), "jpg", encoded);
        byte[] base = encoded.toByteArray(); // SOI, APP0 (JFIF), ...

        int app0Len = ((base[4] & 0xFF) << 8) | (base[5] & 0xFF);
        int afterApp0 = 4 + app0Len;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(base, 0, afterApp0);
        writeSegment(out, 0xE1, concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiffWithGps(orientation)));
        writeSegment(out, 0xE1, ("http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>" + SECRET + "</x:xmpmeta>")
                .getBytes(StandardCharsets.ISO_8859_1));
        writeSegment(out, 0xFE, "secret comment".getBytes(StandardCharsets.ISO_8859_1));
        out.write(base, afterApp0, base.length - afterApp0);
        out.writeBytes(("TRAILING Exif " + SECRET).getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    /** Little-endian TIFF: IFD0 = ImageDescription, Orientation, GPSInfo -> GPS IFD with latitude. */
    static byte[] tiffWithGps(int orientation) {
        byte[] description = (SECRET + "\0").getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer b = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        int ifd0Size = 2 + 3 * 12 + 4;
        int descOffset = 8 + ifd0Size;
        int gpsOffset = descOffset + description.length;
        b.putShort((short) 3);
        b.putShort((short) 0x010E).putShort((short) 2).putInt(description.length).putInt(descOffset);
        b.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        b.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(gpsOffset);
        b.putInt(0);
        b.put(description);
        int rationals = gpsOffset + 2 + 2 * 12 + 4;
        b.putShort((short) 2);
        b.putShort((short) 0x0001).putShort((short) 2).putInt(2).put((byte) 'N').put((byte) 0).putShort((short) 0);
        b.putShort((short) 0x0002).putShort((short) 5).putInt(3).putInt(rationals);
        b.putInt(0);
        b.putInt(43).putInt(1).putInt(39).putInt(1).putInt(1152).putInt(100);
        return Arrays.copyOf(b.array(), b.position());
    }

    static byte[] pngWithMetadata(int orientation) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(sample(), "png", encoded);
        byte[] base = encoded.toByteArray();
        int ihdrEnd = 8 + 12 + ByteBuffer.wrap(base, 8, 4).getInt();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(base, 0, ihdrEnd);
        writeChunk(out, "eXIf", tiffWithGps(orientation));
        writeChunk(out, "tEXt", ("Comment\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1));
        out.write(base, ihdrEnd, base.length - ihdrEnd);
        return out.toByteArray();
    }

    /** Synthetic extended WebP: VP8X (EXIF+XMP flags), a VP8 chunk, EXIF and XMP chunks. */
    static byte[] webpWithMetadata(int orientation) {
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        byte[] vp8x = new byte[10];
        vp8x[0] = 0x08 | 0x04;
        writeRiffChunk(chunks, "VP8X", vp8x);
        writeRiffChunk(chunks, "VP8 ", new byte[]{1, 2, 3, 4, 5, 6});
        writeRiffChunk(chunks, "EXIF", tiffWithGps(orientation));
        writeRiffChunk(chunks, "XMP ", ("<x:xmpmeta>" + SECRET + "</x:xmpmeta>").getBytes(StandardCharsets.ISO_8859_1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(4 + chunks.size()).array());
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(chunks.toByteArray());
        return out.toByteArray();
    }

    private static void writeSegment(ByteArrayOutputStream out, int marker, byte[] payload) {
        int len = payload.length + 2;
        out.write(0xFF);
        out.write(marker);
        out.write(len >> 8);
        out.write(len & 0xFF);
        out.writeBytes(payload);
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeAndData = concat(type.getBytes(StandardCharsets.US_ASCII), data);
        CRC32 crc = new CRC32();
        crc.update(typeAndData);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(typeAndData);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static void writeRiffChunk(ByteArrayOutputStream out, String fourcc, byte[] data) {
        out.writeBytes(fourcc.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length).array());
        out.writeBytes(data);
        if ((data.length & 1) == 1) {
            out.write(0);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    static boolean contains(byte[] haystack, String needle) {
        return indexOf(haystack, needle.getBytes(StandardCharsets.ISO_8859_1)) >= 0;
    }

    static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
