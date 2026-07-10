package com.crm.app;

import java.awt.Taskbar;
import java.awt.Toolkit;
import java.util.concurrent.CompletableFuture;

import com.crm.controller.SplashScreenController;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class Main extends Application {

    private Stage mainStage;
    private Stage splashStage;
    private SplashScreenController splashController;
    private Parent mainRoot;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.mainStage = primaryStage;
        showSplashScreen();
    }

    private void showSplashScreen() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crm/view/SplashScreen.fxml"));
        Parent root = loader.load();
        splashController = loader.getController();

        splashStage = new Stage();
        splashStage.initStyle(StageStyle.TRANSPARENT);
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/style-dark.css").toExternalForm());
        splashStage.setScene(scene);
        
        splashStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/app-icon.png")));
        splashStage.centerOnScreen();
        splashStage.show();

        startLoadingTask();
    }

    private void startLoadingTask() {
        Task<Parent> loadTask = new Task<>() {
            @Override
            protected Parent call() throws Exception {
                // 1. Core Initialization
                updateMessage("Core Initialization...");
                updateProgress(0.1, 1.0);
                Thread.sleep(400); 

                // 2. UI Resource Loading
                updateMessage("Loading UI Resources...");
                updateProgress(0.3, 1.0);
                FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/com/crm/view/MainView.fxml"));
                Parent root = mainLoader.load();
                updateProgress(0.6, 1.0);
                Thread.sleep(300);

                // 3. Database and Model Setup
                updateMessage("Database Synchronization...");
                updateProgress(0.8, 1.0);
                Thread.sleep(400);

                // 4. Finalization
                updateMessage("Interface Optimization...");
                updateProgress(1.0, 1.0);
                Thread.sleep(300);

                return root;
            }
        };

        loadTask.messageProperty().addListener((obs, old, msg) -> splashController.setStatus(msg));
        loadTask.progressProperty().addListener((obs, old, prog) -> splashController.setProgress(prog.doubleValue()));

        loadTask.setOnSucceeded(e -> {
            this.mainRoot = loadTask.getValue();
            transitionToMainApp();
        });

        new Thread(loadTask).start();
    }

    private void transitionToMainApp() {
        if (mainRoot == null) return;

        // 1. Main Stage Setup
        mainStage.setTitle("VoidReach CRM");
        mainStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/app-icon.png")));
        
        Scene scene = new Scene(mainRoot);
        scene.setFill(Color.web("#0f172a"));
        scene.getStylesheets().add(getClass().getResource("/css/style-dark.css").toExternalForm());
        
        mainStage.setScene(scene);
        mainStage.setMaximized(true);
        mainStage.setOpacity(0.0); // Fully invisible at start
        mainStage.show();

        // 2. Content Preparation
        mainRoot.setOpacity(1.0); // Content is already opaque, fade the whole Stage
        
        // 3. Timeline for smooth transition (Cross-Fade Stage)
        Timeline transitionTimeline = new Timeline();
        
        // Splash animation fades out faster (600ms)
        KeyValue splashValue = new KeyValue(splashStage.opacityProperty(), 0.0, Interpolator.EASE_BOTH);
        KeyFrame splashFrame = new KeyFrame(Duration.millis(600), splashValue);
        
        // Main animation fades in smoothly (800ms)
        KeyValue mainValue = new KeyValue(mainStage.opacityProperty(), 1.0, Interpolator.EASE_OUT);
        KeyFrame mainFrame = new KeyFrame(Duration.millis(800), mainValue);
        
        transitionTimeline.getKeyFrames().addAll(splashFrame, mainFrame);
        transitionTimeline.setOnFinished(e -> {
            splashStage.close();
            setupOSIcons();
        });

        // 4. Synchronized Execution (Optimized for macOS/Windows/Linux)
        Platform.runLater(() -> {
            // Delay for better feel
            new Thread(() -> {
                try {
                    Thread.sleep(250); 
                    Platform.runLater(transitionTimeline::play);
                } catch (InterruptedException ignored) {}
            }).start();
        });
    }

    private void setupOSIcons() {
        CompletableFuture.runAsync(() -> {
            try {
                if (Taskbar.isTaskbarSupported()) {
                    Taskbar taskbar = Taskbar.getTaskbar();
                    if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                        java.awt.Image dockIcon = Toolkit.getDefaultToolkit()
                                .getImage(getClass().getResource("/images/app-icon.png"));
                        taskbar.setIconImage(dockIcon);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
