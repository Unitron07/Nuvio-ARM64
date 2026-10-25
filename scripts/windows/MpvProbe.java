import com.sun.jna.*;
public class MpvProbe {
  public static void main(String[] args) {
    if (!System.getProperty("os.arch").equals("aarch64")) throw new IllegalStateException("ARM64 JVM required");
    try (NativeLibrary lib = NativeLibrary.getInstance(new java.io.File(args[0]).getAbsolutePath())) {
      System.out.println("JVM=" + System.getProperty("os.arch") + " JNA=" + Native.VERSION);
      Pointer handle = lib.getFunction("mpv_create").invokePointer(new Object[]{});
      if (handle == null) throw new IllegalStateException("mpv_create failed");
      try {
        for (String[] option : new String[][]{{"config","no"},{"vo","null"},{"ao","null"},{"terminal","no"}}) {
          int rc = lib.getFunction("mpv_set_option_string").invokeInt(new Object[]{handle,option[0],option[1]});
          if (rc < 0) throw new IllegalStateException("mpv option " + option[0] + "=" + rc);
        }
        int rc = lib.getFunction("mpv_initialize").invokeInt(new Object[]{handle});
        if (rc < 0) throw new IllegalStateException("mpv_initialize=" + rc);
        for(String key : new String[]{"mpv-version","ffmpeg-version","mpv-configuration"}) {
          Pointer value = lib.getFunction("mpv_get_property_string").invokePointer(new Object[]{handle,key});
          if (value != null) { System.out.println(key+"="+value.getString(0)); lib.getFunction("mpv_free").invokeVoid(new Object[]{value}); }
        }
      } finally {
        System.out.println("mpv_terminate_destroy: begin");
        lib.getFunction("mpv_terminate_destroy").invokeVoid(new Object[]{handle});
        System.out.println("mpv_terminate_destroy: complete");
      }
      System.out.println("NativeLibrary.close: begin");
    }
  }
}
