package com.bulletin.app;

import android.app.*;
import android.view.*;
import android.widget.*;

/** Same bordered panel, spacing, sizing and controls as the Bulletin forms. */
final class NotesDialogs {
    static Dialog show(Activity activity,Ui ui,LinearLayout content){
        Dialog dialog=new Dialog(activity);ScrollView scroll=new ScrollView(activity);content.setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(16));scroll.addView(content);scroll.setBackground(ui.panel(ui.border));dialog.setContentView(scroll);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);dialog.show();dialog.getWindow().setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-ui.dp(16),ui.dp(560)),ViewGroup.LayoutParams.WRAP_CONTENT);return dialog;
    }
    static Button button(Activity activity,Ui ui,String label){Button b=new BorderedButton(activity,ui.border);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(ui.text);b.setBackground(ui.button(ui.border));return b;}
    static LinearLayout column(Activity a){LinearLayout v=new LinearLayout(a);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static TextView label(Activity a,Ui ui,String value){TextView text=new TextView(a);text.setText(value);text.setTextSize(15);text.setTextColor(ui.text);text.setPadding(0,ui.dp(8),0,ui.dp(8));return text;}
    static LinearLayout.LayoutParams field(Ui ui){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,ui.dp(48));p.setMargins(0,ui.dp(6),0,ui.dp(6));return p;}
    static EditText input(Activity a,Ui ui,String hint){EditText text=new EditText(a);text.setSingleLine(true);text.setTextColor(ui.text);text.setHintTextColor(ui.muted);text.setTextSize(15);text.setHint(hint);text.setPadding(ui.dp(12),0,ui.dp(12),0);text.setBackground(ui.panel(ui.border));return text;}
}
