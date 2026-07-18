package com.crm.service;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Window;

import java.util.Objects;
import java.util.Optional;

/** Centralizes themed informational and error alerts. */
public final class DialogService {
    private static final double MIN_RESIZE_WIDTH = 200;
    private static final double MAX_RESIZE_WIDTH = 1000;
    private static final double MIN_RESIZE_HEIGHT = 60;
    private static final double MAX_RESIZE_HEIGHT = 700;

    private final ThemeService themeService;

    public DialogService(ThemeService themeService) {
        this.themeService = Objects.requireNonNull(themeService);
    }

    public void showError(String title, String content) {
        Alert alert = create(Alert.AlertType.ERROR, title, content);
        Node contentLabel = alert.getDialogPane().lookup(".content.label");
        if (contentLabel != null) contentLabel.setStyle("-fx-text-fill: #fca5a5;");
        alert.showAndWait();
    }

    public void showInfo(String title, String content) {
        create(Alert.AlertType.INFORMATION, title, content).showAndWait();
    }

    /** Themed warning with a confirm/cancel choice. Returns true only when the confirm button is chosen. */
    public boolean confirmWarning(String title, String content, String confirmText) {
        Alert alert = create(Alert.AlertType.WARNING, title, content);
        ButtonType proceed = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(cancel, proceed);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == proceed;
    }

    private Alert create(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        themeService.applyTo(alert);
        return alert;
    }

    /**
     * Wraps a text area with a browser-style grip in its bottom-right corner: dragging
     * the grip resizes the area (the dialog follows), double-clicking restores the
     * default size. Add the returned wrapper to the layout instead of the text area.
     */
    public static StackPane withResizeGrip(TextArea area) {
        SVGPath gripIcon = new SVGPath();
        gripIcon.setContent("M0 8 L8 0 M3 8 L8 3 M6 8 L8 6");
        gripIcon.setStroke(Color.gray(0.5, 0.9));
        gripIcon.setStrokeWidth(1.2);
        gripIcon.setFill(null);

        StackPane grip = new StackPane(gripIcon);
        grip.setCursor(Cursor.SE_RESIZE);
        grip.setMinSize(16, 16);
        grip.setPrefSize(16, 16);
        grip.setMaxSize(16, 16);

        // Screen coordinates stay stable while the dialog itself grows under the cursor.
        double[] dragStart = new double[4];
        grip.setOnMousePressed(event -> {
            dragStart[0] = event.getScreenX();
            dragStart[1] = event.getScreenY();
            dragStart[2] = area.getWidth();
            dragStart[3] = area.getHeight();
            event.consume();
        });
        grip.setOnMouseDragged(event -> {
            area.setPrefWidth(clamp(dragStart[2] + event.getScreenX() - dragStart[0],
                    MIN_RESIZE_WIDTH, MAX_RESIZE_WIDTH));
            area.setPrefHeight(clamp(dragStart[3] + event.getScreenY() - dragStart[1],
                    MIN_RESIZE_HEIGHT, MAX_RESIZE_HEIGHT));
            resizeWindowToContent(area);
            event.consume();
        });
        grip.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                area.setPrefWidth(Region.USE_COMPUTED_SIZE);
                area.setPrefHeight(Region.USE_COMPUTED_SIZE);
                resizeWindowToContent(area);
            }
            event.consume();
        });

        StackPane wrapper = new StackPane(area, grip);
        StackPane.setAlignment(grip, Pos.BOTTOM_RIGHT);
        return wrapper;
    }

    private static void resizeWindowToContent(Node content) {
        if (content.getScene() == null) return;
        Window window = content.getScene().getWindow();
        if (window != null) window.sizeToScene();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
