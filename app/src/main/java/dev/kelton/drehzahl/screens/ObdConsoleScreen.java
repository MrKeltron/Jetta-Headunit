package dev.kelton.drehzahl.screens;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.obd.ObdSource;
import dev.kelton.drehzahl.ui.Screen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;

/** Type AT and OBD commands straight to the adapter. Handy for testing a new adapter or car. */
public class ObdConsoleScreen extends Screen {
    private static final String[] QUICK = { "ATI", "ATRV", "ATDPN", "0100", "010C", "010B", "0902", "03" };
    private TextView out;
    private ScrollView scroll;

    @Override public String title() { return "Adapter console"; }
    @Override public int section() { return MainActivity.CAR; }

    @Override protected View build() {
        Context c = host;
        LinearLayout root = W.vbox(c);
        root.setPadding(Theme.dp(24), Theme.dp(12), Theme.dp(24), Theme.dp(16));

        scroll = new ScrollView(c);
        scroll.setBackground(Theme.outline(Theme.alpha(Theme.PANEL, 0.92f), Theme.LINE, 6));
        out = W.text(c, "", 15, Theme.TEXT, Theme.mono());
        out.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));
        out.setTextIsSelectable(true);
        scroll.addView(out);
        root.addView(scroll, W.vweight(1));

        HorizontalScrollView qs = new HorizontalScrollView(c);
        qs.setHorizontalScrollBarEnabled(false);
        LinearLayout quick = W.hbox(c);
        for (final String q : QUICK) {
            TextView b = W.button(c, q, false, new View.OnClickListener() { public void onClick(View v) { send(q); } });
            b.setTypeface(Theme.mono());
            b.setAllCaps(false);
            b.setText(q);
            b.setMinWidth(Theme.dp(90));
            LinearLayout.LayoutParams lp = W.lp(W.WRAP, W.WRAP);
            lp.rightMargin = Theme.dp(8);
            quick.addView(b, lp);
        }
        qs.addView(quick);
        root.addView(qs, W.margins(W.lp(W.MATCH, W.WRAP), 0, 12, 0, 10));

        LinearLayout row = W.hbox(c);
        final EditText in = new EditText(c);
        in.setHint("AT or OBD command, for example 010C");
        in.setTypeface(Theme.mono());
        in.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Theme.dpf(18));
        in.setTextColor(Theme.TEXT);
        in.setHintTextColor(Theme.FAINT);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setImeOptions(EditorInfo.IME_ACTION_SEND);
        in.setBackground(Theme.outline(Theme.PANEL, Theme.LINE, 8));
        in.setPadding(Theme.dp(14), Theme.dp(10), Theme.dp(14), Theme.dp(10));
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                send(in.getText().toString()); in.setText(""); return true;
            }
        });
        row.addView(in, new LinearLayout.LayoutParams(0, Theme.dp(56), 1f));
        row.addView(W.gap(c, 10));
        row.addView(W.button(c, "Send", true, new View.OnClickListener() {
            public void onClick(View v) { send(in.getText().toString()); in.setText(""); }
        }));
        row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row, W.lp(W.MATCH, W.WRAP));

        List<String> log = ObdSource.get(c).log();
        StringBuilder sb = new StringBuilder("// recent adapter log\n");
        for (int i = Math.max(0, log.size() - 30); i < log.size(); i++) sb.append(log.get(i)).append('\n');
        sb.append("// ").append(ObdSource.STATE_NAMES[ObdSource.get(c).state].toLowerCase(Locale.US)).append('\n');
        out.setText(sb.toString());
        return root;
    }

    private void send(String cmd) {
        final String c = cmd.trim().toUpperCase(Locale.US).replace(" ", "");
        if (c.isEmpty()) return;
        append("> " + c + "  ...");
        ObdSource.get(host).submit(new String[] { c }, false, new ObdSource.Callback() {
            public void done(String r) {
                String body = r.startsWith("> ") && r.indexOf('\n') > 0 ? r.substring(r.indexOf('\n') + 1) : r;
                append(body.trim().isEmpty() ? "(empty reply)" : body.trim());
            }
        });
    }

    private void append(String s) {
        out.append(s + "\n");
        scroll.post(new Runnable() { public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
    }
}
