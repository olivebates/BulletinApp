# Notes (1.96)

Notes work offline. Create a document with a name and an optional emoji, or a category with an inherited or custom hue. Categories support nested documents and categories. The shared searchable emoji picker is also available when editing a document's metadata. Emojis have an 8 physical pixel left margin; category plus controls are vertically centered.

The notes menu has Back and Read-only. Find Beige appears only when at least one visible note contains a beige passage and read-only is off. Read-only applies to every note and category: it prevents body/title edits, creation, deletion, renaming, color/emoji changes and rearranging. Browsing, expanding categories, changing display size and finding text still work. The toggle appears only in the notes menu and resets off when the app is reopened.

The open note, its cursor and scroll position, the list position and collapsed categories survive leaving Notes and restarting the app. Returning to the Tools tab reopens Notes automatically while Notes remains open. Back from the notes list, or tapping Tools while already in Notes, returns to the tools menu and clears automatic restoration. Font size is shared across notes and persists too.

## Editor and search

The toolbar contains text size (Aa), heading, bold, beige text, bullets, numbered lists, undo and redo. Existing italic and link content still renders. Beige uses the darker warm text color #C4A06A, stored in local Markdown as `<span color="beige">text</span>`. When all selected text is beige, the beige button removes the color; otherwise it makes the entire selection beige. Other formatting and beige outside the selection are preserved, including after reopening a note.

Inside a note, Find sits immediately to the right of Back. It opens a sticky search field below the title. Search ignores case, highlights matches and centers the current match when the document has enough scroll space. The up/down arrows wrap through results, and a counter shows the current match. Search works in read-only mode, excluding hidden beige passages. Beige content is hidden in read-only mode and surrounding content closes up, including wholly beige paragraphs/headings. The content stays saved and returns when read-only is disabled.

Find Beige lists only passage text, without note titles, across all visible notes regardless of collapsed categories. Consecutive beige spans form one box even across heading changes, inline formatting, blank lines and line breaks. Ordinary non-beige text, including an uncolored heading, splits passages. Fenced code examples do not become results. Tapping a box opens the note at that passage. Back returns to the results at the saved scroll position; the results screen's bottom Back returns to the notes list.

Hold a note/category to enter rearrange mode. Nearby rows animate as you drag. Drop a note on a category to move it inside, on another note to reorder, or on the top-level drop target to move it out. Sibling categories can be reordered. Edit and trash controls appear while rearranging. Deletion requires confirmation and hides the subtree locally immediately.

## Notion connection and one-way uploads

Use Settings > Notion settings or Notes > Sync to connect. Create a personal Notion connection at https://www.notion.so/profile/integrations with read, insert and update capabilities, add it to a shared Bulletin page, and paste its token into the app. The five setup steps are shown in the connection dialog. Choose the shared Bulletin page as the destination container. Tokens are encrypted with Android Keystore AES-GCM; they are never bundled with the app.

The app is authoritative. Sync creates app notes in Notion and uploads their local titles and full contents. It does not import Notion-only pages, remote text, remote titles or conflicts into Notes. Only locally changed documents upload. An external Notion edit remains until that document changes locally; its next upload replaces the remote title and full body. The shared container and `BulletinBackupCodes` are excluded from Notes content sync. Metadata reads remain necessary to select the destination and recover a creation whose network response was lost.

Deleting a note/category in the app archives its Notion counterpart after pending drafts have uploaded; deleting a category also archives its subtree. Offline creation, edits, moves and deletion requests remain queued durably. Uploads are queued once when leaving the Tools tab, switching apps, or locking the screen, only if there are pending changes. Moving between tools, reopening Notes, and rotating do not trigger uploads. The old periodic job is cancelled. A persisted one-time job waits for connectivity and retries failures with exponential backoff; successfully uploaded documents are skipped on retries. Android controls background timing. Network errors retain local data. Notion's fixed palette has no beige; uploads map beige text to Notion's brown while retaining beige locally. Local category hues and emoji metadata are not applied to Notion's page appearance.

Notes are stored separately from the main game save and are not included in game save exports. No live workspace credentials are bundled; live Notion round trips require the user's connection.

## Notion app-save backups

Settings and Notes share the encrypted connection and selected Bulletin page. Backups are kept in `BulletinBackupCodes`, with the latest 20 completed backups retained. Foreground checks upload when four hours have passed since the last successful backup. Failed automatic backup attempts wait at least 15 minutes before trying again. Uploads are read back for verification. Restoring validates the selected save and requires a fresh verified safety backup before applying it.

App-save backups include quests, laws, settings, history, progress, YouTube bookmarks and video playback positions. Notes documents, PDFs and connection credentials remain outside that save.

API references: https://developers.notion.com/reference/update-page-markdown and https://developers.notion.com/reference/update-a-page.

