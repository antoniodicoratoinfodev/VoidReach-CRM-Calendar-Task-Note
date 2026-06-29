# VoidReach

VoidReach is a desktop CRM, calendar, and task management application built with Java and JavaFX, featuring a modern and clean interface designed for professional use. The project is actively under development and will be expanded with new features and modules over time.

---

## Screenshots

| macOS | Windows |
|-------|---------|
| ![VoidReach on macOS](sample/voidreachmac.png) | ![VoidReach on Windows](sample/voidreachwindows.png) |

---

## Features

### Contact Management
- **Contact Table** — displays contacts with the following fields: Name, Company, Job Title, Email, Phone, Last Interaction, and Tag.
- **Add Contact** — create new contacts via a dedicated dialog with all fields and tag assignment.
- **Edit Contact** — double-click any row to open the edit dialog and modify any field.
- **Delete Contact** — select a contact and press `Delete` or `Backspace`; a confirmation dialog prevents accidental deletions.
- **Color-coded Tags** — contacts can be labeled as **Client**, **Tech**, or **Follow-up**, each rendered with a distinct color badge.

### Search & Filtering
- **Real-time search** across Name, Company, and Email fields — results update instantly as you type.

### Pagination
- Configurable rows per page: **15**, **25**, **50**, **100**, or **All**.
- Live pagination info label showing the current range and total contacts (e.g. *Showing 1–15 of 103 Contacts*).

### Calendar & Task Management
- **Day-view timeline** with a full 24-hour grid, divided into hours and 15-minute intervals.
- **Create tasks** by clicking on any time slot on the timeline.
- **Drag & drop tasks** to reschedule them by moving them along the timeline.
- **Resize tasks** by dragging the bottom handle of any task block.
- **Edit tasks** by double-clicking a task or right-clicking it — opens a dialog to modify title, start/end time, color, and description.
- **Delete tasks** by selecting a task and pressing `Delete` or `Backspace`.
- **Task colors** — Blue, Red, Green, Yellow, Orange, Purple.
- **Date navigation** — previous day, next day, and jump-to-today buttons.

### Right Sidebar
- **Mini calendar** — monthly view with navigation arrows for month-by-month browsing; the selected day is highlighted in blue, today is outlined.
- **Upcoming Activities** — list of tasks for the selected day, showing time slot and title. Clicking an activity navigates to the Calendar view.

### Theme
- **Dark / Light mode toggle** — switch between themes at any time; the button icon updates accordingly.

### Navigation
The left sidebar provides access to all sections:
- **Dashboard** *(coming soon)*
- **Contacts** — fully implemented
- **Calendar** — fully implemented
- **Leads** *(coming soon)*
- **Opportunities** *(coming soon)*
- **Accounts** *(coming soon)*
- **Tasks** *(coming soon)*
- **Settings** *(coming soon)*

---

## Requirements

- Java JDK 17 or higher (developed and tested on JDK 25)
- Apache Maven

---

## How to Run

Clone the repository and, from the `CRM-APP` directory, run:

```bash
mvn clean javafx:run
```

Or use the provided script:

```bash
./run.sh
```

The project can also be opened and run directly from IntelliJ IDEA.

---

## Project Structure

```
CRM-APP(WINDOWS-MAC-LINUX)/
├── pom.xml
├── run.sh
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       ├── app/
        │       │   ├── AppLauncher.java    # Launcher wrapper
        │       │   └── Main.java           # JavaFX entry point
        │       ├── controller/
        │       │   ├── MainController.java # All UI logic
        │       │   └── SplashScreen.java   # Splash screen controller
        │       └── model/
        │           └── Contact.java        # Contact data model
        └── resources/
            └── com/
                ├── view/
                │   ├── MainView.fxml       # Main UI layout
                │   └── SplashScreen.fxml   # Splash screen layout
                ├── css/
                │   ├── style-dark.css      # Dark theme
                │   └── style.css           # Light theme
                └── images/
                    └── app-icon.png        # Application icon
```

---

## License

See [LICENSE](LICENSE) for terms of use.
