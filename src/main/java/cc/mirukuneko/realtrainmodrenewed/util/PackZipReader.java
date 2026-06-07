package cc.mirukuneko.realtrainmodrenewed.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Reads old RTM add-on packs whose ZIP entry names were often encoded with
 * legacy Japanese code pages rather than UTF-8.
 */
public final class PackZipReader {
    private static final List<Charset> ENTRY_NAME_CHARSETS = List.of(
        StandardCharsets.UTF_8,
        Charset.forName("MS932"),
        Charset.forName("Shift_JIS"),
        Charset.forName("GB18030"),
        StandardCharsets.ISO_8859_1
    );

    private PackZipReader() {
    }

    public static void read(InputStream input, EntryConsumer consumer) throws IOException {
        read(input.readAllBytes(), consumer);
    }

    public static void read(byte[] bytes, EntryConsumer consumer) throws IOException {
        Charset charset = detectEntryNameCharset(bytes);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), charset)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                try {
                    consumer.accept(entry, zip);
                } finally {
                    zip.closeEntry();
                }
            }
        }
    }

    private static Charset detectEntryNameCharset(byte[] bytes) throws IOException {
        IOException last = null;
        for (Charset charset : ENTRY_NAME_CHARSETS) {
            try {
                validateEntryNames(bytes, charset);
                return charset;
            } catch (IOException e) {
                if (!looksLikeEntryNameEncodingFailure(e)) {
                    throw e;
                }
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        return StandardCharsets.UTF_8;
    }

    public static ZipFile openZipFile(java.nio.file.Path path) throws IOException {
        IOException last = null;
        for (Charset charset : ENTRY_NAME_CHARSETS) {
            try {
                return new ZipFile(path.toFile(), charset);
            } catch (IOException e) {
                if (!looksLikeEntryNameEncodingFailure(e)) {
                    throw e;
                }
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        return new ZipFile(path.toFile());
    }

    private static void validateEntryNames(byte[] bytes, Charset charset) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), charset)) {
            while (zip.getNextEntry() != null) {
                zip.closeEntry();
            }
        }
    }

    private static boolean looksLikeEntryNameEncodingFailure(IOException e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("bad entry name") || lower.contains("malformed input")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    @FunctionalInterface
    public interface EntryConsumer {
        void accept(ZipEntry entry, InputStream input) throws IOException;
    }
}
