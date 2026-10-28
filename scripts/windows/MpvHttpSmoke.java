import com.sun.jna.*;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import javax.net.ssl.*;
import java.security.KeyStore;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Decode a generated AVI over loopback HTTP, seek, reach EOF, destroy and unload. */
public class MpvHttpSmoke {
  public static void main(String[] args) throws Exception {
    if (!System.getProperty("os.arch").equals("aarch64")) throw new IllegalStateException("ARM64 JVM required");
    byte[] media = Files.readAllBytes(Path.of(args[1]));
    AtomicInteger requests = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    AtomicInteger redirects = new AtomicInteger();
    boolean tls = args.length == 4;
    HttpServer server;
    if (tls) {
      KeyStore store = KeyStore.getInstance("PKCS12");
      try (var input = Files.newInputStream(Path.of(args[2]))) { store.load(input, "fixture-only".toCharArray()); }
      KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keys.init(store, "fixture-only".toCharArray());
      SSLContext context = SSLContext.getInstance("TLS"); context.init(keys.getKeyManagers(), null, null);
      HttpsServer secure = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      secure.setHttpsConfigurator(new HttpsConfigurator(context)); server = secure;
    } else server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    String base = (tls ? "https" : "http") + "://127.0.0.1:" + server.getAddress().getPort();
    String query = "fixture=one%2Btwo&session=local";
    server.createContext("/redirect", exchange -> {
      redirects.incrementAndGet();
      exchange.getResponseHeaders().set("Location", base + "/fixture.avi?" + exchange.getRequestURI().getRawQuery());
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.createContext("/fixture.avi", exchange -> {
      requests.incrementAndGet();
      var headers = exchange.getRequestHeaders();
      if (!query.equals(exchange.getRequestURI().getRawQuery()) ||
          !"Bearer fixture-only".equals(headers.getFirst("Authorization")) ||
          !"Nuvio-Stage3-Fixture".equals(headers.getFirst("User-Agent")) ||
          !(base + "/").equals(headers.getFirst("Referer")) ||
          !"comma,backslash\\value".equals(headers.getFirst("X-Fixture"))) {
        rejected.incrementAndGet();
        System.err.println("Fixture rejected: query=" + query.equals(exchange.getRequestURI().getRawQuery()) +
            " auth=" + "Bearer fixture-only".equals(headers.getFirst("Authorization")) +
            " agent=" + "Nuvio-Stage3-Fixture".equals(headers.getFirst("User-Agent")) +
            " referer=" + (base + "/").equals(headers.getFirst("Referer")) +
            " extra=" + "comma,backslash\\value".equals(headers.getFirst("X-Fixture")));
        exchange.sendResponseHeaders(401, -1); exchange.close(); return;
      }
      int start = 0, end = media.length - 1, status = 200;
      String range = exchange.getRequestHeaders().getFirst("Range");
      if (range != null && range.matches("bytes=\\d+-\\d*")) {
        String[] parts = range.substring(6).split("-", -1);
        start = Integer.parseInt(parts[0]);
        if (!parts[1].isEmpty()) end = Math.min(end, Integer.parseInt(parts[1]));
        status = 206;
      }
      if (start > end) { exchange.sendResponseHeaders(416, -1); exchange.close(); return; }
      exchange.getResponseHeaders().set("Content-Type", "video/x-msvideo");
      exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
      if (status == 206) exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + media.length);
      exchange.sendResponseHeaders(status, end - start + 1);
      try (var out = exchange.getResponseBody()) { out.write(media, start, end - start + 1); }
    });
    server.start();
    try (NativeLibrary lib = NativeLibrary.getInstance(Path.of(args[0]).toAbsolutePath().toString(),
        Map.of(Library.OPTION_OPEN_FLAGS, 0x1100))) {
      for (int cycle = 0; cycle < 3; cycle++) {
      Pointer handle = lib.getFunction("mpv_create").invokePointer(new Object[]{});
      if (handle == null) throw new IllegalStateException("mpv_create failed");
      try {
        for (String option : new String[]{"config", "osc", "load-scripts", "load-stats-overlay", "load-console",
            "load-auto-profiles", "ytdl", "load-select", "load-positioning", "load-commands", "load-context-menu",
            "d3d11-exclusive-fs", "hwdec"})
          set(lib, handle, option, "no");
        set(lib, handle, "vo", "null"); set(lib, handle, "ao", "null");
        if (tls) { set(lib, handle, "tls-verify", "yes"); set(lib, handle, "tls-ca-file", Path.of(args[3]).toAbsolutePath().toString()); }
        setHeaders(lib, handle, new String[]{"Authorization: Bearer fixture-only", "User-Agent: Nuvio-Stage3-Fixture",
            "Referer: " + base + "/", "X-Fixture: comma,backslash\\value"});
        set(lib, handle, "terminal", "yes");
        check(lib.getFunction("mpv_initialize").invokeInt(new Object[]{handle}), "initialize");
        String url = base + "/redirect?" + query;
        check(lib.getFunction("mpv_command").invokeInt(new Object[]{handle,new StringArray(new String[]{"loadfile",url,"replace"})}), "loadfile");
        boolean loaded = false, eof = false, seeked = false, decoded = false;
        double maxPosition = 0;
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (!eof && System.nanoTime() < deadline) {
          Pointer event = lib.getFunction("mpv_wait_event").invokePointer(new Object[]{handle,0.05});
          int id = event.getInt(0);
          if (id == 8) loaded = true; // MPV_EVENT_FILE_LOADED
          DoubleByReference position = new DoubleByReference();
          if (lib.getFunction("mpv_get_property").invokeInt(new Object[]{handle,"time-pos",5,position}) >= 0)
            maxPosition = Math.max(maxPosition, position.getValue());
          DoubleByReference width = new DoubleByReference();
          if (lib.getFunction("mpv_get_property").invokeInt(new Object[]{handle,"video-params/w",5,width}) >= 0 && width.getValue() == 64)
            decoded = true;
          if (loaded && decoded && maxPosition > 0.1 && !seeked) {
            check(lib.getFunction("mpv_command").invokeInt(new Object[]{handle,new StringArray(new String[]{"set","pause","yes"})}), "pause");
            Memory paused = new Memory(4);
            check(lib.getFunction("mpv_get_property").invokeInt(new Object[]{handle,"pause",3,paused}), "query pause");
            if (paused.getInt(0) != 1) throw new IllegalStateException("Pause state did not propagate");
            check(lib.getFunction("mpv_command").invokeInt(new Object[]{handle,new StringArray(new String[]{"seek","1","absolute+exact"})}), "seek");
            check(lib.getFunction("mpv_command").invokeInt(new Object[]{handle,new StringArray(new String[]{"set","pause","no"})}), "resume");
            seeked = true;
          }
          if (id == 7) { // MPV_EVENT_END_FILE
            Pointer data = event.getPointer(16);
            if (data.getInt(0) != 0 || data.getInt(4) < 0)
              throw new IllegalStateException("Non-EOF end-file: reason=" + data.getInt(0) + " error=" + data.getInt(4));
            eof = true;
          }
        }
        if (!loaded || !decoded || !seeked || !eof || requests.get() == 0 || redirects.get() == 0 || rejected.get() != 0 || maxPosition < 1.0)
          throw new IllegalStateException("loaded="+loaded+" decoded="+decoded+" seek="+seeked+" eof="+eof+" requests="+requests+" time="+maxPosition);
        System.out.println("PASS: ARM64 " + (tls ? "HTTPS" : "HTTP") + " protected redirect, headers, decode, pause, seek, resume and EOF; requests="+requests+" maxPosition="+maxPosition);
        check(lib.getFunction("mpv_command").invokeInt(new Object[]{handle,new StringArray(new String[]{"stop"})}), "stop");
      } finally { lib.getFunction("mpv_terminate_destroy").invokeVoid(new Object[]{handle}); }
      }
    } finally { server.stop(0); }
    System.out.println("PASS: player destruction and DLL unload after " + (tls ? "HTTPS" : "HTTP") + " decoding");
  }
  static void set(NativeLibrary lib, Pointer handle, String key, String value) {
    check(lib.getFunction("mpv_set_option_string").invokeInt(new Object[]{handle,key,value}), "option " + key);
  }
  // Same public libmpv node-array ABI used by the Windows application bridge.
  static void setHeaders(NativeLibrary lib, Pointer handle, String[] headers) {
    List<Memory> strings = new ArrayList<>();
    Memory values = new Memory(16L * headers.length);
    values.clear();
    for (int i = 0; i < headers.length; i++) {
      byte[] bytes = Native.toByteArray(headers[i], "UTF-8");
      Memory text = new Memory(bytes.length); text.write(0, bytes, 0, bytes.length); strings.add(text);
      values.setPointer(16L * i, text); values.setInt(16L * i + 8, 1); // MPV_FORMAT_STRING
    }
    Memory list = new Memory(24); list.clear(); list.setInt(0, headers.length); list.setPointer(8, values);
    Memory node = new Memory(16); node.clear(); node.setPointer(0, list); node.setInt(8, 7); // NODE_ARRAY
    check(lib.getFunction("mpv_set_option").invokeInt(new Object[]{handle,"http-header-fields",6,node}), "HTTP header array");
    java.lang.ref.Reference.reachabilityFence(strings);
    java.lang.ref.Reference.reachabilityFence(values);
    java.lang.ref.Reference.reachabilityFence(list);
  }
  static void check(int result, String action) { if (result < 0) throw new IllegalStateException(action+" failed: "+result); }
}
