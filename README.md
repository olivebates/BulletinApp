# Bulletin for Android 1.73

Standalone source project for the offline Android Bulletin app. Minimal (Low) energy uses a 7-minute quest and a 2-minute rest.

## Build

Open this folder in Android Studio and let it sync. Install Android SDK Platform 36 and SDK Build-Tools 35.0.0 when prompted. Use JDK 17 or Android Studio's compatible Gradle JDK. The Gradle wrapper downloads Gradle 8.13; the first build needs internet to download dependencies.

Android Studio creates local.properties for your SDK location. For a command-line build, set ANDROID_HOME to your Android SDK directory and JAVA_HOME to your JDK, then run:

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

macOS/Linux:

```sh
sh ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is generated at app/build/outputs/apk/debug/app-debug.apk. Application ID: com.bulletin.app. Version code: 74. Minimum Android API: 26.

## Contents

- app/src: Java source, Android manifest/resources, offline emoji assets and their Unicode license, and unit tests.
- app/build.gradle and root Gradle files: Android build configuration and dependencies.
- gradle/wrapper, gradlew, gradlew.bat: Gradle wrapper, including its required JAR.
- .gitignore: excludes build outputs, caches, local SDK paths, APKs and signing keys.

Upload the contents of this folder as your GitHub repository root. No downloaded SDK/JDK, generated files or existing APKs are needed in GitHub.

## Signing and updates

This source export intentionally excludes signing keys. A new computer normally generates a different debug signing key, so an APK built there cannot update an existing installation signed with the original key. Keep the original signing key privately if you need updates that preserve existing app data. Do not commit it to GitHub.
Verified from this standalone folder: debug APK build, all 235 unit tests, and Android lint (no errors).

Version 1.34 corrects column in the heatmap description, starts analytics categories expanded with independent saved collapse states, and adds a small color-only reset symbol beside the Settings hue slider.

Version 1.35 adds separate quest Pause/Cancel actions, Cancel Quest in paused menus, midnight reset for paused attempts, and Today/Week/Month/3 Months/Year range buttons for both quest and law pie/bar charts.

Version 1.36 includes the supplied OGG law alarm, Cancel Quest label, a saved two-handle balance date range with five-year retention, maximum-compression saves compatible with legacy JSON, and 30 dated backups on opening/backgrounding and every two continuous foreground hours. Settings supports confirmed backup restores and copy/paste export/import. A current-state backup is required before a restore or import; invalid saves are rejected before replacement. Exports contain gameplay data, not the separate backup archive.

Version 1.37 selects the shortest of B1 and 24 DEFLATE/LZMA/LZMA2 combinations, optionally using lossless string dictionary/integer delta packing and a second compressor. B3 uses denser printable encoding, length validation and CRC32; plain JSON/B1/B2 saves remain supported. Regular saves commit immediately and refine in the background with protection against stale overwrites. Exports and snapshots use the full search. The test save shrank from 1,554 to 1,018 characters; actual savings depend on data. Backups/Export/Import now sit above Balance, and the dropdown says Backups without a count.

Additional dependency: org.tukaani:xz:1.12 (pure Java, 0BSD), https://tukaani.org/xz/java.html . Gradle downloads it on the first build.

## Version 1.38: compact saves and full-history analytics

Schema 18 uses shared strings, packed UUIDs, short IDs for new entities, short field keys or dense arrays, omitted defaults, relative timestamps, and reconstructed derivable values. JSON is minified before the existing compression pipeline. Existing entity IDs and references remain intact. A multi-year test fixture's raw JSON shrank by about 85% before compression.

History older than three calendar months is aggregated daily; history older than one calendar year is aggregated weekly (Monday-based, with partial boundary weeks). Completion counts, recorded money totals and focus duration are preserved. Older individual timestamps and transaction descriptions are replaced by summaries. Unknown money in legacy history stays unknown. Balance summaries preserve opening, closing, minimum and maximum values, with the existing five-year retention. Recent undo and running/paused quest state remain intact.

Timeline has independent saved quest/law date ranges, with one shared two-handle slider controlling every row on its page. There is no display age limit. A selection overlapping an aggregated period includes that whole period. Pie charts now offer All Time and use contrasting colors and slice separators. Existing active-quest/law analytics filters remain in effect.

Old JSON and B1/B2/B3 saves remain readable. Before the first schema-18 upgrade, an untouched safety snapshot and a dated Before history compaction backup are retained. The schema version remains explicit to protect against loading future formats. Save imports and backup restores still take a safety backup first.

Validation: 235 unit tests pass; Android lint reports no errors. Emulator checks covered legacy migration, shared timeline filtering and persistence, All Time totals, contrasting quest/law charts, compact import and old-backup restore.

## Version 1.39: pie labels and outline colors

Pie slices display small two-line labels (name, then focus duration or law report count). Nearby labels are spread apart with short connectors, and long names are shortened to fit. Slice and bar colors retain the GUI outline saturation and brightness while shifting hue. Validated with 235 unit tests and Android lint.


## Version 1.40: centered percentage labels

Pie labels show percentages instead of durations or counts, centered within each sector. Slices below 10% use a single radial line with the name, middle dot, and percentage; larger slices use two centered lines. Fill brightness is reduced from 77% to 54%, keeping consistent saturation and varying hue. Bar colors match. All 235 tests and lint pass, with Android visual verification.


## Version 1.41: Dead energy timing

Dead now uses a two-minute quest and a 40-second rest. New timed sessions use these durations and proportional bounty; accepted running/paused sessions keep their captured durations and rewards. Balance retains individual records for three calendar months, daily summaries thereafter, weekly summaries after one year, and five years of history. All 235 tests and Android lint pass.


## Version 1.42: alarm volume

All synthesized effects, completion/rest alerts, and alarmsfx.ogg use alarm audio attributes, so they follow the system alarm volume. The phone volume buttons control alarm volume while Bulletin is open. The ongoing countdown notification remains silent; its completion sounds are played through the same alarm audio path.


## Version 1.43: default volume buttons

Removed Bulletin's alarm volume-button override so Android handles the volume buttons normally. Sound effects and timer alerts still use alarm audio attributes and the alarm volume setting.


## Version 1.44: timeline intensity

Individual quest completion markers range from maroon (Dead) through Minimal, Relax, Normal and Extra to the original yellow (Extreme), based on the energy preset captured at acceptance. That preset survives pause/resume and save/reload even if the current preset changes. Older sessions with unknown energy and law reports retain yellow. Daily summaries use each quest's total recorded focus time that day, normalized against the highest quest/day total across all recorded daily focus data. Weekly summaries use weekly totals and a separate weekly maximum. Date selection and two-option mode do not affect these scales. Missing historical focus totals retain yellow. Existing schema-18 field tables are unchanged; optional metadata uses the existing extension mechanism and legacy saves remain readable.

Validation for 1.44: 241 unit tests pass, including energy capture, pause/reload, old saves, aggregation totals and timeline color scales; Android lint passes.


## Version 1.45: historical session midpoint

Quest sessions with unknown historical energy now use the exact midpoint of the maroon-to-yellow gradient. Known energy colors and time-based daily/weekly summary colors are retained; law reports remain yellow.

## Version 1.46: Tools and Choice Randomizer

Tools sits between Law and Settings. Its pages hide the board header and both board control bars, retaining the bottom navigation. Choice Randomizer opens a collection of named lists. The fixed + button creates a list; the adjacent minus button toggles removal mode, where tapping a list removes it. Each list has editable items, add/remove symbols and drag handles, with animated reordering and scrolling. No explanatory copy is added to these pages.

Randomize Choices stays fixed above the bottom navigation while the editor scrolls. Toggling it on shows two distinct nonblank entries (one if the list only contains one). Selecting a choice flashes it green for 350 ms and replaces the pair. Unselected items take priority; once all have been selected, the oldest selections are offered first. When equally eligible unseen items exist, the previous pair is avoided when possible. Toggling off starts editing again; the next session resets selection history. Each list retains its own session while navigating during the same app run.

Named lists, item contents and order are included in compressed saves, exports and backups. Empty/older saves need no migration; optional data uses the existing schema extension mechanism. New list IDs use the same compact ID counter. Back navigates from items to named lists to Tools, then Bulletin.

Validation: all 247 unit tests pass; lint reports no errors. Native Android checks covered list creation/deletion, item addition/removal, dragging, two-choice refresh, the green flash, fixed controls while scrolling, portrait/landscape, saved order after restart and update, Settings and Back navigation.
## Version 1.47: consistent Tools styling

Tools retains its title card; named-list and item pages use Choice Randomizer as their title in both orientations. Named-list creation uses the quest dialog's outlined container, text field, padding and action layout, with Cancel/Create buttons. Fixed named-list actions are Remove on the left and Add on the right. Bottom actions have eight-unit margins on all sides.

Choice editor rows match quest prerequisite dimensions, outlined text fields, fonts and spacing. Long press in a text field performs normal Android text selection; reordering uses the left drag handle. Randomized options use the standard GUI border color and app off-white text. The fixed bottom toggle is now Randomize, with Back to its right. Back returns to the named-list page.

Validation: all 247 unit tests pass and lint has no errors. Android checks verified the outlined dialog, title cards, selection toolbar, handle-only reordering, Randomize/Back actions, colors and final margins in portrait and landscape.
## Version 1.48: restore choice editing and Tools palette

Choice editors remember their own scroll offset, active text field, cursor/selection and keyboard visibility when backgrounded and resumed. Position is captured before focus and keyboard are lost; restoration waits for layout, prevents automatic focus scrolling from replacing the saved offset, and preserves normal scrolling once touched. The same per-list editor snapshots are included in Android Activity state for recreation. They are separate from the choice data and do not alter the save format. Back navigation also preserves editor position.

Tools and all subpages/dialogs use the app hue shifted clockwise by 20 degrees, wrapping at 360. The main app hue is unchanged. Named-list actions are Back / Remove / Add; item actions are Back / Randomize.

Validation: 249 unit tests pass and lint has no errors. Android checks used a 24-item list with the keyboard shown, compared exact field bounds before/after backgrounding, and verified continued typing at the original cursor and restored field. Activity-state restoration, per-list Back navigation, both action orders and the shifted palette were also checked.
## Version 1.49: keep Tools context across tabs

Switching tabs no longer resets Tools to its home page. Returning restores the same subpage and selected named list, the named-list scroll offset, or the item editor's scroll/focus/cursor/keyboard state. The active randomizer remains enabled with its exact pair and selection history; only explicitly toggling it off ends that session. Named-list removal mode also remains selected until changed or backed out of.

Named-list positions are restored after layout. Per-list randomizer session snapshots (current options and last-selection order) join the existing editor snapshots in Android Activity state, so recreation can restore a valid session. Invalid/stale snapshots are ignored safely. These temporary UI/session snapshots do not change the app save format.

Validation: 251 unit tests pass and lint has no errors. Native Android checks compared exact named-list and editor field bounds across tab changes, verified the active field/cursor with continued typing, and confirmed the same two choices and prior-selection avoidance after returning from Bulletin, Law and Settings.

## Version 1.50: faster waiting bounty and Tools hue

Unfinished quest bounty growth is tripled from 0.4% to 1.2% of the current bounty per complete hour, compounded, with the existing 700% baseline cap and completion reset. The cap is reached at 164 complete waiting hours. Existing waiting timestamps remain intact and use the new rate. Tools and its subpages/dialogs use a total hue offset of +55 degrees (another +35 beyond the previous +20), wrapping at 360. No save-format changes.

Validation: all 251 unit tests pass, including whole-hour growth, the cap boundary, energy scaling, completion/reset, undo, offline completion and save reload. Android lint reports no errors.

## Version 1.51: Creative Brief Generator and Advanced quests

Choice Randomizer item rows and option buttons now use the same 48 dp height as Bulletin quest cards. Tools also includes Creative Brief Generator, with independent named lists and Back/Remove/Add actions matching the existing tool. Brief lists contain selectable field categories, with drag handles and remove buttons. Edit-mode actions are Back/Add/Generate; generated mode shows Back/Generate and a fixed New Brief button above them. Toggling Generate off restores the category editor.

The category dialog groups Theme/Time Period/Ambience under General, Location/Architecture/Scale under Landscapes, and Animal/Clothes Style/Body Type/Age/Personality under Creatures. General starts expanded, the other groups collapsed. Group states persist during the tool session. Options are quest-height buttons; Nevermind remains fixed beneath the scrollable list, including after rotation.

The authored offline dictionary contains 1,900 unique prompt entries: 500 each for Theme and Ambience, and 100 for each of the other nine categories. Entries include short phrases where useful, such as time periods and clothing styles. Generated briefs show each category in bold with its word or phrase indented on the next line. New Brief rerolls the prompts, avoiding the prior value when possible and avoiding duplicate words for repeated categories while unused words remain.

Brief field order and named lists are included in saves, exports and backups. Generated briefs and scroll positions are retained during tab navigation and included in Android Activity session state. Existing save versions and frozen schema-18 tables remain readable; optional new data uses the existing extension mechanism. Future-save version checks run before parsing new tool data.

Quest Color, Multiple Times a Day, Timed, Must Complete, Recurring, Days between quests and First appearance are now under Advanced in quest creation/editing. The group starts collapsed and remembers its expansion state in the save, even after canceling or restarting. Existing checkbox visibility rules and whole-dialog hue updates remain intact.

Validation: all 260 unit tests pass; lint reports no errors. Android checks covered dictionary-backed generation and rerolls, bold/indented output, field selection/reordering/deletion, default and remembered picker groups, cancellation, pinned landscape controls, tab sessions, saved data after update/restart, exact quest-height rows, and Advanced persistence across dialogs and restart.
## Version 1.52: named-list rearranging and brief visibility

Long-press a name in either Choice Randomizer or Creative Brief Generator to enter rearrange mode. Rows show the Bulletin-style six-dot grip, pencil and trash controls. Drag the grip or hold the row to reorder; neighboring rows animate, edge scrolling accelerates near/outside the list, and canceled drops restore the original order. The pencil renames the list while keeping its ID, items and session. Trash asks for confirmation before deleting the list and its items. Rearrange mode has one full-width Back button, which exits the mode without leaving the names page. Normal names pages now have Back/Add; the footer Remove button has been removed. Modes are independent for the two tools and retained during tab navigation.

All 1,900 dictionary entries are capitalized. The 500 Theme entries are drawing genres, world settings and aesthetics, including Steam Punk, Fantasy and their subgenres; abstract concepts and drawing-media prompts have been replaced. Other category counts remain unchanged.

Generated briefs have a square eye button fixed at the top right below the title card. Closed hides the category labels; open displays the bold labels above the words. Each new Generate session starts closed. New Brief and tab navigation retain the eye choice during the current session. Session snapshots include visibility and can read older snapshots without eye metadata with labels hidden. Saved named-list data remains compatible with previous versions.

Validation: all 261 unit tests pass; lint reports no errors. Android checks covered both long-press rearrange modes, edit/delete controls, reorder persistence after restart/update, deletion cancellation/confirmation, retained list contents after renaming, footer removal, the square eye position, hidden/open labels, per-session reset, capitalized words and genre-based themes.
## Version 1.53: saved eye preference and Landscapes categories

The brief eye defaults open when no preference has been saved. Its open/closed choice is now a tool-wide preference stored in the app save, included in exports and backups, and retained across new generation sessions and app restarts. New Brief, Generate toggling and cached session restoration use the saved choice. Old saves without the optional preference default open; schema-18 field tables are unchanged.

The category picker now contains Landscapes and Creatures. Landscapes lists Theme, Time Period and Ambience first, followed by Location, Architecture and Scale. General has been removed. Landscapes starts expanded and Creatures collapsed for a fresh tool session. Older three-group Activity states merge the former General/Landscapes expansion states safely. Nevermind remains fixed below the scrolling options.

Validation: all 262 unit tests pass; lint reports no errors. Native Android checks verified the exact category order and removal of General, the initially open eye, and both saved eye states across Generate toggles, fresh sessions and cold restarts.

## Version 1.54: individual heatmap intensity

Each quest heatmap now scales its tile intensity against that quest's own highest daily focus time within the displayed year. Law heatmaps similarly use their own highest daily report count. Each heatmap's peak reaches full intensity independently; empty days retain the existing muted appearance. Other charts, timeline coloring and retained data are unchanged.

Validation: all 263 unit tests pass; lint has no errors. Regression checks cover different-sized quest/law peaks, proportional intermediate intensities, empty days and exclusion of dates outside the heatmap year.

## Time-based bounty growth (1.55)

The base bounty grows by 10% (rounded up to a cent) for each 25 minutes of completed optional timed quests. Partial time carries between completions; a long quest can earn multiple increases. Untimed completions, required quests, rest, canceled quests and time spent paused do not add growth. Completed work before a pause is credited when that quest finishes. Fines and the new day reset the progress. Restoring a completed one-time quest removes its time credit and recalculates growth while preserving later credits. Existing earned increases and older saves remain compatible, including active and paused timers.

Validation: 274 unit tests passed; debug APK build and Android lint passed with no errors.
## First quest and tool introductions (1.56)

Choice Randomizer and Creative Brief Generator show introductory text in the GUI outline color while their names list contains zero or one entry. The first quest creation dialog hides Advanced. Bottom navigation stays hidden until a quest has been created; creating the first quest reveals it after a 500 ms delay with a 350 ms linear slide that moves the upper bars upward. Existing installations with prior quests retain navigation immediately, including after deleting their quests.
## Guidance and timeline presets (1.57)

New-quest milestones explain the dice at quest 3, rearranging at quest 4, and categories at quest 7 if no category has been created. Category creation is remembered after deletion; older saves with existing categories infer that history. Enabling Must Do in a category displays its explanation while keeping the editor open. Both timelines have Today, Week, Month, 3 Months and All Time presets above their existing saved custom range slider. The summary text beneath the slider and energy-button long-press tooltips have been removed.
## First energy-button tap (1.58)

The first energy-button tap shows the requested Pomodoro explanation after 400 ms, including a tap on the already-selected preset. Rapid taps schedule only one explanation. If the app is backgrounded or a dialog is open, it waits until the app is resumed and the dialog is closed. The shown state persists in compressed saves; old saves default to not yet shown.
## Revised first-quest onboarding (1.59)

Before the first quest, + opens quest creation directly and the empty-board prompt points to the bottom-left button. The first-creation message encourages adding more quests; the rearrange tip now appears after quest 2 and refers to the arrow button. The first successful timed quest start shows the focus explanation once, with its shown state saved using a backwards-compatible optional field. Tutorial bodies now use 16sp text. Updated energy tutorial and Creative Brief Generator introduction wording.
## Second-quest navigation reveal (1.60)

Bottom navigation stays hidden until the second quest has been created. It remains hidden while the second-quest rearrange tutorial is open; closing that dialog starts the 500 ms delay, followed by the existing 350 ms upward slide that pushes the other bars upward. Existing saves with two or more created quests display navigation immediately.

## Activity field (1.61)

The quest creation and editing name field is now labeled Activity.

## Welcome dialog (1.62)

New installations show the requested Welcome message on first launch, with the tutorial dialog styling and 16sp text. Its shown state is saved so it does not repeat. Existing older saves are treated as already welcomed; updating the app preserves progress without restarting onboarding. The optional save field remains compatible with schema 18 packing.

## Fourth-quest navigation (1.63)

Bottom navigation now unlocks after four quests have been created, with the existing 500 ms delay and upward slide. The rearrange tutorial still appears after quest two. Existing saves with four or more created quests show navigation immediately.


## First creation prompt (1.64)

Opening quest creation before any quest has been created displays: Write a good habit you want to cultivate. This tutorial is shown once and saved even if the quest editor is canceled. Closing the popup returns to the open quest editor. Includes the fourth-quest navigation unlock from 1.63.


## Sixth-quest dice reminder (1.65)

Creating quest six displays the requested dice reminder unless more than 10 seconds have been spent with two-option mode enabled on the Bulletin board. Foreground board time accumulates across uses and saves; background time, other tabs, timelines and rearrange mode do not count. The stored counter stops just beyond the threshold. Older saves default to zero recorded usage because earlier versions did not record it.


## Guided onboarding and larger tutorials (1.66)

The Bulletin board displays Create more quests to continue! after quest one until quest four. Category creation and Advanced unlock after three created quests; before then + opens quest creation directly. New quest dialogs include the good-habit subtitle. First timeline opening and first dice activation display the requested explanations. Editing and closing a quest during the first rearrange session explains how to exit rearrange mode. Tutorial shown flags persist via optional compact-save fields. Tutorial popup body text is now 18sp.


## Demo tool lists and permanent descriptions (1.67)

New installations start with Exercises (Demo), containing seven exercises, and Recipes (Demo), containing five recipes, plus Landscapes (Demo) with Location, Time Period and Architecture and People (Demo) with Body Type, Age, Clothes Style and Personality. Demos are seeded only for a new save; existing saves are unchanged and deleted demos stay deleted. Both tool names-list descriptions are permanently visible, with the requested shortened wording. Tutorial popup body text is now 20sp.

## Meditation Bell and PDF Reader (1.68)

Tools now includes Meditation Bell and PDF Reader, using the tools hue and native Back navigation.

Meditation Bell has separate minute/second fields, Start/Pause and a countdown, an attributed random quotation, and a bottom Back button. The 500 distinct offline quotations are excerpts from the public-domain Dhammapada translation by F. Max Muller and Tao Te Ching translation by James Legge. Source details are in app/src/main/assets/MEDITATION-SOURCES.txt. Three soft bell tones are synthesized locally, using alarm volume. Starting/resuming rings immediately, then the selected interval repeats; consecutive bells never use the same tone. A foreground notification provides Pause, and bounded renewable wake locks keep the interval running with the screen locked. Leaving the page keeps meditation running until paused. The interval fields are remembered locally; a process termination does not silently restart meditation.

PDF Reader maintains an offline library with Back and Add PDF actions. Its search dialog lists accessible PDFs by modification date, newest first. Browse phone opens Android's searchable PDF picker; Choose folder grants access to a document folder and indexes PDFs in its subfolders. Android's scoped storage prevents silently scanning every private folder on the device. Selection imports a private local copy, so the library remains usable if the original provider becomes unavailable. Long press a library entry to remove its imported copy. PDF files and tool preferences stay on this installation and are not embedded in the compact quest save or its string exports/backups.

The full-screen PDF renderer supports vertical scrolling/flinging, pinch zoom and double-tap zoom, and renders visible pages in a bounded bitmap cache instead of loading every page. A one-physical-pixel outlined Back button slides down at top left when scrolling toward earlier content, stays for two seconds after movement, then slides away. Rotation preserves the top page and fractional position and keeps zoom. Android's Back gesture/button also exits the reader. Damaged, encrypted and unsupported PDFs show an error rather than crashing.

Validation: 282 unit tests, APK build and Android lint pass (0 errors, 7 existing warnings). Native emulator checks covered bell start/pause and foreground notification, file and folder import, newest-first search, adding PDFs, zoom, scrolling, the timed Back overlay and portrait/landscape top-position preservation.
## Meditation refinements and rearrange controls (1.69)

Meditation Bell now displays the requested five-senses description, has two unlabeled time fields with 8dp margins, and places Start/Pause on a separate row. The three locally synthesized bells rise smoothly in volume over 1.5 seconds before decaying. The 500-quotation bank now attributes sayings to Ramakrishna (200), Swami Vivekananda (100), and Rabindranath Tagore (200), using public-domain published collections and short complete excerpts. Source details remain in app/src/main/assets/MEDITATION-SOURCES.txt; ancient scripture attributions were replaced. Quote wording is preserved apart from normalized markup/whitespace.

Add PDF opens Android's searchable phone PDF picker directly. Long press a library PDF to enter rearrange mode. Each row has a drag grip, Edit and Delete controls. Edit changes the library display name while keeping the imported document and source file intact. Drag order and renamed titles are saved locally; removal still asks for confirmation. A fixed Back button and the phone's Back button exit rearrange mode before leaving the library.

Long pressing a Bulletin quest or Law card now enters rearrange mode directly. Its edit/delete/grip controls remain on the cards, while the bottom bars are replaced by one fixed Back button. Normal scrolling remains available outside the drag grip/long press. The first-session exit hint now points to Back.

Validation: 283 unit tests, APK build and Android lint pass with 0 errors and 7 existing warnings. Native emulator checks covered quest/law long-press entry and fixed Back, the revised meditation layout, direct PDF picker opening, PDF rename, drag reordering, saved names/order and exiting rearrange mode. Fade-in behavior is verified numerically; the headless emulator has no audible output.
## Persistent reading anchors, live intervals and tool navigation (1.70)

Each imported PDF now remembers its own top-of-screen page/fraction anchor, position within a page gap, zoom and horizontal fraction. Positions save after movement settles and commit when leaving/backgrounding the reader. Restoration waits for the viewport layout, remains independent for each document and survives app restarts, renaming and rotation. A saved top anchor near the end of a PDF is preserved even if a taller viewport needs empty space below it. Fling motion stops before saving the last visible position. Deleting a library PDF removes its saved anchor. Reading positions are local tool preferences, alongside the existing local PDF library.

The reader's Back overlay appears while scrolling toward earlier content and slides away immediately when scrolling toward later content; the two-second timeout still applies when movement stops.

Meditation minute/second fields stay editable while the bell is running. Valid edits apply after a 350 ms typing pause, save the new interval and reschedule the next bell from that change without pausing or ringing an extra bell. Merely navigating away does not reset the interval. Every tool page now includes the main Bulletin/Law/Tools/Settings navigation and live counters, including Meditation Bell and the PDF library/rearrange page. An open PDF remains fullscreen without that navigation. Tab changes return to the existing main activity instead of building duplicate screens.

As selected by the user, the broad quote collection was replaced with 25 carefully verified single-sentence quotations about attention, stillness, direct sensory experience and the present moment. Authors include Thich Nhat Hanh, Eckhart Tolle, J. Krishnamurti, Swami Vivekananda and Rabindranath Tagore. Attribution/source details are in app/src/main/assets/MEDITATION-SOURCES.txt. Restored meditation sessions also validate the saved quote against the new collection.

Validation: 284 unit tests, APK build and Android lint pass (0 errors, 7 existing warnings). Native emulator checks verified live interval edits while Pause stayed active and the notification changed, tool navigation to Law, independent PDF positions, exact reopening/rotation anchors to 1e-6 tolerance, persistence after process restart and directional Back-button hiding.
## Original meditation reflections and initial interval (1.71)

Meditation Bell now uses 120 distinct original single-sentence reflections about returning to the present through breathing, sight, sound, touch, smell, taste and ordinary activities. Only the reflection text is displayed, with no author, attribution or label. The old attributed collection is replaced, including any old quotation restored from activity state. Internal numeric TSV IDs are never displayed.

The description is now: Let the periodic sound bring you back to appreciating your 5 senses.
The initial unsaved meditation interval is 1 minute and 0 seconds. Existing saved intervals remain unchanged.

Validation: 284 unit tests, APK build and Android lint pass. The collection check verifies all 120 entries are unique, nonempty single sentences.
## Bell volume and emoji tool buttons (1.72)

Meditation Bell has a saved 0–100% bell-volume slider directly above Start/Pause, with a percentage readout. New saves use 55%, preserving the earlier bell playback gain. Changing volume updates currently sounding and future bells without rescheduling the timer. The small Minutes and Seconds captions are restored above their fields, keeping the 8dp field margins. The description now reads: Let the periodic sound bring you back to your 5 senses.

The meditation controls and reflection scroll together when vertical space is limited; Back and the main navigation remain fixed at the bottom. The existing live interval editing, one-minute first-use default, and 120 unlabeled original reflections remain available.

All four Tools menu buttons are left aligned with an emoji before their names: dice for Choice Randomizer, artist palette for Creative Brief Generator, bell for Meditation Bell, and open book for PDF Reader. Their plain tool names remain available as accessibility labels.

Validation: 284 unit tests, APK build and lint pass. Native emulator checks verified slider placement, field captions, live volume changes without pausing, saved volume after reopening, landscape navigation, and all four emoji buttons. Audio output is disabled in the headless emulator, so perceived loudness was not assessed by listening.
## Instant tool navigation and Tools home (1.73)

Opening or closing Meditation Bell, the PDF library, or a full-screen PDF no longer uses activity transition animations. Launches use zero-animation activity options; tool activities override opening and closing transitions on API 34+, with a zero-transition fallback on older supported Android versions. App Back buttons and system Back follow the same behavior. Android's external PDF picker keeps its own system behavior.

Pressing Tools while already inside a tool returns directly to the Tools front page. This covers Choice Randomizer and Creative Brief Generator names/item pages, Meditation Bell, and the PDF library. Existing list data, editor/session state and PDF reading positions are preserved; ordinary tab switching from other tabs retains its prior behavior.

Validation: 284 unit tests, APK build and Android lint pass (0 errors, 7 existing warnings). Native emulator checks covered Tools-home routing from all four tools, a nested item editor, and opening/Back paths for meditation, the PDF library and a PDF document.