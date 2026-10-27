import com.sun.jna.*;
import java.util.*;

/** Native lifecycle probe. Each crashing control run must use a fresh JVM. */
public class MpvProbe {
  static void option(NativeLibrary lib, Pointer handle, String key, String value) {
    int rc = lib.getFunction("mpv_set_option_string").invokeInt(new Object[]{handle, key, value});
    if (rc < 0) throw new IllegalStateException("mpv option " + key + "=" + rc);
  }
  public static void main(String[] args) throws Exception {
    if (!Set.of("aarch64", "amd64").contains(System.getProperty("os.arch")))
      throw new IllegalStateException("A native 64-bit Windows JVM is required");
    int cycles = Integer.parseInt(System.getProperty("mpv.cycles", "20"));
    int dwell = Integer.parseInt(System.getProperty("mpv.dwellMillis", "50"));
    System.out.println("JVM=" + System.getProperty("os.arch") + " JNA=" + Native.VERSION);
    try (NativeLibrary lib = NativeLibrary.getInstance(new java.io.File(args[0]).getAbsolutePath())) {
      for (int cycle = 0; cycle < cycles; cycle++) {
        Pointer handle = lib.getFunction("mpv_create").invokePointer(new Object[]{});
        if (handle == null) throw new IllegalStateException("mpv_create failed");
        try {
          for (String[] opt : new String[][]{{"config","no"},{"vo","null"},{"ao","null"},{"terminal","yes"}})
            option(lib, handle, opt[0], opt[1]);
          for (int i = 1; i < args.length; i++) {
            String[] opt = args[i].split("=", 2);
            if (opt.length != 2) throw new IllegalArgumentException("Expected option=value");
            option(lib, handle, opt[0], opt[1]);
          }
          int rc = lib.getFunction("mpv_initialize").invokeInt(new Object[]{handle});
          if (rc < 0) throw new IllegalStateException("mpv_initialize=" + rc);
          if (cycle == 0) {
            for (String key : new String[]{"mpv-version", "ffmpeg-version"}) {
              Pointer value = lib.getFunction("mpv_get_property_string").invokePointer(new Object[]{handle,key});
              if (value == null) throw new IllegalStateException("Missing property " + key);
              System.out.println(key + "=" + value.getString(0));
              lib.getFunction("mpv_free").invokeVoid(new Object[]{value});
            }
          }
          Thread.sleep(dwell);
        } finally {
          System.out.println("cycle " + cycle + ": destroy begin");
          lib.getFunction("mpv_terminate_destroy").invokeVoid(new Object[]{handle});
          System.out.println("cycle " + cycle + ": destroy complete");
        }
      }
    }
    System.out.println("PASS: " + cycles + " initialize/destroy cycles and DLL unload");
  }
}
