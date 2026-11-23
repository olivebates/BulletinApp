# YouTube Bookmark and power usage (1.96)

The bookmark library and playback positions remain part of the main save. The last library/video location is remembered separately as tool navigation state, including across restarts. Back from a video opens its library and keeps audio playing; Back from the library returns to the tools menu. Tapping Tools inside a tool also opens the tools menu. Returning to Tools from another tab restores the last open Notes or YouTube screen.

One `YoutubePlaybackService` owns the browser. Rotation does not recreate it. The browser remains attached to the player window, or to a covered host behind another Bulletin screen, so changing screens does not discard its playback session. Activity context references are detached when their owner is destroyed. Background playback uses a media foreground service with Play/Pause and Stop notification controls. Chromium manages audio focus; Bulletin pauses when headphones disconnect. Removing the app task stops playback and destroys the browser. Opening a saved video after an app restart restores its position without automatically starting audio.

The app samples position every second while viewing the video and every five seconds in the background. Small checkpoints are stored at most every five seconds; full game saves happen at most every 30 seconds during playback, plus pause/stop. These checkpoints are merged after process death and discarded when importing a different save. Abrupt termination can lose the last few seconds. A wake lock is renewed in short intervals only while the browser reports active playback, and released when paused/stopped.

Background YouTube behavior depends on Android System WebView and YouTube. Early document scripts suppress visibility-triggered web pauses on the YouTube origins; devices whose WebView lacks document-start support retain ordinary foreground playback. The app does not bundle Brave or an extraction library. It does not expose a JavaScript-to-Java bridge to YouTube. Separate advertising domains remain filtered, but ads carried in the video stream are not guaranteed to be blocked.

## Power changes

- Notes no longer upload after each edit, every 30 seconds, or through a periodic 15-minute job. Leaving Tools or backgrounding/locking queues one network-constrained job if documents changed. Successful documents are skipped thereafter; failed changes retry with backoff.
- Failed automatic Notion app-save backups wait at least 15 minutes between attempts. The existing four-hour backup cadence remains.
- Whole-save video writes are reduced from once per second to the checkpoint schedule above.
- App-open/app-close backups follow application foreground transitions rather than each internal activity transition.
- The main UI checks time/state once per second instead of four times. Notes no longer poll an idle sync indicator.
- Countdown notifications update less often while the display is off, while still scheduling the next quest/rest/law deadline. Existing timer and meditation timing requirements are preserved.

Battery improvement has not been quantified on physical hardware. Streaming still consumes power; this browser approach does not guarantee an audio-only network stream in the background.

## Verification

Unit tests cover changed-document uploads, unchanged-document skipping, failed replacements after renames, restart recovery, and preservation of edits made during upload. Notes device checks cover read-only collapse, excluding hidden text from search, restoring text after unlocking, and conditional Find Beige visibility. `VideoPlaybackInstrumentation` exercises a real local WebView audio stream across rotation, Back, tab navigation, Home, screen lock, pause/resume, saved positions, and task removal. Its optional `-e live true` probe reports YouTube results separately from those deterministic checks.

Final build verification: 387 unit tests, 71 Notes UI assertions, and 15 real WebView audio lifecycle assertions passed. Android lint reports 0 errors and 11 warnings. GithubSrc mirrors the complete source and test trees.

The available emulator runs Android 16 with WebView 133. Physical Samsung SM-G990B2/DS Android 15 behavior remains unverified. Live YouTube testing showed rotation continuity and advancing playback with the screen off; the background stream also buffered, so uninterrupted playback across all devices is not claimed.
