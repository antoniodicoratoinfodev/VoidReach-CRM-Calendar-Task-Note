# VoidReach

VoidReach is a desktop CRM application developed with Java 26 and JavaFX 26.0.1. It currently provides local contact, calendar, and task management, account authentication, and persistent data storage on the user's computer.

> Current status: the **Contacts** and **Calendar** sections are operational. The other navigation items are present in the interface but still display an “under development” placeholder.

## Available Features

### Account and Authentication

- Registration with name, email address, and a password of at least 8 characters.
- Login and logout.
- Password recovery through a six-digit code that remains valid for 15 minutes. In this local version, the code is displayed directly in the application; no email is sent.
- Password changes and name updates from the account menu.
- **Stay signed in on this device** option: only the account email is stored, never the password.
- Passwords and password-recovery codes are stored as PBKDF2-derived values with a salt.

### Contacts

- Table showing name, company, job title, email, phone, last interaction, and tags.
- Contact creation and editing through a dialog.
- Available fields: name, company, job title, email, phone, tag, and description.
- Available tags: `Client`, `Tech`, and `Follow-up`.
- Real-time search by name, company, email, and description.
- Pagination with 15, 25, 50, or 100 contacts, or all contacts.
- Single or multiple selection and deletion with confirmation.
- Double-click a row to edit a contact.
- Copy selected contacts to the clipboard with `Ctrl+C` on Windows/Linux or `Cmd+C` on macOS.

### Calendar and Task Management

- **Day** and **Week** views on a 24-hour grid with 15-minute intervals.
- Create a task by clicking on the timeline.
- Edit tasks with a double-click or right-click.
- Reschedule tasks with drag and drop.
- Resize a task by dragging its bottom edge.
- Edit the date, start time, end time, description, and color.
- Tasks must last at least 5 minutes and remain within the same day.
- Available colors: `Blue`, `Red`, `Green`, `Yellow`, `Orange`, and `Purple`.
- Navigate to the previous or next day and return to today.
- Timeline zoom from 0.75x to 3x with `Ctrl`/`Cmd` + mouse wheel; `Ctrl+0`/`Cmd+0` resets the zoom.

### Sidebar and Theme

- Monthly mini-calendar with month navigation.
- Selected day and current day highlighting.
- List of tasks for the selected day; clicking a task opens the Calendar view.
- Dark and light themes selectable from the sidebar.
- Profile avatar support for PNG and JPG images, with preview, circular cropping, and zoom.

### Sections Not Yet Implemented

The **Home**, **Dashboard**, **Leads**, **Opportunities**, **Accounts**, **Tasks**, and **Settings** items are navigable but currently display a placeholder. **Tasks** is not a standalone section: task management is integrated into the **Calendar**.


## Local Persistence and Security

Data is stored in the `.voidreach-crm` directory inside the user's home directory:

```text
~/.voidreach-crm/
├── users.properties                         # accounts and derived credentials
├── users.properties.bak                     # previous revision, when available
├── session.properties                      # remembered-session email
├── data/<account-id>.properties             # contacts, tasks, and calendar preferences
├── data/<account-id>.properties.bak         # atomic backup of the data file
├── avatars/<account-id>-<uuid>.png          # cropped avatar saved as PNG
└── backup/<account-id>/                     # automatic CRM data backups
    └── crm-data-<timestamp>.properties      # up to 3 copies are retained
```

Primary saves are atomic. If a file is corrupted, the application attempts to recover the previous `.bak` revision. Unreadable records are isolated in files with the `.corrupt.properties` suffix so that the remaining valid data can still be loaded when possible.

Avatars support PNG and JPG/JPEG images ranging from 300×300 to 20,000×20,000 pixels, with a maximum file size of 10 MB. Images are processed with bounded memory usage and saved as a PNG master image of up to 1024×1024 pixels.

## Requirements

- JDK 26 (the project is currently verified with JDK 26.0.1)
- Apache Maven 3.9 or newer
- An operating system with JavaFX support (macOS, Windows, or Linux)

JavaFX 26.0.1 is resolved by Maven; a separate JavaFX SDK installation is not required. Java 26 is a non-LTS release, so the runtime should be reassessed when Java 27 becomes available.

Confirm that Maven is using JDK 26 before building:

```bash
java -version
mvn -version
```

If Maven selects a different JDK, set `JAVA_HOME` to the JDK 26 installation before running the commands below.

## Running the Application

From the Maven module directory:

```bash
cd VoidReach-CRM-Final-No-FatJar
mvn clean javafx:run
```

Alternatively, on macOS/Linux:

```bash
cd VoidReach-CRM-Final-No-FatJar
./run.sh
```

The project can also be imported into IntelliJ IDEA as a Maven project by selecting the `pom.xml` file inside the `VoidReach-CRM-Final-No-FatJar` module.

## Native packages

The application icon is kept as a transparent 512×512 PNG for JavaFX. Native packages use platform-specific, multi-resolution files: `src/main/packaging/macos/VoidReach.icns` (macOS) and `src/main/packaging/windows/VoidReach.ico` (Windows).

Create a native app image on its target operating system:

```bash
cd VoidReach-CRM-Final-No-FatJar
./scripts/package-macos.sh
```

On Windows, run `scripts/package-windows.ps1` from PowerShell. Both scripts write only under `target/packages/`; run the test suite separately before publishing a package.

## Tests

To run the unit tests:

```bash
cd VoidReach-CRM-Final-No-FatJar
mvn test
```

The large-image integration test runs through the `avatar-large-image` profile:

```bash
mvn verify -Pavatar-large-image
```

## Project Structure

```text
VoidReach-CRM-Calendar-Task/
├── README.md
├── LICENSE
├── sample/
│   ├── voidreachmac.png
│   └── voidreachwindows.png
├── agenda_icon_preview.png
└── VoidReach-CRM-Final-No-FatJar/
    ├── pom.xml
    ├── run.sh
    ├── scripts/
    │   ├── package-macos.sh                         # macOS app-image with VoidReach.icns
    │   └── package-windows.ps1                      # Windows app-image with VoidReach.ico
    └── src/
        ├── main/
        │   ├── java/com/crm/
        │   │   ├── app/
        │   │   │   ├── AppLauncher.java            # Application launcher and configuration
        │   │   │   └── Main.java                   # JavaFX entry point and transitions
        │   │   ├── controller/
        │   │   │   ├── AccountController.java       # Account, password, logout, and avatar UI
        │   │   │   ├── CalendarController.java      # Calendar, tasks, timeline, and zoom
        │   │   │   ├── ContactsController.java      # Contact table, dialogs, search, and pagination
        │   │   │   ├── LoginController.java         # Login, registration, and recovery
        │   │   │   ├── MainController.java          # Main-view composition and shared workspace state
        │   │   │   ├── NavigationController.java    # Sidebar navigation and view switching
        │   │   │   └── SplashScreenController.java  # Splash screen
        │   │   ├── model/
        │   │   │   ├── Contact.java                 # Contact model
        │   │   │   ├── CrmDataSnapshot.java         # Account data snapshot
        │   │   │   ├── Task.java                    # Activity model
        │   │   │   └── UserAccount.java             # Account model
        │   │   ├── repository/
        │   │   │   ├── AtomicPropertiesStore.java  # Atomic saves and .bak files
        │   │   │   ├── CorruptRecordQuarantine.java # Corrupt-record isolation
        │   │   │   ├── CrmBackupService.java        # Rotating automatic backups
        │   │   │   ├── CrmDataRepository.java       # CRM data contract
        │   │   │   ├── LocalCrmDataRepository.java  # Local CRM persistence
        │   │   │   ├── LocalUserRepository.java     # Local account persistence
        │   │   │   └── UserRepository.java          # Account persistence contract
        │   │   └── service/
        │   │       ├── AuthService.java             # Registration and authentication
        │   │       ├── AvatarImageProcessor.java    # Avatar validation, cropping, and resizing
        │   │       ├── AvatarService.java            # Avatar storage and loading
        │   │       ├── CrmWorkspaceService.java      # CRM persistence and backup lifecycle
        │   │       ├── DialogService.java            # Themed application alerts
        │   │       ├── SessionService.java           # Remembered session
        │   │       └── ThemeService.java             # Scene and dialog theme management
        │   ├── packaging/
        │   │   ├── macos/VoidReach.icns              # Native macOS icon (16–512 px)
        │   │   └── windows/VoidReach.ico             # Native Windows icon (16–256 px)
        │   └── resources/
        │       ├── com/crm/view/
        │       │   ├── LoginView.fxml                # Authentication views
        │       │   ├── MainView.fxml                 # Main layout
        │       │   └── SplashScreen.fxml             # Splash-screen layout
        │       ├── css/
        │       │   ├── style.css                     # Light theme
        │       │   └── style-dark.css                # Dark theme
        │       └── images/
        │           └── app-icon.png                  # Application icon
        └── test/java/com/crm/
            ├── model/                                # Task model tests
            ├── repository/                           # Persistence and backup tests
            └── service/                              # Avatar and image-processing tests
```

## Screenshots

| macOS | Windows |
|---|---|
| ![VoidReach on macOS](sample/voidreachmac.png) | ![VoidReach on Windows](sample/voidreachwindows.png) |

## License

See [LICENSE](LICENSE) for the terms of use.
