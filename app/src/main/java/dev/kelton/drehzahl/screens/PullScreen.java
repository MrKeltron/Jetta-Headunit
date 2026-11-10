package dev.kelton.drehzahl.screens;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.io.File;
import java.text.DateFormat;
import java.util.List;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.obd.PullStore;
import dev.kelton.drehzahl.ui.PullChartView;
import dev.kelton.drehzahl.ui.Screen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;

/** One recorded pull: the chart, what stood out, and where the file is. */
public class PullScreen extends Screen {
    private final File txt;

    public PullScreen(File txt) { this.txt = txt; }

    @Override public String title() {
        return "Pull, " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(PullStore.when(txt));
    }

    @Override public int section() { return MainActivity.CAR; }

    @Override protected View build() {
        Context c = host;
        ScrollView sv = new ScrollView(c);
        LinearLayout root = W.vbox(c);
        root.setPadding(Theme.dp(24), Theme.dp(12), Theme.dp(24), Theme.dp(24));
        sv.addView(root);

        List<String> lines = PullStore.lines(txt);
        Object[] cols = PullStore.columns(txt);
        if (cols == null) {
            root.addView(W.note(c, "The readings for this pull are missing."));
            return sv;
        }
        PullChartView chart = new PullChartView(c, (String[]) cols[0], (float[][]) cols[1], PullStore.cuts(lines));
        chart.setBackground(Theme.outline(Theme.alpha(Theme.PANEL, 0.92f), Theme.LINE, 6));
        root.addView(chart, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(340)));
        root.addView(W.note(c, "Touch and slide across the chart to read every value at that moment. Red lines mark torque cuts."));

        root.addView(W.header(c, "What stood out"));
        List<String> text = PullStore.readable(lines);
        for (int i = 1; i < text.size(); i++) root.addView(W.note(c, text.get(i)));
        if (!text.isEmpty()) root.addView(W.note(c, "Pull: " + text.get(0)));

        root.addView(W.header(c, "File"));
        root.addView(W.note(c, txt.getAbsolutePath().replace(".txt", ".csv")
                + "\nOne row per reading. Opens in any spreadsheet. The PC toolkit can copy the whole folder with: PULL "
                + PullStore.dir(c).getAbsolutePath() + " pulls"));
        return sv;
    }
}
