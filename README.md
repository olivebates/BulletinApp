# Bulletin for Android 1.94

Version 1.94 adds note creation emojis and category colors, a menu-only read-only toggle, persistent note locations, sticky Find, numbered lists, beige text and Find Beige results with return navigation. Notion notes now upload one way: app content replaces remote edits and app deletions archive their Notion counterparts. Notes drag previews animate neighboring rows.

Bulletin hides active time behind Tap to show until tapped, resets that reveal when leaving the tab, halves single-task bounties for existing and new tasks, and defers tool reward animations until Bulletin opens. An on-device reminder is scheduled for 20:00 local time when must-do tasks remain; notification permission is required, and Android may delay delivery during battery-saving modes. Boot and clock changes reschedule the alarm.

Tools now includes Youtube Bookmark, with a PDF-style library, automatic video titles, rename/delete/reorder controls, a video-only player with Back and bottom navigation, and playback positions included in the main save and Notion app-save backups. Playback pauses when leaving. Separate advertising endpoints are filtered; ads delivered through the video stream are not guaranteed to be blocked. Live playback and ad filtering require network/device verification.

Version 1.86 adds **Tools → Notes**, with nested Notion documents, local drafts, background sync, conflict choices, and a Notion Trash page. See [Notes setup and behavior](docs/notes.md) for connection instructions and current limitations.

Version 1.87 hides the shared Notes container and adds Notion app-save backups: four-hour foreground checks, the latest 20 backups in `BulletinBackupCodes`, and Settings setup/restore with a verified safety backup before restoring.

Version 1.88 adds a rich Notes editor with direct formatting, undo/redo, tappable checkboxes, and saved cursor/scroll positions. Notes keeps the bottom navigation visible, hides Sync once connected, and moves document selection to Settings → Notion settings.

Version 1.89 hides the Notes Back/status row and bottom navigation while the keyboard is open. Formatting uses seven matching square vector buttons; Numbered List and Checkbox controls are omitted. Notion settings now opens the API-token connection dialog with the setup instructions, replacing the document-selection dialog.

Version 1.90 makes Notes creation and Trash work immediately offline, queues Notion changes durably, opens conflict comparison automatically, and matches Bulletin's bordered forms and rearrange controls. The editor has a full-width Back row without save-status or word-count text. Notion setup includes links for all five steps and the required content permissions.

Version 1.91 makes setup steps 2, 4 and 5 plain text, adds the empty-editor placeholder, and gives Notes collapsible Bulletin-style Categories and an inset rearrange scrollbar. Category editing supports queued Notion renames and inherited app-side colors; Document editing supports queued renames and app-side emojis through the shared Bulletin picker. List scroll position is preserved across deletion and rebuilds.

Version 1.92 adds paragraph spacing, an editable title box with queued Notion renames, and a saved display-size slider under the Aa toolbar button. Category collapse states survive sessions, the syncing message disappears after completion, and Document edit buttons appear only inside rearrange cards beside Trash.

Version 1.93 adds **Emotion** and **Internal Conflict** under **Creatures** in the Creative Brief Generator, with 100 distinct offline entries each. The full dictionary now contains 2,100 entries. Both fields use the existing generation, repeat-avoidance, session restoration and save formats.

A native, offline quest board for Android 8.0 and newer. The app includes quest and fine boards, adjustable activity timelines, timers and manual completion, Euro balances, generated sound effects, and a balance graph.

## Build and update

Open the folder in Android Studio, or use Java 17, SDK platform 36 and build tools 35.0.0:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

This workspace also has a portable toolchain:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
```

The APK is `app/build/outputs/apk/debug/app-debug.apk` (version code 95, version name 1.94), also copied to `Bulletin-1.94.apk`. **Install over the existing app to retain progress.** Keep the same package ID (`com.bulletin.app`) and signing key. The build helper reuses `.tooling/android-user/debug.keystore`; retain that key when moving the workspace.

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Configure the SDK with `local.properties` or `ANDROID_HOME`. On macOS/Linux use `sh gradlew`. Notes/Notion and YouTube use network access. Offline tools need no account. XZ provides save compression.

## Timelines and balance graph

- The button immediately to the right of + switches between the board and timelines. Leaving Bulletin or Law closes its timelines. Timelines use one 48 dp row per quest or fine in both orientations. Add and timeline controls remain available during quests and rest.
- Markers show recorded completions and reports within the selected date range, without a three-month display limit. Closely spaced events stack and show their count. The complete recorded history is retained, including expired one-day quests; only the visible window rolls forward.
- A matching timeline appears directly above the action buttons in each quest briefing and fine confirmation.
- The Settings graph fills the remaining space and plots recorded balance changes, including negative balances, penalties, rewards and daily interest.
- New installations save the first-open date immediately. Versions 1.0/1.1 did not record a complete event log or a first-open timestamp. Their known last quest completion dates/counts are imported; older fine reports and past balance movements cannot be reconstructed. For these installations, the earliest retained saved day anchors the timeline, and full event/balance history begins with this update.

## Euro rewards and quest timing

Money is stored in integer cents and displayed with two decimal places. Existing balances, pending earnings and bounties convert at **10 old coins = ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬1.00**, including debt. This preserves their value relative to the former 10-coin base bounty. Conversion runs once; an untouched pre-migration snapshot is retained.

- The base daily bounty is EUR1.00. Each 25 minutes of completed optional timed focus raises the base by 10%, rounded up to a cent. Waiting grows a quest quote by 1.2% per whole hour, capped at +300% extra (4 times its baseline).
- **Must Complete** quests pay exactly twice the matching optional reward, after cent rounding and waiting bonuses, and do not raise the base bounty. Singles use a 15% optional baseline before doubling (1.94 onward).
- Repeatable quests use the selected energy preset, including existing quests with older duration overrides. Running and paused sessions retain the duration and reward reference already accepted.
- The duration and reference time are captured at acceptance, so changing Settings does not alter a running quest's quoted reward.
- The creation/edit form has no per-quest duration field. Single quests complete immediately, or show one Instructions box and Complete Quest when prerequisites exist. Hold a prerequisite or its drag handle to reorder it; surrounding rows animate into place. Completed single quests leave the board with a celebration. The trashcan lists all completed single quests that day and lets you restore any one. Must Complete and Multiple Times a Day hide each other.
- **Pause** saves an active quest timer until resumed, canceled, or local midnight. **Cancel**, to its left, discards the timer without a reward or completion. A paused quest has Cancel Quest under Nevermind in its long-press menu. Paused timers reset at midnight, including while offline. Time already spent remains in focus totals. Canceling one paused quest does not interrupt another active quest or law timer.
- **Recurring** repeats a quest with a configurable number of intervening days: 0 (the default) means every day; 1 means every other day. Existing recurrence schedules are retained.

Swipe right on a quest or law to delete it, or left to edit it. Toggle Rearrange to drag quests or laws; neighboring entries animate into the new positions. Outside rearrange mode, long-press opens Nevermind / Edit / Delete. Deleting a quest or law also deletes its timeline history. Reporting a law resets the base bounty to ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬1.00.

## Penalties and interest

- **Laws:** the first report of each law each day costs the greater of EUR3.00 and 10% of the positive balance. Each repeat of that law costs the previous actual charge plus 10%, rounded up to a cent, with the same EUR3.00 minimum. Charges persist across restarts and reset at local midnight.
- **Unfinished Must Do quests and categories:** each outstanding required quest or category quota slot counts once at midnight. The first costs the greater of EUR3.00 and 10% of the positive opening balance; each extra slot costs the preceding charge plus 10%, rounded up. At a zero opening balance, one/two/three misses cost EUR3.00/EUR6.30/EUR9.93. Each day starts a new sequence, including offline days.
- The EUR3.00 minimum applies even at zero or negative balances and can create or increase debt. Displays show actual deductions; each law confirmation quotes that law's exact charge. Daily rent retains its separate rules.
- Day losses retain their existing rules: laws lose 2 days, doubling per report that day; `n` missed requirements lose `n(n+3)/2` days. Day deductions stop at zero.
- Gain one day only if at least one quest was completed that day and no required quests are unfinished. After missed-quest deductions, each midnight a positive balance earns **0.04% interest rounded up to a cent**, followed by daily rent. Debt earns no interest. Offline days settle chronologically, once each.

## Layout, persistence and sound

Buttons have a complete two-physical-pixel border. Quest and timeline cards are 48 dp high; the plus, view toggle and navigation buttons are 32 dp. Bulletin cards have no subtext; required quests are highlighted and show ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“Fine if unfinishedÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â. The Euro sign and money amount are rendered together as a single text string. The footer shows the unfinished-quest fine only while required quests remain. Landscape hides the title box and places the daily focus total at the right of the footer.

Settings save valid changes automatically. Quest and rest durations come from the six fixed energy presets. Only timed completions start rest. Rest displays its countdown, and the add/timeline buttons remain usable. Timed sessions use a foreground service and a bounded wake lock. Reopening after a force-stop reconciles timestamps and rewards; Android cannot play sounds while an app is force-stopped.

Schema 15 preserves paused timer progress as well as quests, prerequisites, fine definitions, daily state, active sessions, history, balances and timing data. Atomic preference writes retain the previous valid save. Pre-migration snapshots are retained; unreadable or unsupported newer saves are preserved rather than overwritten. Uninstalling or clearing app storage removes local data, and cloud backup remains disabled.

Victory, rest, reward and failure sounds are synthesized locally. Reward coins launch over one second; fines trigger a red flash, shake and animated deductions. Physical-device audio has not been auditioned.

## Verification

Version 1.94 passes all **380 unit tests** and **56 Android emulator assertions** for Notes UI, formatting, read-only mode, Find and beige-result navigation. Android lint reports **0 errors and 8 warnings**. The debug APK is built with the existing package and signing configuration. Live Notion round trips, YouTube playback/ad behavior, and delivery of the evening notification on a physical device have not been verified.

## Bounty caps, minimum fines and Must Do editing (1.80)

The hourly waiting rate remains 1.2%, but its maximum extra reward is halved from +600% to +300%, making the total cap four times the baseline. Must Do rewards are exactly double the matching optional reward after rounding and waiting growth. This includes active/paused tasks and category-required tasks. Singles retain the optional 30% baseline before doubling. Accepted durations and historical reward/refund amounts are preserved; required focus still does not increase the base bounty.

Both law reports and missed Must Do/category slots have a EUR3.00 minimum per offense, including at zero/negative balances. Initial 10% charges still apply when higher; the existing 10% escalation continues for repeat law offenses and successive missed slots. Fines can create debt independently of rent's debt floor. Midnight settlement and missed-task correction preserve actual charges, interest and rent differences.

An existing category's Required Tasks field, All control and Must Do checkbox remain locked until its daily target is fulfilled or three qualifying completions have been counted, whichever comes first. Numeric quotas use their existing completion count; All quotas use distinct completed tasks. New, optional, empty and zero-quota categories remain configurable. A task's own Must Complete checkbox cannot be cleared before that task is completed today. Names, colors and other unrelated edits remain available. Save-time checks enforce the same restrictions.

Blocked attempts retain the original values and show exactly: "You must complete the Must Do quests before you can change this." Eligibility persists across relaunches and resets with the day.

Validation: all 333 unit tests pass, APK assembly succeeds, and lint has 0 errors with 7 existing warnings. Emulator checks covered each locked category control, the exact popup, retaining an unfinished task's Must Complete flag, unlocking category amounts after three completions, and displayed EUR0.60 required-single rewards/EUR3.00 law minimums.

The final emulator check also verified saving a completed task with Must Complete removed. Category headings now have an explicit 32 dp height so removing the last standalone Must Do cannot stretch a category heading over the task list.

## Must Do calendar and PDF gestures (1.81)

The daily hold wave now has a maximum radius 40% smaller than before. Both completion screens highlight the active square with a bright yellow outline and dark yellow fill. The Must Do calendar's active square is twice the size of the surrounding boxes, with extra row space so it cannot overlap them. The normal top and bottom board bars remain visible, while rest instructions and countdown stay off the completion content.

Must Do cards show Fine if unfinished directly after MUST DO and display their ordinary bounty at the right. Finishing the final outstanding requirement queues a separate daily Must Do calendar after any task acknowledgement, including when a repeated completion fills the final category slot. Its yellow heading is exactly You completed your Must Do's! Three centered columns show real calendar days, including February's actual length, with small stacked uppercase month labels. Missed days remain empty and future squares are dimmed. Today uses the same hold, haptic, collapse, spring and 0.2-second check animation, with half the daily wave's maximum radius.

The first earned milestone anchors the first month at the left, followed by the next two months. The current month moves to the middle in month two and to the right in month three. Later months shift the window left, keeping the current month at the right. Daily milestones, their anchor and acknowledgements survive compressed saves and restarts; acknowledgement never changes rewards or rest deadlines. Restoring a completed Must Do invalidates today's milestone when it reopens outstanding requirements. Tracking starts with this update because historical changes to requirements cannot reliably be reconstructed from old quest events.

PDF controls start hidden and appear on double tap, with Back at the left and Invert/Sepia at the right. Single taps and scrolling do not reveal them. Pinch zoom remains available; double tap now reveals controls rather than zooming. Each document shows Double tap to bring out controls. on its first opening. An accessibility action also reveals the toolbar.

Validation: all 342 unit tests pass, APK assembly succeeds, and Android lint reports 0 errors with 7 existing warnings. Emulator checks covered task-to-calendar ordering, direct calendar display after a repeat, the rolling three-month history, enlarged highlighted Today, retained bars, held wave, acknowledgement persistence, Must Do bounty layout, first-open PDF hint, hidden controls on reopening and double-tap toolbar reveal. Physical-device sound and vibration feel remain unassessed.
## Compact Must Do celebration (1.82)

The Must Do celebration now groups its warm yellow heading, a large centered check-off square, and three compact month panels into a single centered composition. Horizontal month labels sit directly above seven-column calendars, aligned Monday through Sunday with blank leading cells and each month's actual day count. Completed days have soft green fills and checks, missed days have muted outlines, and future days are faint. The current month panel and today's cell use warm yellow accents.

The large square has a translucent fill, rounded corners and a soft golden glow. Holding and releasing it retains the wave, vibration, collapse, spring and 0.2-second check animation. Its checkmark and today's small calendar cell draw together. The three-month progression, saved acknowledgements, rewards, rest deadlines, and normal top/bottom bars retain their existing behavior. The composition scales to the available viewport without scrolling or extra explanatory labels.

Validation: 342 unit tests pass; APK assembly and lint succeed (0 errors, 7 existing warnings). Emulator checks verified portrait and landscape fit, retained bars, rolling month history, synchronized large/small checkmarks, and successful acknowledgement through the large checkbox. Original emulator progress was restored.


## Larger hold circles (1.83)

Both completion screens fit their hold wave to the nearest window edge from the checkbox center, reserving a 12 dp margin and space for outward wave peaks. This replaces the smaller fixed daily and half-size milestone limits. APK assembly, unit tests and lint pass.


## Full hold waves and continuing confetti (1.84)

Both hold circles now use the largest radius that fits the window with a 12 dp margin, including outward wave peaks. The wave center shifts inward as necessary while growing, then returns to the checkbox as it collapses. This lets the bottom daily checkbox reach the full size. The effects overlay is retained across screen renders, so daily and Must Do confetti finishes falling naturally across transitions. Build, unit tests and lint pass; emulator verification confirms confetti remains on the rest screen after acknowledgement.


## Fixed-center daily hold circle (1.85)

The daily hold circle stays centered on its checkbox and can extend below the window. Its maximum radius is 20% smaller than in 1.84. Must Do circle fitting and continuous confetti retain their existing behavior.

Validation: APK assembly, unit tests and lint pass (0 errors, 7 existing warnings).


