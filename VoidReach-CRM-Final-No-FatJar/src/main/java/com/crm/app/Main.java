package com.crm.app;

import com.crm.controller.SplashScreenController;
import com.crm.controller.LoginController;
import com.crm.controller.MainController;
import com.crm.model.UserAccount;
import com.crm.repository.LocalUserRepository;
import com.crm.service.AvatarService;
import com.crm.service.AvatarService.PreloadedAvatars;
import com.crm.service.SessionService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.Taskbar;
import java.io.InputStream;
import java.util.Locale;
import javax.imageio.ImageIO;

public class Main extends Application {

    private static final String APP_ICON_PATH = "/images/app-icon.png";

    private Stage mainStage;
    private Stage splashStage;
    private SplashScreenController splashController;
    private Parent loginRoot;
    private LoginController loginController;
    private MainController rememberedAppController;
    private UserAccount rememberedUser;
    private PreloadedAvatars rememberedAvatars;
    private final SessionService sessionService = new SessionService(new LocalUserRepository());

    @Override
    public void start(Stage primaryStage) throws Exception {
        Locale.setDefault(Locale.ENGLISH);
        this.mainStage = primaryStage;
        configureMacDockIcon();
        showSplashScreen();
    }

    private void showSplashScreen() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crm/view/SplashScreen.fxml"));
        Parent root = loader.load();
        splashController = loader.getController();

        splashStage = new Stage();
        splashStage.initStyle(StageStyle.TRANSPARENT);
        splashStage.setAlwaysOnTop(true);
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/style-dark.css").toExternalForm());
        splashStage.setScene(scene);
        
        addAppIcon(splashStage);
        splashStage.centerOnScreen();
        splashStage.show();
        splashStage.toFront();
        splashStage.requestFocus();

        startLoadingTask();
    }

    private void startLoadingTask() {
        Task<StartupData> loadTask = new Task<>() {
            @Override
            protected StartupData call() throws Exception {
                // 1. Core Initialization
                updateMessage("Core Initialization...");
                updateProgress(0.1, 1.0);
                Thread.sleep(400); 

                // 2. Authentication and session loading
                updateMessage("Checking saved session...");
                updateProgress(0.3, 1.0);
                UserAccount savedUser = sessionService.getRememberedUser().orElse(null);
                PreloadedAvatars avatars = null;
                if (savedUser != null) {
                    updateMessage("Loading profile image...");
                    avatars = new AvatarService(new LocalUserRepository())
                            .preloadCommonRenditions(savedUser);
                    updateMessage("Loading your workspace...");
                }
                updateProgress(0.6, 1.0);
                Thread.sleep(300);

                // 3. Database and Model Setup
                updateMessage("Preparing local account storage...");
                updateProgress(0.8, 1.0);
                Thread.sleep(400);

                // 4. Finalization
                updateMessage("Interface Optimization...");
                updateProgress(1.0, 1.0);
                Thread.sleep(300);

                return new StartupData(savedUser, avatars);
            }
        };

        loadTask.messageProperty().addListener((obs, old, msg) -> splashController.setStatus(msg));
        loadTask.progressProperty().addListener((obs, old, prog) -> splashController.setProgress(prog.doubleValue()));

        loadTask.setOnSucceeded(e -> {
            try {
                StartupData startupData = loadTask.getValue();
                rememberedUser = startupData.user();
                rememberedAvatars = startupData.avatars();
                loadLoginView();
                if (rememberedUser != null) loadAndShowRememberedApp();
                else transitionToLogin();
            } catch (Exception ex) {
                recoverFromStartupFailure(ex);
            }
        });
        loadTask.setOnFailed(e -> recoverFromStartupFailure(loadTask.getException()));

        Thread loaderThread = new Thread(loadTask, "voidreach-startup-loader");
        loaderThread.setDaemon(true);
        loaderThread.start();
    }

    private void loadLoginView() throws Exception {
        FXMLLoader loginLoader = new FXMLLoader(getClass().getResource("/com/crm/view/LoginView.fxml"));
        loginRoot = loginLoader.load();
        loginController = loginLoader.getController();
        configureLoginHandler();
    }

    private void loadAndShowRememberedApp() throws Exception {
        FXMLLoader appLoader = new FXMLLoader(getClass().getResource("/com/crm/view/MainView.fxml"));
        Parent root = appLoader.load();
        rememberedAppController = appLoader.getController();
        transitionToRememberedApp(root);
    }

    private void transitionToLogin() {
        if (loginRoot == null) return;
        loginController.resetForLoginScreen();

        // 1. Main Stage Setup
        mainStage.setTitle("VoidReach CRM — Sign in");
        addAppIcon(mainStage);
        
        Scene scene = new Scene(loginRoot);
        scene.setFill(Color.web("#0f172a"));
        scene.getStylesheets().add(getClass().getResource("/css/style-dark.css").toExternalForm());
        
        mainStage.setScene(scene);
        mainStage.setMaximized(false);
        mainStage.setWidth(580);
        mainStage.setHeight(700);
        mainStage.centerOnScreen();
        showMainStage(loginController::requestInitialFocus);
    }

    private void transitionToRememberedApp(Parent root) {
        if (root == null || rememberedAppController == null) return;
        mainStage.setTitle("VoidReach CRM");
        addAppIcon(mainStage);
        Scene scene = new Scene(root);
        scene.setFill(Color.web("#0f172a"));
        scene.getStylesheets().add(getClass().getResource("/css/style-dark.css").toExternalForm());
        mainStage.setScene(scene);
        mainStage.setMaximized(true);
        Screen startupScreen = Screen.getPrimary();
        int targetPixelSize = (int) Math.ceil(56 * Math.max(
                startupScreen.getOutputScaleX(), startupScreen.getOutputScaleY()));
        if (rememberedAvatars == null) {
            rememberedAppController.setCurrentUser(rememberedUser, this::logout);
        } else {
            rememberedAppController.setCurrentUser(
                    rememberedUser, this::logout,
                    rememberedAvatars.closestTo(targetPixelSize),
                    rememberedAvatars.closestSizeTo(targetPixelSize));
            rememberedAvatars = null;
        }
        showMainStage(rememberedAppController::requestInitialFocus);
    }

    private void configureLoginHandler() {
        loginController.setOnAuthenticated((user, remember) -> {
            String persistenceWarning = null;
            try {
                if (remember) sessionService.remember(user);
                else sessionService.forget();
            } catch (IllegalStateException e) {
                persistenceWarning = "Sign-in succeeded, but the saved session could not be updated.";
            }
            showMainApplication(user);
            if (persistenceWarning != null) showPersistenceWarning(persistenceWarning);
        });
    }

    private void showMainApplication(UserAccount user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crm/view/MainView.fxml"));
            Parent root = loader.load();
            MainController controller = loader.getController();
            controller.setCurrentUser(user, this::logout);
            Scene scene = new Scene(root);
            scene.setFill(Color.web("#0f172a"));
            scene.getStylesheets().add(getClass().getResource("/css/style-dark.css").toExternalForm());
            mainStage.setTitle("VoidReach CRM");
            addAppIcon(mainStage);
            mainStage.setScene(scene);
            mainStage.setMaximized(true);
            mainStage.show();
            mainStage.toFront();
            mainStage.requestFocus();
            Platform.runLater(controller::requestInitialFocus);
        } catch (Exception e) {
            throw new IllegalStateException("The application could not be opened", e);
        }
    }

    private void showLoginScreen() {
        mainStage.setTitle("VoidReach CRM — Sign in");
        // The login scene is retained for the application's lifetime; reset its form state on every logout.
        loginController.resetForLoginScreen();
        configureLoginHandler();
        Scene loginScene = new Scene(loginRoot);
        loginScene.setFill(Color.web("#0f172a"));
        loginScene.getStylesheets().add(getClass().getResource("/css/style-dark.css").toExternalForm());
        mainStage.setScene(loginScene);
        mainStage.setMaximized(false);
        mainStage.setWidth(580);
        mainStage.setHeight(700);
        mainStage.centerOnScreen();
        bringToFront(loginController::requestInitialFocus);
    }

    private void logout() {
        String persistenceWarning = null;
        try { sessionService.forget(); }
        catch (IllegalStateException e) { persistenceWarning = "The saved session could not be removed from disk."; }
        showLoginScreen();
        if (persistenceWarning != null) showPersistenceWarning(persistenceWarning);
    }

    private void showPersistenceWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Local save failed");
        alert.setHeaderText(null);
        alert.setContentText(message + " Data already open remains available in this session.");
        alert.showAndWait();
    }

    /** Closes the splash and offers a usable login screen even when startup data cannot be loaded. */
    private void recoverFromStartupFailure(Throwable failure) {
        closeSplashStage();
        try {
            rememberedUser = null;
            if (loginRoot == null || loginController == null) loadLoginView();
            transitionToLogin();

            ButtonType retry = new ButtonType("Retry startup", ButtonBar.ButtonData.OK_DONE);
            ButtonType continueToLogin = new ButtonType("Continue to sign in", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Recovery startup");
            alert.setHeaderText("Initial loading could not be completed.");
            alert.setContentText("The splash screen was closed. You can sign in manually or retry loading.");
            alert.getButtonTypes().setAll(retry, continueToLogin);
            if (alert.showAndWait().filter(retry::equals).isPresent()) retryStartup();
        } catch (Exception recoveryFailure) {
            showUnrecoverableStartupError(failure, recoveryFailure);
        }
    }

    private void retryStartup() {
        try {
            showSplashScreen();
        } catch (Exception retryFailure) {
            recoverFromStartupFailure(retryFailure);
        }
    }

    private void showUnrecoverableStartupError(Throwable failure, Exception recoveryFailure) {
        if (failure != null) recoveryFailure.addSuppressed(failure);
        ButtonType retry = new ButtonType("Retry", ButtonBar.ButtonData.OK_DONE);
        ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Startup failed");
        alert.setHeaderText("Even the sign-in screen could not be opened.");
        alert.setContentText("You can retry. If the problem persists, keep the .bak and .corrupt.properties files for recovery.");
        alert.getButtonTypes().setAll(retry, close);
        if (alert.showAndWait().filter(retry::equals).isPresent()) retryStartup();
        else Platform.exit();
    }

    private void closeSplashStage() {
        if (splashStage != null) {
            splashStage.close();
            splashStage = null;
        }
    }

    private void showMainStage(Runnable focusTarget) {
        closeSplashStage();
        mainStage.setOpacity(1.0);
        bringToFront(focusTarget);
    }

    /** Focuses the JavaFX window without starting a second native GUI toolkit. */
    private void bringToFront(Runnable focusTarget) {
        mainStage.setIconified(false);
        mainStage.show();
        mainStage.toFront();
        mainStage.requestFocus();
        Platform.runLater(() -> {
            if (!mainStage.isShowing()) return;
            mainStage.toFront();
            mainStage.requestFocus();
            focusTarget.run();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }

    private record StartupData(UserAccount user, PreloadedAvatars avatars) {}

    private void addAppIcon(Stage stage) {
        if (stage == null || stage.getIcons().isEmpty()) {
            Image icon = new Image(getClass().getResourceAsStream(APP_ICON_PATH));
            if (stage != null) stage.getIcons().add(icon);
        }
    }

    private void configureMacDockIcon() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return;
        if (!Taskbar.isTaskbarSupported()) return;

        Taskbar taskbar = Taskbar.getTaskbar();
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return;

        try (InputStream icon = getClass().getResourceAsStream(APP_ICON_PATH)) {
            if (icon != null) taskbar.setIconImage(ImageIO.read(icon));
        } catch (Exception ignored) {
            // The JavaFX stage icon still applies if macOS does not allow changing the Dock icon.
        }
    }
}
