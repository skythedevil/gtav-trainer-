package io.github.skythedevil.gtavmenu.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Android port of the installer in GronedWaffel/gta-v-ps5-menu
 * (src/connection.mjs, src/gta-profiles.mjs, src/gta-story-trainer.mjs,
 *  src/gta-native-bridge.mjs, src/install.mjs).
 *
 * What it does: checks that the right game build is running in Story Mode,
 * copies the menu program into the game's memory, switches it on, and leaves.
 * The menu then runs by itself inside GTA. Every memory write is checked first
 * ("is the memory what I expect?") and verified afterwards.
 */
public final class Installer {

    // ---- things the app supplies ----------------------------------------------------

    public interface Assets { byte[] read(String path) throws IOException; }
    public interface RecoveryStore { String load(); void save(String json); }
    public interface Progress { void say(String message); }

    public static final class Config {
        public final String host; public final int port; public final String identity;
        public Config(String host, int port, String identity) { this.host = host; this.port = port; this.identity = identity; }
    }

    public static final class Result {
        public String titleId, version, firmware, message;
        public boolean installed, framesAdvancing;
    }

    // ---- constants (copied from the original project) -------------------------------

    private static final List<String> FIRMWARES = Arrays.asList("11.00", "11.20", "11.60", "12.00", "12.02", "12.20",
            "12.40", "12.60", "12.70", "13.00", "13.20", "13.40", "13.42", "13.60");
    private static final Pattern NG_132 = Pattern.compile("ps5debug[- ]ng\\b.*\\bv1\\.3\\.2\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final long MAIN_SCRIPT_HASH = 1459623836L;
    private static final long BRIDGE_SIZE = 0x20000L;
    private static final long BRIDGE_SIGNATURE = 0x325654474e5350L;
    private static final String[] BRIDGE_NATIVES = {"GET_FRAME_COUNT", "PLAYER_PED_ID", "GET_VEHICLE_PED_IS_IN", "IS_HORN_ACTIVE",
            "GET_ENTITY_SPEED", "SET_VEHICLE_FORWARD_SPEED", "APPLY_FORCE_TO_ENTITY_CENTER_OF_MASS", "DISABLE_CONTROL_ACTION",
            "GET_FRAME_TIME", "GET_GAME_TIMER", "GET_ENTITY_COORDS", "GET_WATER_HEIGHT_NO_WAVES", "SET_ENTITY_COORDS_NO_OFFSET",
            "GET_ENTITY_HEADING", "SET_ENTITY_HEADING", "DOES_ENTITY_EXIST"};
    private static final int TIMEOUT_MS = 8000;

    // ---- public entry points --------------------------------------------------------

    /** Safe check: connects and reads memory, never writes anything. */
    public static Result inspect(Config cfg, Assets assets, Progress progress) {
        Installer in = new Installer(cfg, assets, null, progress);
        try {
            in.connect();
            in.say("Verifying GTA and Story Mode…");
            in.selectProfile();
            in.target();
            Result r = new Result();
            r.titleId = in.profile.titleId; r.version = in.profile.version; r.firmware = in.caps.firmware;
            r.message = "Everything looks good. GTA V " + r.titleId + " (" + r.version + "), PS5 firmware " + r.firmware + ". Nothing was changed.";
            return r;
        } finally { in.close(); }
    }

    /** Installs and switches on the in-game menu. */
    public static Result install(Config cfg, Assets assets, RecoveryStore recovery, Progress progress) {
        Installer in = new Installer(cfg, assets, recovery, progress);
        try {
            in.connect();
            in.say("Verifying GTA and Story Mode…");
            in.selectProfile();
            in.say("Installing the in-game menu… Keep GTA running.");
            in.ensureBridge();
            in.enableMenu();
            long base = in.base;
            long before = Bytes.u32(in.read(base + 0x10600L, 12), 0);
            sleep(1000);
            long after = Bytes.u32(in.read(base + 0x10600L, 12), 0);
            Result r = new Result();
            r.titleId = in.profile.titleId; r.version = in.profile.version; r.firmware = in.caps.firmware;
            r.installed = true;
            r.framesAdvancing = Long.compareUnsigned(after, before) > 0;
            r.message = "In-game menu installed. Press L1 + D-pad Right in GTA. D-pad navigates, Cross selects, Circle goes back. You can close this app.";
            return r;
        } finally { in.close(); }
    }

    // ---- state ----------------------------------------------------------------------

    private static final class Fingerprint { long address; int length; String sha256; }
    private static final class Profile {
        String titleId, contentId, version;
        long playerRoot, networkFlag, scriptRegistry, runOffset;
        Fingerprint[] fingerprints;
    }
    private static final class Target { long pid, root, player, playerInfo, weapons; float health, maxHealth; }

    private final Config cfg;
    private final Assets assets;
    private final RecoveryStore recovery;
    private final Progress progress;
    private Ps5Debug dbg;
    private Ps5Debug.Capabilities caps;
    private String connectionId;
    private Profile profile;
    private String dir;           // folder inside assets for the chosen game build

    // bridge session
    private long sessionPid;
    private long base, slot;
    private byte[] original;

    private Installer(Config cfg, Assets assets, RecoveryStore recovery, Progress progress) {
        this.cfg = cfg; this.assets = assets; this.recovery = recovery;
        this.progress = progress != null ? progress : new Progress() { public void say(String m) { } };
    }

    private void say(String m) { progress.say(m); }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InstallException("Cancelled"); }
    }

    private void close() { if (dbg != null) dbg.close(); }

    // ---- connection -----------------------------------------------------------------

    private void connect() {
        validateEndpoint(cfg.host, cfg.port);
        if (cfg.identity == null || cfg.identity.isEmpty()) throw new InstallException("Missing local installation identity");
        say("Connecting to PS5Debug-NG…");
        dbg = new Ps5Debug(cfg.host, cfg.port, TIMEOUT_MS);
        dbg.connect();
        caps = dbg.detect();
        if (!FIRMWARES.contains(caps.firmware))
            throw new InstallException("PS5 firmware " + caps.firmware + " is not in this menu's supported list.");
        if (!NG_132.matcher(caps.branding).find())
            throw new InstallException("Load PS5Debug-NG 1.3.2 first (found: " + caps.branding + ").");
        connectionId = cfg.identity + ":" + cfg.host + ":" + cfg.port;
    }

    public static void validateEndpoint(String host, int port) {
        java.util.regex.Matcher m = host == null ? null : IPV4.matcher(host.trim());
        boolean ok = m != null && m.matches();
        if (ok) for (int i = 1; i <= 4; i++) if (Integer.parseInt(m.group(i)) > 255) ok = false;
        if (!ok) throw new InstallException("Enter your PS5's IP address, like 192.168.1.25");
        if (port < 1 || port > 65535) throw new InstallException("Debugger port must be 1–65535 (normally 744).");
    }

    private byte[] read(long at, int length) { return dbg.read(sessionOrTargetPid, at, length); }
    private long sessionOrTargetPid;

    // ---- game build detection (gta-profiles.mjs) ------------------------------------

    private String text(String path) {
        try { return new String(assets.read(path), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new InstallException("Missing app file: " + path, e); }
    }

    private static String join(String dir, String name) { return dir.isEmpty() ? name : dir + "/" + name; }

    private long singleGtaPid() {
        long pid = -1; int n = 0;
        for (Ps5Debug.Proc p : dbg.processes()) if ("eboot.bin".equals(p.name)) { pid = p.pid; n++; }
        if (n != 1) throw new InstallException("GTA V is not running (or more than one game is open). Start GTA V and enter Story Mode.");
        return pid;
    }

    private void selectProfile() {
        long pid = singleGtaPid();
        sessionOrTargetPid = pid;
        Ps5Debug.ProcInfo info = dbg.info(pid);
        List<Object> entries = MiniJson.list(MiniJson.parse(text("profiles.json")));
        for (Object e : entries) {
            String entry = String.valueOf(e);
            if (entry.contains("..") || entry.startsWith("/")) throw new InstallException("Invalid GTA profile path");
            String d = entry.equals(".") ? "" : entry;
            Profile p = parseProfile(MiniJson.obj(MiniJson.parse(text(join(d, "profile.json")))));
            if (!p.titleId.equals(info.titleId) || !p.contentId.equals(info.contentId)) continue;
            boolean matches = p.fingerprints.length > 0;
            for (Fingerprint f : p.fingerprints) {
                byte[] b = dbg.read(pid, f.address, f.length);
                if (b.length != f.length || !sha256(b).equals(f.sha256)) { matches = false; break; }
            }
            if (matches) { profile = p; dir = d; return; }
        }
        throw new InstallException("This GTA V version isn't supported (" + info.titleId + "). Nothing was changed. "
                + "Supported: PPSA04263 01.000.000 and PPSA04264 01.010.002.");
    }

    private static Profile parseProfile(Map<String, Object> m) {
        Profile p = new Profile();
        p.titleId = MiniJson.str(m, "titleId"); p.contentId = MiniJson.str(m, "contentId"); p.version = MiniJson.str(m, "version");
        if (p.titleId == null || p.contentId == null) throw new InstallException("Bad game profile in app");
        p.playerRoot = Bytes.addr(m.get("playerRoot"));
        p.networkFlag = Bytes.addr(m.get("networkFlag"));
        p.scriptRegistry = m.containsKey("scriptRegistry") ? Bytes.addr(m.get("scriptRegistry")) : 0x4688118L;
        p.runOffset = m.containsKey("runOffset") ? Bytes.addr(m.get("runOffset")) : 0xcb0L;
        List<Object> fps = MiniJson.list(m.get("fingerprints"));
        p.fingerprints = new Fingerprint[fps.size()];
        for (int i = 0; i < fps.size(); i++) {
            Map<String, Object> f = MiniJson.obj(fps.get(i));
            Fingerprint fp = new Fingerprint();
            fp.address = Bytes.addr(f.get("address"));
            fp.length = (int) MiniJson.num(f, "length", 0);
            fp.sha256 = MiniJson.str(f, "sha256");
            if (fp.length <= 0 || fp.sha256 == null) throw new InstallException("Bad fingerprint in app profile");
            p.fingerprints[i] = fp;
        }
        return p;
    }

    private static String sha256(byte[] b) {
        try { return Bytes.hex(MessageDigest.getInstance("SHA-256").digest(b)); }
        catch (NoSuchAlgorithmException e) { throw new InstallException("SHA-256 unavailable", e); }
    }

    // ---- Story Mode checks (gta-story-trainer.mjs: target) --------------------------

    private static boolean mapped(List<Ps5Debug.MapEntry> maps, long at, long length, boolean write) {
        int need = write ? 3 : 1;
        for (Ps5Debug.MapEntry m : maps)
            if ((m.prot & need) == need && Bytes.ule(m.start, at) && Bytes.ule(at, at + length)
                    && Bytes.ule(at + length, m.end)) return true;
        return false;
    }

    private void requireMapped(List<Ps5Debug.MapEntry> maps, long at, long length, boolean write) {
        if (!mapped(maps, at, length, write)) throw new InstallException("Player state is outside the expected mapped memory");
    }

    private Target target() {
        long pid = singleGtaPid();
        Ps5Debug.ProcInfo info = dbg.info(pid);
        if (!info.titleId.equals(profile.titleId) || !info.contentId.equals(profile.contentId))
            throw new InstallException("This menu does not match the running game");
        List<Ps5Debug.MapEntry> maps = dbg.maps(pid);
        for (Fingerprint f : profile.fingerprints)
            if (!sha256(dbg.read(pid, f.address, f.length)).equals(f.sha256))
                throw new InstallException("GTA executable version mismatch; writes refused");
        if (dbg.read(pid, profile.networkFlag, 1)[0] != 0) throw new InstallException("This menu supports Story Mode only (not GTA Online).");
        long root = Bytes.u64(dbg.read(pid, profile.playerRoot, 8), 0);
        if (root == 0) throw new InstallException("Enter Story Mode and wait until your character is loaded.");
        requireMapped(maps, root, 16, false);
        long player = Bytes.u64(dbg.read(pid, root + 8, 8), 0);
        if (player == 0) throw new InstallException("Enter Story Mode and wait until your character is loaded.");
        requireMapped(maps, player, 0x1098, true);
        byte[] raw = dbg.read(pid, player, 0x1098);
        float health = Bytes.f32(raw, 0x250), maxHealth = Bytes.f32(raw, 0x254);
        if (Float.isNaN(health) || Float.isInfinite(health) || Float.isNaN(maxHealth) || Float.isInfinite(maxHealth)
                || maxHealth <= 0 || maxHealth > 100000 || health < 0 || health > maxHealth * 10)
            throw new InstallException("Player state is not ready. Unpause the game and try again.");
        long playerInfo = Bytes.u64(raw, 0x1088), weapons = Bytes.u64(raw, 0x1090);
        requireMapped(maps, playerInfo, profile.runOffset + 4, true);
        requireMapped(maps, weapons, 0x72, true);
        Target t = new Target();
        t.pid = pid; t.root = root; t.player = player; t.playerInfo = playerInfo; t.weapons = weapons;
        t.health = health; t.maxHealth = maxHealth;
        sessionOrTargetPid = pid;
        return t;
    }

    // ---- the bridge (gta-native-bridge.mjs) -----------------------------------------

    private Target validate() {
        Target t = target();
        if (sessionPid != 0 && t.pid != sessionPid) throw new InstallException("GTA session changed; start again");
        return t;
    }

    /** Check memory is what we expect, write, and read back to confirm. */
    private void write(long at, byte[] before, byte[] after) {
        Target t = validate();
        byte[] now = dbg.read(t.pid, at, before.length);
        if (!Arrays.equals(now, before)) throw new InstallException("Game memory changed; write refused.");
        dbg.writeMemory(t.pid, at, after);
        if (!Arrays.equals(dbg.read(t.pid, at, after.length), after)) throw new InstallException("Native bridge write did not verify");
    }

    private long[] locateProgram(long hash) {
        byte[] g = read(profile.scriptRegistry, 0x58);
        long count = Bytes.u32(g, 0x50);
        if (count == 0 || count > 65536) throw new InstallException("Invalid script registry");
        long entries = Bytes.u64(g, 0x40), buckets = Bytes.u64(g, 0x48);
        int index = Bytes.i32(read(buckets + (hash % count) * 4, 4), 0);
        for (int n = 0; index >= 0 && index < 16384 && n < 16384; n++) {
            byte[] e = read(entries + (long) index * 12, 12);
            if (Bytes.u32(e, 0) == hash) {
                int slotIdx = Bytes.i32(e, 4);
                long stride = Bytes.u32(g, 0x1c);
                if (slotIdx < 0 || slotIdx > 16384 || stride != 16) throw new InstallException("Invalid program slot");
                long p = Bytes.u64(read(Bytes.u64(g, 8) + slotIdx * stride, 8), 0);
                byte[] header = read(p, 0x78);
                if (Bytes.u32(header, 0x58) != hash) throw new InstallException("Script program identity mismatch");
                return new long[]{p, Bytes.u64(header, 0x40), Bytes.u32(header, 0x2c)};
            }
            index = Bytes.i32(e, 8);
        }
        throw new InstallException("Required Story Mode script is not loaded. Make sure you are playing (not in a menu) and try again.");
    }

    private static long handler(Map<String, Object> names, String name) {
        Object o = names.get(name);
        if (!(o instanceof Map)) throw new InstallException("Missing game function in profile: " + name);
        return Bytes.addr(MiniJson.obj(o).get("handler"));
    }

    private void ensureBridge() {
        Target t = validate();
        sessionPid = t.pid;
        Map<String, Object> names = MiniJson.obj(MiniJson.parse(text(join(dir, "native-addresses.json"))));
        long[] prog = locateProgram(MAIN_SCRIPT_HASH);
        long table = prog[1], count = prog[2];
        if (count == 0 || count > 8192) throw new InstallException("Invalid native table");
        byte[] natives = read(table, (int) count * 8);
        byte[] orig = Bytes.ptr(handler(names, "PLAYER_ID"));
        long[] slots = new long[(int) count]; int found = 0;
        for (int i = 0; i < count; i++)
            if (Arrays.equals(Arrays.copyOfRange(natives, i * 8, i * 8 + 8), orig)) slots[found++] = table + i * 8L;
        byte[] imageFile = readBinary(join(dir, "gta-bridge.bin"));

        if (found != 1) {
            // Already installed by this app earlier? Re-attach only to our own exact build.
            say("Menu may already be installed; checking…");
            Map<String, Object> saved = loadRecovery();
            String savedOrig = MiniJson.str(saved, "original");
            if (found != 0 || MiniJson.num(saved, "pid", -1) != t.pid
                    || !connectionId.equals(MiniJson.str(saved, "connectionId"))
                    || !sha256(imageFile).equals(MiniJson.str(saved, "imageSha256"))
                    || savedOrig == null || !savedOrig.equals(Bytes.hex(orig)))
                throw new InstallException("PLAYER_ID is already hooked by a different build. Restart GTA, then run the install again.");
            long sl = Bytes.addr(saved.get("slot"));
            long bs = Bytes.u64(Bytes.fromHex(MiniJson.str(saved, "installed")), 0);
            if (Long.compareUnsigned(sl, table) < 0 || Long.compareUnsigned(sl, table + count * 8) >= 0 || (sl - table) % 8 != 0
                    || !Arrays.equals(read(sl, 8), Bytes.ptr(bs)))
                throw new InstallException("Resident hook identity changed. Restart GTA, then run the install again.");
            if (Bytes.u64(read(bs + 0x10000L, 8), 0) != BRIDGE_SIGNATURE
                    || !Arrays.equals(read(bs, 64), Arrays.copyOfRange(imageFile, 0, 64)))
                throw new InstallException("The menu already in memory is different. Restart GTA, then run the install again.");
            base = bs; slot = sl; original = orig;
            return;
        }

        slot = slots[0]; original = orig;
        say("Allocating memory in the game…");
        Ps5Debug allocator = new Ps5Debug(cfg.host, cfg.port, TIMEOUT_MS);
        try { allocator.connect(); base = allocator.alloc(t.pid, (int) BRIDGE_SIZE); }
        finally { allocator.close(); }
        boolean execOk = false;
        if (base != 0) for (Ps5Debug.MapEntry m : dbg.maps(t.pid))
            if ((m.prot & 7) == 7 && Bytes.ule(m.start, base) && Bytes.ule(base + BRIDGE_SIZE, m.end)) execOk = true;
        if (!execOk) throw new InstallException("The PS5 did not give executable memory inside the game. Nothing was installed.");
        if (imageFile.length != BRIDGE_SIZE) throw new InstallException("Menu image in the app is the wrong size");

        byte[] image = imageFile.clone();
        Bytes.putU64(image, 0x10000, BRIDGE_SIGNATURE);
        System.arraycopy(orig, 0, image, 0x10008, 8);
        for (int i = 0; i < BRIDGE_NATIVES.length; i++) Bytes.putU64(image, 0x10550 + i * 8, handler(names, BRIDGE_NATIVES[i]));
        say("Copying the menu into the game…");
        byte[] zeros = new byte[4096];
        for (int off = 0; off < image.length; off += 4096)
            write(base + off, zeros, Arrays.copyOfRange(image, off, off + 4096));

        if (recovery != null) recovery.save("{\n"
                + "  \"pid\": " + t.pid + ",\n"
                + "  \"connectionId\": \"" + connectionId + "\",\n"
                + "  \"slot\": \"" + Bytes.hexAddr(slot) + "\",\n"
                + "  \"installed\": \"" + Bytes.hex(Bytes.ptr(base)) + "\",\n"
                + "  \"original\": \"" + Bytes.hex(orig) + "\",\n"
                + "  \"imageSha256\": \"" + sha256(imageFile) + "\"\n}");
        say("Hooking the menu into the game…");
        write(slot, orig, Bytes.ptr(base));
    }

    private Map<String, Object> loadRecovery() {
        String s = recovery == null ? null : recovery.load();
        if (s == null || s.trim().isEmpty()) return MiniJson.obj(MiniJson.parse("{}"));
        try { return MiniJson.obj(MiniJson.parse(s)); } catch (InstallException e) { return MiniJson.obj(MiniJson.parse("{}")); }
    }

    private byte[] readBinary(String path) {
        try { return assets.read(path); } catch (IOException e) { throw new InstallException("Missing app file: " + path, e); }
    }

    private void enableMenu() {
        long at = base + 0x105fcL;
        write(at, read(at, 4), Bytes.u32Bytes(1));
    }
}
