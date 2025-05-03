import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MainView {
    private final Scene scene;
    private final FolderManager folderManager;
    private final VBox contentPane;
    private final Stack<File> navigationStack = new Stack<>();
    private final Button backButton;
    private final Button addFolderButton;
    private final ComboBox<String> sortComboBox;
    private final CheckBox sortOrderCheckBox;
    private final Label sortOrderLabel;
    private final BorderPane mainLayout;
    private static final long SIZE_CALCULATION_TIMEOUT = 5000;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private List<FileItem> originalItemsCache = new ArrayList<>();
    private boolean isDescending = false;
    private boolean isInVirtualFolder = false;

    private static final String PRIMARY_COLOR = "#4a6baf";
    private static final String SECONDARY_COLOR = "#f8f9fa";
    private static final String ACCENT_COLOR = "#ff7043";
    private static final String CARD_BG = "#ffffff";
    private static final String CARD_HOVER = "#f5f9ff";
    private static final String FOLDER_NAME_COLOR = "#2c4a8c";
    private static final String DELETE_BUTTON_COLOR = "#e74c3c";

    private static final String FOLDER_ICON = "📁";
    private static final String IMAGE_ICON = "🖼️";
    private static final String VIDEO_ICON = "🎬";
    private static final String AUDIO_ICON = "🎵";
    private static final String PDF_ICON = "📄";
    private static final String DOC_ICON = "📝";
    private static final String DEFAULT_FILE_ICON = "📋";
    private static final String DELETE_ICON = "🗑️";

    public MainView() {
        this.backButton = new Button("←");
        this.addFolderButton = new Button("＋ Add Folder");
        this.sortComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "Sort by name", "Sort by size", "Sort by extension"
        ));
        this.sortOrderCheckBox = new CheckBox();
        this.sortOrderLabel = new Label("Asc");
        this.folderManager = new FolderManager();

        sortOrderCheckBox.setStyle("-fx-background-color: transparent;");
        sortOrderLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        sortOrderCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            isDescending = newVal;
            sortOrderLabel.setText(isDescending ? "Desc" : "Asc");
            if (!isInVirtualFolder && sortComboBox.getValue() != null) {
                sortFiles(sortComboBox.getValue().replace("Sort by ", "").toLowerCase());
            }
        });

        mainLayout = new BorderPane();
        mainLayout.setBackground(new Background(new BackgroundFill(
                new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#f5f7fa")),
                        new Stop(1, Color.web("#c3cfe2"))),
                CornerRadii.EMPTY, Insets.EMPTY)));
        mainLayout.setPadding(new Insets(15));

        HBox toolbar = createToolbar();

        HBox pathBar = new HBox();
        pathBar.setPadding(new Insets(5, 10, 5, 10));
        pathBar.setStyle("-fx-background-color: #e0e8ff; -fx-background-radius: 5;");

        VBox topContainer = new VBox(toolbar, pathBar);
        mainLayout.setTop(topContainer);

        contentPane = new VBox(5);
        contentPane.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(contentPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: transparent;");

        StackPane contentContainer = new StackPane(scrollPane);
        contentContainer.setPadding(new Insets(0));
        contentContainer.setStyle("-fx-background-color: " + SECONDARY_COLOR + ";");
        contentContainer.setEffect(new DropShadow(5, Color.gray(0, 0.1)));

        mainLayout.setCenter(contentContainer);
        scene = new Scene(mainLayout);

        showRootFolders();
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(8, 15, 8, 15));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: " + PRIMARY_COLOR + "; -fx-background-radius: 5;");
        toolbar.setEffect(new InnerShadow(3, Color.gray(0, 0.2)));

        styleButton(backButton, SECONDARY_COLOR);
        backButton.setDisable(true);
        backButton.setOnAction(e -> goBack());

        styleButton(addFolderButton, ACCENT_COLOR);
        addFolderButton.setOnAction(e -> addFolder());

        styleComboBox(sortComboBox);

        sortComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isInVirtualFolder) {
                String sortBy = newVal.replace("Sort by ", "").toLowerCase();
                sortFiles(sortBy);
            }
        });

        HBox sortOrderBox = new HBox(5, sortOrderCheckBox, sortOrderLabel);
        sortOrderBox.setAlignment(Pos.CENTER_LEFT);

        toolbar.getChildren().addAll(backButton, addFolderButton, sortComboBox, sortOrderBox);
        return toolbar;
    }


    private void updatePathBreadcrumbs(File currentFolder) {
        HBox pathContainer = (HBox) ((VBox) mainLayout.getTop()).getChildren().get(1);
        pathContainer.getChildren().clear();

        Label pcLabel = new Label("Main Screen");
        pcLabel.setStyle("-fx-text-fill: " + PRIMARY_COLOR + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        pcLabel.setOnMouseEntered(e -> pcLabel.setStyle("-fx-text-fill: " + ACCENT_COLOR + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-underline: true;"));
        pcLabel.setOnMouseExited(e -> pcLabel.setStyle("-fx-text-fill: " + PRIMARY_COLOR + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-underline: false;"));
        pcLabel.setOnMouseClicked(e -> showRootFolders());
        pathContainer.getChildren().add(pcLabel);

        if (currentFolder == null) return;

        List<File> pathHierarchy = new ArrayList<>();
        File temp = currentFolder;

        File topLevelFolder = null;
        for (File rootFolder : folderManager.getFolders()) {
            if (currentFolder.getAbsolutePath().startsWith(rootFolder.getAbsolutePath())) {
                topLevelFolder = rootFolder;
                break;
            }
        }

        if (topLevelFolder == null) return;

        while (temp != null && !temp.equals(topLevelFolder.getParentFile())) {
            pathHierarchy.add(0, temp);
            temp = temp.getParentFile();
        }

        for (File folder : pathHierarchy) {
            Label separator = new Label(" > ");
            separator.setStyle("-fx-text-fill: #666; -fx-font-size: 13px;");
            pathContainer.getChildren().add(separator);

            Label pathSegment = new Label(folder.getName());
            pathSegment.setStyle("-fx-text-fill: " + PRIMARY_COLOR + "; -fx-font-size: 13px; -fx-font-weight: bold;");
            pathSegment.setOnMouseEntered(e -> {
                pathSegment.setStyle("-fx-text-fill: " + ACCENT_COLOR + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-underline: true;");
                pathSegment.setCursor(javafx.scene.Cursor.HAND);
            });
            pathSegment.setOnMouseExited(e -> {
                pathSegment.setStyle("-fx-text-fill: " + PRIMARY_COLOR + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-underline: false;");
            });
            pathSegment.setOnMouseClicked(e -> showFolderContents(folder));
            pathContainer.getChildren().add(pathSegment);
        }
    }

    private void sortFiles(String sortBy) {
        List<FileItem> allItems = collectCurrentItems();
        contentPane.getChildren().clear();

        if ("extension".equals(sortBy)) {
            Map<String, List<FileItem>> groupedFiles = allItems.stream()
                    .filter(item -> !item.getFile().isDirectory())
                    .collect(Collectors.groupingBy(item -> {
                        String ext = item.getExtension().toLowerCase();
                        if (ext.matches("jpg|jpeg|png|gif|bmp")) return "Images";
                        if (ext.matches("mp4|avi|mov|mkv|flv")) return "Videos";
                        if (ext.matches("mp3|wav|ogg|aac")) return "Audio";
                        if (ext.equals("pdf")) return "PDFs";
                        if (ext.matches("doc|docx|txt|rtf")) return "Documents";
                        return "Other Files";
                    }));

            List<FileItem> folders = allItems.stream()
                    .filter(item -> item.getFile().isDirectory())
                    .sorted((a, b) -> isDescending ?
                            b.getName().compareToIgnoreCase(a.getName()) :
                            a.getName().compareToIgnoreCase(b.getName()))
                    .collect(Collectors.toList());

            for (FileItem item : folders) {
                contentPane.getChildren().add(createFolderCard(item.getFile(), true));
            }

            groupedFiles.entrySet().stream()
                    .sorted((e1, e2) -> isDescending ?
                            e2.getKey().compareToIgnoreCase(e1.getKey()) :
                            e1.getKey().compareToIgnoreCase(e2.getKey()))
                    .forEach(entry -> {
                        if (!entry.getValue().isEmpty()) {
                            contentPane.getChildren().add(
                                    createVirtualFolderCard(entry.getKey(), entry.getValue()));
                        }
                    });
        } else {
            List<FileItem> folders = allItems.stream()
                    .filter(item -> item.getFile().isDirectory())
                    .collect(Collectors.toList());

            List<FileItem> files = allItems.stream()
                    .filter(item -> !item.getFile().isDirectory())
                    .collect(Collectors.toList());

            Sorter.mergeSort(folders, sortBy);
            if (isDescending) Collections.reverse(folders);

            Sorter.mergeSort(files, sortBy);
            if (isDescending) Collections.reverse(files);

            for (FileItem item : folders) {
                contentPane.getChildren().add(createFolderCard(item.getFile(), true));
            }

            for (FileItem item : files) {
                contentPane.getChildren().add(createFileCard(item.getFile()));
            }
        }
    }

    private void showVirtualFolderContents(String folderName, List<FileItem> files) {
        isInVirtualFolder = true;
        originalItemsCache = collectCurrentItems();

        sortComboBox.setDisable(true);
        sortOrderCheckBox.setDisable(true);
        sortOrderLabel.setDisable(true);

        contentPane.getChildren().clear();

        Button backButton = new Button("← Back to " + folderName);
        backButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        backButton.setOnAction(e -> {
            isInVirtualFolder = false;

            sortComboBox.setDisable(false);
            sortOrderCheckBox.setDisable(false);
            sortOrderLabel.setDisable(false);

            if (originalItemsCache != null) {
                contentPane.getChildren().clear();
                originalItemsCache.forEach(item -> {
                    if (item.getFile().isDirectory()) {
                        contentPane.getChildren().add(createFolderCard(item.getFile(), true));
                    } else {
                        contentPane.getChildren().add(createFileCard(item.getFile()));
                    }
                });
            }
        });
        contentPane.getChildren().add(backButton);

        List<FileItem> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        if (isDescending) {
            Collections.reverse(sortedFiles);
        }
        sortedFiles.forEach(item -> contentPane.getChildren().add(createFileCard(item.getFile())));
    }



    private StackPane createVirtualFolderCard(String folderName, List<FileItem> files) {
        StackPane card = new StackPane();
        card.setUserData(files);
        card.setPrefSize(750, 60);
        card.setMaxWidth(750);

        Rectangle bg = new Rectangle(750, 60);
        bg.setArcHeight(10);
        bg.setArcWidth(10);
        bg.setFill(Color.web("#e3f2fd"));
        bg.setStroke(Color.web("#2196F3"));
        bg.setStrokeWidth(1.2);

        HBox content = new HBox(15);
        content.setPadding(new Insets(8, 15, 8, 15));
        content.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("📂");
        icon.setStyle("-fx-font-size: 20;");

        VBox details = new VBox(3);
        details.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(folderName);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #2196F3;");

        Label countLabel = new Label(files.size() + " files");
        countLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        details.getChildren().addAll(nameLabel, countLabel);
        content.getChildren().addAll(icon, details);
        card.getChildren().addAll(bg, content);

        card.setOnMouseClicked(e -> showVirtualFolderContents(folderName, files));
        return card;
    }

    private void styleButton(Button button, String bgColor) {
        button.setStyle("-fx-background-color: " + bgColor + "; " +
                "-fx-text-fill: " + (bgColor.equals(ACCENT_COLOR) ? "white" : "#333") + "; " +
                "-fx-font-weight: bold; " +
                "-fx-background-radius: 4; " +
                "-fx-padding: 6 12; " +
                "-fx-cursor: hand;");

        button.setOnMouseEntered(e -> button.setEffect(new Glow(0.15)));
        button.setOnMouseExited(e -> button.setEffect(null));
    }

    private void styleComboBox(ComboBox<String> comboBox) {
        comboBox.setStyle("-fx-background-color: white; " +
                "-fx-border-color: #ddd; " +
                "-fx-border-radius: 4; " +
                "-fx-padding: 4 8;");
        comboBox.setPromptText("Sort by...");
    }

    private void addFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Folder");
        File selectedFolder = directoryChooser.showDialog(null);

        if (selectedFolder != null) {
            folderManager.addFolder(selectedFolder);
            showRootFolders();
        }
    }

    private void showRootFolders() {
        contentPane.getChildren().clear();
        addFolderButton.setVisible(true);
        navigationStack.clear();
        backButton.setDisable(true);
        updatePathBreadcrumbs(null);

        LinkedList<File> folders = folderManager.getFolders();
        if (folders.isEmpty()) {
            Label emptyLabel = new Label("No folders added yet. Click 'Add Folder' to start.");
            emptyLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 13px;");
            contentPane.getChildren().add(emptyLabel);
        } else {
            List<FileItem> folderItems = folders.stream()
                    .map(FileItem::new)
                    .collect(Collectors.toList());

            Sorter.mergeSort(folderItems, "name");
            if (isDescending) {
                Collections.reverse(folderItems);
            }

            for (FileItem item : folderItems) {
                contentPane.getChildren().add(createFolderCard(item.getFile(), true));
            }
        }
    }

    private void showFolderContents(File folder) {
        contentPane.getChildren().clear();
        addFolderButton.setVisible(false);
        backButton.setDisable(navigationStack.isEmpty());

        if (!navigationStack.isEmpty() && !navigationStack.peek().equals(folder)) {
            navigationStack.push(folder);
        } else if (navigationStack.isEmpty()) {
            navigationStack.push(folder);
        }

        updatePathBreadcrumbs(folder);

        File[] files = folder.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory()) {
                    contentPane.getChildren().add(createFolderCard(file, true));
                }
            }

            for (File file : files) {
                if (!file.isDirectory()) {
                    contentPane.getChildren().add(createFileCard(file));
                }
            }

            if (sortComboBox.getValue() != null) {
                sortFiles(sortComboBox.getValue().replace("Sort by ", "").toLowerCase());
            }
        } else {
            Label emptyLabel = new Label("This folder is empty");
            emptyLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 13px;");
            contentPane.getChildren().add(emptyLabel);
        }
    }
    private List<FileItem> collectCurrentItems() {
        List<FileItem> items = new ArrayList<>();
        for (javafx.scene.Node node : contentPane.getChildren()) {
            if (node instanceof StackPane) {
                Object userData = ((StackPane) node).getUserData();
                if (userData instanceof FileItem) {
                    items.add((FileItem) userData);
                } else if (userData instanceof File) {
                    items.add(new FileItem((File) userData));
                } else if (userData instanceof List) {
                    // Handle virtual folder contents
                    ((List<?>) userData).forEach(item -> {
                        if (item instanceof FileItem) {
                            items.add((FileItem) item);
                        }
                    });
                }
            }
        }
        return items;
    }
    private StackPane createFolderCard(File folder, boolean isRegular) {
        StackPane card = new StackPane();
        card.setUserData(folder);
        card.setPrefSize(750, 60);
        card.setMaxWidth(750);

        Rectangle bg = new Rectangle(750, 60);
        bg.setArcHeight(10);
        bg.setArcWidth(10);
        bg.setFill(Color.web(CARD_BG));
        bg.setStroke(Color.web(isRegular ? PRIMARY_COLOR : "#aaa"));
        bg.setStrokeWidth(isRegular ? 1.2 : 0.8);

        InnerShadow innerShadow = new InnerShadow(3, Color.gray(0, 0.05));
        bg.setEffect(innerShadow);

        HBox content = new HBox(15);
        content.setPadding(new Insets(8, 15, 8, 15));
        content.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label(FOLDER_ICON);
        icon.setStyle("-fx-font-size: 20;");

        VBox details = new VBox(3);
        details.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(folder.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        nameLabel.setTextFill(Color.web(isRegular ? FOLDER_NAME_COLOR : "#555"));

        Label sizeLabel = new Label("Calculating...");
        sizeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + PRIMARY_COLOR + ";");

        details.getChildren().addAll(nameLabel, sizeLabel);
        content.getChildren().addAll(icon, details);

        if (isRegular && navigationStack.isEmpty()) {
            Button deleteButton = new Button(DELETE_ICON);
            deleteButton.setStyle("-fx-background-color: " + DELETE_BUTTON_COLOR + "; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 12px; " +
                    "-fx-background-radius: 3; " +
                    "-fx-padding: 3 6;");
            deleteButton.setOnMouseEntered(e -> deleteButton.setEffect(new Glow(0.15)));
            deleteButton.setOnMouseExited(e -> deleteButton.setEffect(null));
            deleteButton.setOnAction(e -> {
                folderManager.removeFolder(folder);
                showRootFolders();
            });

            HBox buttonContainer = new HBox(deleteButton);
            buttonContainer.setAlignment(Pos.CENTER_RIGHT);
            HBox.setHgrow(buttonContainer, Priority.ALWAYS);

            content.getChildren().add(buttonContainer);
        }

        card.getChildren().addAll(bg, content);

        safeFolderSizeCalculation(folder, size -> {
            sizeLabel.setText(formatFileSize(size));
        });

        if (isRegular) {
            card.setOnMouseEntered(e -> {
                bg.setFill(Color.web(CARD_HOVER));
                bg.setEffect(new DropShadow(5, Color.web(PRIMARY_COLOR, 0.2)));
            });

            card.setOnMouseExited(e -> {
                bg.setFill(Color.web(CARD_BG));
                bg.setEffect(innerShadow);
            });

            card.setOnMousePressed(e -> {
                bg.setFill(Color.web("#e0e8ff"));
                bg.setEffect(new InnerShadow(3, Color.web(PRIMARY_COLOR, 0.2)));
            });

            card.setOnMouseReleased(e -> {
                bg.setFill(Color.web(CARD_HOVER));
                bg.setEffect(new DropShadow(5, Color.web(PRIMARY_COLOR, 0.2)));
            });

            final int[] clickCount = {0};
            card.setOnMouseClicked(e -> {
                clickCount[0]++;
                if (clickCount[0] == 2) {
                    clickCount[0] = 0;
                    showFolderContents(folder);
                } else {
                    new java.util.Timer().schedule(
                            new java.util.TimerTask() {
                                @Override
                                public void run() {
                                    clickCount[0] = 0;
                                }
                            },
                            300
                    );
                }
            });
        } else {
            card.setOnMouseClicked(e -> goBack());
        }

        return card;
    }

    private StackPane createFileCard(File file) {
        FileItem fileItem = new FileItem(file);
        StackPane card = new StackPane();
        card.setUserData(fileItem);
        card.setPrefSize(750, 60);
        card.setMaxWidth(750);

        Rectangle bg = new Rectangle(750, 60);
        bg.setArcHeight(10);
        bg.setArcWidth(10);
        bg.setFill(Color.web(CARD_BG));
        bg.setStroke(Color.web("#ddd"));
        bg.setStrokeWidth(0.8);
        bg.setEffect(new InnerShadow(2, Color.gray(0, 0.05)));

        HBox content = new HBox(15);
        content.setPadding(new Insets(8, 15, 8, 15));
        content.setAlignment(Pos.CENTER_LEFT);

        String fileIcon = getFileIcon(file);
        Label icon = new Label(fileIcon);
        icon.setStyle("-fx-font-size: 20;");

        VBox details = new VBox(3);
        details.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(file.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        String fileSize = formatFileSize(file.length());
        String fileType = getFileExtension(file).toUpperCase();

        Label infoLabel = new Label(fileType + "  •  " + fileSize);
        infoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + PRIMARY_COLOR + ";");

        details.getChildren().addAll(nameLabel, infoLabel);
        content.getChildren().addAll(icon, details);
        card.getChildren().addAll(bg, content);

        card.setOnMouseEntered(e -> {
            bg.setFill(Color.web(CARD_HOVER));
            bg.setEffect(new DropShadow(5, Color.gray(0, 0.1)));
        });

        card.setOnMouseExited(e -> {
            bg.setFill(Color.web(CARD_BG));
            bg.setEffect(new InnerShadow(2, Color.gray(0, 0.05)));
        });

        card.setOnMousePressed(e -> {
            bg.setFill(Color.web("#e0e0e0"));
            bg.setEffect(new InnerShadow(3, Color.gray(0, 0.1)));
        });

        card.setOnMouseReleased(e -> {
            bg.setFill(Color.web(CARD_HOVER));
            bg.setEffect(new DropShadow(5, Color.gray(0, 0.1)));
        });

        final int[] clickCount = {0};
        card.setOnMouseClicked(e -> {
            clickCount[0]++;
            if (clickCount[0] == 2) {
                clickCount[0] = 0;
                openFile(file);
            } else {
                new java.util.Timer().schedule(
                        new java.util.TimerTask() {
                            @Override
                            public void run() {
                                clickCount[0] = 0;
                            }
                        },
                        300
                );
            }
        });

        return card;
    }

    private void safeFolderSizeCalculation(File folder, Consumer<Long> onComplete) {
        executor.submit(() -> {
            long size = calculateFolderSize(folder);
            Platform.runLater(() -> {
                if (size >= 0) {
                    onComplete.accept(size);
                } else {
                    onComplete.accept(folder.length() * 2);
                }
            });
        });
    }

    private long calculateFolderSize(File folder) {
        if (folder == null || !folder.exists()) {
            return 0;
        }

        if (folder.length() < 1_000_000_000L) {
            long length = 0;
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        length += file.length();
                    } else {
                        length += calculateFolderSize(file);
                    }
                }
            }
            return length;
        }

        return calculateLargeFolderSize(folder);
    }

    private long calculateLargeFolderSize(File folder) {
        AtomicLong size = new AtomicLong(0);
        Thread calculationThread = new Thread(() -> {
            try {
                size.set(Files.walk(folder.toPath())
                        .parallel()
                        .filter(p -> p.toFile().isFile())
                        .mapToLong(p -> p.toFile().length())
                        .sum());
            } catch (IOException e) {
                size.set(-1);
            }
        });

        calculationThread.start();
        try {
            calculationThread.join(SIZE_CALCULATION_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return size.get() > 0 ? size.get() : folder.length() * 3;
    }

    private String getFileIcon(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif")) {
            return IMAGE_ICON;
        } else if (name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mov")) {
            return VIDEO_ICON;
        } else if (name.endsWith(".mp3") || name.endsWith(".wav")) {
            return AUDIO_ICON;
        } else if (name.endsWith(".pdf")) {
            return PDF_ICON;
        } else if (name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".txt")) {
            return DOC_ICON;
        }
        return DEFAULT_FILE_ICON;
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf('.');
        return lastIndexOf == -1 ? "" : name.substring(lastIndexOf + 1);
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private void openFile(File file) {
        try {
            java.awt.Desktop.getDesktop().open(file);
        } catch (Exception e) {
            showAlert("Error", "Could not open file: " + e.getMessage());
        }
    }

    private void goBack() {
        if (!navigationStack.isEmpty()) {
            navigationStack.pop();
            if (navigationStack.isEmpty()) {
                showRootFolders();
            } else {
                showFolderContents(navigationStack.peek());
            }
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: " + SECONDARY_COLOR + ";");
        dialogPane.setHeaderText(null);
        dialogPane.setGraphic(null);

        alert.showAndWait();
    }

    public void setDefaultSorting() {
        sortComboBox.getSelectionModel().selectFirst();
        sortFiles("name");
    }

    public Scene getScene() {
        return scene;
    }
}