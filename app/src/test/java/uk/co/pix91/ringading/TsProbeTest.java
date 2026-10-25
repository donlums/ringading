package uk.co.pix91.ringading;

import org.junit.Test;
import static org.junit.Assert.*;

public class TsProbeTest {
    private byte[] packets(int count) {
        byte[] b = new byte[count * TsProbe.TS_BYTES];
        for (int i = 0; i < count; i++) b[i * TsProbe.TS_BYTES] = 0x47;
        return b;
    }
    @Test public void acceptsFiveSynchronizedPackets() {
        byte[] b = packets(5);
        assertEquals(0, TsProbe.syncOffset(b,b.length));
    }
    @Test public void identifiesNonzeroOffset() {
        byte[] b = new byte[1+5*TsProbe.TS_BYTES];
        for (int i=0; i<5; i++) b[1+i*TsProbe.TS_BYTES] = 0x47;
        assertEquals(1,TsProbe.syncOffset(b,b.length));
    }
    @Test public void rejectsShortOrFalseSync() {
        byte[] b = packets(4);
        assertEquals(-1,TsProbe.syncOffset(b,b.length));
        byte[] random = new byte[1500];
        random[0]=0x47;
        assertEquals(-1,TsProbe.syncOffset(random,random.length));
    }
    @Test public void detectsBadPacketBoundaries() {
        assertTrue(TsProbe.isAligned(packets(6)));
        byte[] b=packets(6);
        b[188] = 0;
        assertFalse(TsProbe.isAligned(b));
        assertFalse(TsProbe.isAligned(new byte[700]));
    }
}
