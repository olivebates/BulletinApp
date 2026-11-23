package com.bulletin.app;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String MUST_DO_EDIT_NOTICE="You must complete the Must Do quests before you can change this.";
    private GameStore store;
    private Game game;
    private Ui ui;
    private int tab;
    private long navigationRevealAt;
    private long randomUsageAt;
    private boolean trackingRandomUsage, firstRearrangeSession;
    private boolean energyHintPending;
    private int toolsPage, toolsTool;
    private CreativeBriefView briefEditor;
    private BriefDictionary briefDictionary;
    private final java.util.Map<String,CreativeBrief> briefSessions = new java.util.HashMap<>();
    private Bundle briefSessionStates = new Bundle();
    private boolean[] briefPickerExpanded = {true, false};
    private ChoiceRandomizerView choiceEditor;
    private ScrollView toolsScroll;
    private final int[] toolsScrollPositions = new int[3];
    private int toolsScrollPage;
    private boolean toolsScrollRestoring;
    private Bundle choiceEditorStates = new Bundle();
    private Bundle choiceSessionStates = new Bundle();
    private String choiceListId = "";
    private final boolean[] toolNamesArrange = new boolean[2];
    private ToolListReorder toolListDrag;
    private final java.util.Map<String,ChoiceRandomizer> choiceRandomizers = new java.util.HashMap<>();
    private final Runnable saveChoices = () -> { store.save(); renderedRevision = store.revision; };
    private boolean questTimelines, fineTimelines;
    private boolean questRearrange, lawRearrange;
    private int questAnalyticsTab, lawAnalyticsTab;
    private int questPieRange = 4, lawPieRange = 4;
    private int questTimelinePreset = -1, lawTimelinePreset = -1;
    private boolean moreOptionsExpanded;
    private BoardArrangement dragArrangement;
    private final android.graphics.RectF originalDragSlot = new android.graphics.RectF();
    private View ungroupedDrop;
    private FrameLayout frame;
    private LinearLayout root;
    private TextView daysValue, coinsValue, timerValue, focusValue; private boolean activeTimeRevealed;
    private View coinTarget;
    private CoinFlight flight;
    private BalanceGraph balanceGraph;
    private final List<TimelineView> timelineViews=new ArrayList<>();
    private final java.util.Map<String, TextView> remainingLabels = new java.util.HashMap<>();
    private final java.util.Map<String, TextView> bountyLabels = new java.util.HashMap<>();
    private final java.util.Map<String, TextView> lawTimers = new java.util.HashMap<>();
    private final java.util.Map<String, android.animation.ValueAnimator> lawImpacts = new java.util.HashMap<>();
    private Game.MissedReview deathReviewShown;
    private boolean deathPlaying;
    private long renderedRevision = -1;
    private boolean resumed, animating;
    private Dialog dialog;
    private Dialog emojiMenu;
    private Dialog briefPicker;
    private android.widget.PopupWindow pasteNotice;
    private EmojiCatalog emojiCatalog;
    private TextView receipt;
    private android.animation.ValueAnimator counterAnimation;
    private long displayedCoins, displayedDays;
    private boolean countersInitialized, completingCard, completionCelebrating, showingMustDoCompletion;
    private float rewardX = -1, rewardY = -1;
    private long lastPenaltyAt;
    private final java.util.Map<String, View> questViews = new java.util.HashMap<>();
    private ScrollView boardScroll;
    private int boardScrollY;
    private String boardDragId;
    private long lastBoardDragScrollAt;
    private float boardDragScrollSpeed, boardDragScrollRemainder;
    private boolean boardDragScrollRunning;
    private final Runnable boardDragScroll = new Runnable() {
        @Override public void run() {
            if (boardDragId == null || boardScroll == null || !resumed || boardDragScrollSpeed == 0) { stopBoardDragScroll(); return; }
            long now = android.os.SystemClock.uptimeMillis();
            float distance = boardDragScrollRemainder + boardDragScrollSpeed * Math.min(50, now - lastBoardDragScrollAt) / 1000f;
            int pixels = (int) distance; boardDragScrollRemainder = distance - pixels;
            boardScroll.scrollBy(0, pixels); lastBoardDragScrollAt = now;
            effects.postDelayed(this, 16);
        }
    };
    private boolean draggingCategory, boardMovePending;
    private float dragOriginX = Float.NaN, dragOriginY;
    private String dropDestination, lastDropTarget, creatingCategoryId = "";
    private final java.util.Map<String, View> categoryViews = new java.util.HashMap<>();
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Handler effects = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            accountRandomUsage();
            store.sync();
            if (completingCard || completionCelebrating || boardDragId != null) { ticker.postDelayed(this, 250); return; }
            if (renderedRevision != store.revision && tab != 2 && tab != 3) render();
            if (tab == 2 && balanceGraph != null && renderedRevision != store.revision) {
                balanceGraph.invalidate(); renderedRevision = store.revision;
            }
            updateTimer();
            if (!animating) updateCounters(counterDays(), counterCoins());
            showWarning();
            showNotices();
            showMissedReview();
            if (tab == 0 && !questTimelines && game.session == null && dialog == null && !deathPlaying && game.missedReviews.isEmpty() && game.pendingConfetti > 0 && game.completionLog.pending.isEmpty() && !game.mustDoLog.hasPending(game.day)) {
                int celebration = game.pendingConfetti; game.pendingConfetti = 0;
                store.save(); renderedRevision = store.revision;
                flight.rain(celebration > 1);
            }
            ticker.postDelayed(this, 250);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                if (!navigateBack()) moveTaskToBack(true);
            });
        store = ((BulletinApplication) getApplication()).store();
        game = store.game;
        if (savedInstanceState != null) {
            tab = savedInstanceState.getInt("tab", 0);
            toolsTool = Math.max(0, Math.min(1, savedInstanceState.getInt("toolsTool")));
            boolean[] arrange = savedInstanceState.getBooleanArray("toolNamesArrange"); if (arrange != null && arrange.length == 2) { toolNamesArrange[0] = arrange[0]; toolNamesArrange[1] = arrange[1]; }
            Bundle briefs = savedInstanceState.getBundle("briefSessionStates"); if (briefs != null) briefSessionStates = briefs;
            boolean[] groups = savedInstanceState.getBooleanArray("briefPickerExpanded"); if (groups != null && groups.length == 2) briefPickerExpanded = groups; else if (groups != null && groups.length == 3) briefPickerExpanded = new boolean[]{groups[0] || groups[1], groups[2]};
            Bundle sessions = savedInstanceState.getBundle("choiceSessionStates"); if (sessions != null) choiceSessionStates = sessions;
            Bundle editors = savedInstanceState.getBundle("choiceEditorStates"); if (editors != null) choiceEditorStates = editors;
            int[] positions = savedInstanceState.getIntArray("toolsScrollPositions"); if (positions != null) for (int i = 0; i < Math.min(positions.length, toolsScrollPositions.length); i++) toolsScrollPositions[i] = Math.max(0, positions[i]);
            toolsPage = savedInstanceState.getInt("toolsPage"); choiceListId = savedInstanceState.getString("choiceListId", "");
            questTimelines = savedInstanceState.getBoolean("questTimelines"); fineTimelines = savedInstanceState.getBoolean("fineTimelines");
            questRearrange = savedInstanceState.getBoolean("questRearrange"); lawRearrange = savedInstanceState.getBoolean("lawRearrange");
            questAnalyticsTab = savedInstanceState.getInt("questAnalyticsTab"); lawAnalyticsTab = savedInstanceState.getInt("lawAnalyticsTab");
            questPieRange = savedInstanceState.getInt("questPieRange", 4); lawPieRange = savedInstanceState.getInt("lawPieRange", 4);
            moreOptionsExpanded = savedInstanceState.getBoolean("moreOptionsExpanded");

        }
        if (getIntent().hasExtra("navigateTab")) { tab = Math.max(0, Math.min(3, getIntent().getIntExtra("navigateTab", 0))); if (tab == 3 && getIntent().getBooleanExtra("toolsHome", false)) toolsPage = 0; getIntent().removeExtra("navigateTab"); getIntent().removeExtra("toolsHome"); }
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        store.sync();
        render();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);setIntent(intent);
        if(intent.hasExtra("navigateTab")){activeTimeRevealed=false;tab=Math.max(0,Math.min(3,intent.getIntExtra("navigateTab",0)));questTimelines=false;fineTimelines=false;if(tab==3&&intent.getBooleanExtra("toolsHome",false))toolsPage=0;intent.removeExtra("navigateTab");intent.removeExtra("toolsHome");store.sync();render();}
    }
    private boolean navigateBack() {
        if (tab == 3 && toolsPage == 1 && toolNamesArrange[toolsTool]) { toolNamesArrange[toolsTool] = false; render(); return true; }
        if (tab == 3 && toolsPage > 0) { toolsPage--; render(); return true; }
        if (rearranging()) {
            questRearrange = false; lawRearrange = false; render(); return true;
        }
        if (questTimelines || fineTimelines) {
            questTimelines = false; fineTimelines = false; boardScrollY = 0; render(); return true;
        }
        if (tab != 0) {
            store.sync(); tab = 0; boardScrollY = 0; render(); return true;
        }
        return false;
    }
    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("GestureBackNavigation") // API 26–32 fallback; API 33+ uses OnBackInvokedDispatcher above.
    @Override public void onBackPressed() {
        if (!navigateBack()) super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBooleanArray("toolNamesArrange", toolNamesArrange.clone());
        if (briefEditor != null) briefEditor.captureState();
        for (java.util.Map.Entry<String,CreativeBrief> entry : briefSessions.entrySet()) briefSessionStates.putStringArray(entry.getKey(), entry.getValue().snapshot());
        out.putBundle("briefSessionStates", briefSessionStates); out.putInt("toolsTool", toolsTool); out.putBooleanArray("briefPickerExpanded", briefPickerExpanded);
        captureToolsScroll(); out.putIntArray("toolsScrollPositions", toolsScrollPositions.clone());
        if (choiceEditor != null) choiceEditor.captureState();
        for (java.util.Map.Entry<String,ChoiceRandomizer> entry : choiceRandomizers.entrySet()) choiceEditorStates.putIntArray(entry.getKey(), entry.getValue().editor.snapshot());
        for (java.util.Map.Entry<String,ChoiceRandomizer> entry : choiceRandomizers.entrySet()) choiceSessionStates.putLongArray(entry.getKey(), entry.getValue().snapshot());
        out.putBundle("choiceSessionStates", choiceSessionStates);
        out.putBundle("choiceEditorStates", choiceEditorStates);
        out.putBoolean("questRearrange", questRearrange); out.putBoolean("lawRearrange", lawRearrange);
        out.putInt("questAnalyticsTab", questAnalyticsTab); out.putInt("lawAnalyticsTab", lawAnalyticsTab);
        out.putInt("questPieRange", questPieRange); out.putInt("lawPieRange", lawPieRange);
        out.putBoolean("moreOptionsExpanded", moreOptionsExpanded);
        out.putInt("toolsPage", toolsPage); out.putString("choiceListId", choiceListId); out.putInt("tab", tab); out.putBoolean("questTimelines", questTimelines); out.putBoolean("fineTimelines", fineTimelines); super.onSaveInstanceState(out);
    }
    @Override protected void onStart() { super.onStart(); store.appOpened(); }
    @Override protected void onStop() { store.appClosed(); super.onStop(); }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        store.lawWarningListener = () -> {
            if (!resumed) return;
            if (renderedRevision != store.revision && boardDragId == null && !completingCard && tab != 2 && tab != 3) render();
            frame.post(this::startLawImpacts);
        };
        store.sync(); render(); ensureTimer();
        if(Build.VERSION.SDK_INT>=33&&!getPreferences(0).getBoolean("reminderPermissionAsked",false)){
            getPreferences(0).edit().putBoolean("reminderPermissionAsked",true).apply();
            if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        }
        if (!game.welcomeShown) {
            game.welcomeShown = true; store.save(); renderedRevision = store.revision;
            showInformation("Welcome!\n\nThis app is designed to make good habits more rewarding and bad habits easier to resist.\n\nCreate quests for activities you want to do more often, and set laws to discourage habits you want to quit. Choose an activity, focus on it, and earn a bounty. Or you can let the app pick two options when you’re unsure what to do next.\n\nTrack where your time goes, see your progress, and make each day a little more intentional.");
        }
        ticker.removeCallbacks(tick); ticker.post(tick);
    }
    @Override protected void onPause() {
        if (toolListDrag != null) toolListDrag.cancel();
        captureToolsScroll();
        if (briefEditor != null) briefEditor.freezeState();
        if (choiceEditor != null) choiceEditor.freezeState();
        stopBoardDragScroll();
        store.lawWarningListener = null; cancelLawImpacts();
        if (pasteNotice != null) pasteNotice.dismiss();
        resumed = false; accountRandomUsage();
        if (deathPlaying) { deathPlaying = false; deathReviewShown = null; }
        if (boardDragId != null) { if (dragArrangement != null) dragArrangement.restore(game); boardDragId = null; draggingCategory = false; boardMovePending = false; store.save(); }
        completingCard = false; completionCelebrating = false;
        for (View card : questViews.values()) card.animate().cancel();
        ticker.removeCallbacks(tick);
        stopAnimation();
        store.sync(); store.save();
        activeTimeRevealed=false;super.onPause();
    }
    @Override protected void onDestroy() {
        if (pasteNotice != null) pasteNotice.dismiss();
        ticker.removeCallbacksAndMessages(null); effects.removeCallbacksAndMessages(null);
        if (emojiMenu != null) emojiMenu.dismiss();
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration); render();
        if (dialog != null && dialog.isShowing()) { if (dialog == emojiMenu) fitEmojiMenu(dialog); else if (dialog == briefPicker) fitBriefPicker(dialog); else fitDialog(dialog); }
    }
    private void ensureTimer() {
        if (game.hasRunningTimers()) startForegroundService(new Intent(this, TimerService.class));
    }
    private void showWarning() {
        if (store.warning != null) { Toast.makeText(this, store.warning, Toast.LENGTH_LONG).show(); store.warning = null; }
    }
    private long visibleCoins() { return game.coins - game.pendingCoins; }
    private long counterDays() {
        long value = game.days;
        for (Game.MissedReview review : game.missedReviews) value = Game.add(value, review.daysLost);
        return value;
    }
    private long counterCoins() {
        long value = visibleCoins();
        for (Game.MissedReview review : game.missedReviews) value = Game.add(value, review.coinsLost);
        return value;
    }
    private boolean landscape() { return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE; }

    private void accountRandomUsage() {
        long now = android.os.SystemClock.uptimeMillis();
        long before = game.randomModeUsedMillis;
        if (trackingRandomUsage) game.randomModeUsedMillis = Math.min(10001, before + Math.max(0, now - randomUsageAt));
        randomUsageAt = now;
        trackingRandomUsage = resumed && tab == 0 && game.randomMode && !questTimelines && !questRearrange;
        if (before <= 10000 && game.randomModeUsedMillis > 10000) store.save();
    }
    private void render() {
        completionCelebrating=false;
        showingMustDoCompletion=tab==0&&!questRearrange&&!questTimelines&&game.completionLog.pending.isEmpty()&&game.mustDoLog.hasPending(game.day)&&(game.session==null||game.session.phase==Game.REST);
        accountRandomUsage();
        if (toolListDrag != null) { toolListDrag.cancel(); toolListDrag = null; }
        if (briefEditor != null) { briefEditor.captureState(); briefEditor = null; }
        captureToolsScroll(); toolsScroll = null;
        if (choiceEditor != null) { choiceEditor.captureState(); choiceEditor = null; }
        cancelLawImpacts();
        stopAnimation();
        if (boardScroll != null) boardScrollY = boardScroll.getScrollY();
        boardScroll = null;
        questViews.clear();
        ui = new Ui(this, tab == 1 ? 0 : tab == 3 ? (game.hue + 55) % 360 : game.hue);
        if (tab == 0 && game.session != null) for (Game.Quest q : game.quests)
            if (q.id.equals(game.session.questId)) { ui = questPalette(q); break; }
        getWindow().setNavigationBarColor(ui.background);
        getWindow().setStatusBarColor(ui.background);
        timerValue = null;
        focusValue = null;
        balanceGraph = null;timelineViews.clear();
        remainingLabels.clear();
        bountyLabels.clear();
        lawTimers.clear();
        frame = new FrameLayout(this);
        frame.setBackgroundColor(ui.background);
        root = column();
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets system = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(system.left, system.top, system.right, system.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(frame);
        root.requestApplyInsets();
        if (!landscape() || tab == 3) addHeader();
        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        if (tab == 3) buildTools(body);
        else if (tab == 2) buildSettings(body);
        else if (tab == 0 ? questTimelines && game.questControlsUnlocked() : fineTimelines && game.lawControlsUnlocked()) buildTimelines(body);
        else if (tab == 0 && !questRearrange && !game.completionLog.pending.isEmpty() && (game.session == null || game.session.phase == Game.REST)) buildCompletion(body);
        else if (showingMustDoCompletion) buildMustDoCompletion(body);
        else if (tab == 0 && game.session != null && !questRearrange) buildTimer(body);
        else {
            buildBoard(body);
            if (tab == 0 && game.questsCreated >= 1 && game.questsCreated < 4) {
                TextView encouragement = text("Create more quests to continue!", 16, ui.border);
                encouragement.setGravity(Gravity.CENTER); encouragement.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
                FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
                body.addView(encouragement, position);
            }
        }
        if (rearranging()) {
            addNavigation(); root.getChildAt(root.getChildCount() - 1).setVisibility(View.GONE);
            LinearLayout actions = row(); Button back = button("Back", ui.border); addAction(actions, back); root.addView(actions); back.setOnClickListener(v -> navigateBack());
        } else {
        if (analyticsPage()) {
            View divider = new View(this); divider.setBackgroundColor(ui.border); root.addView(divider, new LinearLayout.LayoutParams(-1, 1));
            addAnalyticsBar();
        } else if (tab == 0 && game.hasActiveRepeatableQuests()) {
            View energyDivider = new View(this); energyDivider.setBackgroundColor(ui.border);
            root.addView(energyDivider, new LinearLayout.LayoutParams(-1, 1));
            addEnergyBar();
        }
        if (tab == 0 || tab == 1) {
            View divider = new View(this); divider.setBackgroundColor(ui.border);
            root.addView(divider, new LinearLayout.LayoutParams(-1, 1));
            addFooter();
        }
        addNavigation();
        }
        if(flight==null)flight=new CoinFlight(this);
        else if(flight.getParent() instanceof ViewGroup)((ViewGroup)flight.getParent()).removeView(flight);
        flight.clearCompletionWave();
        frame.addView(flight, new FrameLayout.LayoutParams(-1, -1));
        receipt = text("", 14, ui.text);
        receipt.setGravity(Gravity.CENTER); receipt.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        receipt.setBackground(ui.panel(Ui.GREEN)); receipt.setVisibility(View.GONE);
        receipt.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        FrameLayout.LayoutParams receiptParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        receiptParams.setMargins(ui.dp(16), ui.dp(88), ui.dp(16), 0); frame.addView(receipt, receiptParams);
        receipt.setOnClickListener(v -> receipt.setVisibility(View.GONE));
        updateCounters(countersInitialized ? displayedDays : counterDays(), countersInitialized ? displayedCoins : counterCoins());
        renderedRevision = store.revision;
        if (resumed && tab == 0 && game.pendingCoins > 0 && game.missedReviews.isEmpty()) {
            long award = game.takePendingCoins();
            store.save(); renderedRevision = store.revision;
            if (!showingMustDoCompletion && game.completionLog.pending.isEmpty() && store.notices.stream().noneMatch(n -> n.earned > 0)) frame.post(() -> animateReward(award));
        }
        frame.post(() -> {
            startLawImpacts();
            showNotices();
            if (!animating && (displayedCoins != counterCoins() || displayedDays != counterDays()))
                animateCounters(counterDays(), counterCoins());
        });
    }
    private void addHeader() {
        LinearLayout header = row(); header.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        header.setBackground(ui.panel(ui.border));
        header.addView(ui.icon(tab == 1 ? "fine" : tab == 2 || tab == 3 ? "settings" : "bulletin", ui.border, 38));
        LinearLayout words = column(); words.setPadding(ui.dp(10), 0, 0, 0); words.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(tab == 0 && questTimelines || tab == 1 && fineTimelines ? "Timeline" : tab == 3 ? (toolsPage == 0 ? "Tools" : toolsTool == 1 ? "Creative Brief Generator" : "Choice Randomizer") : tab == 1 ? "LAW" : tab == 2 ? "SETTINGS" : "BULLETIN", 20, ui.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setLetterSpacing(.13f);
        if (tab == 3) { title.setSingleLine(true); title.setAutoSizeTextTypeUniformWithConfiguration(12, 20, 1, android.util.TypedValue.COMPLEX_UNIT_SP); }
        words.addView(title);
        TextView subtitle = text(tab == 1 ? "Keep your word. Own your mistakes." : tab == 2 ? "Set your own rhythm." : LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())), 11, ui.muted);
        if (tab != 2 && tab != 3) words.addView(subtitle);
        header.addView(words, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(header, spaced(-1, 64));
    }
    private void buildCompletion(FrameLayout body) {
        String id=game.completionLog.pending.get(0).id;
        String name="Task completed";long firstDay=game.completionLog.latestDay(id);
        for(Game.Quest q:game.quests) if(q.id.equals(id)){name=q.name;firstDay=q.firstDay;break;}
        LinearLayout content=column();content.setGravity(Gravity.CENTER_HORIZONTAL);content.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(8));
        TextView heading=text(name,20,Ui.GREEN);heading.setGravity(Gravity.CENTER);heading.setTypeface(Typeface.DEFAULT_BOLD);content.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        final long earned=game.completionLog.earned(id);final boolean grand=game.completionLog.grand(id);
        CompletionColumn[] column=new CompletionColumn[1];
        column[0]=new CompletionColumn(this,ui,game.completionLog,id,firstDay,game.day,()->{
            completionCelebrating=true;game.completionLog.acknowledge(id);
            if(game.completionLog.pending.isEmpty())game.pendingConfetti=0;
            store.save();renderedRevision=store.revision;store.sounds.play(Synth.VICTORY);
        },()->{
            if(!resumed)return;
            flight.reward("+"+Money.format(earned),-1,-1);flight.rain(grand);store.sounds.play(Synth.COIN);
            animateCounters(counterDays(),counterCoins());
        },()->{
            column[0].setVisibility(View.INVISIBLE);
            effects.postDelayed(()->{if(resumed&&completionCelebrating){completionCelebrating=false;render();}},1000);
        });
        column[0].setWaveRenderer((source,x,y,radius,color)->{if(flight!=null)flight.completionWave(source,x,y,radius,color,false);});
        LinearLayout.LayoutParams columnParams=new LinearLayout.LayoutParams(-1,0,1);columnParams.gravity=Gravity.CENTER_HORIZONTAL;
        content.addView(column[0],columnParams);
        body.addView(content,new FrameLayout.LayoutParams(-1,-1));
    }
    private void buildMustDoCompletion(FrameLayout body) {
        LinearLayout content=column();content.setGravity(Gravity.CENTER);content.setPadding(ui.dp(12),ui.dp(16),ui.dp(12),ui.dp(12));
        TextView heading=text("You completed your Must Do's!",22,Ui.GOLD);heading.setGravity(Gravity.CENTER);heading.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        long day=game.day;
        MustDoCalendar calendar=new MustDoCalendar(this,ui,game.mustDoLog,day,()->{
            completionCelebrating=true;game.mustDoLog.acknowledge(day);game.pendingConfetti=0;store.save();renderedRevision=store.revision;store.sounds.play(Synth.VICTORY);
        },()->{if(resumed)flight.rain(true);},()->{if(resumed&&completionCelebrating){completionCelebrating=false;render();}});
        calendar.setWaveRenderer((source,x,y,radius,color)->{if(flight!=null)flight.completionWave(source,x,y,radius,color);});
        content.addView(calendar,new LinearLayout.LayoutParams(-1,-2));body.addView(content,new FrameLayout.LayoutParams(-1,-1));
    }
    private List<Game.Quest> visibleQuests() {
        List<String> before = new ArrayList<>(game.randomQuestIds);
        long previousDay = game.randomDay;
        List<Game.Quest> result = game.boardQuests();
        if (previousDay != game.randomDay || !before.equals(game.randomQuestIds)) store.save();
        return result;
    }
    private void enterBoardRearrange() {
        if (tab == 0) { questRearrange = true; questTimelines = false; firstRearrangeSession = !game.rearrangeVisited; game.rearrangeVisited = true; store.save(); }
        else { lawRearrange = true; fineTimelines = false; }
        render();
    }
    private boolean rearranging() { return tab == 0 ? questRearrange : tab == 1 && lawRearrange; }
    private List<Game.Quest> boardQuestsForMode() { return questRearrange && tab == 0 ? new ArrayList<>(game.quests) : visibleQuests(); }
    private void buildBoard(FrameLayout body) {
        List<Game.Quest> quests = boardQuestsForMode();
        int count = tab == 1 ? game.fines.size() : quests.size();
        if (count == 0 && !(tab == 0 && game.randomMode && !questRearrange) && (tab != 0 || game.categories.isEmpty())) {
            LinearLayout empty = column(); empty.setGravity(Gravity.CENTER); empty.setPadding(ui.dp(24), 0, ui.dp(24), 0);
            empty.addView(ui.icon(tab == 1 ? "fine" : "bulletin", ui.border, 56));
            TextView title = text(tab == 1 ? "A clean slate." : game.completedToday > 0 ? "Well done, adventurer." : "Your next chapter starts here.", 20, ui.text);
            title.setGravity(Gravity.CENTER); title.setPadding(0, ui.dp(12), 0, ui.dp(8)); empty.addView(title);
            String explanation = tab == 1 ? "Add a law for a habit you want to leave behind."
                : game.completedToday > 0 ? game.completedToday + " quests completed today. Your board is clear."
                : questsScheduledLater() ? "No quests are due right now. Your scheduled quests will appear on their date."
                : "Tap the + button in the bottom left corner to create a quest!";
            TextView hint = text(explanation, 13, ui.muted); hint.setGravity(Gravity.CENTER); empty.addView(hint);
            if (tab == 0 && game.randomMode) addRandomRefresh(empty);
            body.addView(empty, new FrameLayout.LayoutParams(-1, -1)); return;
        }
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(rearranging()); scroll.setClipToPadding(false);
        if (rearranging()) {
            scroll.setVerticalScrollBarEnabled(true); scroll.setHorizontalScrollBarEnabled(false);
            scroll.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
            scroll.setScrollBarSize(ui.dp(30)); scroll.setScrollbarFadingEnabled(false);
            scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            if (Build.VERSION.SDK_INT >= 29) {
                android.graphics.drawable.GradientDrawable thumb = new android.graphics.drawable.GradientDrawable();
                thumb.setColor(ui.border); thumb.setCornerRadius(ui.dp(4));
                android.graphics.drawable.GradientDrawable track = new android.graphics.drawable.GradientDrawable();
                track.setColor((ui.border & 0xffffff) | 0x33000000); track.setCornerRadius(ui.dp(4));
                scroll.setVerticalScrollbarThumbDrawable(thumb); scroll.setVerticalScrollbarTrackDrawable(track);
            }
        }
        boardScroll = scroll;
        scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() { scroll.getViewTreeObserver().removeOnPreDrawListener(this); scroll.scrollTo(0, boardScrollY); return true; }
        });
        LinearLayout list = column(); list.setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(4));
        fillBoardGrid(list, quests);
        if (rearranging()) enableQuestReorder(list);
        scroll.addView(list); body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }
    private void addRandomRefresh(LinearLayout list) {
        Button refresh = button("↻  Two new quests", ui.border);
        refresh.setContentDescription("Refresh two random quests");
        list.addView(refresh, fieldParams());
        refresh.setOnClickListener(v -> { store.sync(); game.refreshRandomQuests(new java.util.Random()); store.save(); render(); });
        list = collapsibleSection(list, "Categories", ui, !moreOptionsExpanded, collapsed -> moreOptionsExpanded = !collapsed);
        addRandomCategoryCheckbox(list, "", "Uncategorized");
        for (Game.Category c : game.categories) addRandomCategoryCheckbox(list, c.id, c.name);
    }
    private LinearLayout collapsibleSection(LinearLayout parent, String title, Ui palette, boolean collapsed, java.util.function.Consumer<Boolean> changed) {
        LinearLayout group = column(); group.setPadding(0, ui.dp(12), 0, ui.dp(12)); parent.addView(group);
        TextView heading = text((collapsed ? "▸  " : "▾  ") + title, 13, palette.border);
        heading.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        heading.setGravity(landscape() ? Gravity.CENTER : Gravity.CENTER_VERTICAL); heading.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        heading.setContentDescription("Section: " + title + (collapsed ? ", collapsed" : ", expanded"));
        group.addView(heading, new LinearLayout.LayoutParams(-1, ui.dp(32)));
        LinearLayout content = column(); content.setVisibility(collapsed ? View.GONE : View.VISIBLE); group.addView(content);
        heading.setOnClickListener(v -> {
            boolean next = content.getVisibility() == View.VISIBLE; content.setVisibility(next ? View.GONE : View.VISIBLE);
            heading.setText((next ? "▸  " : "▾  ") + title); heading.setContentDescription("Section: " + title + (next ? ", collapsed" : ", expanded")); changed.accept(next);
        });
        return content;
    }
    private void addGroupedAnalytics(LinearLayout rows, List<String> ids, java.util.function.BiConsumer<LinearLayout, String> add) {
        for (java.util.Map.Entry<String, List<String>> entry : game.analyticsGroups(ids, tab == 1).entrySet()) {
            Game.Category category = game.category(entry.getKey());
            String sectionKey = tab + ":" + analyticsTab() + ":" + entry.getKey();
            boolean collapsed = game.collapsedAnalyticsSections.contains(sectionKey);
            Ui palette = category != null ? new Ui(this, category.hue >= 0 ? category.hue : game.hue) : ui;
            LinearLayout group = collapsibleSection(rows, category != null ? game.categoryTitle(category) : "Uncategorized", palette, collapsed, next -> {
                game.collapsedAnalyticsSections.remove(sectionKey);
                if (next) game.collapsedAnalyticsSections.add(sectionKey);
                store.save(); renderedRevision = store.revision;
            });
            if (category != null && game.categoryActive(category)) {
                TextView heading = (TextView) ((LinearLayout) group.getParent()).getChildAt(0);
                heading.setTextColor(Ui.GOLD); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD); heading.setBackgroundColor((palette.border & 0xffffff) | 0x30000000);
            }
            for (String id : entry.getValue()) add.accept(group, id);
        }
    }
    private void addRandomCategoryCheckbox(LinearLayout list, String id, String name) {
        CheckBox option = check(name); option.setMinHeight(0); option.setMinimumHeight(ui.dp(36));
        option.setContentDescription("Random category: " + name); option.setChecked(!game.excludedRandomCategories.contains(id));
        list.addView(option, new LinearLayout.LayoutParams(-1, -2));
        option.setOnCheckedChangeListener((v, checked) -> { store.sync(); game.setRandomCategoryEnabled(id, checked); store.save(); render(); });
    }
    private void fillBoardGrid(LinearLayout list, List<Game.Quest> quests) {
        list.removeAllViews(); questViews.clear(); remainingLabels.clear(); bountyLabels.clear(); lawTimers.clear();
        categoryViews.clear(); ungroupedDrop = null;
        if (tab == 0 && (!game.randomMode || questRearrange)) {
            List<Game.Quest> required = new ArrayList<>();
            for (Game.Quest q : quests) if (game.standaloneRequired(q)) required.add(q);
            addQuestRows(list, required);
            List<Game.Quest> ungrouped = new ArrayList<>();
            for (String id : game.orderedBoardItems()) {
                Game.Category category = game.category(id);
                if (category != null) {
                    addQuestRows(list, ungrouped); ungrouped.clear();
                    LinearLayout group = column(); group.setPadding(0, ui.dp(12), 0, ui.dp(12));
                    group.addView(categoryRow(category));
                    if ((!category.collapsed || questRearrange) && !draggingCategory) {
                        List<Game.Quest> members = new ArrayList<>();
                        for (Game.Quest q : quests) if (!game.standaloneRequired(q) && q.categoryId.equals(id)) members.add(q);
                        addQuestRows(group, members);
                    }
                    list.addView(group);
                } else for (Game.Quest q : quests) if (!game.standaloneRequired(q) && q.categoryId.isEmpty() && q.id.equals(id)) ungrouped.add(q);
            }
            addQuestRows(list, ungrouped);
            if (questRearrange) {
                TextView drop = text("Drop here to move outside categories", 11, ui.border);
                drop.setGravity(Gravity.CENTER); drop.setMinimumHeight(ui.dp(64));
                drop.setContentDescription("Move quest outside categories to bottom of board");
                list.addView(drop, new LinearLayout.LayoutParams(-1, ui.dp(64))); ungroupedDrop = drop;
            }
            return;
        }
        int count = tab == 1 ? game.fines.size() : quests.size();
        int columns = landscape() ? 2 : 1;
        for (int index = 0; index < count; index += columns) {
            LinearLayout row = row();
            for (int col = 0; col < columns; col++) {
                int pos = index + col;
                if (pos >= count) {
                    LinearLayout.LayoutParams empty = new LinearLayout.LayoutParams(0, ui.dp(tab == 0 ? 48 : 64), 1);
                    empty.setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
                    row.addView(new View(this), empty); continue;
                }
                View card = tab == 1 ? fineCard(game.fines.get(pos)) : questCard(quests.get(pos));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ui.dp(tab == 0 ? 48 : 64), 1);
                p.setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4)); row.addView(card, p);
            }
            list.addView(row);
        }
        if (tab == 0 && game.randomMode) {
            if (quests.isEmpty()) list.addView(help("No active quests match the selected categories."));
            addRandomRefresh(list);
        }
    }
    private void addQuestRows(LinearLayout list, List<Game.Quest> quests) {
        int columns = landscape() ? 2 : 1;
        for (int index = 0; index < quests.size(); index += columns) {
            LinearLayout line = row();
            for (int col = 0; col < columns; col++) {
                View card = index + col < quests.size() ? questCard(quests.get(index + col)) : new View(this);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ui.dp(48), 1);
                p.setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4)); line.addView(card, p);
            }
            list.addView(line);
        }
    }
    private View categoryRow(Game.Category c) {
        FrameLayout line = new FrameLayout(this); line.setLayoutParams(new LinearLayout.LayoutParams(-1,ui.dp(32))); line.setMinimumHeight(ui.dp(32));
        Ui palette = new Ui(this, c.hue >= 0 ? c.hue : game.hue);
        boolean active = game.categoryActive(c);
        String title = game.categoryTitle(c);
        TextView name = text((c.collapsed && !questRearrange || draggingCategory ? "▸  " : "▾  ") + title, 13, active ? Ui.GOLD : palette.border);
        name.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        if (active) {
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD); line.setBackgroundColor((palette.border & 0xffffff) | 0x30000000);
            View stripe = new View(this); stripe.setBackgroundColor(Ui.GOLD); line.addView(stripe, new FrameLayout.LayoutParams(ui.dp(3), -1, Gravity.START));
        }
        name.setGravity(landscape() ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        name.setPadding(ui.dp(landscape() ? 36 : 8), 0, ui.dp(rearranging() ? 108 : 36), 0);
        name.setContentDescription("Category: " + c.name + (c.collapsed && !questRearrange || draggingCategory ? ", collapsed" : ", expanded"));
        line.addView(name, new FrameLayout.LayoutParams(-1, ui.dp(32)));
        name.setOnClickListener(v -> { if (!rearranging()) { c.collapsed = !c.collapsed; store.save(); render(); } });
        TextView plus = text("+", 20, active ? Ui.GOLD : palette.border); plus.setGravity(Gravity.CENTER); plus.setContentDescription("Add quest to " + c.name);
        line.addView(plus, new FrameLayout.LayoutParams(ui.dp(36), ui.dp(32), Gravity.END));
        plus.setOnClickListener(v -> createQuestIn(c.id));
        name.setOnLongClickListener(v -> { if (rearranging()) beginBoardDrag(line, c); else manageCategory(c); return true; });
        if (rearranging()) {
            LinearLayout actions = new LinearLayout(this);
            for (String action : new String[]{"edit", "trash"}) {
                Button control = button("", palette.border);
                Ui.Icon icon = new Ui.Icon(action, palette.border); icon.setBounds(0, 0, ui.dp(20), ui.dp(20));
                control.setCompoundDrawables(icon, null, null, null); control.setMinHeight(0); control.setMinimumHeight(0); control.setPadding(ui.dp(6), 0, ui.dp(6), 0);
                control.setContentDescription((action.equals("edit") ? "Edit category: " : "Delete category: ") + c.name);
                control.setOnClickListener(v -> { if (action.equals("edit")) editCategory(c); else deleteCategory(c); });
                actions.addView(control, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
            }
            FrameLayout.LayoutParams controls = new FrameLayout.LayoutParams(ui.dp(64), ui.dp(32), Gravity.END); controls.rightMargin = ui.dp(40); line.addView(actions, controls);
        }
        if (c.id.equals(boardDragId)) line.setAlpha(.3f);
        categoryViews.put(c.id, line); return line;
    }
    private void enableQuestReorder(LinearLayout list) {
        frame.setOnDragListener((target, event) -> {
            Object moving = event.getLocalState();
            if (!(moving instanceof Game.Quest) && !(moving instanceof Game.Category) && !(moving instanceof Game.Fine)) return false;
            if (event.getAction() == android.view.DragEvent.ACTION_DRAG_LOCATION) {
                int[] position = new int[2]; target.getLocationOnScreen(position); updateBoardDragScroll(position[1] + event.getY());
            } else if (event.getAction() == android.view.DragEvent.ACTION_DRAG_EXITED || event.getAction() == android.view.DragEvent.ACTION_DRAG_ENDED) stopBoardDragScroll();
            return event.getAction() != android.view.DragEvent.ACTION_DROP;
        });
        list.setOnDragListener((target, event) -> {
            Object moving = event.getLocalState();
            if (!(moving instanceof Game.Quest) && !(moving instanceof Game.Category) && !(moving instanceof Game.Fine)) return false;
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED: return true;
                case android.view.DragEvent.ACTION_DRAG_LOCATION:
                    float x = event.getX(), y = event.getY();
                    if (Float.isNaN(dragOriginX)) { dragOriginX = x; dragOriginY = y; }
                    int[] origin = new int[2]; list.getLocationOnScreen(origin);
                    String nearest = null; double distance = Double.MAX_VALUE;
                    java.util.Map<String, View> targets = new java.util.HashMap<>(categoryViews);
                    if (moving instanceof Game.Quest && ungroupedDrop != null) targets.put("ungrouped-end", ungroupedDrop);
                    if (moving instanceof Game.Quest || moving instanceof Game.Fine) targets.putAll(questViews);
                    else for (Game.Quest q : boardQuestsForMode()) if (!game.standaloneRequired(q) && q.categoryId.isEmpty() && questViews.containsKey(q.id)) targets.put(q.id, questViews.get(q.id));
                    for (java.util.Map.Entry<String, View> entry : targets.entrySet()) {
                        if (entry.getKey().equals(boardDragId)) continue;
                        View card = entry.getValue(); int[] position = new int[2]; card.getLocationOnScreen(position);
                        float cx = position[0] - origin[0] - card.getTranslationX() + card.getWidth() / 2f;
                        float cy = position[1] - origin[1] - card.getTranslationY() + card.getHeight() / 2f;
                        if (Math.abs(x - cx) > card.getWidth() / 2f || Math.abs(y - cy) > card.getHeight() / 2f) continue;
                        double d = Math.hypot(x - cx, y - cy);
                        if (d < distance) { distance = d; nearest = entry.getKey(); }
                    }
                    if (originalDragSlot.contains(x, y)) nearest = "original-slot";
                    dropDestination = nearest;
                    if (nearest != null && Math.hypot(x - dragOriginX, y - dragOriginY) > ui.dp(10) && !nearest.equals(lastDropTarget) && !boardMovePending) {
                        lastDropTarget = nearest; boardMovePending = true; String destination = nearest;
                        list.post(() -> {
                            if (boardDragId == null) { boardMovePending = false; return; }
                            java.util.Map<String, float[]> positions = boardPositions(list);
                            applyBoardMove(moving, destination);
                            fillBoardGrid(list, boardQuestsForMode()); animateBoardLayout(list, positions);
                        });
                    }
                    updateBoardDragScroll(origin[1] + y);
                    return true;
                case android.view.DragEvent.ACTION_DROP:
                    String finalDestination = dropDestination;
                    list.post(() -> {
                        if (finalDestination != null && !finalDestination.equals(lastDropTarget)) {
                            applyBoardMove(moving, finalDestination); lastDropTarget = finalDestination;
                        }
                    }); return true;
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    stopBoardDragScroll();
                    boolean accepted = event.getResult();
                    list.post(() -> { if (!accepted && dragArrangement != null) dragArrangement.restore(game); boardDragId = null; dragArrangement = null; draggingCategory = false; boardMovePending = false; lastDropTarget = null; store.save(); render(); }); return true;
                default: return true;
            }
        });
    }
    private void updateBoardDragScroll(float screenY) {
        if (boardScroll == null || boardDragId == null) return;
        int[] position = new int[2]; boardScroll.getLocationOnScreen(position);
        float density = getResources().getDisplayMetrics().density;
        boardDragScrollSpeed = DragScroll.speed((screenY - position[1]) / density, boardScroll.getHeight() / density) * density;
        if (boardDragScrollSpeed == 0) { stopBoardDragScroll(); return; }
        if (!boardDragScrollRunning) {
            boardDragScrollRunning = true; lastBoardDragScrollAt = android.os.SystemClock.uptimeMillis(); effects.post(boardDragScroll);
        }
    }
    private void stopBoardDragScroll() {
        effects.removeCallbacks(boardDragScroll); boardDragScrollRunning = false; boardDragScrollSpeed = 0; boardDragScrollRemainder = 0;
    }
    private void applyBoardMove(Object moving, String destination) {
        if (destination.equals("original-slot")) { if (dragArrangement != null) dragArrangement.restore(game); return; }
        if (moving instanceof Game.Fine) game.moveLaw(((Game.Fine) moving).id, destination);
        else if (moving instanceof Game.Category) game.moveBoardItem(((Game.Category) moving).id, destination);
        else {
            Game.Quest quest = (Game.Quest) moving; Game.Category c = game.category(destination);
            if (c != null) game.moveToCategoryEnd(quest, c.id);
            else if (destination.equals("ungrouped-end")) game.moveToCategoryEnd(quest, "");
            else for (Game.Quest other : game.quests) if (other.id.equals(destination)) {
                game.assignCategory(quest, other.categoryId); game.moveQuest(quest.id, other.id);
                if (other.categoryId.isEmpty()) game.moveBoardItem(quest.id, other.id);
                break;
            }
        }
    }
    private java.util.Map<String, float[]> boardPositions(View list) {
        java.util.Map<String, float[]> positions = new java.util.HashMap<>();
        java.util.Map<String, View> views = new java.util.HashMap<>(questViews); views.putAll(categoryViews);
        int[] origin = new int[2]; list.getLocationOnScreen(origin);
        for (java.util.Map.Entry<String, View> entry : views.entrySet()) {
            int[] at = new int[2]; entry.getValue().getLocationOnScreen(at);
            positions.put(entry.getKey(), new float[]{at[0] - origin[0], at[1] - origin[1]});
        }
        return positions;
    }
    private void animateBoardLayout(LinearLayout list, java.util.Map<String, float[]> before) {
        list.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                list.getViewTreeObserver().removeOnPreDrawListener(this);
                java.util.Map<String, float[]> after = boardPositions(list);
                java.util.Map<String, View> views = new java.util.HashMap<>(questViews); views.putAll(categoryViews);
                if (Feedback.motion(MainActivity.this)) for (java.util.Map.Entry<String, View> entry : views.entrySet()) {
                    float[] a = before.get(entry.getKey()), b = after.get(entry.getKey());
                    if (a == null || b == null || entry.getKey().equals(boardDragId)) continue;
                    View card = entry.getValue(); card.setTranslationX(a[0] - b[0]); card.setTranslationY(a[1] - b[1]);
                    card.animate().translationX(0).translationY(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                }
                list.postDelayed(() -> boardMovePending = false, 140); return true;
            }
        });
    }
    private boolean questsScheduledLater() { return game.quests.stream().anyMatch(q -> q.firstDay > game.day || q.forever); }
    private void buildTimelines(FrameLayout body) {
        ScrollView scroll = new ScrollView(this); LinearLayout rows = column(); rows.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4));
        if (landscape()) { TextView title = text("Timeline", 20, ui.text); title.setGravity(Gravity.CENTER); rows.addView(title, fieldParams()); }
        if (analyticsTab() != 0) { buildStatistics(rows); scroll.addView(rows); body.addView(scroll, new FrameLayout.LayoutParams(-1, -1)); return; }
        String kind = tab == 1 ? Game.FINE_HISTORY : Game.QUEST_HISTORY;
        TimelineRange.Window window=TimelineRange.window(game,kind,System.currentTimeMillis(),ZoneId.systemDefault());
        LinearLayout presets = row(); String[] presetNames = {"Today", "Week", "Month", "3 Months", "All Time"};
        int selectedPreset = tab == 1 ? lawTimelinePreset : questTimelinePreset;
        for (int i = 0; i < presetNames.length; i++) {
            final int index = i; Button preset = button(presetNames[i], selectedPreset == i ? Ui.GOLD : ui.border);
            preset.setTextSize(11); preset.setMinHeight(0); preset.setMinimumHeight(0); preset.setPadding(0, 0, 0, 0);
            preset.setSelected(selectedPreset == i); preset.setContentDescription("Timeline range: " + presetNames[i]);
            presets.addView(preset, new LinearLayout.LayoutParams(0, ui.dp(36), 1));
            preset.setOnClickListener(v -> {
                long firstDay = Statistics.rangeStart(game.day, index);
                long start = index == 4 ? 0 : java.time.LocalDate.ofEpochDay(firstDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (kind.equals(Game.QUEST_HISTORY)) { game.questTimelineStart = start; game.questTimelineEnd = 0; questTimelinePreset = index; }
                else { game.lawTimelineStart = start; game.lawTimelineEnd = 0; lawTimelinePreset = index; }
                store.save(); render();
            });
        }
        rows.addView(presets, new LinearLayout.LayoutParams(-1, -2));
        BalanceRangeSlider range=new BalanceRangeSlider(this,ui,"Timeline date range",window.minimum,window.maximum,window.start,window.end,(start,end,finished)->{
            questTimelinePreset = -1; lawTimelinePreset = -1; for (int i = 0; i < presets.getChildCount(); i++) { Button preset = (Button) presets.getChildAt(i); preset.setSelected(false); preset.setTextColor(ui.border); preset.setBackground(ui.button(ui.border)); ((BorderedButton) preset).setBorderColor(ui.border); }
            if(kind.equals(Game.QUEST_HISTORY)){game.questTimelineStart=start;game.questTimelineEnd=end;}else{game.lawTimelineStart=start;game.lawTimelineEnd=end;}
            TimelineRange.Window selected=TimelineRange.window(game,kind,System.currentTimeMillis(),ZoneId.systemDefault());for(TimelineView view:timelineViews)view.refreshRange(selected);if(finished){store.save();renderedRevision=store.revision;}
        });
        rows.addView(range,new LinearLayout.LayoutParams(-1,ui.dp(60)));
        java.util.Map<String, String> entities = new java.util.LinkedHashMap<>();
        if (tab == 0) for (Game.Quest q : game.activeRepeatableQuests()) entities.put(q.id, q.name);
        else for (Game.Fine f : game.fines) entities.put(f.id, f.name);
        if (entities.isEmpty()) rows.addView(help(tab == 1 ? "No laws yet." : "No repeatable quests today."));
        addGroupedAnalytics(rows, new ArrayList<>(entities.keySet()), (group, id) -> addTimeline(group, kind, id, entities.get(id), true));
        scroll.addView(rows); body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }
    private void addTimeline(LinearLayout content, String kind, String id, String name) {
        addTimeline(content, kind, id, name, false);
    }
    private void addTimeline(LinearLayout content, String kind, String id, String name, boolean showName) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, ui.dp(48)); p.setMargins(0, ui.dp(12), 0, ui.dp(12));
        Ui palette = ui;
        if (kind.equals(Game.QUEST_HISTORY)) for (Game.Quest q : game.quests) if (q.id.equals(id)) { palette = questPalette(q); break; }
        TimelineView view=new TimelineView(this,game,palette,kind,id,name,showName);timelineViews.add(view);content.addView(view,p);
    }
    private View questCard(Game.Quest quest) {
        boolean required = game.isRequired(quest);
        String description = (quest.repeatable ? "REPEATABLE" : "ONCE TODAY") + (required ? "  /  MUST FINISH" : "  /  OPTIONAL");
        Ui palette = questPalette(quest);
        LinearLayout card = card(quest.name, "", required ? "clock" : "bulletin", palette);
        int completions=quest.done(game.day)?quest.completions:0;
        int outline=completions>0?CompletionVisuals.completedOutline(palette.border,completions):required?Ui.GOLD:palette.border;
        card.setBackground(palette.questButton(outline,required,completions));
        if (required) {
            LinearLayout copy = (LinearLayout) card.getChildAt(1);
            ((TextView) copy.getChildAt(0)).setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView badge = text("MUST DO", 9, Ui.GOLD); badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout status=row();badge.setPadding(0,ui.dp(3),0,0);status.addView(badge);
            TextView fine=text("Fine if unfinished",9,Ui.RED);fine.setPadding(ui.dp(6),ui.dp(3),0,0);fine.setSingleLine(true);status.addView(fine);copy.addView(status);
        }
        setEmojiIcon(card, quest.emoji, palette);
        card.removeViewAt(card.getChildCount() - 1);
        if (rearranging()) {
            Button edit = button("", palette.border); Ui.Icon pencil = new Ui.Icon("edit", palette.border); pencil.setBounds(0, 0, ui.dp(24), ui.dp(24));
            edit.setCompoundDrawables(pencil, null, null, null); edit.setMinHeight(0); edit.setMinimumHeight(0); edit.setPadding(ui.dp(4), 0, ui.dp(4), 0);
            edit.setContentDescription("Edit quest: " + quest.name);
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(ui.dp(32), ui.dp(36)); editParams.rightMargin = ui.dp(4); card.addView(edit, editParams);
            edit.setOnClickListener(v -> editQuest(quest));
            Button trash = button("", palette.border); Ui.Icon icon = new Ui.Icon("trash", palette.border); icon.setBounds(0, 0, ui.dp(24), ui.dp(24));
            trash.setCompoundDrawables(icon, null, null, null); trash.setMinHeight(0); trash.setMinimumHeight(0); trash.setPadding(ui.dp(4), 0, ui.dp(4), 0);
            trash.setContentDescription("Delete quest: " + quest.name); card.addView(trash, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(36)));
            trash.setOnClickListener(v -> deleteEntry(quest, null));
        } else {
        LinearLayout terms = column(); terms.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (game.isTimed(quest)) {
            TextView remaining = text(TimerService.format(game.remainingFor(quest, System.currentTimeMillis())), 12, palette.border);
            remaining.setTypeface(Typeface.MONOSPACE); remaining.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            terms.addView(remaining, new LinearLayout.LayoutParams(-2, -2)); remainingLabels.put(quest.id, remaining);
        } else {
            terms.addView(palette.icon("exclamation", palette.border, 18));
        }
        TextView bounty = text("+" + Money.format(game.bountyFor(quest, System.currentTimeMillis())), 11, Ui.GREEN);
        bounty.setPadding(0, ui.dp(3), 0, 0); terms.addView(bounty, new LinearLayout.LayoutParams(-2, -2));
        bountyLabels.put(quest.id, bounty);
        card.addView(terms, new LinearLayout.LayoutParams(-2, -1));
        }
        if (rearranging()) immediateDrag(addDragGrip(card), card, quest);
        card.setContentDescription(quest.name + ". " + description + ". Hold and drag to reorder. Swipe left to edit, right to delete");
        questViews.put(quest.id, card);
        if (quest.id.equals(boardDragId)) card.setVisibility(View.INVISIBLE);
        if (rearranging()) {
            card.setAlpha(game.questActiveToday(quest) ? 1f : .5f);
            card.setOnClickListener(v -> {});
            card.setOnLongClickListener(v -> beginBoardDrag(card, quest));
        } else {
            addSwipe(card, () -> deleteEntry(quest, null), () -> editQuest(quest));
            card.setOnLongClickListener(v -> { enterBoardRearrange(); return true; });
            card.setOnClickListener(v -> {
                if (!quest.repeatable && quest.done(game.day)) { manageEntry(quest, null); return; }
                showQuest(quest);
            });
        }
        card.setContentDescription(quest.name + ". " + description + (rearranging() ? ". Drag to rearrange" + (game.questActiveToday(quest) ? "" : ". Inactive today") : ". Long press to rearrange. Swipe left to edit, right to delete"));
        return card;
    }
    private View fineCard(Game.Fine fine) {
        String detail = rearranging() ? "DRAG TO REARRANGE" : fine.mode == Game.AMOUNT ? "DAILY ALLOWANCE" : fine.mode == Game.TIMED ? fine.remaining(System.currentTimeMillis()) == 0 ? "LIMIT REACHED" : fine.endsAt > 0 ? "TAP TO PAUSE" : "TAP TO START" : "TAP TO REPORT";
        LinearLayout card = card(fine.name, detail, "fine");
        card.removeViewAt(card.getChildCount() - 1);
        if (fine.mode != Game.STRICT) {
            TextView terms = text(fine.mode == Game.AMOUNT ? fine.amountUsed + "/" + fine.amountLimit : TimerService.format(fine.remaining(System.currentTimeMillis())), 13, ui.border);
            terms.setGravity(Gravity.CENTER); terms.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            card.addView(terms, new LinearLayout.LayoutParams(-2, -1)); lawTimers.put(fine.id, terms);
        }
        setEmojiIcon(card, fine.emoji, ui); questViews.put(fine.id, card);
        if (rearranging()) immediateDrag(addDragGrip(card), card, fine);
        if (fine.id.equals(boardDragId)) card.setVisibility(View.INVISIBLE);
        card.setContentDescription(fine.name + (rearranging() ? ". Drag to rearrange" : fine.mode == Game.AMOUNT ? ". Amount allowance. Tap to increase. Long press to rearrange" : fine.mode == Game.TIMED ? ". Timed allowance. Tap to start or pause. Long press to rearrange" : ". Tap to report. Long press to rearrange"));
        if (rearranging()) {
            card.setOnClickListener(v -> {});
            card.setOnLongClickListener(v -> beginBoardDrag(card, fine));
        } else {
            addSwipe(card, () -> deleteEntry(null, fine), () -> editFine(fine));
            card.setOnLongClickListener(v -> { enterBoardRearrange(); return true; });
            card.setOnClickListener(v -> activateLaw(fine));
        }
        return card;
    }
    private View addDragGrip(LinearLayout card) {
        Ui palette = tab == 3 ? ui : new Ui(this, game.hue);
        View grip = new View(this) {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas); paint.setColor(palette.border);
                paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(1);
                canvas.drawRect(.5f, .5f, getWidth() - .5f, getHeight() - .5f, paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);
                for (int row = 0; row < 3; row++) for (int col = 0; col < 2; col++)
                    canvas.drawCircle(getWidth() * (col == 0 ? .33f : .67f), getHeight() / 2f + palette.dp((row - 1) * 7), palette.dp(1.2f), paint);
            }
        };
        grip.setContentDescription("Drag to rearrange"); grip.setOnClickListener(v -> {});
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ui.dp(16), ui.dp(tab == 1 ? 52 : 36)); p.rightMargin = ui.dp(6);
        card.addView(grip, 0, p);
        return grip;
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility") // Accessible click uses the same drag action.
    private void immediateDrag(View touchTarget, View shadow, Object entry) {
        touchTarget.setOnClickListener(v -> beginBoardDrag(shadow, entry));
        touchTarget.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) return beginBoardDrag(shadow, entry);
            return true;
        });
    }
    private boolean beginBoardDrag(View card, Object entry) {
        if (boardDragId != null || !rearranging()) return false;
        dragArrangement = new BoardArrangement(game);
        int[] start = new int[2], origin = new int[2]; card.getLocationOnScreen(start);
        ((View) boardScroll.getChildAt(0)).getLocationOnScreen(origin);
        originalDragSlot.set(start[0] - origin[0], start[1] - origin[1], start[0] - origin[0] + card.getWidth(), start[1] - origin[1] + card.getHeight());
        boardDragId = entry instanceof Game.Quest ? ((Game.Quest) entry).id : entry instanceof Game.Fine ? ((Game.Fine) entry).id : ((Game.Category) entry).id;
        draggingCategory = entry instanceof Game.Category; lastDropTarget = null; dropDestination = null; dragOriginX = Float.NaN;
        android.content.ClipData clip = android.content.ClipData.newPlainText("board entry", boardDragId);
        if (!card.startDragAndDrop(clip, new View.DragShadowBuilder(card), entry, 0)) { boardDragId = null; draggingCategory = false; return false; }
        if (draggingCategory) card.post(() -> {
            if (boardScroll != null && draggingCategory) fillBoardGrid((LinearLayout) boardScroll.getChildAt(0), boardQuestsForMode());
        });
        else card.setVisibility(View.INVISIBLE);
        return true;
    }
    private LinearLayout card(String title, String detail, String icon) {
        return card(title, detail, icon, ui);
    }
    private Ui questPalette(Game.Quest quest) {
        return new Ui(this, game.questHue(quest));
    }
    private void withUi(Ui palette, Runnable build) {
        Ui previous = ui; ui = palette;
        try { build.run(); } finally { ui = previous; }
    }
    private void setEmojiIcon(LinearLayout card, String emoji, Ui palette) {
        if (emoji == null || emoji.isEmpty()) return;
        card.removeViewAt(0);
        TextView icon = text(emoji, 22, palette.text); icon.setGravity(Gravity.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(icon, 0, new LinearLayout.LayoutParams(ui.dp(30), ui.dp(36)));
    }
    private LinearLayout card(String title, String detail, String icon, Ui palette) {
        LinearLayout card = row(); card.setBackground(palette.button(palette.border)); card.setPadding(ui.dp(10), 0, ui.dp(10), 0);
        card.setFocusable(true);
        card.addView(palette.icon(icon, palette.border, 30));
        LinearLayout copy = column(); copy.setGravity(Gravity.CENTER_VERTICAL); copy.setPadding(ui.dp(10), 0, ui.dp(6), 0);
        TextView name = text(title, 16, palette.text); name.setSingleLine(); name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);
        if (!detail.isEmpty()) {
            TextView sub = text(detail, 9, palette.muted); sub.setSingleLine(); sub.setEllipsize(TextUtils.TruncateAt.END); sub.setPadding(0, ui.dp(5), 0, 0); copy.addView(sub);
        }
        card.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        card.addView(palette.icon("arrow", palette.border, 22)); return card;
    }
    private void buildTimer(FrameLayout body) {
        LinearLayout center = column(); center.setGravity(Gravity.CENTER); center.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        Game.Session session = game.session;
        if (session.phase == Game.QUEST && !session.timed) {
            TextView name = text(session.questName, 22, Ui.GREEN); name.setGravity(Gravity.CENTER);
            name.setPadding(0, 0, 0, ui.dp(16)); center.addView(name);
            Button complete = button("Complete quest", Ui.GREEN);
            complete.setOnClickListener(v -> { complete.setEnabled(false); store.completeManual(); ensureTimer(); render(); });
            center.addView(complete, new LinearLayout.LayoutParams(-1, ui.dp(64)));
            addCancelQuest(center);
            body.addView(center, new FrameLayout.LayoutParams(-1, -1)); return;
        }
        timerValue = text("", 22, session.phase == Game.QUEST ? Ui.GREEN : ui.border);
        timerValue.setGravity(Gravity.CENTER); timerValue.setTypeface(Typeface.MONOSPACE);
        timerValue.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        timerValue.setFontFeatureSettings("tnum");
        timerValue.setBackground(ui.panel(session.phase == Game.QUEST ? Ui.GREEN : ui.border));
        if (session.phase == Game.REST) timerValue.setTextColor(Ui.GOLD);
        center.addView(timerValue, new LinearLayout.LayoutParams(-1, ui.dp(session.phase == Game.REST ? 112 : 64)));
        if (session.phase == Game.QUEST) addCancelQuest(center);
        body.addView(center, new FrameLayout.LayoutParams(-1, -1)); updateTimer();
    }
    private void addCancelQuest(LinearLayout center) {
        LinearLayout actions = row(); actions.setGravity(Gravity.CENTER);
        Button cancel = button("Cancel Quest", Ui.RED), pause = button("Pause", ui.border);
        for (Button control : new Button[]{cancel, pause}) {
            control.setTextSize(12); control.setMinHeight(0); control.setMinimumHeight(0);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ui.dp(120), ui.dp(32));
            p.setMargins(ui.dp(4), 0, ui.dp(4), 0); actions.addView(control, p);
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, ui.dp(32)); p.topMargin = ui.dp(8); center.addView(actions, p);
        cancel.setOnClickListener(v -> {
            store.sync();
            if (game.session != null && game.session.phase == Game.QUEST) {
                for (Game.Quest q : game.quests) if (q.id.equals(game.session.questId)) { cancelQuestTimer(q); break; }
            }
            render();
        });
        pause.setOnClickListener(v -> {
            store.sync();
            if (game.pauseQuest(System.currentTimeMillis())) {
                saveTimerAction(); store.notices.add(new GameStore.Notice("Quest paused. Your progress is saved.", 0));
                pause.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            }
            render();
        });
    }
    private void saveTimerAction() {
        store.save();
        if (game.hasRunningTimers()) ensureTimer(); else stopService(new Intent(this, TimerService.class));
    }
    private void cancelQuestTimer(Game.Quest q) {
        if (game.cancelQuest(q, System.currentTimeMillis())) {
            saveTimerAction(); store.notices.add(new GameStore.Notice("Quest canceled.", 0));
        }
    }
    private void updateTimer() {
        for (Game.Fine law : game.fines) { TextView value = lawTimers.get(law.id); if (value != null) changedText(value, law.mode == Game.TIMED ? TimerService.format(law.remaining(System.currentTimeMillis())) : law.amountUsed + "/" + law.amountLimit); }
        startLawImpacts();
        for (Game.Quest q : game.quests) {
            TextView label = remainingLabels.get(q.id);
            if (label != null) changedText(label, TimerService.format(game.remainingFor(q, System.currentTimeMillis())));
            TextView bounty = bountyLabels.get(q.id);
            if (bounty != null) changedText(bounty, "+" + Money.format(game.bountyFor(q, System.currentTimeMillis())));
        }
        if (timerValue != null && game.session != null) changedText(timerValue, (game.session.phase == Game.QUEST ? game.session.questName + "\n" : "Take a rest.\nThink about the good you just did.\n") + TimerService.format(game.session.endsAt - System.currentTimeMillis()));
        if (focusValue != null) {
            long seconds = game.focusMillisToday(System.currentTimeMillis()) / 1000;
            changedText(focusValue, activeTimeRevealed?String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60):"Tap to show");
            focusValue.setContentDescription("Time spent on timed quests today: " + focusValue.getText());
        }
    }
    private void addEnergyBar() {
        LinearLayout bar = row();
        String[] labels = {"Extreme", "Extra", "Normal", "Relax", "Minimal", "Dead"};
        String[] symbols = {"flame", "bolt", "sun", "coffee", "leaf", "skull"};
        int[] modes = {Game.EXTREME, Game.EXTRA, Game.NORMAL, Game.RELAX, Game.MINIMAL, Game.DEAD};
        for (int i = 0; i < labels.length; i++) {
            final int energy = modes[i]; boolean selected = game.energy == energy;
            FrameLayout option = new FrameLayout(this); option.setFocusable(true); option.setSelected(selected);
            option.setContentDescription("Energy: " + labels[i]); option.setBackground(ui.button(ui.border));
            if (selected) {
                android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
                fill.setColor(ui.border); option.setBackground(fill);
            }
            int color = selected ? ui.background : ui.border;
            if (landscape()) {
                TextView label = text(labels[i], 12, color); label.setGravity(Gravity.CENTER);
                option.addView(label, new FrameLayout.LayoutParams(-1, -1));
            } else option.addView(ui.icon(symbols[i], color, 24), new FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER));
            bar.addView(option, new LinearLayout.LayoutParams(0, ui.dp(28), 1));
            option.setOnClickListener(v -> {
                store.sync();
                if (game.energy != energy) { game.energy = energy; store.save(); render(); }
                if (!game.energyHintShown && !energyHintPending) {
                    energyHintPending = true;
                    effects.postDelayed(new Runnable() {
                        @Override public void run() {
                            if (game.energyHintShown) { energyHintPending = false; return; }
                            if (!resumed || dialog != null && dialog.isShowing()) { effects.postDelayed(this, 200); return; }
                            game.energyHintShown = true; energyHintPending = false; store.save(); renderedRevision = store.revision;
                            showInformation("You just set your energy level. This changes the duration of your next quest.\nThese quest and rest durations are based on the Pomodoro Principles.");
                        }
                    }, 400);
                }
            });
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }
    private void cancelLawImpacts() {
        for (android.animation.ValueAnimator animator : new ArrayList<>(lawImpacts.values())) animator.cancel();
        lawImpacts.clear();
    }
    private void startLawImpacts() {
        long now = android.os.SystemClock.uptimeMillis();
        store.lawWarningStarts.entrySet().removeIf(entry -> now - entry.getValue() >= store.sounds.warningDurationMillis);
        if (!Feedback.motion(this)) return;
        for (java.util.Map.Entry<String, Long> entry : store.lawWarningStarts.entrySet()) {
            String id = entry.getKey(); TextView value = lawTimers.get(id); long start = entry.getValue();
            if (value == null || value.getVisibility() != View.VISIBLE || value.getWidth() == 0 || lawImpacts.containsKey(id)) continue;
            long left = store.sounds.warningDurationMillis - (now - start); if (left <= 0) continue;
            value.setPivotX(value.getWidth() / 2f); value.setPivotY(value.getHeight() / 2f);
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0, 1);
            animator.setDuration(left); animator.setInterpolator(new android.view.animation.LinearInterpolator());
            Runnable update = () -> {
                long elapsed = android.os.SystemClock.uptimeMillis() - start;
                float force = Synth.warningEnvelope((double) elapsed * Synth.WARNING_MILLIS / store.sounds.warningDurationMillis);
                float scale = 1 + .28f * force;
                value.setScaleX(scale); value.setScaleY(scale);
                value.setTranslationX((float) Math.sin(elapsed * .14) * ui.dp(5) * force);
                value.setTranslationY((float) Math.sin(elapsed * .21) * ui.dp(2) * force);
            };
            animator.addUpdateListener(a -> update.run());
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    value.setScaleX(1); value.setScaleY(1); value.setTranslationX(0); value.setTranslationY(0);
                    if (lawImpacts.get(id) == animator) lawImpacts.remove(id);
                }
            });
            lawImpacts.put(id, animator); update.run(); animator.start();
        }
    }
    private boolean analyticsPage() { return tab == 0 && questTimelines && game.questControlsUnlocked() || tab == 1 && fineTimelines && game.lawControlsUnlocked(); }
    private int analyticsTab() { return tab == 1 ? lawAnalyticsTab : questAnalyticsTab; }
    private void addAnalyticsBar() {
        LinearLayout bar = row(); String[] names = {"Timeline", "Pie chart", "Heatmap"};
        for (int i = 0; i < names.length; i++) {
            final int index = i; Button option = button(names[i], analyticsTab() == i ? Ui.GOLD : ui.border);
            option.setTextSize(11); option.setMinHeight(0); option.setMinimumHeight(0); option.setPadding(0, 0, 0, 0);
            option.setSelected(analyticsTab() == i); option.setContentDescription("Statistics: " + names[i]);
            bar.addView(option, new LinearLayout.LayoutParams(0, ui.dp(28), 1));
            option.setOnClickListener(v -> { if (tab == 1) lawAnalyticsTab = index; else questAnalyticsTab = index; boardScrollY = 0; render(); });
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }
    private void buildStatistics(LinearLayout rows) {
        int selectedRange = tab == 1 ? lawPieRange : questPieRange;
        if (analyticsTab() == 1) {
            LinearLayout ranges = row(); String[] names = {"Today", "Week", "Month", "3 Months", "All Time"};
            for (int i = 0; i < names.length; i++) {
                final int index = i; Button range = button(names[i], selectedRange == i ? Ui.GOLD : ui.border);
                range.setTextSize(11); range.setMinHeight(0); range.setMinimumHeight(0); range.setPadding(0, 0, 0, 0);
                range.setSelected(selectedRange == i); range.setContentDescription("Chart range: " + names[i]);
                ranges.addView(range, new LinearLayout.LayoutParams(0, ui.dp(36), 1));
                range.setOnClickListener(v -> { if (tab == 1) lawPieRange = index; else questPieRange = index; boardScrollY = 0; render(); });
            }
            rows.addView(ranges, new LinearLayout.LayoutParams(-1, -2));
        }
        Statistics data = new Statistics(game, tab == 1, System.currentTimeMillis(), ZoneId.systemDefault(),
            analyticsTab() == 1 ? Statistics.rangeStart(game.day, selectedRange) : Long.MIN_VALUE);
        if (tab == 0) rows.addView(help("Quest time recorded since " + java.time.Instant.ofEpochMilli(game.analyticsSince).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))));
        if (analyticsTab() == 1) {
            rows.addView(new ActivityChart(this, data, ui), new LinearLayout.LayoutParams(-1, ui.dp(240)));
            if (data.slices.isEmpty()) { rows.addView(help(data.laws ? "No laws reported yet." : "No recorded time for completed quests yet.")); return; }
            long largest = 1; for (Statistics.Series series : data.slices) largest = Math.max(largest, series.value);
            for (int i = 0; i < data.slices.size(); i++) {
                Statistics.Series series = data.slices.get(i); int color = ActivityChart.color(series, i, data.laws);
                LinearLayout group = column(); group.setPadding(ui.dp(6), ui.dp(8), ui.dp(6), ui.dp(8));
                LinearLayout caption = row(); TextView name = text(series.name, 12, ui.text); name.setMaxLines(2);
                caption.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                TextView value = text(Statistics.amount(series.value, data.laws) + " · " + String.format(Locale.getDefault(), "%.1f%%", 100d * series.value / Math.max(1, data.total)), 11, color);
                value.setPadding(ui.dp(8), 0, 0, 0); caption.addView(value); group.addView(caption);
                FrameLayout track = new FrameLayout(this); track.setBackgroundColor((color & 0xffffff) | 0x22000000);
                View fill = new View(this); fill.setBackgroundColor(color); final double ratio = (double) series.value / largest;
                track.addView(fill, new FrameLayout.LayoutParams(1, -1));
                track.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { ViewGroup.LayoutParams p = fill.getLayoutParams(); int width = Math.max(1, (int) ((r - l) * ratio)); if (p.width != width) { p.width = width; fill.setLayoutParams(p); } });
                LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, ui.dp(12)); bar.topMargin = ui.dp(5); group.addView(track, bar);
                group.setContentDescription(series.name + ": " + Statistics.amount(series.value, data.laws)); rows.addView(group);
            }
        } else {
            rows.addView(help("Each column is a week, each square is a day. Days light up when you do the thing on those days, and the intensity of the squares color shows how much."));
            if (data.heatSeries.isEmpty()) rows.addView(help(data.laws ? "No laws yet." : "No active multi-use quests today."));
            java.util.Map<String, Statistics.Series> series = new java.util.LinkedHashMap<>();
            for (Statistics.Series item : data.heatSeries) series.put(item.id, item);
            addGroupedAnalytics(rows, new ArrayList<>(series.keySet()), (group, id) -> addHeatmap(group, data, series.get(id)));
        }
    }
    private void addHeatmap(LinearLayout rows, Statistics data, Statistics.Series series) {
        Ui palette = new Ui(this, series.hue); TextView name = text(series.name, 13, palette.border);
        name.setPadding(ui.dp(4), ui.dp(10), 0, 0); rows.addView(name);
        android.widget.HorizontalScrollView pan = new android.widget.HorizontalScrollView(this);
        int width = Math.max(getResources().getDisplayMetrics().widthPixels - ui.dp(32), ui.dp(600));
        pan.addView(new ActivityHeatmap(this, data, series, palette), new FrameLayout.LayoutParams(width, ui.dp(140)));
        pan.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() { if (pan.getWidth() == 0 || pan.getChildAt(0).getWidth() == 0) return true; pan.getViewTreeObserver().removeOnPreDrawListener(this); pan.scrollTo(Math.max(0, pan.getChildAt(0).getWidth() - pan.getWidth()), 0); return true; }
        });
        rows.addView(pan, new LinearLayout.LayoutParams(-1, ui.dp(140)));
    }
    private void addFooter() {
        FrameLayout footer = new FrameLayout(this);
        int controlGravity = Gravity.CENTER_VERTICAL;
        int controlBottom = 0;
        Button plus = button("+", ui.border); plus.setTextSize(22); plus.setMinHeight(0); plus.setMinimumHeight(0); plus.setPadding(0, 0, 0, 0);
        plus.setContentDescription(tab == 1 ? "Create a law" : "Create a quest");
        plus.setOnClickListener(v -> { if (tab == 1) createFine(); else if (game.questsCreated < 3) createQuest(); else showAddMenu(); });
        FrameLayout.LayoutParams add = new FrameLayout.LayoutParams(ui.dp(32), ui.dp(32), Gravity.START | controlGravity);
        add.setMargins(0, 0, 0, controlBottom); footer.addView(plus, add);
        boolean canRearrange = tab == 0 ? game.questRearrangeUnlocked() : game.lawRearrangeUnlocked();
        if (canRearrange) {
            Button edit = button("", rearranging() ? Ui.GOLD : ui.border);
            Ui.Icon icon = new Ui.Icon("rearrange", rearranging() ? Ui.GOLD : ui.border); icon.setBounds(0, 0, ui.dp(24), ui.dp(24));
            edit.setCompoundDrawables(icon, null, null, null); edit.setMinHeight(0); edit.setMinimumHeight(0); edit.setPadding(ui.dp(4), 0, ui.dp(4), 0);
            edit.setSelected(rearranging()); edit.setContentDescription(rearranging() ? "Finish rearranging" : "Rearrange " + (tab == 1 ? "laws" : "quests"));
            FrameLayout.LayoutParams editParams = new FrameLayout.LayoutParams(ui.dp(32), ui.dp(32), Gravity.START | controlGravity);
            editParams.leftMargin = ui.dp(32); footer.addView(edit, editParams);
            edit.setOnClickListener(v -> {
                if (tab == 0) { questRearrange = !questRearrange; questTimelines = false; firstRearrangeSession = questRearrange && !game.rearrangeVisited; if (questRearrange) { game.rearrangeVisited = true; store.save(); } }
                else { lawRearrange = !lawRearrange; fineTimelines = false; }
                render();
            });
        }
        boolean timelines = tab == 1 ? fineTimelines : questTimelines;
        Button view = button(timelines ? "☷" : "↔", ui.border); view.setTextSize(18); view.setMinHeight(0); view.setMinimumHeight(0); view.setPadding(0, 0, 0, 0);
        view.setContentDescription(timelines ? "Show board" : "Show timelines");
        view.setVisibility((tab == 0 ? game.questControlsUnlocked() : game.lawControlsUnlocked()) ? View.VISIBLE : View.GONE);
        if (!timelines) {
            view.setText(""); Ui.Icon icon = new Ui.Icon("timeline", ui.border); icon.setBounds(0, 0, ui.dp(24), ui.dp(24));
            view.setCompoundDrawables(icon, null, null, null); view.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        }
        view.setOnClickListener(v -> { if (tab == 1) { fineTimelines = !fineTimelines; lawRearrange = false; } else { questTimelines = !questTimelines; questRearrange = false; } render(); if ((questTimelines || fineTimelines) && !game.timelineHintShown) { game.timelineHintShown = true; store.save(); showInformation("Here you can track your progress over time."); } });
        FrameLayout.LayoutParams toggle = new FrameLayout.LayoutParams(ui.dp(32), ui.dp(32), Gravity.START | controlGravity);
        toggle.setMargins(ui.dp(canRearrange ? 64 : 32), 0, 0, controlBottom); footer.addView(view, toggle);
        LinearLayout values = row(); values.setGravity(Gravity.CENTER);
        Game.Penalty penalty = tab == 1 ? game.nextLawFine() : game.requiredPenalty();
        TextView fine = text((tab == 0 ? "Undone quests fine" : "Fine from") + "\n−" + compact(penalty.daysLost(game.days)) + " days\n" + Money.format(-penalty.coins(game.coins)), 9, Ui.RED);
        fine.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL); fine.setContentDescription(penalty.daysLost(game.days) + " days and " + Money.format(penalty.coins(game.coins)) + " penalty");
        values.addView(fine, new LinearLayout.LayoutParams(-2, -1));
        FrameLayout.LayoutParams valueParams = new FrameLayout.LayoutParams(-2, ui.dp(32), Gravity.CENTER);
        if (tab == 1 ? game.lawControlsUnlocked() : penalty.days > 0) footer.addView(values, valueParams);
        if (tab == 0) {
            LinearLayout activeTime = column(); activeTime.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            TextView activeTimeTitle = text("Active Time", 9, ui.muted); activeTimeTitle.setSingleLine();
            activeTime.addView(activeTimeTitle, new LinearLayout.LayoutParams(-2, -2));
            focusValue = text("", 9, ui.text); focusValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); focusValue.setSingleLine();focusValue.setOnClickListener(v->{activeTimeRevealed=true;updateTimer();});
            activeTime.addView(focusValue, new LinearLayout.LayoutParams(-2, -2));
            FrameLayout.LayoutParams time = new FrameLayout.LayoutParams(-2, ui.dp(32), Gravity.END | controlGravity);
            time.setMargins(0, 0, ui.dp(game.questControlsUnlocked() ? 72 : !game.deletedQuests.isEmpty() ? 40 : 8), controlBottom); footer.addView(activeTime, time);
            Button undo = button("", ui.border);
            Ui.Icon trash = new Ui.Icon("trash", ui.border); trash.setBounds(0, 0, ui.dp(24), ui.dp(24));
            undo.setCompoundDrawables(trash, null, null, null);
            undo.setMinHeight(0); undo.setMinimumHeight(0); undo.setPadding(0, 0, 0, 0);
            undo.setPadding(ui.dp(4), 0, ui.dp(4), 0);
            undo.setContentDescription("Quest trash");
            undo.setVisibility(game.questControlsUnlocked() || !game.deletedQuests.isEmpty() ? View.VISIBLE : View.GONE);
            FrameLayout.LayoutParams undoParams = new FrameLayout.LayoutParams(ui.dp(32), ui.dp(32), Gravity.END | controlGravity);
            undoParams.setMargins(0, 0, ui.dp(game.questControlsUnlocked() ? 32 : 0), controlBottom); footer.addView(undo, undoParams);
            undo.setOnClickListener(v -> showCompletedQuests());
            Button random = button("⚄", game.randomMode ? Ui.GOLD : ui.border); random.setTextSize(20);
            random.setMinHeight(0); random.setMinimumHeight(0); random.setPadding(0, 0, 0, 0);
            random.setSelected(game.randomMode); random.setContentDescription(game.randomMode ? "Show all quests" : "Show two random quests");
            random.setVisibility(game.questControlsUnlocked() ? View.VISIBLE : View.GONE);
            FrameLayout.LayoutParams randomParams = new FrameLayout.LayoutParams(ui.dp(32), ui.dp(32), Gravity.END | Gravity.CENTER_VERTICAL);
            randomParams.rightMargin = 0; footer.addView(random, randomParams);
            random.setOnClickListener(v -> {
                store.sync(); game.randomMode = !game.randomMode;
                if (game.randomMode) game.refreshRandomQuests(new java.util.Random());
                boolean explainChoices = game.randomMode && !game.choiceHintShown;
                if (explainChoices) game.choiceHintShown = true;
                store.save(); render();
                if (explainChoices) showInformation("You activated choice mode. Use this when you don't know what quest to pick.");
            });
            footer.post(() -> {
                int width = footer.getWidth();
                if (focusValue == null || activeTime.getParent() != footer) return;
                if (width < ui.dp(360)) {
                    focusValue.setTextSize(7); focusValue.setWidth(ui.dp(40));
                }
                int rightSpace = ui.dp(width < ui.dp(360) ? 128 : 148);
                int maxWidth = Math.max(ui.dp(40), width - 2 * Math.max(ui.dp(84), rightSpace));
                if (values.getWidth() > maxWidth) {
                    float size = Math.max(5, 9f * maxWidth / values.getWidth());
                    for (int i = 0; i < values.getChildCount(); i++) ((TextView) values.getChildAt(i)).setTextSize(size);
                }
            });
        }
        root.addView(footer, new LinearLayout.LayoutParams(-1, ui.dp(32)));
    }
    private void addNavigation() {
        LinearLayout bar = row(); bar.setPadding(0, 0, 0, 0);
        bar.setGravity(Gravity.TOP);
        LinearLayout counters = row(); counters.setGravity(Gravity.CENTER); counters.setBackground(ui.panel(ui.border));
        LinearLayout dayGroup = row(); dayGroup.setGravity(Gravity.CENTER);
        dayGroup.addView(ui.icon("calendar", ui.border, 21));
        daysValue = text("0", 12, ui.text); daysValue.setTypeface(Typeface.MONOSPACE); dayGroup.addView(daysValue);
        counters.addView(dayGroup, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout coinGroup = row(); coinGroup.setGravity(Gravity.CENTER);
        coinsValue = text("0.00", 12, Ui.GOLD); coinsValue.setTypeface(Typeface.MONOSPACE);
        coinsValue.setSingleLine(true); coinsValue.setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        coinGroup.addView(coinsValue, new LinearLayout.LayoutParams(0, -1, 1)); coinsValue.setGravity(Gravity.CENTER);
        coinTarget = coinGroup; counters.addView(coinGroup, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(0, ui.dp(32), 2.3f);
        bar.addView(counters, counterParams);
        String[] labels = {"Bulletin", "Law", "Tools", "Settings"};
        int[] tabs = {0, 1, 3, 2};
        String[] icons = {"bulletin", "fine", "settings"};
        for (int i = 0; i < labels.length; i++) {
            final int index = tabs[i];
            LinearLayout item = column(); item.setGravity(Gravity.CENTER);
            item.setBackground(ui.button(tab == index ? ui.text : ui.border));
            item.setFocusable(true); item.setContentDescription(labels[i]); item.setSelected(tab == index);
            TextView label = text(labels[i], 9, tab == index ? ui.text : ui.muted); label.setGravity(Gravity.CENTER); item.addView(label);
            item.setOnClickListener(v -> { if (tab != index || index == 3 && toolsPage > 0) { if (briefEditor != null) briefEditor.freezeState(); if (choiceEditor != null) choiceEditor.freezeState(); hideKeyboard(); store.sync(); questTimelines = false; fineTimelines = false; if (tab == 3 && index == 3) toolsPage = 0; activeTimeRevealed=false;tab = index; render(); } });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ui.dp(32), .65f);
            bar.addView(item, p);
        }
        LinearLayout navigation = column();
        View divider = new View(this); divider.setBackgroundColor(ui.border);
        navigation.addView(divider, new LinearLayout.LayoutParams(-1, 1)); navigation.addView(bar);
        FrameLayout reveal = new FrameLayout(this); reveal.setClipChildren(true);
        int fullHeight = ui.dp(32) + 1;
        reveal.addView(navigation, new FrameLayout.LayoutParams(-1, fullHeight));
        LinearLayout.LayoutParams revealParams = new LinearLayout.LayoutParams(-1, fullHeight);
        root.addView(reveal, revealParams);
        if (game.questsCreated < 4) reveal.setVisibility(View.GONE);
        else if (navigationRevealAt > 0) {
            long elapsed = android.os.SystemClock.uptimeMillis() - navigationRevealAt;
            if (elapsed < 350) {
                float initial = Math.max(0f, elapsed / 350f);
                revealParams.height = Math.round(fullHeight * initial); reveal.setLayoutParams(revealParams);
                navigation.setTranslationY(fullHeight * (1f - initial));
                android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(initial, 1f);
                animator.setStartDelay(Math.max(0, -elapsed)); animator.setDuration(Math.round(350 * (1f - initial)));
                animator.setInterpolator(new android.view.animation.LinearInterpolator());
                animator.addUpdateListener(value -> {
                    if (!reveal.isAttachedToWindow()) { animator.cancel(); return; }
                    float fraction = (Float) value.getAnimatedValue();
                    revealParams.height = Math.round(fullHeight * fraction); reveal.setLayoutParams(revealParams);
                    navigation.setTranslationY(fullHeight * (1f - fraction));
                });
                reveal.post(animator::start);
            }
        }
    }
    private void captureToolsScroll() {
        if (toolsScroll != null && !toolsScrollRestoring) toolsScrollPositions[toolsScrollPage] = toolsScroll.getScrollY();
    }
    private List<Game.ChoiceList> activeToolLists() { return toolsTool == 1 ? game.briefLists : game.choiceLists; }
    private Button toolMenuButton(String name, String emoji) {
        Button item = button(emoji + "  " + name, ui.border); item.setTextColor(ui.text);
        item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); item.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        item.setContentDescription(name); return item;
    }
    private void buildTools(FrameLayout body) {
        if (toolsPage == 2) {
            Game.ChoiceList selected = null;
            for (Game.ChoiceList list : activeToolLists()) if (list.id.equals(choiceListId)) selected = list;
            if (selected != null && toolsTool == 1) {
                if (briefDictionary == null) try (java.io.Reader reader = new java.io.InputStreamReader(getAssets().open("creative-brief.tsv"), java.nio.charset.StandardCharsets.UTF_8)) {
                    briefDictionary = BriefDictionary.read(reader);
                } catch (java.io.IOException error) { Toast.makeText(this, "Could not load brief dictionary", Toast.LENGTH_LONG).show(); toolsPage = 1; }
                if (briefDictionary != null) {
                    CreativeBrief model = briefSessions.get(selected.id);
                    if (model == null || model.fields != selected.items) { model = new CreativeBrief(selected.items); model.restore(briefSessionStates.getStringArray(selected.id), briefDictionary); briefSessions.put(selected.id, model); }
                    model.showTitles = game.briefShowTitles;
                    briefEditor = new CreativeBriefView(this, ui, model, briefDictionary, () -> { effects.removeCallbacks(saveChoices); effects.postDelayed(saveChoices, 500); }, () -> navigateBack(), this::pickBriefCategory, visible -> { game.briefShowTitles = visible; for (CreativeBrief session : briefSessions.values()) session.showTitles = visible; store.save(); renderedRevision = store.revision; });
                    body.addView(briefEditor, new FrameLayout.LayoutParams(-1, -1)); return;
                }
            }
            if (selected != null && toolsTool == 0) {
                ChoiceRandomizer model = choiceRandomizers.get(selected.id);
                if (model == null || model.items != selected.items) { model = new ChoiceRandomizer(selected.items); model.editor.restore(choiceEditorStates.getIntArray(selected.id)); model.restore(choiceSessionStates.getLongArray(selected.id)); choiceRandomizers.put(selected.id, model); }
                choiceEditor = new ChoiceRandomizerView(this, ui, model, () -> {
                    effects.removeCallbacks(saveChoices); effects.postDelayed(saveChoices, 500);
                }, () -> navigateBack());
                body.addView(choiceEditor, new FrameLayout.LayoutParams(-1, -1));
                return;
            }
            toolsPage = 1;
        }
        LinearLayout content = column();
        ScrollView scroll = new ScrollView(this);
        toolsScroll = scroll; toolsScrollPage = toolsPage == 0 ? 0 : toolsTool + 1; toolsScrollRestoring = true;
        final int sectionPage = toolsScrollPage;
        scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                if (toolsScroll == scroll) { scroll.scrollTo(0, toolsScrollPositions[sectionPage]); toolsScrollRestoring = false; }
                return true;
            }
        });
        LinearLayout list = column(); list.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        scroll.addView(list); content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (toolsPage == 0) {
            Button choices = toolMenuButton("Choice Randomizer", "🎲");
            choices.setOnClickListener(v -> { toolsTool = 0; toolsPage = 1; render(); });
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(-1, ui.dp(52)); choiceParams.bottomMargin = ui.dp(8); list.addView(choices, choiceParams);
            Button briefs = toolMenuButton("Creative Brief Generator", "🎨");
            briefs.setOnClickListener(v -> { toolsTool = 1; toolsPage = 1; render(); }); list.addView(briefs, new LinearLayout.LayoutParams(-1, ui.dp(52)));
            Button meditation = toolMenuButton("Meditation Bell", "🔔");
            meditation.setOnClickListener(v -> startActivity(new Intent(this, MeditationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION), android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()));
            LinearLayout.LayoutParams meditationParams = new LinearLayout.LayoutParams(-1, ui.dp(52)); meditationParams.topMargin = ui.dp(8); list.addView(meditation, meditationParams);
            Button youtube=toolMenuButton("Youtube Bookmark", "▶");youtube.setOnClickListener(v->startActivity(new Intent(this,YoutubeLibraryActivity.class)));LinearLayout.LayoutParams yp=new LinearLayout.LayoutParams(-1,ui.dp(52));yp.topMargin=ui.dp(8);list.addView(youtube,yp);
            Button pdf = toolMenuButton("PDF Reader", "📖");
            pdf.setOnClickListener(v -> startActivity(new Intent(this, PdfLibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION), android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()));
            LinearLayout.LayoutParams pdfParams = new LinearLayout.LayoutParams(-1, ui.dp(52)); pdfParams.topMargin = ui.dp(8); list.addView(pdf, pdfParams);
            Button notes = toolMenuButton("Notes", "✎");
            notes.setOnClickListener(v -> startActivity(new Intent(this, NotesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION), android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()));
            LinearLayout.LayoutParams notesParams = new LinearLayout.LayoutParams(-1, ui.dp(52)); notesParams.topMargin = ui.dp(8); list.addView(notes, notesParams);
        } else {
            {
                TextView introduction = text(toolsTool == 1
                        ? "Let the app create a prompt for your next drawing, 3d model or other creative pursuits."
                        : "Create a list of options and let the app pick for you.", 12, ui.border);
                introduction.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(12));
                list.addView(introduction, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout nameRows = column(); list.addView(nameRows);
            for (Game.ChoiceList entry : activeToolLists()) {
                View choice;
                if (toolNamesArrange[toolsTool]) choice = toolListCard(entry);
                else {
                    Button open = button(entry.name, ui.border); open.setTextColor(ui.text);
                    open.setOnClickListener(v -> { choiceListId = entry.id; toolsPage = 2; render(); });
                    open.setOnLongClickListener(v -> { toolNamesArrange[toolsTool] = true; render(); return true; }); choice = open;
                }
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, ui.dp(52)); p.bottomMargin = ui.dp(8); nameRows.addView(choice, p);
            }
            if (toolNamesArrange[toolsTool]) toolListDrag = new ToolListReorder(nameRows, scroll, content, activeToolLists(), () -> { store.save(); render(); }, ui);
            LinearLayout actions = row();
            Button back = button("Back", ui.border); back.setTextColor(ui.text); back.setOnClickListener(v -> navigateBack());
            LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(0, ui.dp(48), 1); backParams.setMargins(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)); actions.addView(back, backParams);
            if (!toolNamesArrange[toolsTool]) {
                Button add = button("Add", ui.border); add.setTextColor(ui.text); add.setContentDescription(toolsTool == 1 ? "Add brief list" : "Add choice list"); add.setOnClickListener(v -> addChoiceList());
                LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(0, ui.dp(48), 1); addParams.setMargins(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)); actions.addView(add, addParams);
            }
            content.addView(actions);
        }
        body.addView(content, new FrameLayout.LayoutParams(-1, -1));
    }
    private View toolListCard(Game.ChoiceList entry) {
        LinearLayout card = row(); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(ui.dp(8), 0, ui.dp(8), 0); card.setBackground(ui.button(ui.border)); card.setTag(entry);
        TextView name = text(entry.name, 14, ui.text); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END); card.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        for (String action : new String[]{"edit", "trash"}) {
            Button control = button("", ui.border); Ui.Icon icon = new Ui.Icon(action, ui.border); icon.setBounds(0, 0, ui.dp(24), ui.dp(24)); control.setCompoundDrawables(icon, null, null, null);
            control.setMinHeight(0); control.setMinimumHeight(0); control.setPadding(ui.dp(4), 0, ui.dp(4), 0); control.setContentDescription((action.equals("edit") ? "Edit list: " : "Delete list: ") + entry.name);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ui.dp(32), ui.dp(36)); if (action.equals("edit")) p.rightMargin = ui.dp(4); card.addView(control, p);
            control.setOnClickListener(v -> { if (action.equals("edit")) editToolList(entry); else deleteToolList(entry); });
        }
        View grip = addDragGrip(card); grip.setContentDescription("Drag list: " + entry.name);
        View.OnLongClickListener begin = held -> { boolean started = card.startDragAndDrop(android.content.ClipData.newPlainText("tool-list", ""), new View.DragShadowBuilder(card), card, 0); if (started) card.setVisibility(View.INVISIBLE); return started; };
        card.setOnLongClickListener(begin); grip.setOnClickListener(v -> begin.onLongClick(v));
        grip.setOnTouchListener((v, event) -> { if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) { v.performClick(); return true; } return true; });
        card.setContentDescription(entry.name + ". Drag to rearrange"); return card;
    }
    private void deleteToolList(Game.ChoiceList entry) {
        LinearLayout content = column(); content.addView(label("Delete list?")); content.addView(label(entry.name)); content.addView(help("Delete this list and all its items?"));
        LinearLayout actions = row(); Button cancel = button("Cancel", ui.border), delete = button("Delete", Ui.RED); addAction(actions, cancel); addAction(actions, delete); content.addView(actions);
        Dialog window = showDialog(content); cancel.setOnClickListener(v -> window.dismiss());
        delete.setOnClickListener(v -> { activeToolLists().remove(entry); briefSessions.remove(entry.id); briefSessionStates.remove(entry.id); choiceRandomizers.remove(entry.id); choiceEditorStates.remove(entry.id); choiceSessionStates.remove(entry.id); if (choiceListId.equals(entry.id)) choiceListId = ""; store.save(); window.dismiss(); render(); });
    }
    private void addChoiceList() { editToolList(null); }
    private void editToolList(Game.ChoiceList existing) {
        LinearLayout content = column();
        EditText name = input("", false); name.setContentDescription("List name"); name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)}); if (existing != null) name.setText(existing.name);
        content.addView(name, fieldParams());
        LinearLayout actions = row(); Button cancel = button("Cancel", ui.border), create = button(existing == null ? "Create" : "Save", ui.border);
        cancel.setTextColor(ui.text); create.setTextColor(ui.text);
        cancel.setContentDescription("Cancel"); create.setContentDescription(existing != null ? "Save list name" : toolsTool == 1 ? "Create brief list" : "Create choice list");
        addAction(actions, cancel); addAction(actions, create); content.addView(actions);
        Dialog window = showDialog(content);
        cancel.setOnClickListener(v -> window.dismiss());
        create.setOnClickListener(v -> { String value = name.getText().toString().trim(); if (value.isEmpty()) { name.requestFocus(); return; }
            if (existing != null) existing.name = value; else { Game.ChoiceList entry = new Game.ChoiceList(); entry.name = value; if (toolsTool == 1) game.addBriefList(entry); else game.addChoiceList(entry); } store.save(); window.dismiss(); render(); });
        name.requestFocus(); window.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    private void pickBriefCategory(java.util.function.Consumer<String> selected) {
        Ui palette = ui;
        LinearLayout content = column(); content.setBackground(palette.panel(palette.border)); content.setPadding(palette.dp(8), palette.dp(8), palette.dp(8), palette.dp(8));
        ScrollView scroll = new ScrollView(this); LinearLayout options = column(); scroll.addView(options); content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Dialog window = new Dialog(this); dialog = window; briefPicker = window;
        String[] names = {"Landscapes", "Creatures"};
        String[][] categories = {{"Theme", "Time Period", "Ambience", "Location", "Architecture", "Scale"}, {"Animal", "Clothes Style", "Body Type", "Age", "Personality", "Emotion", "Internal Conflict"}};
        for (int g = 0; g < names.length; g++) {
            final int group = g;
            TextView heading = text((briefPickerExpanded[g] ? "▾  " : "▸  ") + names[g], 14, palette.border); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setPadding(palette.dp(8), 0, palette.dp(8), 0); heading.setFocusable(true);
            options.addView(heading, new LinearLayout.LayoutParams(-1, palette.dp(36)));
            LinearLayout rows = column(); rows.setVisibility(briefPickerExpanded[g] ? View.VISIBLE : View.GONE); options.addView(rows);
            heading.setOnClickListener(v -> { briefPickerExpanded[group] = !briefPickerExpanded[group]; rows.setVisibility(briefPickerExpanded[group] ? View.VISIBLE : View.GONE); heading.setText((briefPickerExpanded[group] ? "▾  " : "▸  ") + names[group]); fitBriefPicker(window); });
            for (String category : categories[g]) {
                Button choice = button(category, palette.border); choice.setTextColor(palette.text);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, palette.dp(48)); p.topMargin = palette.dp(4); p.bottomMargin = palette.dp(4); rows.addView(choice, p);
                choice.setOnClickListener(v -> { if (briefEditor != null) briefEditor.captureState(); window.dismiss(); selected.accept(category); briefEditor = null; render(); });
            }
        }
        Button nevermind = button("Nevermind", palette.border); nevermind.setTextColor(palette.text); nevermind.setOnClickListener(v -> window.dismiss());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, palette.dp(48)); p.topMargin = palette.dp(8); content.addView(nevermind, p);
        window.setContentView(content); window.getWindow().setBackgroundDrawableResource(android.R.color.transparent); window.setOnDismissListener(v -> { if (dialog == window) dialog = null; if (briefPicker == window) briefPicker = null; });
        window.show(); fitBriefPicker(window);
    }
    private void fitBriefPicker(Dialog window) {
        int visible = (briefPickerExpanded[0] ? 6 : 0) + (briefPickerExpanded[1] ? 5 : 0);
        window.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - ui.dp(16), ui.dp(560)), Math.min(getResources().getDisplayMetrics().heightPixels - ui.dp(80), ui.dp(Math.min(640, 144 + visible * 56))));
    }
    private void buildSettings(FrameLayout body) {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = column(); content.setPadding(ui.dp(8), 0, ui.dp(8), ui.dp(8));
        NotesStore notion=NotesStore.get(this);
        Button cloudBackup=button(notion.connected()&&!notion.model.parent.isEmpty()?"Restore backup":"Set up backups",ui.border);
        content.addView(cloudBackup,new LinearLayout.LayoutParams(-1,ui.dp(48)));
        cloudBackup.setOnClickListener(v->{if(notion.connected()&&!notion.model.parent.isEmpty())NotionBackupDialog.show(this,saved->restoreSave(saved,"Before Notion backup restore"));else NotionConnection.show(this,this::render);});
        Button notionSettings=button("Notion settings",ui.border);content.addView(notionSettings,new LinearLayout.LayoutParams(-1,ui.dp(48)));
        notionSettings.setOnClickListener(v->NotionConnection.show(this,this::render));
        LinearLayout volumeHeading=row();volumeHeading.addView(label("Volume"),new LinearLayout.LayoutParams(0,-2,1));
        TextView volumeValue=text(game.soundVolume+"%",14,ui.text);volumeHeading.addView(volumeValue);content.addView(volumeHeading);
        SeekBar volume=new SeekBar(this);volume.setMax(100);volume.setProgress(game.soundVolume);volume.setContentDescription("Volume for all app notification sounds");
        volume.setProgressTintList(ColorStateList.valueOf(ui.border));volume.setThumbTintList(ColorStateList.valueOf(ui.border));volume.setProgressBackgroundTintList(ColorStateList.valueOf(ui.muted));
        content.addView(volume,new LinearLayout.LayoutParams(-1,ui.dp(44)));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean user){volumeValue.setText(value+"%");if(user&&game.soundVolume!=value){game.soundVolume=value;store.save();renderedRevision=store.revision;}}
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        content.addView(label("Color Hue"));
        LinearLayout huePanel = row(); huePanel.setBackground(ui.panel(ui.border)); huePanel.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        SeekBar hue = new SeekBar(this); hue.setMax(359); hue.setProgress(Math.round(game.hue));
        hue.setProgressTintList(ColorStateList.valueOf(ui.border)); hue.setThumbTintList(ColorStateList.valueOf(ui.border));
        hue.setContentDescription("App color hue");
        huePanel.addView(hue, new LinearLayout.LayoutParams(0, ui.dp(64), 1));
        TextView preview = text("Aa", 18, ui.text); preview.setGravity(Gravity.CENTER); preview.setBackground(ui.panel(ui.border));
        huePanel.addView(preview, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        content.addView(huePanel, fieldParams());
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int value, boolean user) {
                Ui swatch = new Ui(MainActivity.this, value);
                preview.setTextColor(swatch.text); preview.setBackground(swatch.panel(swatch.border));
                hue.setProgressTintList(ColorStateList.valueOf(swatch.border)); hue.setThumbTintList(ColorStateList.valueOf(swatch.border));
                if (user && game.hue != value) { game.hue = value; store.save(); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { hideKeyboard(); render(); }
        });
        Button reset = button("\u21ba", ui.border); reset.setTextSize(22);
        reset.setMinHeight(0); reset.setMinimumHeight(0); reset.setPadding(0, 0, 0, 0);
        reset.setContentDescription("Reset app color"); reset.setTooltipText("Reset app color");
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(ui.dp(36), ui.dp(40)); resetParams.rightMargin = ui.dp(8);
        huePanel.addView(reset, 1, resetParams);
        reset.setOnClickListener(v -> {
            game.hue = 186; store.save(); hideKeyboard(); render();
        });
        addBackupControls(content);
        LinearLayout.LayoutParams graph = new LinearLayout.LayoutParams(-1, ui.dp(200), 1); graph.topMargin = ui.dp(8);
        balanceGraph = new BalanceGraph(this, game, ui); content.addView(balanceGraph, graph);
        long availableStart = game.balances.isEmpty() ? System.currentTimeMillis() : game.balances.get(0).at;
        long latest = game.balances.isEmpty() ? availableStart : game.balances.get(game.balances.size()-1).at;
        long availableEnd = Math.max(availableStart+1, Math.max(latest, System.currentTimeMillis()));
        BalanceRangeSlider range = new BalanceRangeSlider(this, ui, availableStart, availableEnd, game.balanceRangeStart, game.balanceRangeEnd, (start, end, finished) -> {
            balanceGraph.setRange(start, end);
            if (finished) { game.balanceRangeStart = start; game.balanceRangeEnd = end; store.save(); renderedRevision = store.revision; }
        });
        content.addView(range, new LinearLayout.LayoutParams(-1, ui.dp(60)));

        scroll.addView(content); body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }
    private void addBackupControls(LinearLayout content) {
        LinearLayout controls = row();
        java.util.List<BackupArchive.Entry> entries = store.backups();
        List<String> labels = new ArrayList<>(); labels.add("Backups");
        DateTimeFormatter date = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
        for (BackupArchive.Entry e : entries) labels.add(date.format(java.time.Instant.ofEpochMilli(e.at)) + " · " + e.reason);
        android.widget.Spinner backups = new android.widget.Spinner(this); backups.setContentDescription("Saved backups"); backups.setBackground(ui.button(ui.border));
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convert, parent); view.setTextColor(ui.text); view.setTextSize(12); return view;
            }
            @Override public View getDropDownView(int position, View convert, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convert, parent); view.setTextColor(ui.text); view.setBackgroundColor(ui.background); view.setTextSize(12); view.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12)); return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); backups.setAdapter(adapter);
        backups.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) { BackupArchive.Entry entry = entries.get(position-1); backups.setSelection(0); confirmBackup(entry); }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        controls.addView(backups, new LinearLayout.LayoutParams(0, ui.dp(44), 1));
        for (String action : new String[]{"Export", "Import"}) {
            Button button = button(action, ui.border); button.setTextSize(11); button.setMinHeight(0); button.setMinimumHeight(0); button.setPadding(0,0,0,0);
            button.setContentDescription(action + " save"); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ui.dp(64), ui.dp(44)); p.leftMargin=ui.dp(4); controls.addView(button,p);
            button.setOnClickListener(v -> { if(action.equals("Export")) exportSave(); else importSave(); });
        }
        content.addView(controls, fieldParams());
    }
    private void confirmBackup(BackupArchive.Entry entry) {
        LinearLayout content=column(); content.addView(help("Warning!\n\nThis will delete your current data and restore the backup!\nAre you sure?"));
        LinearLayout actions=row(); Button cancel=button("Cancel",ui.border), restore=button("Restore",Ui.RED); addAction(actions,cancel);addAction(actions,restore);content.addView(actions);
        Dialog window=showDialog(content);cancel.setOnClickListener(v->window.dismiss());
        restore.setOnClickListener(v->{ if(restoreSave(entry.state,"Before backup restore")) window.dismiss(); });
    }
    private boolean restoreSave(String text, String reason) {
        try {
            store.restoreSave(text,reason); hideKeyboard(); game=store.game;
            stopAnimation(); countersInitialized=false; questRearrange=lawRearrange=questTimelines=fineTimelines=false;
            boardScrollY=0; deathReviewShown=null;deathPlaying=false;renderedRevision=-1;
            store.sync(); if(game.hasRunningTimers())ensureTimer();else stopService(new Intent(this,TimerService.class));render();
            Toast.makeText(this,"Save restored",Toast.LENGTH_SHORT).show();return true;
        } catch(Exception ex) { Toast.makeText(this,"Could not load save: "+ex.getMessage(),Toast.LENGTH_LONG).show();return false; }
    }
    private void exportSave() {
        try {
            String saved=store.exportSave();LinearLayout content=column();content.addView(label("Export save"));
            EditText text=input("Save string",false);text.setSingleLine(false);text.setTextSize(11);text.setTypeface(Typeface.MONOSPACE);text.setText(saved);text.setKeyListener(null);text.setTextIsSelectable(true);
            content.addView(text,new LinearLayout.LayoutParams(-1,ui.dp(160)));
            LinearLayout actions=row();Button close=button("Close",ui.border),copy=button("Copy",Ui.GREEN);addAction(actions,close);addAction(actions,copy);content.addView(actions);
            Dialog window=showDialog(content);close.setOnClickListener(v->window.dismiss());
            copy.setOnClickListener(v->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Bulletin save",saved));Toast.makeText(this,"Save copied",Toast.LENGTH_SHORT).show();});
        } catch(Exception ex) { Toast.makeText(this,"Could not export save: "+ex.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void importSave() {
        LinearLayout content=column();content.addView(label("Import save"));EditText text=input("Paste save string",false);
        text.setSingleLine(false);text.setTextSize(11);text.setTypeface(Typeface.MONOSPACE);text.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        content.addView(text,new LinearLayout.LayoutParams(-1,ui.dp(160)));LinearLayout actions=row();Button cancel=button("Cancel",ui.border),load=button("Import",Ui.GREEN);addAction(actions,cancel);addAction(actions,load);content.addView(actions);
        Dialog window=showDialog(content);cancel.setOnClickListener(v->window.dismiss());load.setOnClickListener(v->{if(restoreSave(text.getText().toString(),"Before import"))window.dismiss();});
    }
    private void createQuest() { createQuestIn(""); }
    private void createQuestIn(String categoryId) { creatingCategoryId = categoryId; editQuest(null); }
    private void showAddMenu() {
        LinearLayout content = column(); content.addView(section("ADD TO BULLETIN"));
        Button quest = button("Quest", ui.border), category = button("Category", ui.border);
        content.addView(quest, fieldParams()); content.addView(category, fieldParams());
        Dialog window = showDialog(content);
        quest.setOnClickListener(v -> { window.dismiss(); createQuest(); });
        category.setOnClickListener(v -> { window.dismiss(); createCategory(); });
    }
    private void createCategory() { editCategory(null); }
    private void editCategory(Game.Category existing) {
        withUi(new Ui(this, existing != null && existing.hue >= 0 ? existing.hue : game.hue), () -> editCategoryContent(existing));
    }
    private void editCategoryContent(Game.Category existing) {
        java.util.function.BooleanSupplier requirementsLocked=()->{store.sync();return !game.canChangeCategoryRequirements(existing);};
        LinearLayout content = column(); content.addView(section(existing == null ? "NEW CATEGORY" : "EDIT CATEGORY"));
        EditText name = input("Category name", false); name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)}); content.addView(name, fieldParams());
        CheckBox mustDo = check("Must Do"); content.addView(mustDo, fieldParams());
        LinearLayout quota = row(); EditText requiredTasks = input("Required Tasks", true); requiredTasks.setText(String.valueOf(existing == null ? 1 : existing.requiredTasks));
        quota.addView(namedTimeField(requiredTasks, "Required Tasks"), new LinearLayout.LayoutParams(0, ui.dp(64), 1)); Button all = button("All", ui.border); all.setContentDescription("All category tasks"); quota.addView(all, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(64))); content.addView(quota, fieldParams());
        boolean[] requireAll = {existing != null && existing.requireAll}; String[] quotaValue = {requiredTasks.getText().toString()};
        Runnable updateQuota = () -> { requiredTasks.setEnabled(!requireAll[0]); requiredTasks.setInputType(requireAll[0] ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_CLASS_NUMBER); requiredTasks.setText(requireAll[0] ? "All" : quotaValue[0]); all.setSelected(requireAll[0]); int color = requireAll[0] ? Ui.GOLD : ui.border; all.setTextColor(color); all.setBackground(ui.button(color)); ((BorderedButton) all).setBorderColor(color);
            if(requirementsLocked.getAsBoolean()){requiredTasks.setEnabled(true);requiredTasks.setKeyListener(null);requiredTasks.setFocusable(false);requiredTasks.setCursorVisible(false);}
        };
        all.setOnClickListener(v -> { if(requirementsLocked.getAsBoolean()){showInformation(MUST_DO_EDIT_NOTICE);return;} if (!requireAll[0]) quotaValue[0] = requiredTasks.getText().toString(); requireAll[0] = !requireAll[0]; updateQuota.run(); }); updateQuota.run();
        requiredTasks.setOnClickListener(v->{
            if(requirementsLocked.getAsBoolean()){hideKeyboard();showInformation(MUST_DO_EDIT_NOTICE);return;}
            if(!requireAll[0]){requiredTasks.setInputType(InputType.TYPE_CLASS_NUMBER);requiredTasks.setFocusableInTouchMode(true);requiredTasks.setCursorVisible(true);requiredTasks.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(requiredTasks,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}
        });
        if(requirementsLocked.getAsBoolean())requiredTasks.setFocusable(false);
        boolean[] reverting={false};
        quota.setVisibility(View.GONE); mustDo.setOnCheckedChangeListener((v, checked) -> {
            if(!reverting[0]&&existing!=null&&checked!=existing.mustDo&&requirementsLocked.getAsBoolean()){reverting[0]=true;mustDo.setChecked(existing.mustDo);reverting[0]=false;showInformation(MUST_DO_EDIT_NOTICE);return;}
            quota.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        mustDo.setChecked(existing != null && existing.mustDo);
        mustDo.setOnClickListener(v -> { if (mustDo.isChecked()&&(existing==null||!existing.mustDo)) showInformation("With 'Must Do' enabled, set an amount of quests in this category that must be finished each day before midnight."); });
        CheckBox useColor = check("Use a category color"); content.addView(useColor, fieldParams());
        LinearLayout colors = column(); content.addView(colors);
        Ui[] palette = {ui}; EditText hue = questHuePicker(colors, existing != null && existing.hue >= 0 ? existing.hue : Math.round(game.hue), palette);
        ((TextView) colors.getChildAt(0)).setText("Category color hue");
        colors.setVisibility(View.GONE); useColor.setOnCheckedChangeListener((v, checked) -> colors.setVisibility(checked ? View.VISIBLE : View.GONE));
        if (existing != null) { name.setText(existing.name); useColor.setChecked(existing.hue >= 0); }
        LinearLayout actions = row(); Button cancel = button("Cancel", Ui.RED), save = button(existing == null ? "Create category" : "Save category", Ui.GREEN);
        addAction(actions, cancel); addAction(actions, save); content.addView(actions);
        Dialog window = showDialog(content); cancel.setOnClickListener(v -> window.dismiss());
        save.setOnClickListener(v -> {
            String title = name.getText().toString().trim(); if (title.isEmpty()) { name.setError("Give your category a name"); return; }
            Integer value = Integer.valueOf(-1);
            if (useColor.isChecked()) value = number(hue, 0, 359);
            if (value == null) return;
            Integer required = Integer.valueOf(existing == null ? 1 : existing.requiredTasks);
            if (mustDo.isChecked() && !requireAll[0]) required = number(requiredTasks, 0, 999999);
            if (required == null) return;
            if(existing!=null&&(existing.mustDo!=mustDo.isChecked()||existing.requireAll!=requireAll[0]||existing.requiredTasks!=required)&&requirementsLocked.getAsBoolean()){showInformation(MUST_DO_EDIT_NOTICE);return;}
            Game.Category c = existing == null ? new Game.Category() : existing; c.name = title; c.hue = value;
            if (c.mustDo != mustDo.isChecked() || c.requireAll != requireAll[0] || c.requiredTasks != required) { c.fulfilledDay = Long.MIN_VALUE; c.fulfilledTarget = 0; }
            c.mustDo = mustDo.isChecked(); c.requireAll = requireAll[0]; c.requiredTasks = required;
            game.refreshCategory(c);
            if (existing == null) game.addCategory(c); store.save(); hideKeyboard(); window.dismiss(); render();
        });
    }
    private void showInformation(String message) { showInformation(message, null); }
    private void showInformation(String message, Runnable onClosed) {
        Dialog previous = dialog;
        LinearLayout content = column(); TextView explanation = help(message); explanation.setTextSize(20); content.addView(explanation);
        Button confirm = button("Confirm", ui.border); content.addView(confirm, fieldParams());
        Dialog window = showDialog(content);
        window.setOnDismissListener(v -> { if (dialog == window) dialog = previous != null && previous.isShowing() ? previous : null; if (onClosed != null) onClosed.run(); });
        confirm.setOnClickListener(v -> window.dismiss());
    }
    private void showSwipeHint() {
        LinearLayout content = column(); TextView explanation = help("You just created your first quest! Create some more to get started."); explanation.setTextSize(20); content.addView(explanation);
        Button confirm = button("Confirm", ui.border); content.addView(confirm, fieldParams());
        Dialog window = showDialog(content); window.setCancelable(false);
        confirm.setOnClickListener(v -> { game.swipeHintShown = true; store.save(); window.dismiss(); });
    }
    private EditText emojiPicker(LinearLayout content, String initial) {
        EditText emoji = input("Choose emoji", false); emoji.setText(initial);
        emoji.setFocusable(false); emoji.setCursorVisible(false); emoji.setShowSoftInputOnFocus(false);
        content.addView(emoji, fieldParams());
        emoji.setOnClickListener(v -> {
            float[] hsv = new float[3]; android.graphics.Color.colorToHSV(emoji.getCurrentTextColor(), hsv);
            withUi(new Ui(this, hsv[0]), () -> showEmojiMenu(emoji));
        });
        return emoji;
    }
    private void showEmojiMenu(EditText target) {
        Dialog parent=dialog;Dialog[] opened={null};
        opened[0]=EmojiPickerDialog.show(this,ui,target,parent,()->{if(emojiMenu==opened[0])emojiMenu=null;if(dialog==opened[0])dialog=parent!=null&&parent.isShowing()?parent:null;});
        if(opened[0]!=null){emojiMenu=opened[0];dialog=opened[0];}
    }
    private void fitEmojiMenu(Dialog window) {
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - ui.dp(16), ui.dp(560));
        int height = Math.min(getResources().getDisplayMetrics().heightPixels - ui.dp(80), ui.dp(640));
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
                    moved |= Math.hypot(event.getRawX() - x, event.getRawY() - y) > android.view.ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
                } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP && !moved) {
                    view.setPressed(false); view.performClick(); return true;
                } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) moved = true;
                return false;
            }
        });
    }
    private EditText questHuePicker(LinearLayout content, int initial, Ui[] palette) {
        content.addView(label("Quest color hue"));
        EditText number = input("Quest hue 0–359", true); number.setText(String.valueOf(initial));
        number.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)}); content.addView(number, fieldParams());
        SeekBar slider = new SeekBar(this); slider.setMax(359); slider.setProgress(initial);
        slider.setContentDescription("Quest color hue slider");
        LinearLayout hueRow = row(); hueRow.addView(slider, new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        for (String action : new String[]{"Copy", "Paste", "Reset"}) {
            TextView control = text(action.equals("Copy") ? "⧉" : action.equals("Paste") ? "▣" : "↺", 18, ui.text);
            control.setGravity(Gravity.CENTER); control.setContentDescription(action + " quest hue");
            hueRow.addView(control, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(48)));
            control.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (action.equals("Copy")) clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Quest hue", number.getText().toString()));
                else if (action.equals("Reset")) number.setText(String.valueOf(Math.round(game.hue)));
                else if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                    String value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this).toString().trim();
                    try { int parsed = Integer.parseInt(value); if (parsed < 0 || parsed > 359) throw new NumberFormatException(); number.setText(value); showPasteNotice(v, palette[0]); }
                    catch (NumberFormatException error) { Toast.makeText(this, "Copy a hue from 0 to 359 first", Toast.LENGTH_SHORT).show(); }
                }
            });
        }
        content.addView(hueRow, fieldParams());
        slider.setProgressTintList(ColorStateList.valueOf(ui.border)); slider.setThumbTintList(ColorStateList.valueOf(ui.border));
        java.util.function.IntConsumer change = value -> {
            Ui next = new Ui(this, value); recolorQuestContent(content, palette[0], next); palette[0] = next;
            if (content.getParent() instanceof ScrollView) ((ScrollView) content.getParent()).setBackground(next.panel(next.border));
        };
        number.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int value = Integer.parseInt(s.toString());
                    if (value < 0 || value > 359) { number.setError("Enter a value from 0 to 359"); return; }
                    number.setError(null); slider.setProgress(value); change.accept(value);
                } catch (NumberFormatException ignored) { if (s.length() > 0) number.setError("Enter a whole number"); }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) { if (user) number.setText(String.valueOf(value)); }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        return number;
    }
    private void showPasteNotice(View anchor, Ui palette) {
        if (pasteNotice != null) pasteNotice.dismiss();
        TextView message = text("Value pasted", 12, palette.text);
        message.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8)); message.setBackground(palette.panel(palette.border));
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        android.widget.PopupWindow popup = new android.widget.PopupWindow(message, -2, -2, false);
        popup.setTouchable(false); popup.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setAttachedInDecor(false);
        if (Build.VERSION.SDK_INT >= 29) popup.setIsLaidOutInScreen(true);
        int bottom = 0; WindowInsets insets = anchor.getRootWindowInsets();
        if (insets != null) bottom = Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime()).bottom : insets.getStableInsetBottom();
        pasteNotice = popup; popup.setOnDismissListener(() -> { if (pasteNotice == popup) pasteNotice = null; });
        popup.showAtLocation(anchor.getRootView(), Gravity.BOTTOM | Gravity.END, ui.dp(12), bottom + ui.dp(12));
        effects.postDelayed(popup::dismiss, 1500);
    }
    private void recolorQuestContent(View view, Ui before, Ui after) {
        int stroke = after.border;
        if (view instanceof TextView) {
            TextView text = (TextView) view; int color = text.getCurrentTextColor();
            if (color == Ui.GREEN || color == Ui.RED) stroke = color;
            else text.setTextColor(color == before.muted ? after.muted : color == before.border ? after.border : after.text);
            if (view instanceof EditText) ((EditText) view).setHintTextColor(after.muted);
        }
        if (view.getBackground() != null && !(view instanceof SeekBar)) view.setBackground(view.isClickable() ? after.button(stroke) : after.panel(stroke));
        if (view instanceof BorderedButton) ((BorderedButton) view).setBorderColor(stroke);
        if (view instanceof CheckBox) ((CheckBox) view).setButtonTintList(ColorStateList.valueOf(after.border));
        if (view instanceof SeekBar) {
            ((SeekBar) view).setProgressTintList(ColorStateList.valueOf(after.border));
            ((SeekBar) view).setThumbTintList(ColorStateList.valueOf(after.border));
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) recolorQuestContent(((ViewGroup) view).getChildAt(i), before, after);
    }
    private void editQuest(Game.Quest existing) {
        withUi(questPalette(existing), () -> editQuestContent(existing));
    }
    private void editQuestContent(Game.Quest existing) {
        final String newCategoryId = existing == null ? creatingCategoryId : "";
        LinearLayout content = column();
        content.addView(section(existing == null ? "NEW QUEST" : "EDIT QUEST"));
        if (existing == null) content.addView(help("Write in a good habit you want to cultivate."));
        EditText name = input("Activity", false); name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        content.addView(name, fieldParams());
        EditText emoji = emojiPicker(content, existing == null ? "" : existing.emoji);
        final int advancedStart = content.getChildCount();
        Ui[] editorPalette = {ui};
        EditText hue = questHuePicker(content, existing == null || existing.hue < 0 ? Math.round(existing != null && existing.required ? 145 : game.hue) : existing.hue, editorPalette);
        CheckBox repeat = check("Multiple Times a Day"); content.addView(repeat, fieldParams());
        CheckBox timed = check("Timed"); content.addView(timed, fieldParams()); timed.setVisibility(View.GONE);
        CheckBox required = check("Must Complete"); content.addView(required, fieldParams());
        boolean[] bindingRequirements={true};
        repeat.setOnCheckedChangeListener((view, checked) -> {
            if(!bindingRequirements[0]&&checked&&existing!=null&&existing.required&&!game.canRemoveMustDo(existing)){
                bindingRequirements[0]=true;repeat.setChecked(existing.repeatable);required.setChecked(existing.required);bindingRequirements[0]=false;showInformation(MUST_DO_EDIT_NOTICE);return;
            }
            required.setVisibility(checked ? View.GONE : View.VISIBLE);
            timed.setVisibility(checked ? View.GONE : View.VISIBLE); if (checked) timed.setChecked(false);
            if (checked&&!bindingRequirements[0]) required.setChecked(false);
        });
        required.setOnCheckedChangeListener((view, checked) -> {
            if(!bindingRequirements[0]&&!checked&&existing!=null&&existing.required){store.sync();if(!game.canRemoveMustDo(existing)){bindingRequirements[0]=true;required.setChecked(true);bindingRequirements[0]=false;showInformation(MUST_DO_EDIT_NOTICE);return;}}
            repeat.setVisibility(checked ? View.GONE : View.VISIBLE);
            if (checked&&!bindingRequirements[0]) repeat.setChecked(false);
        });
        CheckBox forever = check("Recurring"); content.addView(forever, fieldParams());
        TextView intervalLabel = label("Days between quests"); content.addView(intervalLabel);
        EditText interval = input("Days between quests", true); interval.setText("0"); content.addView(interval, fieldParams());
        interval.setVisibility(View.GONE); intervalLabel.setVisibility(View.GONE);
        forever.setOnCheckedChangeListener((view, checked) -> { interval.setVisibility(checked ? View.VISIBLE : View.GONE); intervalLabel.setVisibility(checked ? View.VISIBLE : View.GONE); });
        final LocalDate[] date = {LocalDate.now()};
        content.addView(label("First appearance"));
        Button dateButton = button(date[0].format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), ui.border);
        content.addView(dateButton, fieldParams());
        dateButton.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
                date[0] = LocalDate.of(year, month + 1, day);
                dateButton.setText(date[0].format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)));
            }, date[0].getYear(), date[0].getMonthValue() - 1, date[0].getDayOfMonth());
            picker.getDatePicker().setMinDate(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()); picker.show();
        });
        LinearLayout advanced = column();
        while (content.getChildCount() > advancedStart) { View child = content.getChildAt(advancedStart); content.removeViewAt(advancedStart); advanced.addView(child); }
        TextView advancedHeading = text((game.questAdvancedExpanded ? "▾  " : "▸  ") + "Advanced", 14, ui.border);
        advancedHeading.setGravity(Gravity.CENTER_VERTICAL); advancedHeading.setPadding(ui.dp(8), 0, ui.dp(8), 0); advancedHeading.setFocusable(true);
        advancedHeading.setContentDescription(game.questAdvancedExpanded ? "Advanced, expanded" : "Advanced, collapsed");
        advanced.setVisibility(game.questAdvancedExpanded ? View.VISIBLE : View.GONE);
        advancedHeading.setOnClickListener(v -> { game.questAdvancedExpanded = !game.questAdvancedExpanded; advanced.setVisibility(game.questAdvancedExpanded ? View.VISIBLE : View.GONE);
            advancedHeading.setText((game.questAdvancedExpanded ? "▾  " : "▸  ") + "Advanced"); advancedHeading.setContentDescription(game.questAdvancedExpanded ? "Advanced, expanded" : "Advanced, collapsed"); store.save(); renderedRevision = store.revision; });
        if (game.questsCreated >= 3) { content.addView(advancedHeading, new LinearLayout.LayoutParams(-1, ui.dp(40))); content.addView(advanced); }
        content.addView(section("PREREQUISITES"));
        content.addView(help("What needs to be ready before you begin?"));
        LinearLayout list = column(); content.addView(list);
        List<EditText> prerequisites = new ArrayList<>();
        enablePrerequisiteReorder(list, prerequisites);
        Button add = button("+  Add prerequisite", ui.border); content.addView(add, fieldParams());
        add.setOnClickListener(v -> withUi(editorPalette[0], () -> {
            LinearLayout item = row();
            Button drag = button("≡", ui.border); drag.setContentDescription("Hold and drag to reorder prerequisite");
            item.addView(drag, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(64)));
            EditText line = input("Prerequisite " + (prerequisites.size() + 1), false);
            line.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(64), 1); lp.setMargins(0, 0, ui.dp(8), 0); item.addView(line, lp);
            Button remove = button("−", Ui.RED); remove.setContentDescription("Remove prerequisite");
            item.addView(remove, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(64)));
            remove.setOnClickListener(view -> { prerequisites.remove(line); list.removeView(item); });
            prerequisites.add(line); list.addView(item, fieldParams()); line.requestFocus();
            View.OnLongClickListener beginDrag = held -> {
                hideKeyboard(); line.clearFocus();
                android.content.ClipData clip = new android.content.ClipData("prerequisite", new String[]{"application/x-bulletin-prerequisite"}, new android.content.ClipData.Item(""));
                boolean started = item.startDragAndDrop(clip, new View.DragShadowBuilder(item), item, 0);
                if (started) item.setVisibility(View.INVISIBLE);
                return true;
            };
            drag.setOnLongClickListener(beginDrag); item.setOnLongClickListener(beginDrag);
            holdToDrag(line, beginDrag);
            enablePrerequisiteReorder(line, list, prerequisites);
        }));
        LinearLayout actions = row();
        if (existing == null) { repeat.setChecked(true); forever.setChecked(true); }
        if (existing != null) {
            name.setText(existing.name); repeat.setChecked(existing.repeatable); required.setChecked(existing.required);
            timed.setVisibility(existing.repeatable ? View.GONE : View.VISIBLE); timed.setChecked(existing.timed && !existing.repeatable);
            forever.setChecked(existing.forever); interval.setText(String.valueOf(existing.interval - 1));
            date[0] = LocalDate.ofEpochDay(existing.firstDay);
            dateButton.setText(date[0].format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)));
            for (String value : existing.prerequisites) { add.performClick(); prerequisites.get(prerequisites.size() - 1).setText(value); }
        }
        bindingRequirements[0]=false;
        if(existing!=null&&existing.required&&existing.repeatable){required.setVisibility(View.VISIBLE);repeat.setVisibility(View.VISIBLE);}
        Button cancel = button("Cancel", Ui.RED), save = button(existing == null ? "Create quest" : "Save quest", Ui.GREEN);
        addAction(actions, cancel); addAction(actions, save); content.addView(actions);
        Dialog window = showDialog(content);
        if (existing != null && questRearrange && firstRearrangeSession && !game.rearrangeExitHintShown) {
            window.setOnDismissListener(v -> {
                if (dialog == window) dialog = null;
                if (resumed && questRearrange && !game.rearrangeExitHintShown) {
                    game.rearrangeExitHintShown = true; store.save();
                    effects.post(() -> { if (resumed && questRearrange) showInformation("Tap Back to exit rearrange mode"); });
                }
            });
        }
        if (existing == null && game.questsCreated == 0 && !game.questCreationHintShown) {
            game.questCreationHintShown = true; store.save(); renderedRevision = store.revision;
            showInformation("Write a good habit you want to cultivate.");
        }
        cancel.setOnClickListener(v -> window.dismiss());
        save.setOnClickListener(v -> {
            store.sync();
            if(existing!=null&&existing.required&&!required.isChecked()&&!game.canRemoveMustDo(existing)){showInformation(MUST_DO_EDIT_NOTICE);return;}
            String title = name.getText().toString().trim();
            if (title.isEmpty()) { name.setError("Give your quest a name"); return; }
            Integer between = forever.isChecked() ? number(interval, 0, 36500) : 0;
            Integer color = number(hue, 0, 359);
            if (between == null || color == null) return;
            store.sync();
            if (date[0].isBefore(LocalDate.now()) && (existing == null || date[0].toEpochDay() != existing.firstDay)) { dateButton.setText("Choose today or a future date"); return; }
            Game.Quest q = existing == null ? new Game.Quest() : existing; q.name = title; q.repeatable = repeat.isChecked();
            if (existing == null) q.bountySince = Math.max(System.currentTimeMillis(), date[0].atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            q.hue = color; q.emoji = emoji.getText().toString().trim();
            q.required = required.isChecked(); q.timed = !q.repeatable && timed.isChecked(); q.forever = forever.isChecked(); q.interval = between + 1; q.firstDay = date[0].toEpochDay(); q.questMinutes = -1;
            if (!q.repeatable && !q.timed) { q.remainingMillis = -1; q.pausedRewardMinutes = -1; q.pausedReferenceMinutes = -1; q.pausedRewardMillis = -1; q.pausedReferenceMillis = -1; q.pausedRestMillis = -1; q.pausedEnergy = -1; q.pausedActiveMillis = 0; }
            q.prerequisites.clear();
            for (EditText line : prerequisites) { String value = line.getText().toString().trim(); if (!value.isEmpty()) q.prerequisites.add(value); }
            if (existing == null) { game.addQuest(q); game.assignCategory(q, newCategoryId); }
            if (existing == null && game.questsCreated == 4) navigationRevealAt = android.os.SystemClock.uptimeMillis() + 500;
            store.save(); hideKeyboard(); window.dismiss(); render();
            if (existing == null) {
                if (game.questsCreated == 1 && !game.swipeHintShown) showSwipeHint();
                else if (game.questsCreated == 3) showInformation("The dice button in the bottom right lets the app pick two options for you at random. This often makes it easier to pick the next activity.");
                else if (game.questsCreated == 2) showInformation("Use the arrow button in the bottom left to rearrange, edit or delete quests.");
                else if (game.questsCreated == 6 && game.randomModeUsedMillis <= 10000) showInformation("Remember to use the dice button in the bottom right to pick!");
                else if (game.questsCreated == 7 && !game.hasCreatedCategory && game.categories.isEmpty()) showInformation("Create categories to sort your quests into.");
            }
        });
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void holdToDrag(EditText line, View.OnLongClickListener beginDrag) {
        line.setLongClickable(false);
        line.setOnTouchListener(new View.OnTouchListener() {
            float x, y; boolean holding, dragging;
            final Runnable start = () -> {
                if (!holding || !line.isAttachedToWindow()) return;
                dragging = true; beginDrag.onLongClick(line);
            };
            @Override public boolean onTouch(View view, android.view.MotionEvent event) {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        x = event.getRawX(); y = event.getRawY(); holding = true; dragging = false;
                        line.postDelayed(start, android.view.ViewConfiguration.getLongPressTimeout());
                        return false;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (Math.hypot(event.getRawX() - x, event.getRawY() - y) > android.view.ViewConfiguration.get(MainActivity.this).getScaledTouchSlop()) {
                            holding = false; line.removeCallbacks(start);
                        }
                        return dragging;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        holding = false; line.removeCallbacks(start);
                        return dragging;
                    default: return false;
                }
            }
        });
    }
    private void enablePrerequisiteReorder(LinearLayout list, List<EditText> prerequisites) {
        enablePrerequisiteReorder(list, list, prerequisites);
    }
    private void enablePrerequisiteReorder(View dropTarget, LinearLayout list, List<EditText> prerequisites) {
        dropTarget.setOnDragListener((target, event) -> {
            if (!(event.getLocalState() instanceof LinearLayout)) return false;
            LinearLayout moving = (LinearLayout) event.getLocalState();
            if (moving.getParent() != list) return false;
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED: return true;
                case android.view.DragEvent.ACTION_DRAG_LOCATION:
                    int[] targetPosition = new int[2], listOrigin = new int[2];
                    target.getLocationOnScreen(targetPosition); list.getLocationOnScreen(listOrigin);
                    float pointerY = event.getY() + targetPosition[1] - listOrigin[1];
                    int from = list.indexOfChild(moving), to = from;
                    for (int i = 0; i < list.getChildCount(); i++) {
                        View other = list.getChildAt(i);
                        if (i < from && pointerY < other.getTop() + other.getHeight() / 2f) { to = i; break; }
                        if (i > from && pointerY > other.getTop() + other.getHeight() / 2f) to = i;
                    }
                    if (to != from) {
                        final int insertion = to;
                        // Android is iterating its drag targets during dispatch. Reparent after that iteration.
                        list.post(() -> {
                            int current = list.indexOfChild(moving);
                            if (current < 0 || current == insertion || insertion >= list.getChildCount()) return;
                            if (Feedback.motion(this)) android.transition.TransitionManager.beginDelayedTransition(list, new android.transition.ChangeBounds().setDuration(160));
                            list.removeView(moving); list.addView(moving, insertion);
                            EditText line = prerequisites.remove(current); prerequisites.add(insertion, line);
                        });
                    }
                    View ancestor = list;
                    while (ancestor.getParent() instanceof View) {
                        ancestor = (View) ancestor.getParent();
                        if (ancestor instanceof ScrollView) {
                            int[] listPosition = new int[2], scrollPosition = new int[2];
                            list.getLocationOnScreen(listPosition); ancestor.getLocationOnScreen(scrollPosition);
                            float y = listPosition[1] + pointerY - scrollPosition[1];
                            if (y < ui.dp(72)) ancestor.scrollBy(0, -ui.dp(16));
                            else if (y > ancestor.getHeight() - ui.dp(72)) ancestor.scrollBy(0, ui.dp(16));
                            break;
                        }
                    }
                    return true;
                case android.view.DragEvent.ACTION_DROP: return true;
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    list.post(() -> {
                        moving.setVisibility(View.VISIBLE);
                        for (int i = 0; i < prerequisites.size(); i++) {
                            prerequisites.get(i).setHint("Prerequisite " + (i + 1));
                            prerequisites.get(i).setContentDescription("Prerequisite " + (i + 1));
                        }
                    });
                    return true;
                default: return true;
            }
        });
    }
    private void showQuest(Game.Quest q) {
        withUi(questPalette(q), () -> showQuestContent(q));
    }
    private void showQuestContent(Game.Quest q) {
        store.sync();
        if (!game.canStart(q)) { render(); return; }
        LinearLayout content = column();
        content.addView(label("Quest: " + q.name));
        TextView reward = help("Bounty: +" + Money.format(game.bountyFor(q))); reward.setTextColor(Ui.GREEN); content.addView(reward);
        if (!q.prerequisites.isEmpty()) content.addView(section(q.repeatable ? "PREREQUISITES" : "Instructions"));
        LinearLayout instructions = column();
        if (!q.prerequisites.isEmpty()) { instructions.setBackground(ui.panel(ui.border)); content.addView(instructions, fieldParams()); }
        for (int i = 0; i < q.prerequisites.size(); i++) {
            TextView line = text((i + 1) + ".  " + q.prerequisites.get(i), 15, ui.text);
            line.setGravity(Gravity.CENTER_VERTICAL); line.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
            line.setMinHeight(ui.dp(48));
            instructions.addView(line, fieldParams());
        }

        if (q.repeatable) addTimeline(content, Game.QUEST_HISTORY, q.id, q.name);
        LinearLayout actions = row(); Button decline = button("Decline", Ui.RED), accept = button(game.isTimed(q) ? "Accept" : "Complete Quest", Ui.GREEN);
        addAction(actions, decline); addAction(actions, accept); content.addView(actions);
        Dialog window = showDialog(content);
        decline.setOnClickListener(v -> window.dismiss());
        accept.setOnClickListener(v -> {
            store.sync();
            if (!game.isTimed(q)) {
                accept.setEnabled(false); window.dismiss();
                View card = questViews.get(q.id);
                if (card instanceof LinearLayout) completeCard(q, (LinearLayout) card);
                else { if (store.completeInstant(q)) Feedback.vibrate(this, false); render(); }
                return;
            }
            boolean showStartTutorial = false;
            if (game.start(q, System.currentTimeMillis())) {
                if (!game.questStartHintShown) { game.questStartHintShown = true; showStartTutorial = true; }
                store.notices.add(new GameStore.Notice(q.remainingMillis > 0 ? "Quest resumed. Pick up where you left off." : "Quest started. One step at a time.", 0));
                accept.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                store.save(); ensureTimer();
                if (game.session.timed && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
                }
            }
            window.dismiss(); render();
            if (showStartTutorial) showInformation("A quest has started, it is time for you to focus on the task you have set for yourself.");
        });
    }
    private void createFine() { editFine(null); }
    private void editFine(Game.Fine existing) {
        LinearLayout content = column(); content.addView(section(existing == null ? "NEW LAW" : "EDIT LAW"));
        EditText name = input("Law name", false); name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)}); content.addView(name, fieldParams());
        EditText emoji = emojiPicker(content, existing == null ? "" : existing.emoji);
        final int[] mode = {existing == null ? Game.STRICT : existing.mode};
        LinearLayout modes = row(); Button[] modeButtons = new Button[3]; String[] modeNames = {"Strict", "Amount", "Timed"};
        for (int i = 0; i < modeButtons.length; i++) { modeButtons[i] = button(modeNames[i], ui.border); modeButtons[i].setContentDescription("Law mode: " + modeNames[i]); addAction(modes, modeButtons[i]); }
        content.addView(modes);
        LinearLayout amountFields = column(); amountFields.addView(label("Daily amount allowance"));
        EditText amount = input("Allowed amount", true); amount.setText(String.valueOf(existing == null ? 3 : existing.amountLimit)); amountFields.addView(amount, fieldParams()); content.addView(amountFields);
        LinearLayout timeFields = column();
        long duration = existing == null ? 300000 : existing.timeLimitMillis;
        EditText hours = input("Hours", true), minutes = input("Minutes", false);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hours.setText(String.valueOf(duration / 3600000)); minutes.setText(LawDuration.format(duration % 3600000, false));
        LinearLayout timeRow = row();
        LinearLayout.LayoutParams hoursParams = new LinearLayout.LayoutParams(0, ui.dp(64), 1); hoursParams.rightMargin = ui.dp(4);
        LinearLayout.LayoutParams minutesParams = new LinearLayout.LayoutParams(0, ui.dp(64), 1); minutesParams.leftMargin = ui.dp(4);
        timeRow.addView(namedTimeField(hours, "Hours"), hoursParams); timeRow.addView(namedTimeField(minutes, "Minutes"), minutesParams); timeFields.addView(timeRow, fieldParams()); content.addView(timeFields);
        Runnable selectMode = () -> {
            for (int i = 0; i < modeButtons.length; i++) { int color = mode[0] == i ? Ui.GOLD : ui.border; modeButtons[i].setSelected(mode[0] == i); modeButtons[i].setBackground(ui.button(color)); modeButtons[i].setTextColor(color); ((BorderedButton) modeButtons[i]).setBorderColor(color); }
            amountFields.setVisibility(mode[0] == Game.AMOUNT ? View.VISIBLE : View.GONE); timeFields.setVisibility(mode[0] == Game.TIMED ? View.VISIBLE : View.GONE);
        };
        for (int i = 0; i < modeButtons.length; i++) { final int index = i; modeButtons[i].setOnClickListener(v -> { mode[0] = index; selectMode.run(); }); }
        selectMode.run();
        if (existing != null) name.setText(existing.name);
        LinearLayout actions = row(); Button cancel = button("Cancel", Ui.RED), save = button(existing == null ? "Create law" : "Save law", Ui.GREEN);
        addAction(actions, cancel); addAction(actions, save); content.addView(actions);
        Dialog window = showDialog(content); cancel.setOnClickListener(v -> window.dismiss());
        save.setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) { name.setError("Give your law a name"); return; }
            Integer count = mode[0] == Game.AMOUNT ? number(amount, 1, 999999) : existing == null ? 3 : existing.amountLimit;
            if (count == null) return;
            long allowance = duration;
            if (mode[0] == Game.TIMED) {
                try { allowance = LawDuration.parse(hours.getText().toString(), minutes.getText().toString()); }
                catch (IllegalArgumentException | ArithmeticException error) { minutes.setError("Use whole hours and minutes below 60; total must be 1 second to 24 hours"); return; }
            }
            store.sync(); Game.Fine fine = existing == null ? new Game.Fine() : existing; fine.name = title; fine.emoji = emoji.getText().toString().trim();
            game.configureLaw(fine, mode[0], count, allowance, System.currentTimeMillis());
            if (existing == null) game.addLaw(fine); store.save(); ensureTimer();
            hideKeyboard(); window.dismiss(); render();
        });
    }
    private void activateLaw(Game.Fine fine) {
        store.sync();
        if (fine.mode == Game.STRICT) { confirmFine(fine); return; }
        if (fine.mode == Game.TIMED) {
            if (!game.toggleLawTimer(fine, System.currentTimeMillis())) { confirmFine(fine); return; }
            store.save(); ensureTimer(); render();
            if (fine.endsAt > 0 && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
            return;
        }
        if (fine.amountUsed >= fine.amountLimit) { confirmFine(fine); return; }
        withUi(new Ui(this, game.hue), () -> {
            LinearLayout content = column(); content.addView(label("Do you want to increase it by one?")); content.addView(help(fine.name));
            LinearLayout actions = row(); Button no = button("No", ui.border), yes = button("Yes", ui.border); addAction(actions, no); addAction(actions, yes); content.addView(actions);
            Dialog window = showDialog(content); no.setOnClickListener(v -> window.dismiss());
            yes.setOnClickListener(v -> { yes.setEnabled(false); boolean used = store.incrementLawAmount(fine); window.dismiss(); render(); if (!used) confirmFine(fine); });
        });
    }
    private View namedTimeField(EditText field, String name) {
        FrameLayout box = new FrameLayout(this);
        field.setHint("");
        field.setPadding(ui.dp(12), ui.dp(16), ui.dp(12), 0); box.addView(field, new FrameLayout.LayoutParams(-1, -1));
        TextView caption = text(name, 10, ui.muted); caption.setPadding(ui.dp(12), ui.dp(4), 0, 0);
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); box.addView(caption, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        return box;
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void addSwipe(View card, Runnable delete, Runnable edit) {
        card.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY; boolean horizontal, vertical;
            @Override public boolean onTouch(View v, android.view.MotionEvent event) {
                float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startX = event.getRawX(); startY = event.getRawY(); horizontal = vertical = false; return false;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (!horizontal && !vertical && Math.max(Math.abs(dx), Math.abs(dy)) > ui.dp(12)) {
                            horizontal = Math.abs(dx) > Math.abs(dy); vertical = !horizontal;
                            if (horizontal) { v.cancelLongPress(); v.getParent().requestDisallowInterceptTouchEvent(true); }
                        }
                        if (horizontal) { v.setTranslationX(dx); v.setAlpha(Math.max(.4f, 1 - Math.abs(dx) / v.getWidth())); return true; }
                        return false;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (!horizontal) return false;
                        v.setPressed(false); v.setTranslationX(0); v.setAlpha(1);
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP && Math.abs(dx) >= ui.dp(72)) {
                            if (dx > 0) delete.run(); else edit.run();
                        }
                        return true;
                    default: return false;
                }
            }
        });
    }
    private void showCompletedQuests() {
        store.sync();
        LinearLayout content = column(); content.addView(label("Quest trash"));
        content.addView(label("Deleted quests"));
        if (game.deletedQuests.isEmpty()) content.addView(help("No deleted quests."));
        else content.addView(help("Tap to restore a quest as a fresh activity. Rewards and deleted history stay unchanged."));
        Dialog[] window = new Dialog[1];
        for (Game.Quest quest : new ArrayList<>(game.deletedQuests)) {
            Ui palette = questPalette(quest);
            LinearLayout card = card(quest.name, "TAP TO RESTORE QUEST", "trash", palette); setEmojiIcon(card, quest.emoji, palette);
            card.setContentDescription("Restore deleted quest: " + quest.name); content.addView(card, new LinearLayout.LayoutParams(-1, ui.dp(64)));
            card.setOnClickListener(v -> {
                card.setEnabled(false); store.sync();
                if (game.restoreDeletedQuest(quest.id, System.currentTimeMillis())) store.save();
                window[0].dismiss(); render(); showCompletedQuests();
            });
        }
        content.addView(label("Completed today"));
        List<Game.Quest> completed = game.completedOneOffs();
        content.addView(help(completed.isEmpty() ? "No completed one-time quests today." : "Tap a quest to restore it and remove its reward."));
        for (Game.Quest quest : completed) {
            Ui palette = questPalette(quest);
            LinearLayout card = card(quest.name, "TAP TO RESTORE", "check", palette); setEmojiIcon(card, quest.emoji, palette);
            card.setContentDescription("Restore " + quest.name); content.addView(card, new LinearLayout.LayoutParams(-1, ui.dp(64)));
            card.setOnClickListener(v -> {
                card.setEnabled(false); store.sync();
                if (game.restoreOneOff(quest.id, System.currentTimeMillis())) store.save();
                window[0].dismiss(); render(); showCompletedQuests();
            });
        }
        Button close = button("Close", ui.border); content.addView(close, fieldParams());
        window[0] = showDialog(content); close.setOnClickListener(v -> window[0].dismiss());
    }
    private void deleteEntry(Game.Quest quest, Game.Fine fine) {
        withUi(quest != null ? questPalette(quest) : ui, () -> {
            LinearLayout content = column(); content.addView(label(quest != null ? "Delete quest?" : "Delete law?"));
            content.addView(label(quest != null ? quest.name : fine.name));
            content.addView(help("Delete this entry and its timeline history?"));
            LinearLayout actions = row(); Button cancel = button("Cancel", ui.border), delete = button("Delete", Ui.RED);
            addAction(actions, cancel); addAction(actions, delete); content.addView(actions);
            Dialog window = showDialog(content); cancel.setOnClickListener(v -> window.dismiss());
            delete.setOnClickListener(v -> {
                window.dismiss();
                store.sync();
                if (quest != null) game.deleteQuest(quest);
                else game.deleteLaw(fine);
                store.save(); render();
            });
        });
    }
    private void showMissedReview() {
        if (!resumed || dialog != null || deathPlaying || game.missedReviews.isEmpty()) return;
        Game.MissedReview review = game.missedReviews.get(0);
        if (deathReviewShown != review) {
            deathReviewShown = review; deathPlaying = true;
            flight.death(); store.sounds.play(Synth.FAILURE); Feedback.vibrate(this, true);
            effects.postDelayed(() -> {
                deathPlaying = false;
                if (resumed) showMissedReview();
            }, Feedback.motion(this) ? 1200 : 0);
            return;
        }
        LinearLayout content = column(); content.addView(label("You died."));
        content.addView(help(LocalDate.ofEpochDay(review.day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))));
        content.addView(label("You missed:"));
        for (String name : review.names) content.addView(label(name));
        TextView penalty = help("You lost\n" + Money.format(-review.coinsLost) + "\n−" + review.daysLost + " days");
        penalty.setTextColor(Ui.RED); content.addView(penalty);
        Button accept = button("Accept", Ui.RED); content.addView(accept, fieldParams());
        Dialog window = showDialog(content); window.setCancelable(false);
        accept.setOnClickListener(v -> {
            accept.setEnabled(false); long beforeDays = counterDays(), beforeCoins = counterCoins();
            game.resolveMissed(false, System.currentTimeMillis(), ZoneId.systemDefault()); store.save();
            window.dismiss(); render(); updateCounters(beforeDays, beforeCoins); animatePenalty(beforeDays, beforeCoins);
        });
    }
    private void manageEntry(Game.Quest quest, Game.Fine fine) {
        store.sync();
        manageOptions(quest != null ? quest.name : fine.name,
            () -> { if (quest != null) editQuest(quest); else editFine(fine); }, () -> deleteEntry(quest, fine),
            quest != null && game.isPaused(quest) ? () -> { store.sync(); cancelQuestTimer(quest); render(); } : null);
    }
    private void manageCategory(Game.Category category) {
        withUi(new Ui(this, category.hue >= 0 ? category.hue : game.hue), () ->
            manageOptions(category.name, () -> editCategory(category), () -> deleteCategory(category)));
    }
    private void manageOptions(String title, Runnable editAction, Runnable deleteAction) {
        manageOptions(title, editAction, deleteAction, null);
    }
    private void manageOptions(String title, Runnable editAction, Runnable deleteAction, Runnable cancelQuestAction) {
        LinearLayout content = column(); content.addView(label(title));
        Button nevermind = button("Nevermind", ui.border), edit = button("Edit", Ui.GREEN), delete = button("Delete", Ui.RED);
        content.addView(nevermind, fieldParams()); content.addView(edit, fieldParams()); content.addView(delete, fieldParams());
        Dialog window = showDialog(content);
        if (cancelQuestAction != null) {
            Button cancel = button("Cancel Quest", Ui.RED); content.addView(cancel, 2, fieldParams());
            cancel.setOnClickListener(v -> { window.dismiss(); cancelQuestAction.run(); });
        }
        nevermind.setOnClickListener(v -> window.dismiss());
        edit.setOnClickListener(v -> { window.dismiss(); editAction.run(); });
        delete.setOnClickListener(v -> { window.dismiss(); deleteAction.run(); });
    }
    private void deleteCategory(Game.Category category) {
        withUi(new Ui(this, category.hue >= 0 ? category.hue : game.hue), () -> {
            LinearLayout content = column(); content.addView(label("Delete category?")); content.addView(label(category.name));
            content.addView(help("Quests in this category will stay on the board."));
            LinearLayout actions = row(); Button cancel = button("Cancel", ui.border), delete = button("Delete", Ui.RED);
            addAction(actions, cancel); addAction(actions, delete); content.addView(actions);
            Dialog window = showDialog(content); cancel.setOnClickListener(v -> window.dismiss());
            delete.setOnClickListener(v -> { game.deleteCategory(category); store.save(); window.dismiss(); render(); });
        });
    }
    private void confirmFine(Game.Fine fine) {
        store.sync(); LinearLayout content = column(); content.addView(label("You have broken the law."));
        content.addView(label(fine.name));
        Game.Penalty penalty = game.nextFine(fine);
        TextView cost = help("Fine Amount\n−" + penalty.daysLost(game.days) + " days\n" + Money.format(-penalty.coins(game.coins))); cost.setTextColor(Ui.RED); content.addView(cost);
        addTimeline(content, Game.FINE_HISTORY, fine.id, fine.name);
        LinearLayout actions = row(); Button mistake = button("Mistake", Ui.RED), admit = button("I did it.", Ui.GREEN);
        addAction(actions, mistake); addAction(actions, admit); content.addView(actions);
        Dialog window = showDialog(content); mistake.setOnClickListener(v -> window.dismiss());
        admit.setOnClickListener(v -> {
            admit.setEnabled(false);
            store.sync(); long beforeDays = game.days, beforeCoins = game.coins;
            game.incurFine(fine, System.currentTimeMillis()); store.save(); window.dismiss(); render();
            Feedback.vibrate(this, true);
            showReceipt(fine.name + " reported\n" + "¤ " + Money.format(game.coins - beforeCoins).replace("€", "")
                + "\n▦ −" + (beforeDays - game.days), Ui.RED);
            android.text.SpannableString lines = new android.text.SpannableString(receipt.getText());
            for (String marker : new String[]{"¤", "▦"}) {
                int index = lines.toString().lastIndexOf(marker);
                Ui.Icon icon = new Ui.Icon(marker.equals("¤") ? "euro" : "calendar", Ui.RED);
                icon.setBounds(0, 0, ui.dp(18), ui.dp(18));
                lines.setSpan(new android.text.style.ImageSpan(icon, android.text.style.ImageSpan.ALIGN_BOTTOM), index, index + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            receipt.setText(lines);
            animatePenalty(beforeDays, beforeCoins);
        });
    }

    private void completeCard(Game.Quest quest, LinearLayout card) {
        if (completingCard) return;
        boolean firstToday=!quest.done(game.day);
        int[] location = new int[2]; card.getLocationOnScreen(location);
        rewardX = location[0] + card.getWidth() / 2f; rewardY = location[1] + card.getHeight() / 2f;
        if (!store.completeInstant(quest)) { render(); return; }
        if(!firstToday){render();return;}
        if (!quest.repeatable) Feedback.vibrate(this, false);
        completingCard = true; card.setEnabled(false);
        card.removeViewAt(0); card.addView(ui.icon("check", Ui.GREEN, 30), 0);
        card.setBackground(ui.panel(Ui.GREEN));
        if (!Feedback.motion(this)) { completingCard = false; render(); return; }
        card.animate().scaleX(.98f).scaleY(.98f).setDuration(90).withEndAction(() -> {
            card.animate().scaleX(1).scaleY(1).setDuration(100).withEndAction(() -> {
                if (quest.repeatable) { completingCard = false; render(); }
                else card.animate().alpha(0).scaleY(.6f).setDuration(120).withEndAction(() -> {
                    completingCard = false; render();
                }).start();
            }).start();
        }).start();
    }
    private void showNotices() {if(tab!=0)return;
        if(showingMustDoCompletion){store.notices.clear();return;}
        if (!resumed || receipt == null || completingCard || store.notices.isEmpty()) return;
        StringBuilder message = new StringBuilder(); long earned = 0;
        while (!store.notices.isEmpty()) {
            GameStore.Notice notice = store.notices.removeFirst();
            if (message.length() > 0) message.append("\n");
            if(notice.earned>0&&!game.completionLog.pending.isEmpty())continue;
            message.append(notice.message); earned += notice.earned;
        }
        if(message.length()==0)return;
        showReceipt(message.toString(), Ui.GREEN);
        if (earned > 0 && game.completionLog.pending.isEmpty()) {
            root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            if (Feedback.motion(this)) flight.reward("+" + Money.format(earned), rewardX, rewardY);
            animateReward(earned);
        }
    }
    private final Runnable hideReceipt = () -> { if (receipt != null) receipt.setVisibility(View.GONE); };
    private void showReceipt(String message, int color) {
        if (receipt == null) return;
        receipt.animate().cancel(); receipt.setText(message); receipt.setBackground(ui.panel(color));
        receipt.setVisibility(View.VISIBLE); receipt.setAlpha(1); receipt.setTranslationY(0);
        if (Feedback.motion(this)) {
            receipt.setAlpha(0); receipt.setTranslationY(ui.dp(-6));
            receipt.animate().alpha(1).translationY(0).setDuration(160).start();
        }
        effects.removeCallbacks(hideReceipt);
        android.view.accessibility.AccessibilityManager manager = (android.view.accessibility.AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        int timeout = Build.VERSION.SDK_INT >= 29 ? manager.getRecommendedTimeoutMillis(3500,
            android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT | android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS) : 3500;
        effects.postDelayed(hideReceipt, timeout);
    }
    private void animateReward(long amount) {
        if (!resumed || tab!=0 || amount <= 0) return;
        if (Feedback.motion(this)) {
            CoinFlight overlay = flight; View destination = coinTarget;
            float x = rewardX, y = rewardY;
            for (int i = 0; i < 6; i++) {
                final int index = i;
                effects.postDelayed(() -> {
                    if (resumed && tab==0 && flight == overlay) overlay.launch(destination, index, x, y);
                }, i * 200L);
            }
            coinsValue.animate().cancel(); coinsValue.setScaleX(1.1f); coinsValue.setScaleY(1.1f);
            coinsValue.animate().scaleX(1).scaleY(1).setDuration(240).start();
        }
        rewardX = rewardY = -1;
        store.sounds.play(Synth.COIN);
        animateCounters(counterDays(), counterCoins());
    }
    private void animatePenalty(long oldDays, long oldCoins) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastPenaltyAt > 900) {
            store.sounds.play(Synth.FAILURE);
            if (Feedback.motion(this)) {
                flight.fail();
                root.animate().cancel(); root.setTranslationX(0);
                android.animation.ObjectAnimator shake = android.animation.ObjectAnimator.ofFloat(root, "translationX", 0, -ui.dp(2), ui.dp(2), 0);
                shake.setDuration(180); shake.start();
            }
        }
        lastPenaltyAt = now;
        animateCounters(counterDays(), counterCoins());
    }
    private void animateCounters(long days, long coins) {
        if (counterAnimation != null) counterAnimation.cancel();
        if (!Feedback.motion(this)) { animating = false; updateCounters(days, coins); return; }
        long fromDays = displayedDays, fromCoins = displayedCoins;
        animating = true;
        counterAnimation = android.animation.ValueAnimator.ofFloat(0, 1);
        counterAnimation.setDuration(420);
        counterAnimation.addUpdateListener(value -> {
            float fraction = (float) value.getAnimatedValue();
            updateCounters(Feedback.interpolate(fromDays, days, fraction), Feedback.interpolate(fromCoins, coins, fraction));
        });
        counterAnimation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) { animating = false; }
        });
        counterAnimation.start();
    }
    private void stopAnimation() {
        if (counterAnimation != null) counterAnimation.cancel();
        animating = false;
    }
    private void updateCounters(long days, long coins) {
        displayedDays = days; displayedCoins = coins; countersInitialized = true;
        if (daysValue == null || coinsValue == null) return;
        changedText(daysValue, compact(days)); daysValue.setContentDescription(days + " days");
        changedText(coinsValue, Money.format(coins)); coinsValue.setContentDescription(Money.format(coins));
        coinsValue.setTextColor(coins < 0 ? Ui.RED : Ui.GOLD);
    }
    private void changedText(TextView view, String value) { if (!TextUtils.equals(view.getText(), value)) view.setText(value); }
    private static String compact(long value) {
        if (value < 1000) return Long.toString(value);
        if (value < 1_000_000) return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        if (value < 1_000_000_000) return String.format(Locale.ROOT, "%.1fm", value / 1_000_000.0);
        return String.format(Locale.ROOT, "%.1fb", value / 1_000_000_000.0);
    }
    private Dialog showDialog(LinearLayout content) {
        Dialog window = new Dialog(this); dialog = window;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); content.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(16));
        scroll.addView(content); scroll.setBackground(ui.panel(ui.border)); window.setContentView(scroll);
        window.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        window.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setOnDismissListener(v -> { if (dialog == window) dialog = null; });
        window.show(); fitDialog(window); return window;
    }
    private void fitDialog(Dialog window) {
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - ui.dp(16), ui.dp(560));
        window.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        View decor = window.getWindow().getDecorView();
        decor.setPadding(0, 0, 0, 0);
        decor.post(() -> {
            int maxHeight = getResources().getDisplayMetrics().heightPixels - ui.dp(80);
            if (decor.getHeight() > maxHeight) window.getWindow().setLayout(width, maxHeight);
        });
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); v.setBaselineAligned(false); return v; }
    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setIncludeFontPadding(false); v.setFontFeatureSettings("tnum"); return v;
    }
    private TextView label(String value) { TextView v = text(value, 15, ui.text); v.setPadding(0, ui.dp(8), 0, ui.dp(8)); return v; }
    private TextView section(String value) {
        TextView v = text(value, 11, ui.border); v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setLetterSpacing(.12f);
        v.setPadding(0, ui.dp(20), 0, ui.dp(12)); return v;
    }
    private TextView help(String value) { TextView v = text(value, 12, ui.muted); v.setLineSpacing(ui.dp(4), 1); v.setPadding(0, ui.dp(4), 0, ui.dp(12)); return v; }
    private Button button(String value, int color) {
        Button v = new BorderedButton(this, color); v.setText(value); v.setTextSize(14); v.setAllCaps(false); v.setTextColor(color);
        v.setMinHeight(ui.dp(64)); v.setMinimumHeight(ui.dp(64)); v.setMinWidth(0); v.setMinimumWidth(0); v.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        v.setStateListAnimator(null); v.setBackground(ui.button(color)); return v;
    }
    private EditText input(String hint, boolean number) {
        EditText v = new EditText(this); v.setTextColor(ui.text); v.setHintTextColor(ui.muted); v.setTextSize(16); v.setSingleLine(true);
        v.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        v.setHint(hint); v.setContentDescription(hint); v.setPadding(ui.dp(12), 0, ui.dp(12), 0); v.setBackground(ui.panel(ui.border));
        v.setMinimumHeight(ui.dp(64)); v.setSelectAllOnFocus(number); return v;
    }
    private CheckBox check(String value) {
        CheckBox v = new CheckBox(this); v.setText(value); v.setTextColor(ui.text); v.setTextSize(13); v.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        v.setMinHeight(ui.dp(64)); v.setButtonTintList(ColorStateList.valueOf(ui.border)); v.setBackground(ui.panel(ui.border)); return v;
    }
    private LinearLayout.LayoutParams fieldParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, ui.dp(4), 0, ui.dp(4)); return p; }
    private LinearLayout.LayoutParams spaced(int width, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width < 0 ? width : ui.dp(width), ui.dp(height)); p.setMargins(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)); return p;
    }
    private void addAction(LinearLayout parent, Button v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ui.dp(64), 1); p.setMargins(ui.dp(4), ui.dp(8), ui.dp(4), 0); parent.addView(v, p);
        parent.setGravity(Gravity.TOP); parent.setPadding(0, 0, 0, ui.dp(4)); parent.setClipToPadding(false);
    }
    private Integer number(EditText input, int min, int max) {
        try { int value = Integer.parseInt(input.getText().toString()); if (value >= min && value <= max) return value; }
        catch (NumberFormatException ignored) { }
        input.setError("Enter a value from " + min + " to " + max); input.requestFocus(); return null;
    }
    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}

