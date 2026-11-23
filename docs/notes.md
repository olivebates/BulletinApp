# Notes (1.94)

Notes work offline. Create a document with a name and an optional emoji, or a category with an inherited or custom hue. Categories support nested documents and categories. The shared searchable emoji picker is also available when editing a document's metadata. Emojis have an 8 physical pixel left margin; category plus controls are vertically centered.

The notes menu has Back, Read-only and Find Beige. Read-only applies to every note and category: it prevents body/title edits, creation, deletion, renaming, color/emoji changes and rearranging. Browsing, expanding categories, changing display size and finding text still work. The toggle appears only in the notes menu and resets off when the app is reopened.

The open note, its cursor and scroll position, the list position and collapsed categories survive leaving Notes and restarting the app. Font size is shared across notes and persists too.

## Editor and search

The toolbar contains text size (Aa), heading, bold, beige text, bullets, numbered lists, undo and redo. Existing italic and link content still renders. Beige is a text color, stored in local Markdown as `<span color="beige">text</span>`.

Inside a note, Find sits immediately to the right of Back. It opens a sticky search field below the title. Search ignores case, highlights matches and centers the current match when the document has enough scroll space. The up/down arrows wrap through results, and a counter shows the current match. Search works in read-only mode.

Find Beige lists each beige passage in its own box, including its note title, across all visible notes regardless of collapsed categories. Tapping a box opens the note at that passage. Back returns to the results at the saved scroll position; the results screen's bottom Back returns to the notes list.

Hold a note/category to enter rearrange mode. Nearby rows animate as you drag. Drop a note on a category to move it inside, on another note to reorder, or on the top-level drop target to move it out. Sibling categories can be reordered. Edit and trash controls appear while rearranging. Deletion requires confirmation and hides the subtree locally immediately.

## Notion connection and one-way uploads

Use Settings > Notion settings or Notes > Sync to connect. Create a personal Notion connection at https://www.notion.so/profile/integrations with read, insert and update capabilities, add it to a shared Bulletin page, and paste its token into the app. The five setup steps are shown in the connection dialog. Choose the shared Bulletin page as the destination container. Tokens are encrypted with Android Keystore AES-GCM; they are never bundled with the app.

The app is authoritative. Sync creates app notes in Notion and uploads their local titles and full contents. It does not import Notion-only pages, remote text, remote titles or conflicts into Notes. A Notion edit is overwritten on the next successful upload even when the local note has not changed. The shared container and `BulletinBackupCodes` are excluded from Notes content sync. Metadata reads remain necessary to select the destination and recover a creation whose network response was lost.

Deleting a note/category in the app archives its Notion counterpart after pending drafts have uploaded; deleting a category also archives its subtree. Offline creation, edits, moves and deletion requests remain queued durably. Uploads run after a short editing debounce, every 30 seconds while Notes is active, when leaving Notes, and through Android's periodic network retry job. Android controls background timing. Network errors retain local data. Notion's fixed palette has no beige; uploads map beige text to Notion's brown while retaining beige locally. Local category hues and emoji metadata are not applied to Notion's page appearance.

Notes are stored separately from the main game save and are not included in game save exports. No live workspace credentials are bundled; live Notion round trips require the user's connection.

## Notion app-save backups

Settings and Notes share the encrypted connection and selected Bulletin page. Backups are kept in `BulletinBackupCodes`, with the latest 20 completed backups retained. Foreground checks upload when four hours have passed since the last successful backup. Uploads are read back for verification. Restoring validates the selected save and requires a fresh verified safety backup before applying it.

App-save backups include quests, laws, settings, history, progress, YouTube bookmarks and video playback positions. Notes documents, PDFs and connection credentials remain outside that save.

API references: https://developers.notion.com/reference/update-page-markdown and https://developers.notion.com/reference/update-a-page.
