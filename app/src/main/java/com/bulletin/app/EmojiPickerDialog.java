package com.bulletin.app;
import android.app.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.util.*;

/** The same offline searchable emoji picker used by Bulletin and Notes. */
final class EmojiPickerDialog {
    private final Activity activity;
    private final Ui ui;
    private Dialog dialog,emojiMenu;
    private static EmojiCatalog emojiCatalog;
    private final Runnable dismissed;
    private EmojiPickerDialog(Activity activity,Ui ui,Dialog parent,Runnable dismissed){this.activity=activity;this.ui=ui;dialog=parent;this.dismissed=dismissed;}
    static Dialog show(Activity activity,Ui ui,EditText target,Dialog parent,Runnable dismissed){EmojiPickerDialog picker=new EmojiPickerDialog(activity,ui,parent,dismissed);picker.showEmojiMenu(target);return picker.emojiMenu;}
    private LinearLayout column(){return NotesDialogs.column(activity);}
    private LinearLayout row(){return new LinearLayout(activity);}
    private TextView text(String value,int size,int color){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private EditText input(String hint,boolean ignored){return NotesDialogs.input(activity,ui,hint);}
    private Button button(String label,int ignored){return NotesDialogs.button(activity,ui,label);}
    private void hideKeyboard(){View focus=activity.getCurrentFocus();if(focus!=null)activity.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(),0);}
    private void fitDialog(Dialog d){d.getWindow().setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-ui.dp(16),ui.dp(560)),-2);}
    private void showEmojiMenu(EditText target) {
        hideKeyboard();
        if (emojiCatalog == null) {
            try (java.io.Reader repertoire = new java.io.InputStreamReader(activity.getAssets().open("emoji-test.txt"), java.nio.charset.StandardCharsets.UTF_8);
                 java.io.Reader keywords = new java.io.InputStreamReader(activity.getAssets().open("emoji-keywords.tsv"), java.nio.charset.StandardCharsets.UTF_8)) {
                emojiCatalog = EmojiCatalog.read(repertoire, keywords);
                android.graphics.Paint emojiPaint = new android.graphics.Paint(); emojiPaint.setTextSize(ui.dp(28));
                emojiCatalog.entries.removeIf(entry -> !emojiPaint.hasGlyph(entry.emoji));
            } catch (java.io.IOException error) { Toast.makeText(activity, "Could not load emojis", Toast.LENGTH_LONG).show(); return; }
        }
        Ui palette = ui; Dialog parent = dialog;
        LinearLayout content = column(); content.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12));
        content.setBackground(ui.panel(ui.border));
        TextView heading = text("Choose emoji", 16, ui.text); heading.setPadding(0, 0, 0, ui.dp(4)); content.addView(heading);
        EditText search = input("Search emojis", false); search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setMinimumHeight(ui.dp(44)); content.addView(search, new LinearLayout.LayoutParams(-1, ui.dp(44)));
        android.widget.Spinner categories = new android.widget.Spinner(activity);
        List<String> groups = new ArrayList<>(); groups.add("All emojis"); groups.addAll(emojiCatalog.categories);
        android.widget.ArrayAdapter<String> groupAdapter = new android.widget.ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, groups) {
            @Override public View getView(int position, View recycled, ViewGroup parentView) {
                TextView label = (TextView) super.getView(position, recycled, parentView); label.setTextColor(palette.text); return label;
            }
        };
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); categories.setAdapter(groupAdapter);
        categories.setContentDescription("Emoji category"); content.addView(categories, new LinearLayout.LayoutParams(-1, ui.dp(36)));
        TextView count = text("", 11, ui.muted); count.setPadding(0, ui.dp(4), 0, ui.dp(4)); content.addView(count);
        android.widget.GridView grid = new android.widget.GridView(activity);
        grid.setNumColumns(android.widget.GridView.AUTO_FIT); grid.setColumnWidth(ui.dp(52));
        grid.setStretchMode(android.widget.GridView.NO_STRETCH); grid.setVerticalSpacing(ui.dp(4));
        content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        EmojiCatalog.Entry defaultIcon = new EmojiCatalog.Entry("", "Default icon", "", "", "", "clipboard");
        List<EmojiCatalog.Entry> matches = new ArrayList<>(); matches.add(defaultIcon); matches.addAll(emojiCatalog.entries);
        Runnable sizeColumns = () -> {
            int available = grid.getWidth() > 0 ? grid.getWidth() : Math.min(activity.getResources().getDisplayMetrics().widthPixels - palette.dp(16), palette.dp(560)) - palette.dp(24);
            int columns = Math.max(1, Math.min(matches.size(), available / palette.dp(52)));
            if (grid.getNumColumns() != columns) grid.setNumColumns(columns);
        };
        grid.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> sizeColumns.run());
        Dialog[] popup = new Dialog[1];
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override public int getCount() { return matches.size(); }
            @Override public Object getItem(int position) { return matches.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parentView) {
                TextView cell = recycled instanceof TextView ? (TextView) recycled : text("", 28, palette.text);
                EmojiCatalog.Entry entry = matches.get(position); cell.setText(entry.emoji); cell.setGravity(Gravity.CENTER);
                if (entry.emoji.isEmpty()) {
                    Ui.Icon icon = new Ui.Icon("bulletin", palette.text); icon.setBounds(0, 0, palette.dp(28), palette.dp(28));
                    cell.setCompoundDrawables(icon, null, null, null); cell.setPadding(palette.dp(12), 0, 0, 0);
                } else { cell.setCompoundDrawables(null, null, null, null); cell.setPadding(0, 0, 0, 0); }
                cell.setContentDescription(entry.name); cell.setBackground(palette.button(entry.emoji.equals(target.getText().toString()) ? Ui.GOLD : palette.border));
                cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                cell.setOnClickListener(v -> { target.setText(entry.emoji); if (popup[0] != null) popup[0].dismiss(); });
                enableEmojiTap(cell);
                cell.setFocusable(false); cell.setFocusableInTouchMode(false);
                cell.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, palette.dp(52))); return cell;
            }
        };
        grid.setAdapter(adapter);
        grid.setFocusable(false); grid.setFocusableInTouchMode(false);
        Runnable filter = () -> {
            String category = categories.getSelectedItemPosition() <= 0 ? null : groups.get(categories.getSelectedItemPosition());
            matches.clear();
            if (category == null && (search.getText().toString().trim().isEmpty() || "default icon clipboard".contains(search.getText().toString().trim().toLowerCase(Locale.ROOT)))) matches.add(defaultIcon);
            matches.addAll(emojiCatalog.search(search.getText().toString(), category)); sizeColumns.run(); adapter.notifyDataSetChanged();
            count.setText(matches.isEmpty() ? "No matching emojis" : matches.size() == 1 ? "1 emoji" : matches.size() + " emojis"); grid.setSelection(0);
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        categories.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parentView, View view, int position, long id) { filter.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parentView) { }
        });
        LinearLayout actions = row(); Button close = button("Close", ui.border);
        for (Button action : new Button[]{close}) {
            action.setMinHeight(0); action.setMinimumHeight(0);
            actions.addView(action, new LinearLayout.LayoutParams(0, ui.dp(40), 1));
        }
        content.addView(actions);
        Dialog window = new Dialog(activity); popup[0] = window; emojiMenu = window; dialog = window; window.setContentView(content);
        window.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        window.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setOnDismissListener(v -> {
            if (emojiMenu == window) emojiMenu = null;
            if (dialog == window) dialog = parent != null && parent.isShowing() ? parent : null;
            if (parent != null && parent.isShowing()) fitDialog(parent);
            dismissed.run();
        });
        grid.setOnItemClickListener((parentView, view, position, id) -> { target.setText(matches.get(position).emoji); window.dismiss(); });
        close.setOnClickListener(v -> window.dismiss());
        content.setFocusableInTouchMode(true); content.requestFocus(); filter.run(); window.show(); fitEmojiMenu(window);
    }
    private void fitEmojiMenu(Dialog window) {
        int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - ui.dp(16), ui.dp(560));
        int height = Math.min(activity.getResources().getDisplayMetrics().heightPixels - ui.dp(80), ui.dp(640));
        window.getWindow().setLayout(width, height); window.getWindow().getDecorView().setPadding(0, 0, 0, 0);
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility") // Forward a genuine tap to the same accessible click action.
    private void enableEmojiTap(View cell) {
        cell.setOnTouchListener(new View.OnTouchListener() {
            float x, y; boolean moved;
            @Override public boolean onTouch(View view, android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    x = event.getRawX(); y = event.getRawY(); moved = false;
                } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    moved |= Math.hypot(event.getRawX() - x, event.getRawY() - y) > android.view.ViewConfiguration.get(activity).getScaledTouchSlop();
                } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP && !moved) {
                    view.setPressed(false); view.performClick(); return true;
                } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) moved = true;
                return false;
            }
        });
    }
}
