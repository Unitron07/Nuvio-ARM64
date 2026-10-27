import com.sun.jna.*;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.net.httpserver.HttpServer;
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
    HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/fixture.avi", exchange -> {
      requests.incrementAndGet();
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
      Pointer handle = lib.getFunction("mpv_create").invokePointer(new Object[]{});
      if (handle == null) throw new IllegalStateException("mpv_create failed");
      try {
        for (String option : new String[]{"config", "osc", "load-scripts", "load-stats-overlay", "load-console",
            "load-auto-profiles", "ytdl", "load-select", "load-positioning", "load-commands", "load-context-menu",
            "d3d11-exclusive-fs", "hwdec"})
          set(lib, handle, option, "no");
        set(lib, handle, "vo", "null"); set(lib, handle, "ao", "null");
        set(lib, handle, "terminal", "yes");
        check(lib.getFunction("mpv_initialize").invokeInt(new Object[]{handle}), "initialize");
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/fixture.avi";
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
            check(lib.getFunction("mpv_command").invokeInt(new Object[]{handle,new StringArray(new String[]{"seek","1","absolute+exact"})}), "seek");
            seeked = true;
          }
          if (id == 7) { // MPV_EVENT_END_FILE
            Pointer data = event.getPointer(16);
            if (data.getInt(0) != 0 || data.getInt(4) < 0)
              throw new IllegalStateException("Non-EOF end-file: reason=" + data.getInt(0) + " error=" + data.getInt(4));
            eof = true;
          }
        }
        if (!loaded || !decoded || !seeked || !eof || requests.get() == 0 || maxPosition < 1.0)
          throw new IllegalStateException("loaded="+loaded+" decoded="+decoded+" seek="+seeked+" eof="+eof+" requests="+requests+" time="+maxPosition);
        System.out.println("PASS: ARM64 HTTP video decode, seek and EOF; requests="+requests+" maxPosition="+maxPosition);
      } finally { lib.getFunction("mpv_terminate_destroy").invokeVoid(new Object[]{handle}); }
    } finally { server.stop(0); }
    System.out.println("PASS: player destruction and DLL unload after HTTP decoding");
  }
  static void set(NativeLibrary lib, Pointer handle, String key, String value) {
    check(lib.getFunction("mpv_set_option_string").invokeInt(new Object[]{handle,key,value}), "option " + key);
  }
  static void check(int result, String action) { if (result < 0) throw new IllegalStateException(action+" failed: "+result); }
}
