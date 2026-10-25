package uk.co.pix91.ringading;

/**
 * MPEG-TS framing checks for Blink IMMI output.
 * Pure Java: the same validator is exercised by Android unit tests.
 */
final class TsProbe {
    static final int TS_BYTES = 188;
    static final int SNIFF_PACKETS = 5;
    static final int SNIFF_BYTES = TS_BYTES * SNIFF_PACKETS;

    private TsProbe() {}

    /**
     * Return the starting offset of five consecutive TS sync bytes, or -1.
     * A single 0x47 is not evidence of a playable video stream.
     */
    static int syncOffset(byte[] bytes, int length) {
        if (bytes == null || length < SNIFF_BYTES) return -1;
        int maxOffset = Math.min(TS_BYTES - 1, length - SNIFF_BYTES);
        for (int off = 0; off <= maxOffset; off++) {
            boolean aligned = true;
            for (int packet = 0; packet < SNIFF_PACKETS; packet++) {
                if ((bytes[off + packet * TS_BYTES] & 0xff) != 0x47) {
                    aligned = false;
                    break;
                }
            }
            if (aligned) return off;
        }
        return -1;
    }

    static boolean isAligned(byte[] bytes) {
        return bytes != null && bytes.length >= TS_BYTES
            && bytes.length % TS_BYTES == 0
            && syncOffsetWithOnePacket(bytes);
    }

    private static boolean syncOffsetWithOnePacket(byte[] bytes) {
        for (int pos=0; pos<bytes.length; pos+=TS_BYTES) {
            if ((bytes[pos] & 0xff) != 0x47) return false;
        }
        return true;
    }
}
