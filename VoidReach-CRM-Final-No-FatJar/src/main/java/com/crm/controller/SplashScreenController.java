package com.crm.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import javafx.animation.*;
import javafx.util.Duration;

public class SplashScreenController {

    @FXML private ImageView logoView;
    @FXML private Rectangle logoGlow;
    @FXML private Label statusLabel;
    @FXML private Label percentLabel;
    @FXML private Rectangle progressFill;

    private final double MAX_BAR_WIDTH = 320.0;

    @FXML
    public void initialize() {
        // Animazione logo: pulsazione e rotazione leggera
        ScaleTransition scale = new ScaleTransition(Duration.millis(2500), logoView);
        scale.setFromX(1.0); scale.setFromY(1.0);
        scale.setToX(1.1); scale.setToY(1.1);
        scale.setAutoReverse(true);
        scale.setCycleCount(Animation.INDEFINITE);
        scale.play();

        // Animazione Glow: respira con il logo
        FadeTransition glowFade = new FadeTransition(Duration.millis(2500), logoGlow);
        glowFade.setFromValue(0.1);
        glowFade.setToValue(0.3);
        glowFade.setAutoReverse(true);
        glowFade.setCycleCount(Animation.INDEFINITE);
        glowFade.play();
        
        // Apparizione iniziale
        FadeTransition fadeIn = new FadeTransition(Duration.millis(800), logoView);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    public void setStatus(String status) {
        if (statusLabel != null) statusLabel.setText(status.toUpperCase());
    }

    public void setProgress(double progress) {
        // progress è tra 0.0 e 1.0
        if (progressFill != null) {
            double targetWidth = progress * MAX_BAR_WIDTH;
            
            // Animazione fluida della barra
            Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(300), 
                new KeyValue(progressFill.widthProperty(), targetWidth, Interpolator.EASE_BOTH))
            );
            timeline.play();
            
            if (percentLabel != null) {
                percentLabel.setText((int)(progress * 100) + "%");
            }
        }
    }
}
