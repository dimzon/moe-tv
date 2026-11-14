package android.util;

/**
 * JVM-test stand-in for the platform's {@link Base64}, which in android.jar is a stub whose every
 * method returns a default under {@code isReturnDefaultValues} — so the DoH and ClearKey paths built
 * on it could not be tested at all (they silently decoded to null / threw "not mocked"). Test classes
 * come before android.jar on the classpath, so this one wins. It delegates to {@link java.util.Base64},
 * which matches the platform codec for the flag combinations the app uses: URL_SAFE/NO_PADDING/NO_WRAP
 * for encoding, URL_SAFE for decoding (padded and unpadded input both accepted, illegal characters
 * throw IllegalArgumentException on both platforms).
 */
public final class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    private Base64() {}

    public static byte[] decode(String str, int flags) {
        java.util.Base64.Decoder decoder = (flags & URL_SAFE) != 0
            ? java.util.Base64.getUrlDecoder()
            : java.util.Base64.getDecoder();
        return decoder.decode(str);
    }

    public static String encodeToString(byte[] input, int flags) {
        java.util.Base64.Encoder encoder = (flags & URL_SAFE) != 0
            ? java.util.Base64.getUrlEncoder()
            : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) encoder = encoder.withoutPadding();
        // NO_WRAP is the only wrap mode the app encodes with; the platform's 76-char MIME wrap
        // (DEFAULT/CRLF) is not exercised anywhere and is not reproduced here.
        return encoder.encodeToString(input);
    }
}
