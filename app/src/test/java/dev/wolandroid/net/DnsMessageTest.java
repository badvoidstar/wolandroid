package dev.wolandroid.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

public class DnsMessageTest {
    private static final long ADDRESS = Ipv4Subnet.parseAddress("192.168.1.10");

    @Test
    public void readsCompressedPtrResponseAndIgnoresUnrelatedTransactions() throws IOException {
        byte[] reply = reply("desktop.local");
        assertEquals("desktop.local", DnsMessage.readPtr(reply, reply.length, 42, ADDRESS));
        assertEquals("", DnsMessage.readPtr(reply, reply.length, 43, ADDRESS));
        assertEquals("", DnsMessage.readPtr(reply, reply.length, 42, ADDRESS + 1));
        reply[3] = 3;
        assertEquals("", DnsMessage.readPtr(reply, reply.length, 42, ADDRESS));
    }

    @Test
    public void rejectsTruncatedResponsesAndCompressionCycles() throws IOException {
        byte[] reply = reply("desktop.local");
        assertThrows(IOException.class, () -> DnsMessage.readPtr(reply, 11, 42, ADDRESS));
        assertThrows(IOException.class, () -> DnsMessage.readPtr(reply, reply.length - 1, 42, ADDRESS));
        int answer = DnsMessage.ptrQuery(42, ADDRESS).length;
        reply[answer] = (byte) (0xc0 | (answer >>> 8));
        reply[answer + 1] = (byte) answer;
        assertThrows(IOException.class, () -> DnsMessage.readPtr(reply, reply.length, 42, ADDRESS));
    }

    @Test
    public void validatesReverseQuestionWireFormat() throws IOException {
        byte[] query = DnsMessage.ptrQuery(0x1234, ADDRESS);
        assertEquals(0x12, query[0] & 255);
        assertEquals(0x34, query[1] & 255);
        assertEquals("10.1.168.192.in-addr.arpa", DnsMessage.reverseName(ADDRESS));
        assertEquals(12, query[query.length - 3]);
        assertEquals(1, query[query.length - 1]);
    }

    private static byte[] reply(String hostname) throws IOException {
        byte[] question = DnsMessage.ptrQuery(42, ADDRESS);
        question[2] = (byte) 0x81;
        question[3] = (byte) 0x80;
        question[7] = 1;
        ByteArrayOutputStream nameBytes = new ByteArrayOutputStream();
        for (String label : hostname.split("\\.")) {
            nameBytes.write(label.length());
            nameBytes.write(label.getBytes(StandardCharsets.US_ASCII));
        }
        nameBytes.write(0);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(Arrays.copyOf(question, question.length));
        DataOutputStream output = new DataOutputStream(buffer);
        output.writeShort(0xc00c);
        output.writeShort(12);
        output.writeShort(1);
        output.writeInt(120);
        output.writeShort(nameBytes.size());
        output.write(nameBytes.toByteArray());
        return buffer.toByteArray();
    }
}
