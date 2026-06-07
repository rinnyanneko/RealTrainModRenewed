package jp.kaiz.atsassistmod.ifttt;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Serialises IFTTT rule containers (gzip + Jackson polymorphic), faithful port. */
public final class IFTTTUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IFTTTUtil() {}

    public static byte[] toBytes(IFTTTContainer container) {
        try {
            return compress(MAPPER.writeValueAsBytes(container));
        } catch (IOException e) {
            return null;
        }
    }

    public static IFTTTContainer fromBytes(byte[] bytes) {
        try {
            return MAPPER.readValue(decompress(bytes), IFTTTContainer.class);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] compress(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(bytes);
        }
        return out.toByteArray();
    }

    private static byte[] decompress(byte[] compressed) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
        return out.toByteArray();
    }
}
