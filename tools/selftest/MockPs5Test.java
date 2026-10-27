import io.github.skythedevil.gtavmenu.core.*;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Pretend PS5: speaks the PS5Debug protocol and holds a fake GTA process in memory.
 * Runs the real Installer against it and checks that it installs correctly,
 * and that it refuses (without writing) when something is wrong.
 */
public class MockPs5Test {
    static final long HASH = 1459623836L;

    // ---- fake console -----------------------------------------------------------------
    static class Region { long start; byte[] data; int prot; Region(long s, int size, int p) { start = s; data = new byte[size]; prot = p; } }

    static class Fake implements Closeable {
        final List<Region> regions = new ArrayList<>();
        final ServerSocket server;
        int firmwareWord = 1360;
        String branding = "PS5Debug-NG v1.3.2";
        String titleId = "PPSA09999";
        volatile int writeCommands = 0, allocCommands = 0;
        long allocBase = 0x1000000L;
        long allocated = 0;
        final byte[] fingerprint = "FINGERPRINT-TEST".getBytes(StandardCharsets.US_ASCII);
        final long playerIdHandler = 0x17bad00L;

        Fake() throws IOException {
            server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            buildWorld();
            Thread t = new Thread(this::acceptLoop); t.setDaemon(true); t.start();
        }
        int port() { return server.getLocalPort(); }

        ByteBuffer at(long addr, int need) {
            Region r = find(addr, need);
            return ByteBuffer.wrap(r.data).order(ByteOrder.LITTLE_ENDIAN).position((int) (addr - r.start)).slice().order(ByteOrder.LITTLE_ENDIAN);
        }
        Region find(long addr, int len) {
            for (Region r : regions) if (addr >= r.start && addr + len <= r.start + r.data.length) return r;
            return null;
        }
        void put(long addr, byte[] b) { Region r = find(addr, b.length); System.arraycopy(b, 0, r.data, (int) (addr - r.start), b.length); }
        void putU32(long addr, long v) { put(addr, Bytes.u32Bytes(v)); }
        void putU64(long addr, long v) { put(addr, Bytes.ptr(v)); }
        void putF32(long addr, float f) { put(addr, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(f).array()); }
        byte[] get(long addr, int len) { Region r = find(addr, len); return Arrays.copyOfRange(r.data, (int) (addr - r.start), (int) (addr - r.start) + len); }

        void buildWorld() {
            regions.clear();
            regions.add(new Region(0, 0x400000, 3));
            put(0x1000, fingerprint);
            put(0x2000, new byte[]{0});
            putU64(0x3000, 0x100000);
            putU64(0x100008, 0x101000);
            putF32(0x101000 + 0x250, 200f); putF32(0x101000 + 0x254, 200f);
            putU64(0x101000 + 0x1088, 0x108000); putU64(0x101000 + 0x1090, 0x109000);
            // script registry
            putU64(0x4000 + 8, 0x200000); putU32(0x4000 + 0x1c, 16);
            putU64(0x4000 + 0x40, 0x210000); putU64(0x4000 + 0x48, 0x220000); putU32(0x4000 + 0x50, 8);
            putU32(0x220000 + (HASH % 8) * 4, 0);
            putU32(0x210000, HASH); putU32(0x210004, 0); put(0x210008, Bytes.u32Bytes(0xffffffffL));
            putU64(0x200000, 0x230000);
            putU32(0x230000 + 0x58, HASH); putU64(0x230000 + 0x40, 0x240000); putU32(0x230000 + 0x2c, 10);
            for (int i = 0; i < 10; i++) putU64(0x240000 + i * 8L, i == 3 ? playerIdHandler : 0x1700000L + i * 0x10);
            allocated = 0;
        }

        void acceptLoop() {
            while (!server.isClosed()) {
                try { final Socket s = server.accept(); Thread t = new Thread(() -> serve(s)); t.setDaemon(true); t.start(); }
                catch (IOException e) { return; }
            }
        }

        void serve(Socket s) {
            try (Socket sock = s) {
                DataInputStream in = new DataInputStream(sock.getInputStream());
                OutputStream out = new BufferedOutputStream(sock.getOutputStream());
                while (true) {
                    byte[] h = new byte[12]; in.readFully(h);
                    if (Bytes.u32(h, 0) != 0xffaabbccL) return;
                    long cmd = Bytes.u32(h, 4); byte[] body = new byte[(int) Bytes.u32(h, 8)]; in.readFully(body);
                    synchronized (this) { handle(cmd, body, in, out); }
                    out.flush();
                }
            } catch (IOException ignored) { }
        }

        void status(OutputStream o, boolean ok) throws IOException { o.write(Bytes.u32Bytes(ok ? 0x80000000L : 0xF0000001L)); }
        static byte[] fixed(String s, int n) { byte[] b = new byte[n]; byte[] r = s.getBytes(StandardCharsets.UTF_8); System.arraycopy(r, 0, b, 0, Math.min(r.length, n - 1)); return b; }

        void handle(long cmd, byte[] body, DataInputStream in, OutputStream o) throws IOException {
            if (cmd == 0xbd000502L) { o.write(new byte[]{5, 0}); }
            else if (cmd == 0xbd000500L) { o.write(new byte[]{(byte) (firmwareWord & 255), (byte) (firmwareWord >> 8)}); }
            else if (cmd == 0xbd000501L) { byte[] b = (branding + "\0full").getBytes(StandardCharsets.UTF_8); o.write(Bytes.u32Bytes(b.length)); o.write(b); }
            else if (cmd == 0xbdaa0001L) {
                status(o, true); o.write(Bytes.u32Bytes(2));
                o.write(fixed("SceShellUI", 32)); o.write(Bytes.u32Bytes(50));
                o.write(fixed("eboot.bin", 32)); o.write(Bytes.u32Bytes(77));
            } else if (cmd == 0xbdaa000aL) {
                status(o, true); byte[] b = new byte[188];
                Bytes.putU32(b, 0, Bytes.u32(body, 0));
                System.arraycopy(fixed("eboot.bin", 40), 0, b, 4, 40);
                System.arraycopy(fixed("/app0/eboot.bin", 64), 0, b, 44, 64);
                System.arraycopy(fixed(titleId, 16), 0, b, 108, 16);
                System.arraycopy(fixed("TEST-CONTENT", 64), 0, b, 124, 64);
                o.write(b);
            } else if (cmd == 0xbdaa0004L) {
                status(o, true); o.write(Bytes.u32Bytes(regions.size()));
                for (Region r : regions) {
                    byte[] b = new byte[58]; System.arraycopy(fixed("region", 32), 0, b, 0, 32);
                    Bytes.putU64(b, 32, r.start); Bytes.putU64(b, 40, r.start + r.data.length); Bytes.putU64(b, 48, 0);
                    b[56] = (byte) r.prot; o.write(b);
                }
            } else if (cmd == 0xbdaa0002L) {
                long addr = Bytes.u64(body, 4); int len = (int) Bytes.u32(body, 12);
                if (find(addr, len) == null) { status(o, false); return; }
                byte[] data = get(addr, len);
                status(o, true); o.write(data);
                if (allocated != 0 && addr <= allocated + 0x10600L && allocated + 0x10600L < addr + len) putU32(allocated + 0x10600L, Bytes.u32(get(allocated + 0x10600L, 4), 0) + 1); // "frames" tick
            } else if (cmd == 0xbdaa0003L) {
                long addr = Bytes.u64(body, 4); int len = (int) Bytes.u32(body, 12);
                if (find(addr, len) == null) { status(o, false); return; }
                status(o, true); o.flush();
                byte[] data = new byte[len]; in.readFully(data);
                put(addr, data); writeCommands++; status(o, true);
            } else if (cmd == 0xbdaa000bL) {
                allocCommands++;
                status(o, true);
                if (allocated == 0) { regions.add(new Region(allocBase, (int) Bytes.u32(body, 4), 7)); allocated = allocBase; }
                o.write(Bytes.ptr(allocated));
            } else { status(o, false); }
        }

        public void close() throws IOException { server.close(); }
    }

    // ---- fake app files -------------------------------------------------------------------
    static Map<String, byte[]> files(Fake f) throws Exception {
        Map<String, byte[]> m = new HashMap<>();
        String sha = Bytes.hex(MessageDigest.getInstance("SHA-256").digest(f.fingerprint));
        m.put("profiles.json", "[\".\"]".getBytes());
        m.put("profile.json", ("{\"titleId\":\"PPSA09999\",\"contentId\":\"TEST-CONTENT\",\"version\":\"9.9\",\"playerRoot\":\"0x3000\",\"networkFlag\":\"0x2000\","
                + "\"fingerprints\":[{\"address\":\"0x1000\",\"length\":16,\"sha256\":\"" + sha + "\"}],\"scriptRegistry\":\"0x4000\",\"runOffset\":\"0xcb0\"}").getBytes());
        StringBuilder n = new StringBuilder("{\"PLAYER_ID\":{\"handler\":\"0x" + Long.toHexString(f.playerIdHandler) + "\"}");
        String[] names = {"GET_FRAME_COUNT", "PLAYER_PED_ID", "GET_VEHICLE_PED_IS_IN", "IS_HORN_ACTIVE", "GET_ENTITY_SPEED", "SET_VEHICLE_FORWARD_SPEED",
                "APPLY_FORCE_TO_ENTITY_CENTER_OF_MASS", "DISABLE_CONTROL_ACTION", "GET_FRAME_TIME", "GET_GAME_TIMER", "GET_ENTITY_COORDS",
                "GET_WATER_HEIGHT_NO_WAVES", "SET_ENTITY_COORDS_NO_OFFSET", "GET_ENTITY_HEADING", "SET_ENTITY_HEADING", "DOES_ENTITY_EXIST"};
        for (int i = 0; i < names.length; i++) n.append(",\"").append(names[i]).append("\":{\"handler\":\"0x").append(Long.toHexString(0x1800000L + i * 0x20)).append("\"}");
        n.append("}");
        m.put("native-addresses.json", n.toString().getBytes());
        byte[] img = new byte[0x20000]; new Random(42).nextBytes(img);
        for (int i = 0x10000; i < 0x10700; i++) img[i] = 0; // data area starts empty like the real one
        m.put("gta-bridge.bin", img);
        return m;
    }

    static int passed = 0, failed = 0;
    static void check(boolean ok, String what) { if (ok) { passed++; System.out.println("  ok   " + what); } else { failed++; System.out.println("  FAIL " + what); } }

    static Installer.Assets assets(Map<String, byte[]> m) {
        return p -> { byte[] b = m.get(p); if (b == null) throw new IOException("no " + p); return b; };
    }

    static String expectError(Installer.Config c, Installer.Assets a) {
        try { Installer.install(c, a, new Mem(), null); return null; } catch (InstallException e) { return e.getMessage(); }
    }

    static class Mem implements Installer.RecoveryStore {
        String s; public String load() { return s; } public void save(String j) { s = j; }
    }

    public static void main(String[] args) throws Exception {
        List<String> log = new ArrayList<>();

        System.out.println("Test 1: check connection changes nothing");
        try (Fake f = new Fake()) {
            Installer.Config cfg = new Installer.Config("127.0.0.1", f.port(), "id-1");
            Installer.Result r = Installer.inspect(cfg, assets(files(f)), log::add);
            check(r.titleId.equals("PPSA09999") && r.firmware.equals("13.60"), "reports game + firmware");
            check(f.writeCommands == 0 && f.allocCommands == 0, "no writes, no allocation");
        }

        System.out.println("Test 2: full install, then install again (re-attach)");
        try (Fake f = new Fake()) {
            Installer.Config cfg = new Installer.Config("127.0.0.1", f.port(), "id-1");
            Map<String, byte[]> files = files(f); Mem rec = new Mem();
            Installer.Result r = Installer.install(cfg, assets(files), rec, log::add);
            check(r.installed, "install reports success");
            long base = f.allocBase;
            check(Arrays.equals(f.get(0x240000 + 3 * 8, 8), Bytes.ptr(base)), "PLAYER_ID hook points at the menu");
            check(Bytes.u32(f.get(base + 0x105fc, 4), 0) == 1, "menu switched on");
            check(Bytes.u64(f.get(base + 0x10000, 8), 0) == 0x325654474e5350L, "menu signature written");
            check(Arrays.equals(f.get(base, 0x10000), Arrays.copyOf(files.get("gta-bridge.bin"), 0x10000)), "menu code copied exactly");
            check(Arrays.equals(f.get(base + 0x10008, 8), Bytes.ptr(f.playerIdHandler)), "original function saved for forwarding");
            check(Bytes.u64(f.get(base + 0x10550, 8), 0) == 0x1800000L, "game function table filled");
            check(r.framesAdvancing, "menu is running (frame counter moves)");
            check(rec.s != null && rec.s.contains("imageSha256"), "recovery info saved");
            int allocs = f.allocCommands;
            Installer.Result r2 = Installer.install(cfg, assets(files), rec, log::add);
            check(r2.installed && f.allocCommands == allocs, "second install re-attaches without allocating again");
        }

        System.out.println("Test 3: refuses in GTA Online");
        try (Fake f = new Fake()) {
            f.put(0x2000, new byte[]{1});
            String e = expectError(new Installer.Config("127.0.0.1", f.port(), "id-1"), assets(files(f)));
            check(e != null && e.contains("Story Mode"), "error mentions Story Mode");
            check(f.writeCommands == 0 && f.allocCommands == 0, "nothing written");
        }

        System.out.println("Test 4: refuses unknown game version");
        try (Fake f = new Fake()) {
            f.put(0x1000, "SOMETHING-ELSE!!".getBytes());
            String e = expectError(new Installer.Config("127.0.0.1", f.port(), "id-1"), assets(files(f)));
            check(e != null && e.contains("isn't supported"), "error says version unsupported");
            check(f.writeCommands == 0 && f.allocCommands == 0, "nothing written");
        }

        System.out.println("Test 5: refuses wrong firmware / wrong debugger");
        try (Fake f = new Fake()) {
            f.firmwareWord = 900;
            String e = expectError(new Installer.Config("127.0.0.1", f.port(), "id-1"), assets(files(f)));
            check(e != null && e.contains("firmware"), "wrong firmware refused");
            f.firmwareWord = 1360; f.branding = "PS5Debug v1.0";
            e = expectError(new Installer.Config("127.0.0.1", f.port(), "id-1"), assets(files(f)));
            check(e != null && e.contains("PS5Debug-NG 1.3.2"), "wrong debugger version refused");
        }

        System.out.println("Test 6: GTA not running / character not loaded");
        try (Fake f = new Fake()) {
            f.putU64(0x3000, 0);
            String e = expectError(new Installer.Config("127.0.0.1", f.port(), "id-1"), assets(files(f)));
            check(e != null && e.contains("Story Mode"), "asks you to load into Story Mode");
        }

        System.out.println("Test 7: bad input / unreachable console");
        try {
            Installer.validateEndpoint("not-an-ip", 744); check(false, "bad IP rejected");
        } catch (InstallException e) { check(true, "bad IP rejected"); }
        try {
            Installer.install(new Installer.Config("127.0.0.1", 9, "id-1"), assets(new HashMap<>()), null, null); check(false, "unreachable reported");
        } catch (InstallException e) { check(e.getMessage().contains("Could not reach"), "unreachable console gives a clear message"); }

        System.out.println("Test 8: JSON reader on the real bundled profiles");
        for (String p : new String[]{"app/src/main/assets/menu/profile.json", "app/src/main/assets/menu/profiles/ppsa04264-01.010.002/profile.json", "app/src/main/assets/menu/native-addresses.json"}) {
            try {
                Object o = MiniJson.parse(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(p)), StandardCharsets.UTF_8));
                check(o instanceof Map, "parses " + p.substring(p.lastIndexOf('/') + 1));
            } catch (Exception e) { check(false, "parses " + p + ": " + e); }
        }
        Map<String, Object> names = MiniJson.obj(MiniJson.parse(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/assets/menu/native-addresses.json")), StandardCharsets.UTF_8)));
        boolean all = true;
        for (String n : new String[]{"PLAYER_ID", "GET_FRAME_COUNT", "PLAYER_PED_ID", "GET_VEHICLE_PED_IS_IN", "IS_HORN_ACTIVE", "GET_ENTITY_SPEED", "SET_VEHICLE_FORWARD_SPEED",
                "APPLY_FORCE_TO_ENTITY_CENTER_OF_MASS", "DISABLE_CONTROL_ACTION", "GET_FRAME_TIME", "GET_GAME_TIMER", "GET_ENTITY_COORDS", "GET_WATER_HEIGHT_NO_WAVES",
                "SET_ENTITY_COORDS_NO_OFFSET", "GET_ENTITY_HEADING", "SET_ENTITY_HEADING", "DOES_ENTITY_EXIST"}) all &= names.containsKey(n);
        check(all, "real profile contains every game function the installer needs");

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
