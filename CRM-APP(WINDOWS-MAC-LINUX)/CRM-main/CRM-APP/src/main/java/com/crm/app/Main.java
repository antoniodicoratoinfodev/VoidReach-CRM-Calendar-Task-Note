package com.crm.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.awt.Taskbar;
import java.awt.Toolkit;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/com/crm/view/MainView.fxml"));
        primaryStage.setTitle("CRM - Contacts | User 1");

        // Icona finestra (Windows / Linux / barra titolo macOS)
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/app-icon.png")));

        // Icona Dock macOS
        if (Taskbar.isTaskbarSupported()) {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                java.awt.Image dockIcon = Toolkit.getDefaultToolkit()
                        .getImage(getClass().getResource("/images/app-icon.png"));
                taskbar.setIconImage(dockIcon);
            }
        }

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/style-dark.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
