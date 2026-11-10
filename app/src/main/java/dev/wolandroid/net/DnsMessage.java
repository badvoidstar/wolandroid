package dev.wolandroid.net;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class DnsMessage {
    private DnsMessage() {}

    public static String reverseName(long address) {
        return (address & 255) + "." + ((address >>> 8) & 255) + "."
                + ((address >>> 16) & 255) + "." + ((address >>> 24) & 255) + ".in-addr.arpa";
    }

    public static byte[] ptrQuery(int id, long address) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(id);
        output.writeShort(0x0100);
        output.writeShort(1);
        output.writeShort(0);
        output.writeShort(0);
        output.writeShort(0);
        for (String label : reverseName(address).split("\\.")) {
            byte[] encoded = label.getBytes(StandardCharsets.US_ASCII);
            output.writeByte(encoded.length);
            output.write(encoded);
        }
        output.writeByte(0);
        output.writeShort(12);
        output.writeShort(1);
        return bytes.toByteArray();
    }

    public static String readPtr(byte[] bytes, int length, int id, long address) throws IOException {
        if (length < 12 || length > bytes.length) throw new IOException("Truncated DNS header");
        if (word(bytes, 0, length) != (id & 0xffff)) return "";
        int flags = word(bytes, 2, length);
        if ((flags & 0x8000) == 0 || (flags & 0x020f) != 0) return "";
        int questions = word(bytes, 4, length);
        int answers = word(bytes, 6, length);
        if (questions > 16 || answers > 128) throw new IOException("Oversized DNS response");
        int offset = 12;
        for (int i = 0; i < questions; i++) {
            Name question = name(bytes, offset, length);
            offset = question.next + 4;
            if (offset > length) throw new IOException("Truncated DNS question");
        }
        String expected = reverseName(address);
        for (int i = 0; i < answers; i++) {
            Name owner = name(bytes, offset, length);
            offset = owner.next;
            int type = word(bytes, offset, length);
            int recordClass = word(bytes, offset + 2, length) & 0x7fff;
            int size = word(bytes, offset + 8, length);
            offset += 10;
            if (offset + size > length) throw new IOException("Truncated DNS answer");
            if (type == 12 && recordClass == 1 && owner.value.equalsIgnoreCase(expected)) {
                Name target = name(bytes, offset, length);
                if (target.next > offset + size) throw new IOException("Invalid PTR length");
                return target.value.toLowerCase(Locale.ROOT);
            }
            offset += size;
        }
        return "";
    }

    private static int word(byte[] bytes, int offset, int length) throws IOException {
        if (offset < 0 || offset + 2 > length) throw new IOException("Truncated DNS record");
        return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255);
    }

    private static Name name(byte[] bytes, int start, int length) throws IOException {
        int cursor = start;
        int next = -1;
        StringBuilder result = new StringBuilder();
        for (int hops = 0; hops < 128; hops++) {
            if (cursor >= length) throw new IOException("Truncated DNS name");
            int size = bytes[cursor++] & 255;
            if (size == 0) return new Name(result.toString(), next < 0 ? cursor : next);
            if ((size & 0xc0) == 0xc0) {
                if (cursor >= length) throw new IOException("Truncated DNS pointer");
                if (next < 0) next = cursor + 1;
                cursor = ((size & 0x3f) << 8) | (bytes[cursor] & 255);
                continue;
            }
            if (size > 63 || cursor + size > length) throw new IOException("Invalid DNS label");
            if (result.length() > 0) result.append('.');
            for (int i = 0; i < size; i++) {
                int character = bytes[cursor++] & 255;
                if (character < 33 || character > 126) throw new IOException("Invalid hostname");
                result.append((char) character);
            }
            if (result.length() > 253) throw new IOException("DNS name too long");
        }
        throw new IOException("DNS compression loop");
    }

    private static final class Name {
        final String value;
        final int next;

        Name(String value, int next) {
            this.value = value;
            this.next = next;
        }
    }
}
