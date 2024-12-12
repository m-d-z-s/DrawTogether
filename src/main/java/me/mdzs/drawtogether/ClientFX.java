package me.mdzs.drawtogether;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

//попробовать Drawing2D для сглаживания линии

public class ClientFX extends Application {
    private final int WIDTH = 600;
    private final int HEIGHT = 400;
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    private PrintWriter out;
    private BufferedReader in;

    private GraphicsContext gc;
    private Color currentColor = Color.BLACK; // Локальный цвет
    private double currentLineWidth = 2.0;    // Локальная толщина пера
    private boolean isEraserMode = false;     // Режим стёрки

    @Override
    public void start(Stage primaryStage) {
        // --- Холст ---
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0,0,canvas.getWidth(), canvas.getHeight());
        gc.setStroke(currentColor);
        gc.setLineWidth(currentLineWidth);

        // --- Панель инструментов ---
        ColorPicker colorPicker = new ColorPicker(currentColor);
        Spinner<Double> lineWidthSpinner = new Spinner<>(1.0, 20.0, currentLineWidth, 1.0);
        Button clearButton = new Button("Clear Canvas");
        ToggleButton eraserButton = new ToggleButton("Eraser");

        HBox toolbar = new HBox(10, new Label("Color:"), colorPicker,
                new Label("Line Width:"), lineWidthSpinner, eraserButton, clearButton);
        toolbar.setPadding(new Insets(10));

        // --- Обработчики событий ---
        colorPicker.setOnAction(e -> {
            currentColor = colorPicker.getValue();
            if (!isEraserMode) {
                gc.setStroke(currentColor);
            }
        });

        lineWidthSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentLineWidth = newVal;
            gc.setLineWidth(currentLineWidth);
        });

        clearButton.setOnAction(e -> {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            sendDrawAction("CLEAR");
        });

        eraserButton.setOnAction(e -> {
            isEraserMode = eraserButton.isSelected();
            if (isEraserMode) {
                gc.setStroke(Color.WHITE); // Белый цвет для стёрки
            } else {
                gc.setStroke(currentColor); // Возврат к текущему цвету пера
            }
        });

        // Рисование на холсте
        canvas.setOnMousePressed(e -> {
            gc.setLineWidth(currentLineWidth);
            gc.beginPath();
            gc.moveTo(e.getX(), e.getY());
            sendDrawAction(String.format("START %.2f %.2f %s %.2f", e.getX(), e.getY(),
                    isEraserMode ? "ERASER" : currentColor.toString(), currentLineWidth));
        });

        canvas.setOnMouseDragged(e -> {
            gc.lineTo(e.getX(), e.getY());
            gc.stroke();
            sendDrawAction(String.format("DRAW %.2f %.2f %s %.2f", e.getX(), e.getY(),
                    isEraserMode ? "ERASER" : currentColor.toString(), currentLineWidth));
        });

        // --- Макет ---
        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(canvas);

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        primaryStage.setTitle("Shared Drawing Board");
        primaryStage.setScene(scene);
        primaryStage.show();

        connectToServer();
        listenForServerActions();
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            showError("Unable to connect to server.");
            System.exit(1);
        }
    }

    private void listenForServerActions() {
        new Thread(() -> {
            try {String line;
                while ((line = in.readLine()) != null) {
                    handleServerMessage(line);
                }
            } catch (IOException e) {
                showError("Disconnected from server.");
            }
        }).start();
    }

    private void sendDrawAction(String action) {
        if (out != null) {
            out.println(action);
        }
    }

    private void handleServerMessage(String message) {
        javafx.application.Platform.runLater(() -> {
            String[] parts = message.split(" ");
            String command = parts[0];

            switch (command) {
                case "START":
                    double startX = Double.parseDouble(parts[1]);
                    double startY = Double.parseDouble(parts[2]);
                    String modeOrColor = parts[3];
                    double lineWidth = Double.parseDouble(parts[4]);

                    gc.setLineWidth(lineWidth);
                    if (modeOrColor.equals("ERASER")) {
                        gc.setStroke(Color.WHITE); // Стёрка
                    } else {
                        gc.setStroke(Color.valueOf(modeOrColor));
                    }
                    gc.beginPath();
                    gc.moveTo(startX, startY);
                    break;
                case "DRAW":
                    double drawX = Double.parseDouble(parts[1]);
                    double drawY = Double.parseDouble(parts[2]);
                    String drawModeOrColor = parts[3];
                    double drawLineWidth = Double.parseDouble(parts[4]);

                    gc.setLineWidth(drawLineWidth);
                    if (drawModeOrColor.equals("ERASER")) {
                        gc.setStroke(Color.WHITE); // Стёрка
                    } else {
                        gc.setStroke(Color.valueOf(drawModeOrColor));
                    }

                    gc.lineTo(drawX, drawY);
                    gc.stroke();
                    break;
                case "CLEAR":
                    gc.clearRect(0, 0, WIDTH, HEIGHT);
                    break;
            }
        });
    }

    private void showError(String error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(error);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}