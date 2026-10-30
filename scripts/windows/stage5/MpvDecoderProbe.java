import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Standalone visible libmpv probe, NOT the application's embedded player/UI. Local media only. */
public class MpvDecoderProbe {
  static NativeLibrary lib;
  static Pointer handle;
  static String text(String key) {
    PointerByReference value = new PointerByReference();
    if (lib.getFunction("mpv_get_property").invokeInt(new Object[]{handle,key,1,value}) < 0) return "";
    Pointer p=value.getValue();
    if (p==null) return "";
    try { return p.getString(0,"UTF-8"); }
    finally { lib.getFunction("mpv_free").invokeVoid(new Object[]{p}); }
  }
  static String number(String key) {
    boolean flag = Set.of("pause","core-idle").contains(key) || key.endsWith("/selected");
    Memory result=new Memory(8); result.clear();
    if (lib.getFunction("mpv_get_property").invokeInt(new Object[]{handle,key,flag?3:5,result}) < 0) return "null";
    double v=flag?result.getInt(0):result.getDouble(0);
    return Double.isFinite(v)?Double.toString(v):"null";
  }
  static String safe(String value, String... allowed) {
    return value.isEmpty()?"unavailable":Arrays.asList(allowed).contains(value)?value:"other";
  }
  static void set(String key,String value) {
    if(lib.getFunction("mpv_set_option_string").invokeInt(new Object[]{handle,key,value})<0)
      throw new IllegalStateException("Option rejected: "+key);
  }
  static void command(String... args) {
    if(lib.getFunction("mpv_command").invokeInt(new Object[]{handle,new StringArray(args)})<0)
      throw new IllegalStateException("Command rejected: "+args[0]);
  }
  public static void main(String[] args) throws Exception {
    if(args.length!=5 || !Set.of("auto","no").contains(args[2]))
      throw new IllegalArgumentException("DLL LOCAL_MEDIA auto|no SECONDS NEW_OUTPUT.jsonl");
    if(!System.getProperty("os.arch").equals("aarch64")) throw new IllegalStateException("Native ARM64 JDK required");
    Path media=Path.of(args[1]).toAbsolutePath();
    if(!Files.isRegularFile(media)) throw new IllegalArgumentException("Local media required");
    int seconds=Integer.parseInt(args[3]);
    if(seconds<5 || seconds>7200) throw new IllegalArgumentException("Duration 5..7200 seconds");
    Path output=Path.of(args[4]);
    try(var writer=Files.newBufferedWriter(output,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        var library=NativeLibrary.getInstance(Path.of(args[0]).toAbsolutePath().toString(),Map.of(Library.OPTION_OPEN_FLAGS,0x1100))) {
      lib=library; handle=lib.getFunction("mpv_create").invokePointer(new Object[]{});
      if(handle==null) throw new IllegalStateException("mpv_create failed");
      boolean progressing=false; double previous=-1;
      try {
        for(String option:new String[]{"config","osc","load-scripts","load-stats-overlay","load-console","load-auto-profiles",
            "ytdl","load-select","load-positioning","load-commands","load-context-menu","d3d11-exclusive-fs"}) set(option,"no");
        set("terminal","no"); set("msg-level","all=no"); set("title","Nuvio Stage 5 local decoder probe");
        set("vo","gpu-next,gpu,"); set("gpu-api","auto"); set("hwdec",args[2]); set("hwdec-codecs","all");
        set("hwdec-software-fallback","yes"); set("ao","null"); set("loop-file","inf");
        if(lib.getFunction("mpv_initialize").invokeInt(new Object[]{handle})<0) throw new IllegalStateException("Initialize failed");
        command("loadfile",media.toString());
        long started=System.nanoTime(), next=started;
        while(System.nanoTime()-started<seconds*1_000_000_000L) {
          Pointer event=lib.getFunction("mpv_wait_event").invokePointer(new Object[]{handle,0.1});
          if(event!=null && event.getInt(0)==7) {
            Pointer data=event.getPointer(16);
            if(data!=null && data.getInt(0)==4) throw new IllegalStateException("Playback error code "+data.getInt(4));
          }
          if(System.nanoTime()<next) continue;
          next=System.nanoTime()+1_000_000_000L;
          String codec="", decoder="";
          String count=number("track-list/count");
          for(int i=0; !count.equals("null") && i<Math.min(128,Double.parseDouble(count)); i++) {
            String prefix="track-list/"+i+"/";
            if(text(prefix+"type").equals("video") && number(prefix+"selected").equals("1.0")) {
              codec=text(prefix+"codec"); decoder=text(prefix+"decoder"); break;
            }
          }
          String current=safe(text("hwdec-current"),"no","d3d11va","d3d11va-copy","dxva2","dxva2-copy","nvdec","nvdec-copy","vulkan","vulkan-copy");
          StringBuilder row=new StringBuilder("{\"schema\":\"nuvio-playback-v1\",\"session\":1,\"architecture\":\"arm64\",\"phase\":\"sample\",\"requested\":\"")
            .append(args[2]).append("\",\"current\":\"").append(current)
            .append("\",\"codec\":\"").append(safe(codec,"h264","hevc","av1","vp9","rawvideo"))
            .append("\",\"decoder\":\"").append(safe(decoder,"h264","hevc","av1","libdav1d","vp9","rawvideo"))
            .append("\",\"elapsedSeconds\":").append((System.nanoTime()-started)/1e9);
          for(String key:new String[]{"video-params/w","video-params/h","time-pos","pause","core-idle","container-fps",
              "estimated-vf-fps","frame-drop-count","decoder-frame-drop-count","avsync"}) row.append(",\"").append(key).append("\":").append(number(key));
          row.append("}\n"); writer.write(row.toString()); writer.flush();
          String position=number("time-pos");
          if(!position.equals("null")) { double now=Double.parseDouble(position); progressing|=previous>=0&&now>previous; previous=now; }
        }
        if(!progressing) throw new IllegalStateException("No progressing playback observed");
      } finally { lib.getFunction("mpv_terminate_destroy").invokeVoid(new Object[]{handle}); }
    }
    System.out.println("PASS: standalone playback progressed; inspect decoder records. Embedded UI, audio and power acceptance are separate.");
  }
}
