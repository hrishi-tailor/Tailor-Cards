package com.tailorcards.api.buylistchat;

import com.tailorcards.api.listing.ListingImageSanitizer.SanitizedImage;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** Metadata-stripped photo bytes presented as a MultipartFile for BuylistStorageService. */
final class SanitizedPhotoFile implements MultipartFile {

    private final SanitizedImage image;

    SanitizedPhotoFile(SanitizedImage image) {
        this.image = image;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return "photo." + switch (image.type()) {
            case JPEG -> "jpg";
            case PNG -> "png";
            case WEBP -> "webp";
        };
    }

    @Override
    public String getContentType() {
        return image.type().mediaType();
    }

    @Override
    public boolean isEmpty() {
        return image.bytes().length == 0;
    }

    @Override
    public long getSize() {
        return image.bytes().length;
    }

    @Override
    public byte[] getBytes() {
        return image.bytes();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(image.bytes());
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), image.bytes());
    }
}
