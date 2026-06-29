package com.crm.app;

import com.crm.controller.SplashScreenController;
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
import javafx.animation.FadeTransition;
import javafx.util.Duration;

import java.awt.Taskbar;
import java.awt.Toolkit;
import java.util.concurrent.CompletableFuture;

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
                // 1. Inizializzazione Core
                updateMessage("Inizializzazione Core...");
                updateProgress(0.1, 1.0);
                Thread.sleep(400); 

                // 2. Caricamento Risorse UI (Parallelo)
                updateMessage("Caricamento Risorse Grafiche...");
                updateProgress(0.3, 1.0);
                FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/com/crm/view/MainView.fxml"));
                Parent root = mainLoader.load();
                updateProgress(0.6, 1.0);
                Thread.sleep(300);

                // 3. Setup Database e Modelli
                updateMessage("Sincronizzazione Database...");
                updateProgress(0.8, 1.0);
                Thread.sleep(400);

                // 4. Finalizzazione
                updateMessage("Ottimizzazione Interfaccia...");
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

        mainStage.setTitle("VoidReach CRM");
        mainStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/app-icon.png")));
        
        // Setup icone OS in background
        CompletableFuture.runAsync(() -> {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    java.awt.Image dockIcon = Toolkit.getDefaultToolkit()
                            .getImage(getClass().getResource("/images/app-icon.png"));
                    taskbar.setIconImage(dockIcon);
                }
            }
        });

        Scene scene = new Scene(mainRoot);
        scene.setFill(Color.web("#0f172a"));
        scene.getStylesheets().add(getClass().getResource("/css/style-dark.css").toExternalForm());
        
        mainStage.setScene(scene);
        mainStage.setMaximized(true);
        mainStage.setOpacity(0);
        mainStage.show();

        // Cross-Fade Premium
        FadeTransition fadeOutSplash = new FadeTransition(Duration.millis(600), splashStage.getScene().getRoot());
        fadeOutSplash.setFromValue(1.0);
        fadeOutSplash.setToValue(0.0);
        fadeOutSplash.setOnFinished(e -> splashStage.close());

        FadeTransition fadeInMain = new FadeTransition(Duration.millis(800), mainRoot);
        fadeInMain.setFromValue(0.0);
        fadeInMain.setToValue(1.0);
        
        mainStage.setOpacity(1); 
        fadeOutSplash.play();
        fadeInMain.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
