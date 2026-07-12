# VoidReach CRM

VoidReach is a local-first desktop CRM workspace built with Java 26 and JavaFX 26. It brings account management, contacts, calendar planning, task control, dashboards, and linked TXT/Markdown notes into one native application.

All workspace data is stored locally and separated by account. The application does not require a remote server, database installation, or JavaFX SDK installation.

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
- Built-in tags: `Client`, `Tech`, and `Follow-up`.
- Search in real time by contact information and description.
- Sort any visible field in ascending or descending order.
- Clear the active sorting rule.
- Paginate results using 15, 25, 50, or 100 rows, or display all contacts.
- Enable selection mode for single or bulk deletion.
- Copy selected contacts to the clipboard with `Ctrl+C` or `Cmd+C`.
- Resize and reorder table columns with the mouse.
- Rename a column by clicking directly on its label; dragging the surrounding header continues to reorder the column.

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
- Open any note linked to the task.
- Multiple linked notes are available through a compact menu.

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
- Select or clear multiple note links directly in the task dialog.
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

Three themes are available:

- Dark
- Light
- Blue-gray

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
| Contacts | Click a row to edit |
| Contacts | Click a column label to rename it |
| Contacts | Drag a column header to reorder it |
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
│   ├── <account-id>.properties              # contacts, tasks, notes, ordering, links, calendar settings
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

Maven resolves JavaFX 26.0.1, RichTextFX 0.11.7, Ikonli, and image-processing dependencies. A separate JavaFX SDK installation is not required.

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

Native application images must be created on their target operating system.

### macOS

```bash
cd VoidReach-CRM-Final-No-FatJar
./scripts/package-macos.sh
```

### Windows

Run the following script from PowerShell:

```powershell
cd VoidReach-CRM-Final-No-FatJar
./scripts/package-windows.ps1
```

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
├── sample/
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
        │       ├── css/                    # Light, Dark, and Blue-gray themes
        │       └── images/                 # application graphics
        └── test/java/com/crm/              # unit and integration tests
```

## Screenshots

| macOS | Windows |
|---|---|
| ![VoidReach on macOS](sample/voidreachmac.png) | ![VoidReach on Windows](sample/voidreachwindows.png) |

## License

See [LICENSE](LICENSE) for the terms of use.
