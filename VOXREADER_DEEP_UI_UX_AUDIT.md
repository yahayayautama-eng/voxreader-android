# Vox Reader deep UI/UX audit and implementation plan

**Audit date:** 18 August 2026  
**Target:** current `voxreader` Android app, debug build on the connected 1600 x 2560 / 320 dpi device  
**Scope:** navigation, hierarchy, interaction design, adaptive layout, accessibility, UX copy, reader controls, import, library management, and the document-structure correction experience

**Visual direction:** `docs/design/voxreader-ui-concept.png`

This is the UI/UX companion to `VOXREADER_ELEVENREADER_STANDARD_AUDIT.md`. It is intentionally implementation-oriented: Luna should be able to turn each phase into a bounded pull request with testable acceptance criteria.

## Executive verdict

Vox Reader has a recognizable visual identity and a usable core path, but it is not yet a high-standard reader UI. The compact library is the strongest screen. The expanded/tablet experience is mostly a stretched phone UI, several custom controls do not expose enough accessibility semantics, and the product often shows internal implementation details where users need confidence and control.

The most damaging UI problem is not cosmetic: the app confidently presents malformed chapter detection (`46 THOMAS HORN`, `163 chapters`) without a review-and-correct workflow. A premium reader must make uncertain document structure visible and repairable before users depend on it.

The correct design direction is a **quiet editorial instrument**:

- text and listening state are primary;
- decoration is restrained and never competes with content;
- every screen has one obvious primary action;
- private/offline behavior is clear at the moment it matters;
- tablet space reveals useful context instead of stretching controls;
- errors are recoverable and document parsing is inspectable.

### What already works

- Strong dark editorial character with a consistent serif/display voice.
- A clear Library → Book details → Reader path.
- Useful resume cards, reading progress, bookmarks, highlights, sleep timer, voice choice, and listening stats.
- Reader text is constrained to a readable central column rather than spanning the full tablet.
- Destructive single-book deletion has a confirmation that names the data removed.
- Compact-width navigation can display all four destinations, including at 200% text scale.
- The 200% text-scale smoke test remained operable and did not crash.

### Current maturity by area

| Area | Current level | Target |
|---|---|---|
| Visual identity | Promising | Coherent tokenized system |
| Compact layout | Functional | Polished and information-rich |
| Tablet/landscape | Stretched | Adaptive two-pane/supporting-pane |
| Reader | Functional | Focused, ergonomic listening surface |
| Search | Metadata filter | Real library and full-text discovery |
| Import | Two entry points | Complete, recoverable import hub |
| Document structure | Opaque auto-detection | Reviewable and correctable |
| Accessibility | Partial | WCAG 2.1 AA / Android core quality |
| UX copy | Inconsistent | Plain, consistent, localized |
| Library management | Single-item only | Selection mode and safe bulk actions |

## Audit evidence

The audit inspected live states for Library, Search, Bookmarks, Settings, Import, Book details, Reader, Voice selection, Stats, About, delete confirmation, contents, sleep timer, compact width, portrait, and 200% font scale. The source review covered the matching Compose screens, app shell, navigation, theme, repository search, and deletion flow.

The current live accessibility hierarchy on the library contained:

- 77 nodes;
- 6 nodes explicitly marked `NAF` by UI Automator;
- 20 clickable nodes with neither visible text nor a content description at their own node.

Some parent/child merging can make UI Automator more pessimistic than TalkBack, but six book-cover/FAB-style actions being marked not accessibility friendly matches the source review: custom `clickable` surfaces often omit role, selected state, state description, or a merged label.

Confirmed contrast measurements from the current palette:

| Foreground / background | Ratio | Result |
|---|---:|---|
| `#6B7280` tertiary text on `#090D16` | 4.02:1 | Fails 4.5:1 normal-text target |
| `#9CA3AF` muted text on dark | 7.65:1 | Pass |
| Cyan `#38BDF8` on dark | 9.07:1 | Pass |
| White on cyan `#38BDF8` | 2.14:1 | Fail |
| Dark text on cyan `#38BDF8` | 9.07:1 | Pass |

## Priority model

- **P0 — trust or access blocker:** prevents confident reading, hides a core destination/action, causes inaccessible operation, or risks user data.
- **P1 — core-quality gap:** materially slows common reading/import/library tasks or makes the app feel unfinished.
- **P2 — polish and depth:** valuable after the core interaction model is stable.

## P0 findings

### P0.1 — Document structure is presented as fact when it is uncertain

**Observed:** The imported PDF shows `163 chapters`, including headings such as `46 THOMAS HORN`. The reader, contents sheet, book details, progress calculation, and time estimates all reinforce that structure. The only repair affordance is a full-width `Re-scan chapters` button, with no preview, explanation, confidence, or manual correction.

**Why it matters:** Wrong chapters poison navigation, progress, sleep/listening context, bookmarks, and perceived product intelligence. This is the fastest way to lose trust.

**Required UI:** Replace `Re-scan chapters` with `Review document structure`.

The review flow must show:

1. a summary: `163 sections found · 28 need review`;
2. detected entries with source page/location and a short text preview;
3. clear flags such as `Likely heading`, `Possible heading`, and `Body text`;
4. edit title, merge with previous, split here, demote from heading, reorder, and ignore;
5. an original-text/page preview alongside the selected entry on expanded screens;
6. `Apply changes` and `Discard` actions;
7. safe re-analysis that first previews differences: `12 sections added · 31 removed · bookmarks preserved`.

**Do not** start with a fully graphical document editor. A list plus preview pane covers the real need with less code.

**Acceptance criteria:**

- Every automatically created section can be renamed or removed from the table of contents.
- Re-analysis never silently replaces a user-corrected structure.
- Existing position, bookmarks, and highlights are mapped or the user is warned before applying.
- Book details labels uncertain structures instead of asserting exact chapter counts.
- Screen-reader users can perform every correction through visible buttons or an overflow menu; no gesture-only action.

### P0.2 — Custom interactive elements lack complete semantics

**Observed in source/live tree:** custom library cards, category pills, bottom navigation items, highlight colors, and some rows use bare `Modifier.clickable`. Several controls do not expose `Role`, `selected`, `stateDescription`, merged labels, or progress semantics. Book covers were reported as NAF. The bottom bar suppresses ripple feedback.

**Required change:** Prefer Material components with built-in semantics. Where the visual must stay custom, add one merged semantic node with:

- a useful label (`The Extraterrestrial Species Almanac, 19 percent read`);
- role (`Button`, `Tab`, `RadioButton`, or `Checkbox` as appropriate);
- selected/checked state;
- enabled/disabled state;
- progress range or spoken percentage;
- an accessibility action equivalent for every long-press action.

**Acceptance criteria:**

- Zero actionable NAF nodes in the Library, Search, Bookmarks, Reader, Voice, and Settings screens.
- TalkBack reads every book once, with title, author, progress, favorite status, and action.
- Tabs and filters announce selected state.
- Progress bars announce a percentage or current/total position.
- The current sentence highlight is announced without reading decorative elements.
- All custom actions remain usable with TalkBack, Switch Access, keyboard, and D-pad.

### P0.3 — Text and control contrast failures

**Observed:** white labels on the cyan primary surface are approximately 2.14:1. `TextTertiary` is approximately 4.02:1 on the main dark background. Some dimmed metadata and dialog-underlay states are visibly weak.

**Required change:** Use dark content on bright cyan. Raise tertiary text to at least 4.5:1 at its rendered size. Define color roles rather than reusing legacy aliases (`SignalOrange`, `Leaf`, `Denim`, `Azure`) that all resolve to blue.

**Acceptance criteria:**

- 4.5:1 minimum for normal text.
- 3:1 minimum for large text, icons, focus indicators, and meaningful graphics.
- Selection is never communicated by color alone.
- OLED, dark, light, ivory, and sepia reader themes pass the same contrast checks.

### P0.4 — Notification permission is requested without context on first launch

**Observed in `MainActivity`:** Android 13+ receives a notification permission prompt immediately when the activity is created, before the user starts listening or understands why it is needed.

**Required change:** Do not ask on launch. Ask only when a non-exempt notification feature requires it, with an in-app explanation tied to background listening. Media-session notifications are exempt from the Android 13 notification-permission behavior, so confirm whether the permission is needed at all before keeping the request.

**Acceptance criteria:** First launch opens directly to a usable library. No permission prompt appears without a user-initiated feature and clear rationale.

### P0.5 — Camera scan sessions are not process-safe

**Observed in source:** captured pages are retained as `Bitmap` objects in `ImportViewModel`, the pending camera output file is held in composable `remember` state, and a scan is capped at eight pages. A configuration change usually retains the ViewModel, but Android process death while the external camera is open can lose the pending-file reference and the complete in-memory scan session. Keeping multiple decoded bitmaps also creates avoidable memory pressure and explains the user-visible eight-page ceiling.

**Required change:** persist each capture immediately as a normalized temporary image plus a small saved-session manifest. Store only file references and page metadata in UI state. Restore the session after activity/process recreation; clean abandoned sessions by age. Decode thumbnails for the page tray rather than retaining full-size bitmaps.

**Acceptance criteria:**

- Start a scan, capture pages, background/kill/recreate the app, and resume with the same ordered pages.
- Returning from the camera always resolves the correct pending file.
- Users can reorder, remove, rotate, crop, and retake individual pages.
- The eight-page product limit is removed; any practical limit is based on storage with a clear estimate/warning.
- Cancel deletes temporary captures; a crash does not leave unbounded orphaned files.

## Final blind-spot pass

These items were easy to miss in a screen-only review but affect the perceived quality of the shipped product.

| Finding | Priority | Required action |
|---|---|---|
| Final package identity is `com.aistudio.voxreader.xyz` while code symbols still use VoxLeaf | Before Play release | Choose the permanent application ID before the first store publication; rename user-visible and maintainability-facing VoxLeaf symbols separately without a risky all-at-once package rewrite |
| Android cannot currently send a file, selected text, or URL into Vox Reader | P1 | Add scoped `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `ACTION_VIEW`, and optionally `PROCESS_TEXT` handling with a preview before import; validate MIME type/content rather than trusting the intent |
| Search query, annotation tab, dialogs, and draft import fields are not consistently saveable | P1 | Use `SavedStateHandle`/`rememberSaveable` for small UI state; persist large import work by file reference, never in a saved-state bundle |
| Backup and device-to-device transfer are intentionally disabled | P1 trust | Keep that privacy default, but state the consequence clearly and provide a user-initiated local export/restore archive before users accumulate irreplaceable notes and corrections |
| Sensitive book content can appear in Android Recents previews | P1 privacy | Add an optional `Hide content in Recents` privacy setting using the platform secure-window behavior; do not force it because it also blocks screenshots users may need |
| Low-storage and very-large-document behavior is not surfaced | P1 | Preflight free space, show expected local-copy/generated-audio size, allow cancellation, and provide Storage management with per-book cleanup |
| Password-protected, encrypted, DRM, malformed, or unsupported files need distinct outcomes | P1 | Identify the actual reason; request a PDF password where supported, otherwise explain the limitation and preserve the source for retry instead of showing a generic import error |
| Install-time offline voice assets have no explicit repair surface | P1 | If the model is missing/corrupt after installation or update, show `Repair on-device voice` with size, network requirement, progress, and fallback to an online voice only by user choice |
| Meaningful UI regression coverage is very limited | P0 release process | Keep the small navigation/loading checks, replace the placeholder screenshot artifact with compact/medium/expanded tests for core screens, and add semantics, selection, process restoration, and destructive-action tests |
| Font binaries lack repository license notices | Before distribution | Add each font's OFL/license and surface it through Open-source licenses |

The Android intent entry points must all converge on the existing import validation pipeline. Do not create a second parser path for shares/open-with. Likewise, local backup should export a documented Vox Reader archive; cloud sync remains deferred.

## Cross-app UI system

### Information architecture

Keep four top-level destinations:

1. **Library** — resume, browse, sort, filter, select, and import.
2. **Search** — library search first; full-text search when indexing exists.
3. **Bookmarks** — two tabs named `Bookmarks` and `Highlights`.
4. **Settings** — reading, voice, playback, privacy, help, and about.

`Import` remains a prominent contextual action, not a fifth destination. `Stats`, `Voice`, `About`, `Book details`, `Structure review`, and `Reader` are child screens.

Use one vocabulary everywhere:

| Current variants | Standard term |
|---|---|
| Marks / Saved places / Bookmark | Bookmark / Bookmarks |
| Re-scan chapters | Review document structure |
| Find a page | Search library |
| subject | description or category, only if actually searched |
| Edge TTS ready | Voice name · Online |
| ADD TO VOXLEAF | Add to Vox Reader |

### Adaptive layout contract

Use window size classes or Material adaptive scaffolds; do not branch on a single raw `600.dp` threshold for the whole app.

| Window | Navigation | Content behavior |
|---|---|---|
| Compact | Navigation bar | One pane; 2-column library under 420 dp, 3 columns only when title remains legible |
| Medium | Navigation rail | Centered feed; bounded controls; optional supporting pane |
| Expanded | Rail or expanded rail | List-detail for Library/Book details, Search/results, Bookmarks, Structure review, and Settings categories |

Expanded layout rules:

- Body copy: 45–75 characters per line.
- General form/content column: 640–760 dp maximum.
- Reader text: user-adjustable, default about 680–760 dp.
- Dialogs: 360–560 dp depending on content.
- Bottom sheets: capped width on large screens.
- Primary buttons and text fields: do not stretch across a 2560 px display.
- Use a second pane only when it provides real context; otherwise center a bounded column.

### Spacing and size tokens

Use the existing theme rather than introducing another design dependency.

| Token | Value | Use |
|---|---:|---|
| `spaceXs` | 4 dp | icon/text micro-gap |
| `spaceSm` | 8 dp | related controls |
| `spaceMd` | 12 dp | compact card interior |
| `spaceLg` | 16 dp | standard card/section |
| `spaceXl` | 24 dp | screen section gap |
| `space2Xl` | 32 dp | large-screen gutters |
| `radiusSm` | 8 dp | chips/small controls |
| `radiusMd` | 12 dp | rows/cards |
| `radiusLg` | 20 dp | prominent surfaces/dialogs |
| touch target | 48 dp min | every action |

Avoid making every card 24 dp rounded. Reserve the largest radius and strongest tint for primary surfaces so hierarchy remains visible.

### Typography

- Keep the serif for book titles, chapter titles, and occasional screen identity.
- Use sans-serif for navigation, settings, metadata, controls, and dense lists.
- Stop italicizing every top-level title; italic display text is a brand accent, not the default hierarchy.
- Replace fixed `sp` display sizes with the Material type scale and validate 100%, 130%, and 200% font scale.
- Cap long titles to 2–3 lines in cards/details and provide full text through detail view/accessibility label.

#### Font implementation audit

The bundled font selection is already strong:

| Asset | Best job | Current state |
|---|---|---|
| Inter Variable | App UI, controls, navigation, metadata | Bundled but unused |
| Source Serif Variable | Long-form reader text | Used for almost the entire Material UI, but not the Reader body |
| Newsreader Variable | Display titles and book/chapter titles | Used appropriately for many headings |
| Newsreader Italic | Wordmark and rare editorial accent | Overused as a top-level screen-title pattern |
| JetBrains Mono Variable | Timers, section numbers, technical counters | Used through the custom `Eyebrow` style |

The central inconsistency is that `Type.kt` defines high-quality bundled families, but `ReaderScreen.kt` maps reading preferences to Android's generic `FontFamily.Serif`, `SansSerif`, and `Monospace`. The Reader can therefore render differently by device and does not use the Source Serif asset selected specifically for reading. Settings previews use those same generic families, so they do not accurately preview the bundled typography.

The Material type scale also routes body and label roles through Source Serif. This gives buttons, navigation, settings, form fields, and dense metadata an overly literary texture and makes the long Settings screen harder to scan. Inter is already in `res/font`, so this can be fixed without adding a dependency or another font file.

**Required family mapping:**

| Role | Family | Typical weight |
|---|---|---:|
| App navigation, buttons, forms, settings, metadata | Inter | 400–600 |
| Book titles and chapter/section headings | Newsreader | 500–700 |
| Reader body, default | Source Serif | 400 |
| Reader body, clean option | Inter | 400 |
| Timers, section indices, compact counters | JetBrains Mono | 400–500 |
| Vox Reader wordmark only | Newsreader Italic | 600 |

**Reader choices:** rename `Serif / Sans / Mono` to `Book / Clean / Mono`. Default to `Book` using the bundled Source Serif. `Clean` uses bundled Inter. Keep Mono as a secondary accessibility/preference option, not a promoted equal default for long-form reading. A later `Accessible` option can add Atkinson Hyperlegible only if user testing demonstrates a need; do not add another font now.

**Type-scale adjustments:**

- UI body: 14–16 sp with roughly 1.4–1.5 line height, instead of applying 1.6 serif leading to every setting and control.
- Reader body: default 18 sp with 1.55–1.65 line height; preserve the current user adjustment range, but preview the result live.
- Reader headings: body size + 4–8 sp, capped so imported all-caps headings do not dominate.
- Book/card titles: 16–18 sp compact, 18–22 sp expanded, maximum 2–3 lines.
- Labels: Inter 12–14 sp; JetBrains Mono only for deliberate tracked-uppercase eyebrow labels.
- Use relative `em` tracking for display styles where possible; fixed negative `sp` tracking becomes aggressive under large text scaling.

**Code cleanup:** rename stale `VoxLeafFont`, `VoxLeafDisplay`, `VoxLeafSerif`, and `VoxLeafMono` tokens to product-neutral role names such as `ReaderSerif`, `EditorialDisplay`, `BrandItalic`, `UiSans`, and `UtilityMono`. Replace hard-coded 28/38 sp screen titles with typography roles. Add the font license/OFL notices to the repository and the app's open-source licenses screen; the project-level MIT license alone does not document the bundled font licenses.

### Motion

Keep the existing restrained transitions. Add only functional motion:

- selection-mode entry/exit;
- import progress and completed-state transition;
- mini-player expansion;
- current-sentence movement;
- structure-review diff application.

Respect reduced-motion settings. Decorative parallax or constant ambient animation is not needed.

## Screen-by-screen audit and target specification

### 1. Splash and first use

**Current:** clean 300 ms brand animation, followed by the library. Notification permission interrupts the first-use experience at activity creation.

**Improve:** Keep the short splash. Do not add a multi-page onboarding carousel. For an empty library, show a high-quality first-run empty state with three actions:

- `Import a file`
- `Scan pages`
- `Paste text` when implemented

Add a one-time contextual tip when the first book is opened: `Tap play to listen. Long-press a sentence to highlight.` The same features must remain discoverable through visible menus.

### 2. Library

**Current strengths:** clear identity, resume cards, categories, covers, progress, favorites, import action.

**Current problems:**

- Expanded landscape leaves most of the right side empty while resume cards are stranded at 560 dp.
- The giant wordmark and giant search consume excessive vertical space at large text sizes.
- Cover-only library cards hide title/author and make blank covers indistinguishable.
- Blank white fallback covers look like loading errors.
- No sort, view, or bulk-selection controls.
- Categories are derived from metadata, but the Search screen uses unrelated hard-coded genre chips.
- Decorations compete with useful content and become more prominent in empty space.

**Target compact layout:**

- Smaller top app identity; search remains immediately available.
- `Continue listening` as horizontal cards or a maximum of two compact rows.
- Section header: `Library` + item count + sort/view overflow.
- Book card shows cover, 2-line title, 1-line author, and progress/status.
- Generated fallback cover uses initials/title color, never a blank white rectangle.
- Import extended FAB collapses to icon FAB after scroll.

**Target expanded layout:**

- Rail + 40/60 list-detail layout.
- Left pane: searchable/sortable library grid/list.
- Right pane: selected book summary, Resume, progress, recent chapter, and overflow actions.
- If no book is selected, show `Continue listening` and recent imports in the supporting pane.

**Sorting:** `Recent`, `Title`, `Author`, `Progress`, `Date added`.  
**Filters:** `All`, `In progress`, `Unread`, `Finished`, `Favorites`, then user metadata/categories.

#### Multi-select and deletion

This is **not implemented today**. A book tap opens details; there is no `combinedClickable`, selected-ID state, or bulk action bar.

Required interaction:

1. Long-press a book to enter selection mode and select it.
2. Tap other books to add/remove them from the selection.
3. Top contextual bar shows `3 selected`, Close, Select all, and Delete.
4. Overflow can later contain Favorite/unfavorite and Share; do not block deletion on those extras.
5. Confirm with exact scope: `Delete 3 books? Their files, progress, bookmarks, highlights, and generated audio will be removed from this device.`
6. Long-press must never delete immediately.
7. Every card has an overflow action `Select` so TalkBack, keyboard, mouse, and switch users have an equivalent.
8. Escape/Back exits selection mode before leaving the Library.

**Deletion recovery:** The current repository hard-deletes database rows and files, so a truthful Undo snackbar would require staged deletion or a trash layer. Do not fake Undo. Ship confirmation first; add a 7-day Trash only if user testing shows accidental deletion is common.

### 3. Search

**Current:** searches book title, author, description, and genre. It does not search page text. Result cards are cover-only. `Find a page` and `Title, author, or subject` overpromise the behavior. Five hard-coded genre suggestions are unrelated to the actual library.

**Target now — metadata search:**

- Title: `Search library`.
- Placeholder: `Title, author, or description`.
- Remove hard-coded genre chips; use dynamic recent searches or actual library filters.
- Show result count and sort.
- Use list rows by default: cover, title, author, format, progress, and matching metadata snippet.
- Empty query: recent searches plus useful filters, not a large blank panel.
- No results: `No books match “…”` + `Clear search`.

**Target later — full-text search:**

- Index imported text with Room FTS.
- Separate scopes: `Books` and `Inside books`.
- Inside-book results show book, chapter/section, highlighted snippet, and match count.
- Opening a hit navigates to the exact sentence and keeps Next/Previous match controls.

Do not label metadata filtering as page search before FTS exists.

### 4. Bookmarks and highlights

**Current:** top-level nav says `Bookmarks`; screen says `Marks`; first tab says `Saved places`; empty copy calls them `marks`. Individual deletion and highlight export exist.

**Target:**

- Screen title `Bookmarks`.
- Tabs `Bookmarks` and `Highlights`, implemented as proper tabs with selected semantics.
- Search saved content.
- Sort by `Book`, `Recently added`, or `Reading order`.
- Each row shows book, chapter, note/snippet, created date, and overflow.
- Multi-select is useful here after Library selection ships, but it is not required in the first UI phase.
- Export shows format/scope confirmation: Markdown or plain text, current book or all books.
- Replace long-press-only highlight education with `Select text` or overflow discoverability in Reader.

### 5. Import

**Current:** file picker and camera scan are the only visible starts. Branding still says VoxLeaf. The format list omits MOBI/AZW3/FB2 support. The card and buttons stretch across the tablet. Scanned pages have no reorder/delete controls or OCR preview.

**Target import hub:**

- Heading: `Add to Vox Reader`.
- Four cards: `Import file`, `Scan pages`, `Paste text`, `Open link`.
- If only file/scan are ready, show those two and a truthful supported-formats link; do not add disabled teaser cards.
- Small privacy note beside online-only actions, not one generic promise that becomes false for Edge TTS or URL fetching.
- Recent/import queue appears below with stage and recovery action.

**Scan flow:**

1. Capture → crop/rotate/retake.
2. Page tray with reorder, remove, add page, and page count.
3. OCR progress per page.
4. Review extracted text with uncertain spans highlighted.
5. Name document and choose language.
6. Review detected structure.
7. Save and open.

**File import states:** picking, copying locally, parsing, OCR if needed, structure detection, ready. Errors must say which stage failed and what the user can do: retry, choose another file, or keep partial text.

### 6. Book details

**Current:** blank/small cover, very long centered title, full-width Resume, four equally weighted actions, full-width re-scan, and chapters below. In landscape, controls span nearly the full device.

**Target compact:**

- App bar with Back + overflow.
- Header with useful cover fallback, 2–3-line title, author, format, and concise progress.
- Primary `Resume` button.
- Secondary visible actions: Bookmark and Favorite.
- Move Edit, Delete, and Review structure into overflow or a lower `Document` section.
- Chapter section has search, current-position marker, and meaningful duration.

**Target expanded:** list-detail arrangement: metadata/actions in a 360–440 dp supporting pane; chapters in the main pane. Do not center a single giant title over the whole width.

**Copy:** Use `Section` rather than `Chapter` when the source is an article/report and chapter confidence is low.

### 7. Reader

**Current strengths:** calm sepia surface, central reading column, synchronized sentence context, persistent playback, contents, bookmark, sleep, and voice controls.

**Current problems:**

- Long full book title dominates the top bar and truncates poorly.
- Chapter title is repeated in the app bar and content.
- Seven peer-level playback actions overload the primary row.
- The player occupies roughly the bottom third of tablet landscape.
- `Edge TTS ready` is implementation/status copy, not user information.
- Decorative stickers sit behind the reading surface.
- Highlight creation relies on long-press without an obvious accessible alternative.

**Target hierarchy:**

- Tap content to show/hide chrome; text stays stable.
- App bar: Back, short book title, Contents, overflow.
- Content header: section title once.
- Primary player: Back 10 seconds, Play/Pause, Forward 10 seconds.
- Secondary row or overflow: previous/next sentence, previous/next section.
- Progress row: elapsed, remaining, draggable progress, current section context.
- Bottom actions: Sleep, Voice/speed, Bookmark, `Text options`.
- Status: `Ava · Online` or `Luna · On device`; preparing/errors appear separately.

**Text options in Reader:** font family, size, line spacing, theme, margins, and highlight-following. Settings can hold defaults; users expect immediate adjustment while reading.

**Selection/highlight:**

- Long-press sentence remains a shortcut.
- Overflow action `Highlight current sentence` is the accessible equivalent.
- Selection menu shows Highlight, Add note, Copy, and Share where source rights permit.
- Existing highlight shows Edit note, Change color, Remove.
- Highlight colors have names and a selected state; color is not the only indicator.

**Playback error copy examples:**

- `This voice needs an internet connection. Choose an on-device voice or try again.`
- `Preparing audio for this section…`
- `Playback stopped because another app started audio.`

### 8. Contents sheet

**Current:** a wide bottom sheet lists 163 chapter titles, but the current item is not obvious and long malformed titles dominate.

**Target:**

- Cap sheet width on large screens; use a side sheet/supporting pane when expanded.
- Search contents.
- Clearly mark `Current` and auto-scroll to it.
- Show section number and optional duration as secondary text.
- Flag uncertain detections and link to `Review structure`.
- Use sticky part/chapter headers only when the source actually has hierarchy.

### 9. Sleep timer

**Current:** giant dialog with four full-width outlined options and copy tied to `offline voice` even when Edge TTS is active.

**Target:** compact option list: 5, 10, 15, 30, 45, 60 minutes, End of section, Custom. Show current state (`18 min remaining`) and `Cancel timer`. Copy: `Stop playback after:`. The timer is about playback, not the engine.

### 10. Voice and speed

**Current:** engine choice and speed are clear, but controls span the tablet. Edge mode can show hundreds of flat voice rows with only a name. The UI claims `Search 400+ voices` while live availability showed 322. No preview, language grouping, favorites, recently used voices, or online/offline badges in rows.

**Target:**

- Current voice summary at top with Preview.
- Engine choice named `On device` and `Online`; privacy disclosure appears before first online selection and remains available through an info icon.
- Speed slider from supported minimum to maximum with common presets; do not show six equal-width buttons on large screens.
- Voice filters: language, accent/locale, availability, favorites.
- Sections: Current, Recent, Favorites, All voices.
- Each voice row: display name, locale/accent, On-device/Online badge, Preview, selected state.
- Voice count is derived from the actual list. Placeholder says `Search voices`, never a marketing number.
- Empty/error states include Retry and switch-engine action.

### 11. Settings

**Current:** good privacy intent, but the opening paragraph is dense; six theme options overlap conceptually; controls are visually huge; reading defaults, voice, stats, and About form one long page.

**Target compact:** grouped settings list with subpages:

- Reading appearance
- Voice and playback
- Privacy
- Accessibility
- Storage and imports
- Listening stats
- Help and feedback
- About

Show a small live reading preview in `Reading appearance`. Reduce themes to four understandable options: System/light app shell is separate from Reader theme; Reader themes are `Light`, `Sepia`, `Dark`, and `Black`. Ivory can become a warmth slider only if users ask for it.

**Target expanded:** category list on the left and selected settings pane on the right using list-detail. Do not place one 720 dp stack in a mostly empty tablet canvas.

### 12. Listening stats

**Current:** useful totals and heatmap, but the title incorrectly falls back to `Vox Reader` because Stats is missing from the route-title mapping. Three tiles and every book row stretch across landscape. Heatmap cells have no accessible date/value semantics.

**Target:**

- Correct title: `Listening stats`.
- Centered 720–960 dp dashboard or two-column expanded layout.
- Summary: Today, This week, Total, current streak.
- Heatmap cells expose date and minutes; keyboard focus is visible.
- Book rows are bounded, sorted by time, and navigable to the book.
- Explain streak rules and allow stats reset/export only in overflow.

### 13. About, help, and trust

**Current:** About exposes Kotlin, Gradle, Compose, MVVM, Hilt, Room, and Babylon.cpp. That is developer documentation, not a user-facing About screen.

**Target About:**

- App icon, Vox Reader, version/build.
- One-sentence promise: private, flexible document listening.
- Privacy details.
- Open-source licenses.
- Terms/privacy links if distributed.
- Send feedback / report a problem.
- Diagnostics export only behind Help or a clearly named technical section.

Move the architecture stack to `README.md`.

## UX copy replacement table

| Current | Replace with | Reason |
|---|---|---|
| `ADD TO VOXLEAF` | `ADD TO VOX READER` | Brand correctness |
| `Bring a page with you.` | `Listen to your own documents` | Covers books, files, links, and scans |
| `Find a page` | `Search library` | Current search is metadata-only |
| `Title, author, or subject` | `Title, author, or description` | Matches repository fields |
| `Marks` | `Bookmarks` | Familiar, consistent term |
| `Saved places` | `Bookmarks` | Avoid synonym churn |
| `Re-scan chapters` | `Review document structure` | Sets expectation and exposes control |
| `Edge TTS ready` | `Ava · Online` | User-relevant state |
| `Stop the offline voice after:` | `Stop playback after:` | Works for every engine |
| `Available voices · 322` | `322 voices` | Shorter; count remains dynamic |
| `Search 400+ voices…` | `Search voices` | Avoid conflicting counts |
| `Retry` after import error | `Try import again` | Names the action |
| `Remove book?` | `Delete “Book title”?` | Names the target |
| `Private On-Device Reader` | `Private document listening` | Natural, benefit-led phrase |

Move all visible copy to string resources, including plural resources for books, pages, chapters/sections, minutes, and selected counts. The current `strings.xml` contains only 11 entries while nearly all screen copy is hard-coded.

## Accessibility completion checklist

### Semantics and screen readers

- Use headings for screen/section titles.
- Use proper tabs, navigation items, switches, radio groups, and sliders rather than visual imitations.
- Merge book-card descendants into one coherent announcement.
- Give decorative imagery `contentDescription = null` and ensure it is not focusable.
- Add `paneTitle` to dialogs, bottom sheets, and adaptive panes.
- Add `liveRegion` only to important transient states such as import progress completion or playback failure.
- Progress components expose range/current value.
- Lists/grids expose collection and item position where Material/Compose does not provide it automatically.

### Input and focus

- 48 dp minimum touch target.
- Visible focus indication on every custom clickable.
- Tab/arrow navigation follows visual order.
- Spacebar plays/pauses when Reader has focus.
- Media keys map to playback.
- Enter opens the selected book; Delete invokes confirmation in selection mode.
- Right-click/secondary click opens the same book overflow menu.
- Escape/Back closes dialogs/sheets/selection before navigating away.

### Text and reflow

- Validate 100%, 130%, and 200% font scale at compact, medium, and expanded widths.
- No essential label is ellipsized without another way to read it.
- Do not rely on fixed-height text containers.
- Reader margins shrink before text becomes unreasonably narrow.
- Respect bold text, high contrast, remove animations, and system light/dark preferences where applicable.

### Automated checks

- Compose UI tests assert roles, labels, selected states, progress semantics, and 48 dp targets for shared components.
- Add screenshot tests for compact 360 x 800, medium 700 x 1000, expanded 1280 x 800, and 200% font scale.
- Run Accessibility Scanner and manual TalkBack/Switch Access passes before release.

## Luna implementation plan

Each phase should be a separate PR. Do not combine visual restructuring with chapter-parser changes in one review.

### Phase UI-0 — vocabulary, trust, and accessibility baseline

**Goal:** remove confirmed release-quality failures without redesigning every screen.

**Files:** `strings.xml`, `Color.kt`, `Theme.kt`, `VoxLeafApp.kt`, `LibraryScreen.kt`, `SearchScreen.kt`, `BookmarksScreen.kt`, `ReaderScreen.kt`, shared components.

**Work:**

1. Move visible strings/plurals into resources; rename VoxLeaf copy.
2. Standardize Bookmark/Bookmarks terminology.
3. Fix primary-button and tertiary-text contrast.
4. Replace bare custom controls with Material components where visuals permit.
5. Add semantic roles, labels, state, progress, and minimum targets to remaining custom controls.
6. Remove first-launch notification permission; request only if a proven non-exempt feature needs it.
7. Fix Stats route title.

**Exit criteria:** zero actionable NAF nodes in audited core screens; contrast passes; TalkBack completes import → open → play → bookmark → return.

### Phase UI-1 — library management and adaptive shell

**Goal:** make browsing and managing books excellent on phone and tablet.

**Files:** `VoxLeafApp.kt`, `LibraryContract.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt`, shared book-card components, focused Compose tests.

**Work:**

1. Use Material adaptive navigation or an equivalent window-size-class model.
2. Rebuild book cards with title/author/progress and generated fallback cover.
3. Add sort/filter/view controls.
4. Implement long-press selection mode with selected IDs, Select all, Delete, and accessible Select overflow.
5. Add expanded Library/detail supporting pane.
6. Keep existing repository deletion; call it only after the exact-count confirmation.

**Exit criteria:** selection survives recomposition/rotation, Back exits selection first, bulk deletion removes only selected IDs, and all actions are keyboard/TalkBack accessible.

### Phase UI-2 — search, bookmarks, and information architecture

**Goal:** make discovery truthful and saved content consistent.

**Files:** `SearchScreen.kt`, repository/DAO only where needed, `BookmarksScreen.kt`, navigation tests.

**Work:**

1. Rename current behavior to metadata/library search.
2. Remove static genre chips and derive filters from real data.
3. Add informative result rows, count, no-result recovery, and bounded expanded layout.
4. Standardize Bookmarks/Highlights tabs and row hierarchy.
5. Add search/sort for saved content.

**Exit criteria:** every result explains why it is relevant; no copy promises page/full-text search.

**Separate later PR:** Room FTS and exact-sentence navigation for full-text search. Do not hide that data/indexing change inside the UI PR.

### Phase UI-3 — import and structure review

**Goal:** make document ingestion transparent, recoverable, and correctable.

**Files:** `ImportScreen.kt`, import contract/view model, parser result models, new structure-review screen/route, focused persistence tests.

**Work:**

1. Redesign Import as a bounded action hub and stage-based progress flow.
2. Add scan page reorder/remove/retake and OCR text review.
3. Introduce persisted detected-section metadata and confidence/reason.
4. Build the minimal list + preview structure editor.
5. Preview re-analysis diff and preserve manual edits/bookmark mappings.

**Exit criteria:** user can correct the malformed `THOMAS HORN` sections without re-importing; applying changes preserves reading state or gives a specific warning.

### Phase UI-4 — reader and playback controls

**Goal:** make listening effortless and reduce control overload.

**Files:** `ReaderScreen.kt`, global player bar, player state contract, focused accessibility/media tests.

**Work:**

1. Reduce primary player row to back/play/forward.
2. Move sentence/section navigation to secondary controls.
3. Add show/hide chrome without moving text.
4. Put text appearance controls in Reader.
5. Replace engine implementation copy with voice/availability.
6. Provide visible/accessibility-equivalent highlight actions.
7. Make Contents and Sleep surfaces adaptive and stateful.

**Exit criteria:** play/pause is the dominant target, all playback actions work by touch/TalkBack/keyboard/media keys, and player chrome uses substantially less landscape height.

### Phase UI-5 — voice, settings, stats, and trust surfaces

**Goal:** finish the supporting experience after core reading is stable.

**Files:** `VoiceSelectionScreen.kt`, `SettingsScreen.kt`, `StatsScreen.kt`, `AboutScreen.kt`, navigation/title resources.

**Work:**

1. Add voice preview, filters, recent/favorites, locale, and online/on-device labels.
2. Replace long settings page with categories; use list-detail on expanded screens.
3. Simplify reader themes and add a live preview.
4. Bound and annotate Stats; add accessible heatmap semantics.
5. Replace architecture-stack About content with version, privacy, licenses, feedback, and support.

**Exit criteria:** voice selection remains fast with hundreds of entries; About is user-facing; every settings control announces its state.

### Phase UI-6 — release verification

1. Compact/medium/expanded screenshot matrix in dark, light, sepia, and black reader themes.
2. 100%, 130%, and 200% font scale.
3. Portrait/landscape, split screen, resize, and rotation state preservation.
4. TalkBack, Switch Access, keyboard, mouse/trackpad, and media keys.
5. Empty/loading/error/offline states for every network or import surface.
6. Destructive-action tests, including single and bulk delete.
7. No hard-coded user-facing strings; pseudolocale smoke test.

## Suggested component reuse

Keep this small. The app does not need a new UI framework.

- One shared `BookCard` supporting compact grid, list row, selection, fallback art, and semantics.
- One `ContentWidth`/bounded-column modifier or composable for non-adaptive detail pages.
- Material `NavigationSuiteScaffold` if the existing dependency plan permits; otherwise preserve the current shell and derive navigation from window size classes.
- Material tabs/chips/buttons/sliders before custom clickables.
- One shared confirmation dialog that receives exact target/action/consequence copy.
- Existing `SpineColor` can seed generated fallback covers.

Do not create a universal component abstraction for every row or card. Share only patterns used by at least two real screens.

## Features worth adding after the core UI

These improve ElevenReader-level parity but should not delay trust/accessibility work:

1. Paste/write text and URL import.
2. Full-text search with result snippets.
3. Pronunciation dictionary.
4. Downloaded/generated audio management and storage controls.
5. Export/share a clip or selected passage.
6. Per-book voice and reading settings.
7. Optional encrypted backup/sync, only after the offline product promise is precisely defined.

## Explicitly defer

- AI chat about the book.
- Social feeds or public discovery catalog.
- Voice cloning/design.
- Complex cloud account system.
- Decorative dashboard widgets.
- A generic design-system module or new UI dependency solely for styling.

The app will gain more from trustworthy structure, natural playback, strong library management, and accessibility than from headline features.

## Success measures

| Measure | Target |
|---|---:|
| Import → first playback completion | ≥ 90% on supported files |
| Median time from open app → resume playback | < 3 seconds |
| Documents requiring manual structure correction | Measured and decreasing by parser release |
| Manual structure corrections preserved after re-analysis | 100% or explicit conflict resolution |
| Actionable NAF nodes in core flows | 0 |
| Normal-text contrast failures | 0 |
| Core tasks at 200% font scale | 100% completable |
| Bulk-delete wrong-target defects | 0 |
| Crash-free sessions | ≥ 99.8% |

## Reference baseline

Primary sources used for this audit:

- Android core app quality: <https://developer.android.com/docs/quality-guidelines/core-app-quality>
- Android large-screen quality: <https://developer.android.com/docs/quality-guidelines/large-screen-app-quality>
- Android adaptive navigation: <https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation>
- Android list-detail layouts: <https://developer.android.com/develop/adaptive-apps/guides/list-detail>
- Jetpack Compose accessibility: <https://developer.android.com/develop/ui/compose/accessibility>
- Compose semantics: <https://developer.android.com/develop/ui/compose/accessibility/semantics>
- Compose accessibility API defaults and 48 dp targets: <https://developer.android.com/develop/ui/compose/accessibility/api-defaults>
- Android Media3 Compose UI: <https://developer.android.com/media/media3/ui/compose>
- Android notification permission and media-session exemption: <https://developer.android.com/develop/ui/compose/notifications/notification-permission>
- ElevenReader Google Play listing: <https://play.google.com/store/apps/details?id=io.elevenlabs.readerapp>
- ElevenReader content-ingestion help: <https://help.elevenlabs.io/hc/en-us/articles/26197616307985-How-do-I-add-content-to-ElevenReader>

## Final order of work

1. Semantics, contrast, terminology, strings, permission timing, Stats title.
2. Library card quality, sorting, adaptive layout, and multi-select deletion.
3. Truthful search and consistent Bookmarks/Highlights.
4. Import review and document-structure correction.
5. Reader/player simplification and in-reader text controls.
6. Voice/settings/stats/about polish.
7. Full accessibility and adaptive release matrix.

That sequence improves trust and daily usability first while avoiding a large visual rewrite that would sit on top of unstable document structure.
