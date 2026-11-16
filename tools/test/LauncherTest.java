import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.kelton.drehzahl.core.LauncherPick;
import dev.kelton.drehzahl.core.LauncherPick.Home;

/** Which home app the Stock launcher button opens. */
public class LauncherTest {
    static int fails;

    static void eq(String what, Object got, Object want) {
        boolean ok = want == null ? got == null : want.equals(got);
        System.out.println((ok ? "ok    " : "FAIL  ") + what + (ok ? "" : "  got=" + got + " want=" + want));
        if (!ok) fails++;
    }

    static String pkg(Home h) { return h == null ? null : h.pkg; }

    public static void main(String[] a) {
        String self = "dev.kelton.drehzahl";
        Home me = new Home(self, self + ".MainActivity", "Drehzahl", false, 0);
        Home fallback = new Home("com.android.settings", "com.android.settings.FallbackHome", "Settings", true, -1000);
        Home stock = new Home("com.android.launcher3", "com.android.launcher3.uioverrides.QuickstepLauncher", "Quickstep", true, 0);
        Home maker = new Home("com.tcl.android.launcher", "com.tcl.android.launcher.Launcher", "Launcher", true, 1);
        Home nova = new Home("com.teslacoilsw.launcher", "com.teslacoilsw.launcher.NovaLauncher", "Nova", false, 0);

        eq("only Drehzahl and the placeholder: nothing to open", LauncherPick.choose(Arrays.asList(me, fallback), self, ""), null);
        eq("empty list", LauncherPick.choose(new ArrayList<Home>(), self, ""), null);
        eq("null list", LauncherPick.choose(null, self, null), null);
        eq("automatic picks the preinstalled launcher", pkg(LauncherPick.choose(Arrays.asList(me, nova, fallback, stock), self, "")), "com.android.launcher3");
        eq("preinstalled wins over an installed one listed first", pkg(LauncherPick.choose(Arrays.asList(nova, stock), self, "")), "com.android.launcher3");
        eq("higher priority preinstalled launcher wins", pkg(LauncherPick.choose(Arrays.asList(stock, maker, nova), self, "")), "com.tcl.android.launcher");
        eq("no preinstalled launcher left: an installed one is used", pkg(LauncherPick.choose(Arrays.asList(me, fallback, nova), self, "")), "com.teslacoilsw.launcher");
        eq("a saved choice wins", pkg(LauncherPick.choose(Arrays.asList(me, stock, nova), self, nova.id())), "com.teslacoilsw.launcher");
        eq("a saved choice that was uninstalled falls back to automatic", pkg(LauncherPick.choose(Arrays.asList(me, stock), self, nova.id())), "com.android.launcher3");
        eq("Drehzahl can not be the saved choice", pkg(LauncherPick.choose(Arrays.asList(me, stock), self, me.id())), "com.android.launcher3");
        eq("the placeholder can not be the saved choice", LauncherPick.choose(Arrays.asList(me, fallback), self, fallback.id()), null);
        eq("choices leave out Drehzahl and the placeholder", LauncherPick.usable(Arrays.asList(me, fallback, stock, nova), self).size(), 2);
        List<Home> odd = new ArrayList<>();
        odd.add(null);
        odd.add(new Home(null, null, null, true, 0));
        odd.add(stock);
        eq("broken entries are skipped", pkg(LauncherPick.choose(odd, self, "")), "com.android.launcher3");
        eq("id form", stock.id(), "com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher");

        System.out.println(fails == 0 ? "LauncherTest: all passed" : "LauncherTest: " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
