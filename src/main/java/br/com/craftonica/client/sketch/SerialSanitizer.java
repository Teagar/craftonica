package br.com.craftonica.client.sketch;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class SerialSanitizer {
    private SerialSanitizer() {}

    public static String sanitize(byte[] raw) {
        if (raw == null || raw.length == 0) return "";
        String decoded;
        try {
            CharBuffer value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .replaceWith("?").decode(ByteBuffer.wrap(raw));
            decoded = value.toString();
        } catch (CharacterCodingException impossible) {
            decoded = "?";
        }
        StringBuilder safe = new StringBuilder(decoded.length());
        for (int offset = 0; offset < decoded.length();) {
            int codePoint = decoded.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\r' || codePoint == '\n' || codePoint == '\t') safe.appendCodePoint(codePoint);
            else if (codePoint == 0x00a7 || Character.isISOControl(codePoint)) safe.append('?');
            else safe.appendCodePoint(codePoint);
        }
        return safe.toString();
    }
}
