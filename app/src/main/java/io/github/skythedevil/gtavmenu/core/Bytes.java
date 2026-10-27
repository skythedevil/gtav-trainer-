package io.github.skythedevil.gtavmenu.core;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Small helpers for reading/writing little-endian numbers and hex text. */
public final class Bytes {
    private Bytes() {}

    private static ByteBuffer le(byte[] b) { return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN); }

    public static int u16(byte[] b, int off) { return le(b).getShort(off) & 0xFFFF; }
    /** Unsigned 32-bit value, returned as a long. */
    public static long u32(byte[] b, int off) { return le(b).getInt(off) & 0xFFFFFFFFL; }
    public static int i32(byte[] b, int off) { return le(b).getInt(off); }
    public static long u64(byte[] b, int off) { return le(b).getLong(off); }
    public static float f32(byte[] b, int off) { return le(b).getFloat(off); }

    public static void putU32(byte[] b, int off, long v) { le(b).putInt(off, (int) v); }
    public static void putU64(byte[] b, int off, long v) { le(b).putLong(off, v); }

    public static byte[] ptr(long v) { byte[] b = new byte[8]; putU64(b, 0, v); return b; }
    public static byte[] u32Bytes(long v) { byte[] b = new byte[4]; putU32(b, 0, v); return b; }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

    public static byte[] fromHex(String s) {
        if (s == null || s.length() % 2 != 0) throw new InstallException("Bad hex text");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    /** "0x1A2B" or "1234" (as text or as a JSON number) to a 64-bit address. */
    public static long addr(Object v) {
        if (v instanceof Long) return (Long) v;
        if (!(v instanceof String)) throw new InstallException("Bad address value in profile");
        String s = ((String) v).trim();
        BigInteger n;
        try {
            n = s.startsWith("0x") || s.startsWith("0X") ? new BigInteger(s.substring(2), 16) : new BigInteger(s);
        } catch (NumberFormatException e) { throw new InstallException("Bad address value in profile"); }
        if (n.signum() < 0 || n.bitLength() > 64) throw new InstallException("Address is outside the 64-bit range");
        return n.longValue();
    }

    public static String hexAddr(long v) { return "0x" + Long.toHexString(v); }

    /** Unsigned "a <= b". */
    public static boolean ule(long a, long b) { return Long.compareUnsigned(a, b) <= 0; }
}
