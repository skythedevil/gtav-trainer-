package io.github.skythedevil.gtavmenu.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Talks to PS5Debug-NG on the console (default port 744).
 * Same message format as the original Windows installer (src/protocol.mjs).
 * Every request/response pair is kept in order; any error closes the connection
 * so we never continue with a half-read answer.
 */
public final class Ps5Debug {
    public static final class Capabilities {
        public String firmware, branding, capabilityLevel;
    }
    public static final class Proc { public String name; public long pid; }
    public static final class ProcInfo { public long pid; public String name, path, titleId, contentId; }
    public static final class MapEntry { public String name; public long start, end, offset; public int prot; }

    private static final long MAGIC = 0xffaabbccL;
    private static final long OK = 0x80000000L;
    private static final Pattern PS5DEBUG = Pattern.compile("ps5debug\\b", Pattern.CASE_INSENSITIVE);

    private final String host;
    private final int port;
    private final int timeoutMs;
    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private InstallException failure;

    public Ps5Debug(String host, int port, int timeoutMs) {
        this.host = host; this.port = port; this.timeoutMs = timeoutMs;
    }

    public synchronized void connect() {
        socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            in = new DataInputStream(socket.getInputStream());
            out = socket.getOutputStream();
        } catch (IOException e) {
            close();
            throw new InstallException("Could not reach " + host + ":" + port + ". Check that the PS5 is on, on the same Wi-Fi as this phone, and PS5Debug-NG is running.", e);
        }
    }

    public synchronized void close() {
        if (failure == null) failure = new InstallException("Disconnected");
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
    }

    private InstallException fail(InstallException e) {
        if (failure == null) failure = e;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        return e;
    }

    // ---- low level -------------------------------------------------------------------

    private void send(long command, byte[] body) {
        if (failure != null) throw failure;
        byte[] msg = new byte[12 + body.length];
        Bytes.putU32(msg, 0, MAGIC);
        Bytes.putU32(msg, 4, command);
        Bytes.putU32(msg, 8, body.length);
        System.arraycopy(body, 0, msg, 12, body.length);
        write(msg);
    }

    private void write(byte[] data) {
        if (failure != null) throw failure;
        try { out.write(data); out.flush(); }
        catch (IOException e) { throw fail(new InstallException("Lost connection to the PS5 while sending: " + e.getMessage(), e)); }
    }

    private byte[] receive(int length) {
        if (failure != null) throw failure;
        byte[] b = new byte[length];
        try { in.readFully(b); return b; }
        catch (SocketTimeoutException e) { throw fail(new InstallException("The PS5 did not answer in time. Connection dropped to keep things safe.", e)); }
        catch (IOException e) { throw fail(new InstallException("Lost connection to the PS5: " + e.getMessage(), e)); }
    }

    private void status() {
        long s = Bytes.u32(receive(4), 0);
        if (s != OK) throw fail(new InstallException("PS5Debug returned error 0x" + Long.toHexString(s)));
    }

    private static String cstring(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static void check(long v, long min, long max, String label) {
        if (v < min || v > max) throw new InstallException(label + " is out of range: " + v);
    }

    private static byte[] packet(long pid, long addr, int length) {
        check(pid, 1, 0xffffffffL, "PID");
        check(length, 1, 1024 * 1024, "Read/write size");
        if (Long.compareUnsigned(addr + length, addr) < 0) throw new InstallException("Address range overflows");
        byte[] b = new byte[16];
        Bytes.putU32(b, 0, pid); Bytes.putU64(b, 4, addr); Bytes.putU32(b, 12, length);
        return b;
    }

    // ---- commands --------------------------------------------------------------------

    public synchronized Capabilities detect() {
        send(0xbd000502L, new byte[0]);
        int platform = Bytes.u16(receive(2), 0);
        if (platform != 5) throw fail(new InstallException("The debugger does not identify as a PS5. Check the IP address and the payload."));
        send(0xbd000500L, new byte[0]);
        int word = Bytes.u16(receive(2), 0);
        check(word, 100, 9999, "PS5 firmware");
        send(0xbd000501L, new byte[0]);
        int len = (int) Bytes.u32(receive(4), 0);
        check(len, 1, 512, "Branding length");
        byte[] raw = receive(len);
        String branding = cstring(raw, 0, raw.length);
        int firstZero = 0;
        while (firstZero < raw.length && raw[firstZero] != 0) firstZero++;
        String level = firstZero + 1 < raw.length ? cstring(raw, firstZero + 1, raw.length - firstZero - 1) : "";
        if (!PS5DEBUG.matcher(branding).find()) throw fail(new InstallException("Unrecognized PS5 debugger. Load PS5Debug-NG 1.3.2."));
        Capabilities c = new Capabilities();
        c.firmware = (word / 100) + "." + String.format("%02d", word % 100);
        c.branding = branding;
        c.capabilityLevel = level;
        return c;
    }

    public synchronized List<Proc> processes() {
        send(0xbdaa0001L, new byte[0]); status();
        long count = Bytes.u32(receive(4), 0);
        check(count, 0, 4096, "Process count");
        byte[] b = receive((int) count * 36);
        List<Proc> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Proc p = new Proc();
            p.name = cstring(b, i * 36, 32);
            p.pid = Bytes.u32(b, i * 36 + 32);
            list.add(p);
        }
        return list;
    }

    public synchronized ProcInfo info(long pid) {
        check(pid, 1, 0xffffffffL, "PID");
        send(0xbdaa000aL, Bytes.u32Bytes(pid)); status();
        byte[] b = receive(188);
        ProcInfo r = new ProcInfo();
        r.pid = Bytes.u32(b, 0);
        r.name = cstring(b, 4, 40);
        r.path = cstring(b, 44, 64);
        r.titleId = cstring(b, 108, 16);
        r.contentId = cstring(b, 124, 64);
        return r;
    }

    public synchronized List<MapEntry> maps(long pid) {
        check(pid, 1, 0xffffffffL, "PID");
        send(0xbdaa0004L, Bytes.u32Bytes(pid)); status();
        long count = Bytes.u32(receive(4), 0);
        check(count, 0, 65536, "Map count");
        byte[] b = receive((int) count * 58);
        List<MapEntry> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int o = i * 58;
            MapEntry m = new MapEntry();
            m.name = cstring(b, o, 32);
            m.start = Bytes.u64(b, o + 32);
            m.end = Bytes.u64(b, o + 40);
            m.offset = Bytes.u64(b, o + 48);
            m.prot = Bytes.u16(b, o + 56);
            list.add(m);
        }
        return list;
    }

    public synchronized byte[] read(long pid, long addr, int length) {
        byte[] p = packet(pid, addr, length);
        send(0xbdaa0002L, p); status();
        return receive(length);
    }

    public synchronized void writeMemory(long pid, long addr, byte[] data) {
        byte[] p = packet(pid, addr, data.length);
        send(0xbdaa0003L, p); status();
        write(data); status();
    }

    /** Asks the console to set aside executable memory inside the game. Returns its address. */
    public synchronized long alloc(long pid, int size) {
        byte[] req = new byte[8];
        Bytes.putU32(req, 0, pid); Bytes.putU32(req, 4, size);
        send(0xbdaa000bL, req); status();
        return Bytes.u64(receive(8), 0);
    }
}
