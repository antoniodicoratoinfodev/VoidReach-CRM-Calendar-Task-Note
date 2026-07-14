# VoidReach CRM

VoidReach is a local-first desktop CRM workspace built with Java 26 and JavaFX 26. It brings account management, contacts, calendar planning, task control, dashboards, and linked TXT/Markdown notes into one native application.

Workspace data is persisted locally and separated by account, with automatic recovery and backup support. Local storage is the current source of truth, allowing the application to operate independently while keeping each user's data available on the device.

The project is designed to support a future online service that can make workspace data available across devices and provide a controlled migration path from local files to cloud-backed storage. This cloud capability is part of the planned roadmap and is not yet enabled in the current local-first build.

## Current Status

The following sections are operational:

- Home
- Dashboard
- Contacts
- Calendar
- Tasks
- Notes
- Account and profile management
- Per-account themes
- Local persistence and automatic backups

The Settings navigation item and the Help/Notifications top-bar controls are currently placeholders.

## Feature Overview

### Accounts and Authentication

- Create an account with a full name, email address, and password.
- Email addresses are normalized before storage and must use a valid format.
- Passwords must contain at least eight characters.
- Sign in and sign out locally.
- Optionally remember the account email on the current device; passwords are never stored in the remembered-session file.
- Recover a password with a six-digit code valid for 15 minutes.
- In this local build, the recovery code is shown in the application instead of being sent by email.
- Update the account name from the profile dialog.
- Change the password after confirming the current password.
- Each account keeps its own CRM workspace, avatar, theme, notes, and preferences.

Passwords and recovery codes are protected with salted PBKDF2-HMAC-SHA256 derivation using 120,000 iterations.

### Home

The Home section provides a daily operational summary:

- Personalized greeting and current date.
- Total number of contacts.
- Number of tasks scheduled today.
- Number of tasks scheduled during the next seven days.
- Next scheduled task and its time.
- Today's agenda.
- Upcoming tasks.
- Recently available address-book contacts.
- Quick actions for creating a contact and opening the calendar.

### Dashboard

The Dashboard provides read-only analytics generated from the active workspace:

- Total contacts.
- Total calendar tasks.
- Upcoming tasks during the next seven days.
- Total planned time.
- Contact distribution by tag.
- Upcoming workload chart grouped by day.
- Recorded contact interactions.

Dashboard values update whenever contacts or tasks change.

### Contacts

- Create, view, edit, and delete contacts.
- Open a contact by clicking its table row.
- Store name, company, job title, email, phone, last interaction, tag, and description.
- Tags: `Empty` (no tag, the default for new contacts), `Client`, `Tech`, and `Follow-up`.
- The description is shown in its own table column as a single-line preview with ellipsis; hovering shows the full text in a wrapping tooltip.
- Search in real time by contact information and description.
- Sort any visible field, including description and custom fields, in ascending or descending order.
- Clear the active sorting rule.
- Paginate results using 15, 25, 50, or 100 rows, or display all contacts.
- Enable selection mode for single or bulk deletion.
- Copy selected contacts to the clipboard with `Ctrl+C` or `Cmd+C`.
- Resize and reorder table columns with the mouse.
- Rename a column by clicking directly on its label; dragging the surrounding header continues to reorder the column.

#### Quick edit

- The **Quick edit** toggle in the toolbar switches the table to in-place editing: click a cell and type, press `Enter` or click away to save, `Esc` to cancel.
- The tag cell edits through a drop-down list and the description cell through a multi-line editor (`Ctrl+Enter`/`Cmd+Enter` saves).
- While Quick edit is on, **New contact** inserts an empty row directly in the table and starts editing its name; the full pop-up editor stays available with a right click on the row.
- The Quick edit state is saved per account and restored at the next sign-in.

#### Custom fields

- **Add field** in the toolbar, or **＋ Add field** inside the contact pop-up, adds a user-defined column (for example `Automobile`) to the table and to the pop-up editor.
- Field names must be unique; custom fields support inline editing, sorting, and renaming from the column header.
- Right-click a custom column header to remove the field; its stored values are deleted after confirmation.
- Custom field definitions and values are persisted per account.

### Calendar

- Day and Week views over a 24-hour timeline.
- Fifteen-minute visual intervals.
- Create a task from the calendar timeline.
- Open the task editor with a single left-click, double-click, or right-click.
- Edit title, date, start time, end time, color, and description.
- Move tasks vertically to change their time.
- Move tasks horizontally between days in Week view.
- Resize a task from its lower edge.
- Delete a selected task with the keyboard or from the task dialog.
- Marked-complete tasks are visually distinguished.
- Available colors: Blue, Red, Green, Yellow, Orange, and Purple.
- Tasks must last at least five minutes and cannot end after 24:00.
- Navigate to the previous or next day/week and return to today.
- Zoom the timeline from 0.75x to 3x.
- `Ctrl`/`Cmd` + mouse wheel zooms while the pointer is over the calendar; clicking the scrollbar first is not required.
- `Ctrl+0`/`Cmd+0` restores 100% zoom while the pointer is over the calendar.
- Selected date, Day/Week mode, and zoom level are restored from the saved workspace.

Calendar entries display the title of every linked note directly inside the task. Selecting a title opens that specific note's editor in the Notes section; long titles are shortened visually and remain available in a tooltip. Linked notes are also available in the agenda and task-edit dialog.
Renaming, linking, unlinking, or deleting a note refreshes its Calendar and agenda references automatically without changing the selected date or calendar view.

### Tasks

The Tasks section is a dedicated control center for every task stored in the calendar:

- View all calendar tasks in chronological order.
- Metrics for total, due today, next seven days, and completed tasks.
- Search by title or description.
- Filter by All tasks, Today, Upcoming, Overdue, or Completed.
- Mark a task completed or open again.
- Click a task row to open the same editor used by Calendar.
- Open the corresponding calendar date.
- Delete a task with confirmation.
- Open any linked note by selecting its title directly in the task row.
- Creating or editing a task opens the same searchable **Home → folders → subfolders → notes** picker used by Calendar, including multiple selection.

Calendar and Tasks always operate on the same task data; changes made in one section immediately appear in the other.

### Notes

The Notes section provides a per-account knowledge workspace.

#### Note Library

- Create any number of notes.
- Create persistent folders with **Add folder** and open them like a dedicated workspace.
- Create nested folders by using **Add folder** while another folder is open.
- Navigate the complete clickable breadcrumb path, such as **Home › Projects › Client A**.
- Drop a note onto any breadcrumb segment to move it directly to that location.
- The breadcrumb segment currently under the pointer is highlighted during a drag, making the destination name clear before dropping.
- The breadcrumb bar scrolls long paths and automatically keeps the current location visible.
- Dropping on **Home** moves the note to **All notes**; dropping on a folder name moves it into that folder.
- Drag a complete note card onto a folder to move it there.
- Drag a folder card onto another folder to move the complete folder tree, or drop it on a breadcrumb to move it to an ancestor location.
- Invalid folder moves that would create a cycle, target the same location, or duplicate a sibling folder name are rejected.
- Move a note between folders, or back to **All notes**, from the editor's folder selector, which displays each folder's complete breadcrumb path.
- Rename folders or delete them; deleting a folder safely moves its notes and subfolders to the deleted folder's parent location.
- Choose Plain text (`.txt`) or Markdown (`.md`) when creating a note.
- Search by title or content.
- Reorder note cards with drag and drop.
- The chosen order is persisted.
- Click a card to open a full-workspace editor.
- Rename notes from the title field.
- Delete notes with confirmation.
- Changes are saved automatically.

TXT and Markdown are stored as note formats inside the local VoidReach workspace. They are not currently exported as standalone filesystem files.

#### Typography

Both TXT and Markdown notes support per-note presentation settings:

- Any font family installed on the system.
- Font sizes from 12 to 48 px.
- Regular, Medium, Semibold, and Bold weights.
- Quick whole-note Bold control.
- Whole-note italic style.
- Reset to the default typography.

The settings are saved with the note and restored the next time it is opened.

#### Markdown Editing

Markdown notes use a RichTextFX `CodeArea`, so the text, syntax colors, selection, and blinking insertion cursor are rendered by one native editor control.

- Heading, bold, italic, link, code, and checklist toolbar actions.
- `Ctrl+B`/`Cmd+B` wraps the selection in Markdown bold syntax.
- `Ctrl+I`/`Cmd+I` wraps the selection in Markdown italic syntax.
- Tab inserts four spaces for code indentation.
- Inline code uses single backticks.
- Selecting multiple lines and pressing Code creates a fenced code block with triple backticks.
- Older notes containing multiline code between single backticks are recognized for compatibility.
- Obsidian-style wiki links such as `[[Project plan]]` open the matching note from Preview.
- Standard Markdown links are rendered distinctly; clicking one copies its target address to the clipboard.

#### Live Code Highlighting

Code can be written directly in the note editor and displayed with IDE-style semantic syntax highlighting while you type, making keywords, types, variables, functions, literals, comments, and operators visually distinct.

Code inside backticks or fenced blocks is highlighted while editing and in Preview. The lightweight multilingual lexer distinguishes common:

- Keywords
- Data types and class names
- Variables
- Functions
- Strings
- Numbers
- Comments
- Annotations
- Operators

The highlighter is language-agnostic and designed for common Java, JavaScript, Python, SQL, and similar syntax. It does not replace a compiler or a full language server.

#### Markdown Preview

- Separate reading mode with rendered Markdown structure.
- Headings, paragraphs, lists, checklists, quotes, separators, bold, italic, strikethrough, inline code, and fenced code blocks.
- Syntax-highlighted code.
- Clickable Obsidian-style note links.
- Independent Preview font family.
- Independent Preview font size from 12 to 48 px.
- Custom Preview text color.
- Reset reading appearance to the active-theme defaults.
- Preview settings are saved separately for every note.

#### Linking Notes and Tasks

- Notes and tasks use a many-to-many relationship: every note can link to multiple tasks and every task can link to multiple notes.
- Add multiple task links from the note editor; its linked-task menu shows the current count and provides separate Open and Unlink actions.
- Select or clear multiple note links from a searchable hierarchical picker in the task dialog. The picker mirrors **Home**, folders, subfolders, and note files, and filters notes by title without losing the current selections.
- The task dialog displays every currently linked note as an individual button that opens its editor in the Notes section.
- Note cards summarize all available linked tasks.
- Open linked notes from Tasks, Calendar entries, the calendar agenda, or the task dialog.
- Deleting a task removes only that task's links without deleting notes or affecting their links to other tasks.

### Agenda Sidebar

- Toggle the agenda panel from the top toolbar.
- Monthly mini-calendar with previous/next month navigation.
- Highlights the selected date and the current day.
- Displays tasks for the selected day or week.
- Click an agenda item to open its task.
- Open notes linked to agenda tasks.

### Themes

Four themes are available:

- Dark
- Light
- Blue-Gray
- Gray Blue

The last selected theme is stored per account and restored when that account opens the application again. Themes are applied consistently to the main interface, dialogs, calendar, notes editor, Markdown Preview, and syntax highlighting.

### Profile Picture

- Select PNG, JPG, or JPEG images.
- Maximum upload size: 10 MB.
- Supported dimensions range from 300×300 to 20,000×20,000 pixels.
- Interactive circular crop with drag and zoom controls.
- The optimized master image is stored as PNG at up to 1024×1024 pixels.
- Display renditions account for HiDPI output scaling.
- The remembered account avatar is preloaded during startup.
- One master and up to two disposable display renditions are retained.

## Keyboard and Mouse Reference

| Context | Action |
|---|---|
| Contacts | Click a row to open the editor (Quick edit off) |
| Contacts | Click a cell to edit it in place (Quick edit on) |
| Contacts | Right-click a row to open the pop-up editor (Quick edit on) |
| Contacts | `Esc` cancels an in-place edit; `Enter` saves it |
| Contacts | Click a column label to rename it |
| Contacts | Drag a column header to reorder it |
| Contacts | Right-click a custom column label to remove the field |
| Contacts | `Ctrl+C` / `Cmd+C` copies selected contacts |
| Calendar | Click a task to edit |
| Calendar | Drag a task to reschedule it |
| Calendar | Drag the lower edge to change duration |
| Calendar | `Ctrl` / `Cmd` + mouse wheel changes zoom |
| Calendar | `Ctrl+0` / `Cmd+0` resets zoom |
| Tasks | Click a task row to edit it |
| Notes | Drag a note card to reorder it |
| Notes | Drag a note card onto a folder to move it |
| Markdown | `Ctrl+B` / `Cmd+B` inserts bold syntax |
| Markdown | `Ctrl+I` / `Cmd+I` inserts italic syntax |
| Markdown | Tab inserts four spaces |

## Local Persistence and Recovery

VoidReach stores application data under `.voidreach-crm` in the current user's home directory:

```text
~/.voidreach-crm/
├── users.properties                         # accounts, credentials, avatar name, preferred theme
├── users.properties.bak                     # previous atomic revision when available
├── session.properties                       # remembered account email only
├── data/
│   ├── <account-id>.properties              # contacts, custom fields, tasks, notes, ordering, links, view preferences
│   └── <account-id>.properties.bak          # previous atomic workspace revision
├── avatars/
│   ├── <account-id>-<uuid>.png              # authoritative cropped master
│   └── <account-id>-<uuid>.png.<size>.rendition.png
└── backup/<account-id>/
    └── crm-data-<timestamp>.properties      # rotating automatic workspace snapshots
```

- Workspace saves are debounced and performed away from the JavaFX UI thread.
- The latest state is flushed before signing out.
- Primary files are written atomically.
- The application attempts recovery from `.bak` when the primary revision cannot be read.
- Corrupt records are isolated in `.corrupt.properties` files so valid records can still load when possible.
- Automatic snapshots run every two minutes while an account workspace is open.
- Up to three automatic snapshots are retained per account.

Workspace files and automatic backups are local files and are not encrypted. Protect the operating-system account and its home directory accordingly.

## Requirements

- JDK 26; the project is currently configured for Java 26.
- Apache Maven 3.9 or newer.
- macOS, Windows, or Linux with JavaFX support.

Maven resolves JavaFX 26.0.1, RichTextFX 0.11.7, Ikonli, and image-processing dependencies.

Confirm that Maven uses the correct JDK:

```bash
java -version
mvn -version
```

If Maven selects a different JDK, update `JAVA_HOME` before building or running VoidReach.

## Running the Application

From the Maven module:

```bash
cd VoidReach-CRM-Final-No-FatJar
mvn clean javafx:run
```

On macOS or Linux, the helper script runs the same Maven goal:

```bash
cd VoidReach-CRM-Final-No-FatJar
./run.sh
```

For IntelliJ IDEA, import `VoidReach-CRM-Final-No-FatJar/pom.xml` as a Maven project and run `com.crm.app.AppLauncher`.

## Tests

Run the complete unit-test suite:

```bash
cd VoidReach-CRM-Final-No-FatJar
mvn clean test
```

Run the bounded-memory large-avatar integration test:

```bash
mvn verify -Pavatar-large-image
```

The tests cover contact and overview behavior, task and note models, Markdown code tokenization, themes, account/workspace persistence, corruption recovery, atomic saves, rotating backups, asynchronous workspace saves, and avatar processing.

## Native Packaging

Native application images must be created on their target operating system. Packaging requires a JDK 26+ whose `bin` directory (containing `jpackage`) is on `PATH`, plus Apache Maven; both scripts verify this before building.

### macOS

```bash
cd VoidReach-CRM-Final-No-FatJar
./scripts/package-macos.sh
```

The application image is created at `target/packages/macos/VoidReach.app`.

### Windows

Run the script from **PowerShell** (not from `cmd.exe`). The default PowerShell execution policy blocks local scripts, so launch it with a per-run bypass:

```powershell
cd VoidReach-CRM-Final-No-FatJar
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1
```

If your execution policy already allows local scripts, `./scripts/package-windows.ps1` works too.

The application image is created at `target\packages\windows\VoidReach\` and is started with `VoidReach.exe` inside that folder.

Packages are written under `target/packages/`. Packaging scripts do not run the test suite automatically, so run `mvn clean test` before distributing a build.

Platform icons are located at:

- `src/main/packaging/macos/VoidReach.icns`
- `src/main/packaging/windows/VoidReach.ico`

## Technology

- Java 26
- JavaFX 26.0.1
- RichTextFX 0.11.7
- Ikonli FontAwesome 5
- TwelveMonkeys ImageIO
- Maven
- JUnit 5

## Project Structure

```text
VoidReach-CRM-Calendar-Task/
├── README.md
├── LICENSE
└── VoidReach-CRM-Final-No-FatJar/
    ├── pom.xml
    ├── run.sh
    ├── scripts/
    │   ├── package-macos.sh
    │   └── package-windows.ps1
    └── src/
        ├── main/
        │   ├── java/com/crm/
        │   │   ├── app/                    # launcher and JavaFX application lifecycle
        │   │   ├── controller/             # account, overview, contacts, calendar, tasks, notes, navigation
        │   │   ├── model/                  # contacts, tasks, notes, accounts, workspace snapshots
        │   │   ├── repository/             # atomic local storage, recovery, quarantine, backups
        │   │   └── service/                # authentication, sessions, themes, avatars, Markdown code lexer
        │   ├── packaging/                  # native platform icons
        │   └── resources/
        │       ├── com/crm/view/           # FXML layouts
        │       ├── css/                    # Light, Dark, Blue-gray, and Gray Blue themes
        │       └── images/                 # application graphics
        └── test/java/com/crm/              # unit and integration tests
```

## License

See [LICENSE](LICENSE) for the terms of use.
