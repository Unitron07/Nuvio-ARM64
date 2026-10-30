import java.util.*;
import java.util.prefs.*;
public class PreferencesProbe {
    public static void main(String[] args) throws Exception {
        Preferences root=Preferences.userRoot();
        if (!root.getClass().getName().contains("IsolatedPreferencesFactory")) throw new AssertionError("Default registry backend selected");
        Preferences node=root.node("nuvio-synthetic/session");
        if(args[0].equals("write")) {
            if(node.get("session",null)!=null) throw new AssertionError("Fresh isolated store is not empty");
            node.put("session","synthetic-only-\u6f22\nvalue"); node.putInt("number",42);
            Preferences.systemRoot().node("nuvio-synthetic/session").put("session","separate-system");
            root.flush();
        } else {
            if(!node.get("session","").equals("synthetic-only-\u6f22\nvalue") || node.getInt("number",0)!=42) throw new AssertionError("Restart did not restore test values");
            if(!Preferences.systemRoot().node("nuvio-synthetic/session").get("session","").equals("separate-system")) throw new AssertionError("System/user namespace collision");
            if(!Arrays.asList(node.keys()).contains("session") || !root.nodeExists("nuvio-synthetic/session")) throw new AssertionError("Tree/key traversal failed");
            node.removeNode(); root.flush(); root.sync();
            if(root.nodeExists("nuvio-synthetic/session")) throw new AssertionError("Deleted test node restored");
        }
        System.out.println("PASS: isolated preferences "+args[0]);
    }
}
