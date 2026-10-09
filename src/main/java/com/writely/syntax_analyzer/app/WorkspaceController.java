package com.writely.syntax_analyzer.app;

import com.writely.syntax_analyzer.core.analysis.AnalysisOrchestrator;
import com.writely.syntax_analyzer.core.ingestion.DefaultSourceIngestionService;
import com.writely.syntax_analyzer.core.ingestion.IngestionException;
import com.writely.syntax_analyzer.core.ingestion.SourceIngestionService;
import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.AnalysisStatus;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Controller for the main writely workspace UI.
 * Coordinates code editing, file ingestion, tree exploration, and asynchronous syntax analysis.
 */
public class WorkspaceController implements Initializable {

    private final Map<String, Path> ingestedFilePaths = new HashMap<>();

    private static final String DEFAULT_PYTHON_SAMPLE =
        "import os\n" +
        "import requests\n" +
        "\n" +
        "def fetch_data(url):\n" +
        "    response = requests.get(url)\n" +
        "    if response.status_code == 200:\n" +
        "        return response.json()\n" +
        "    else:\n" +
        "        raise Exception(\"Failed request\")\n" +
        "\n" +
        "if __name__ == \"__main__\":\n" +
        "    data = fetch_data(\"https://api.example.com/data\")\n" +
        "    print(data)\n";

    private static final String DEFAULT_JAVA_SAMPLE =
        "public class Main {\n" +
        "    public static void main(String[] args) {\n" +
        "        System.out.println(\"Hello, writely!\");\n" +
        "    }\n" +
        "}\n";

    private static final String DEFAULT_CPP_SAMPLE =
        "#include <iostream>\n" +
        "\n" +
        "int main() {\n" +
        "    std::cout << \"Hello, writely!\" << std::endl;\n" +
        "    return 0;\n" +
        "}\n";

    // FXML View Nodes
    @FXML private BorderPane rootPane;
    @FXML private Button deepScanButton;
    @FXML private Button ingestFileButton;
    @FXML private VBox explorerPane;
    @FXML private TextField searchField;
    @FXML private TreeView<String> fileTreeView;
    @FXML private Button newFolderButton;
    @FXML private TabPane editorTabPane;
    @FXML private Tab activeTab;
    @FXML private TextArea lineGutterArea;
    @FXML private TextArea editorTextArea;
    @FXML private Label totalIssuesBadge;
    @FXML private Label statusBadgeLabel;
    @FXML private Label errorCountLabel;
    @FXML private Label warningCountLabel;
    @FXML private Label validLinesLabel;
    @FXML private Label flaggedLinesLabel;
    @FXML private Label diagnosticsCountLabel;
    @FXML private ScrollPane diagnosticsScrollPane;
    @FXML private VBox diagnosticsListContainer;
    @FXML private Label footerStatusLabel;
    @FXML private Label footerLanguageLabel;
    @FXML private Label footerLinesLabel;
    @FXML private Label footerTokensLabel;

    // Services
    private SourceIngestionService ingestionService;
    private AnalysisOrchestrator orchestrator;

    // State Properties
    private final ObjectProperty<Language> currentLanguage = new SimpleObjectProperty<>(Language.PYTHON);
    private final StringProperty currentFileName = new SimpleStringProperty("filename.py");
    private final ObjectProperty<Path> currentFilePath = new SimpleObjectProperty<>(null);
    private final ObjectProperty<AnalysisResult> latestResult = new SimpleObjectProperty<>(null);
    private final BooleanProperty analyzing = new SimpleBooleanProperty(false);

    private TreeItem<String> rootTreeItem;

    /**
     * Default zero-arg constructor required by JavaFX FXMLLoader.
     */
    public WorkspaceController() {
        this(new DefaultSourceIngestionService(), new AnalysisOrchestrator());
    }

    /**
     * Dependency injection constructor for testing and modular composition.
     */
    public WorkspaceController(SourceIngestionService ingestionService, AnalysisOrchestrator orchestrator) {
        this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService must not be null");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupStateBindings();
        setupLineGutter();
        setupFileExplorer();
        setupEditor();
        updateInitialStatus();
    }

    private void setupStateBindings() {
        if (deepScanButton != null) {
            deepScanButton.disableProperty().bind(analyzing);
        }
    }

    private void setupLineGutter() {
        if (editorTextArea != null && lineGutterArea != null) {
            lineGutterArea.setEditable(false);
            lineGutterArea.setFocusTraversable(false);
            lineGutterArea.setWrapText(false);

            editorTextArea.textProperty().addListener((obs, oldVal, newVal) -> updateLineGutter(newVal));
            editorTextArea.scrollTopProperty().addListener((obs, oldVal, newVal) -> {
                lineGutterArea.setScrollTop(newVal.doubleValue());
            });
        }
    }

    private void setupEditor() {
        if (editorTextArea != null && (editorTextArea.getText() == null || editorTextArea.getText().isBlank())) {
            editorTextArea.setText(DEFAULT_PYTHON_SAMPLE);
            updateLineGutter(DEFAULT_PYTHON_SAMPLE);
        }

        if (activeTab != null) {
            activeTab.setOnClosed(e -> handleActiveTabClosed());
        }

        currentFileName.addListener((obs, oldVal, newVal) -> {
            if (activeTab != null && newVal != null) {
                activeTab.setText(newVal);
            }
        });

        currentLanguage.addListener((obs, oldVal, newVal) -> {
            if (footerLanguageLabel != null && newVal != null) {
                footerLanguageLabel.setText(newVal.displayName());
            }
        });
    }

    private void setupFileExplorer() {
        if (fileTreeView == null) {
            return;
        }

        rootTreeItem = new TreeItem<>("Workspace");
        rootTreeItem.setExpanded(true);

        TreeItem<String> projectAlpha = new TreeItem<>("📁 Project Alpha");
        projectAlpha.setExpanded(true);
        projectAlpha.getChildren().add(new TreeItem<>("filename.py", createLanguageIcon(Language.PYTHON)));
        projectAlpha.getChildren().add(new TreeItem<>("Main.java", createLanguageIcon(Language.JAVA)));
        projectAlpha.getChildren().add(new TreeItem<>("app.cpp", createLanguageIcon(Language.CPP)));

        rootTreeItem.getChildren().add(projectAlpha);
        fileTreeView.setRoot(rootTreeItem);
        fileTreeView.setShowRoot(false);

        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isLeaf()) {
                handleTreeFileSelected(newVal.getValue());
            }
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterTree(newVal));
        }
    }

    private Node createLanguageIcon(Language language) {
        Label badge = new Label();
        badge.getStyleClass().add("tree-lang-badge");
        if (language == Language.PYTHON) {
            badge.setText("PY");
            badge.getStyleClass().add("tree-lang-py");
        } else if (language == Language.JAVA) {
            badge.setText("JA");
            badge.getStyleClass().add("tree-lang-java");
        } else if (language == Language.CPP) {
            badge.setText("C+");
            badge.getStyleClass().add("tree-lang-cpp");
        } else {
            badge.setText("TX");
            badge.getStyleClass().add("tree-lang-default");
        }
        return badge;
    }

    private Language inferLanguageFromFileName(String fileName) {
        if (fileName == null) {
            return Language.PYTHON;
        }
        if (fileName.endsWith(".java")) {
            return Language.JAVA;
        }
        if (fileName.endsWith(".cpp") || fileName.endsWith(".cxx") || fileName.endsWith(".cc")
                || fileName.endsWith(".h") || fileName.endsWith(".hpp")) {
            return Language.CPP;
        }
        return Language.PYTHON;
    }

    private void filterTree(String query) {
        if (rootTreeItem == null || fileTreeView == null) {
            return;
        }
        if (query == null || query.isBlank()) {
            setupFileExplorer();
            return;
        }
        String lowerQuery = query.toLowerCase().trim();
        TreeItem<String> filteredRoot = new TreeItem<>("Filtered");
        filteredRoot.setExpanded(true);

        for (TreeItem<String> folder : rootTreeItem.getChildren()) {
            TreeItem<String> matchingFolder = new TreeItem<>(folder.getValue());
            matchingFolder.setExpanded(true);
            boolean matchedAny = false;

            for (TreeItem<String> child : folder.getChildren()) {
                if (child.getValue().toLowerCase().contains(lowerQuery)) {
                    Language lang = inferLanguageFromFileName(child.getValue());
                    matchingFolder.getChildren().add(new TreeItem<>(child.getValue(), createLanguageIcon(lang)));
                    matchedAny = true;
                }
            }
            if (folder.getValue().toLowerCase().contains(lowerQuery) || matchedAny) {
                filteredRoot.getChildren().add(matchingFolder);
            }
        }
        fileTreeView.setRoot(filteredRoot);
    }

    private void handleTreeFileSelected(String fileName) {
        if (fileName == null) {
            return;
        }
        Path path = ingestedFilePaths.get(fileName);
        if (path != null && Files.exists(path)) {
            ingestFile(path);
            return;
        }
        if (fileName.contains("filename.py")) {
            loadSourceCode(DEFAULT_PYTHON_SAMPLE, "filename.py", Language.PYTHON);
        } else if (fileName.contains("Main.java")) {
            loadSourceCode(DEFAULT_JAVA_SAMPLE, "Main.java", Language.JAVA);
        } else if (fileName.contains("app.cpp")) {
            loadSourceCode(DEFAULT_CPP_SAMPLE, "app.cpp", Language.CPP);
        }
    }

    private void handleActiveTabClosed() {
        if (editorTabPane != null && editorTabPane.getTabs().isEmpty()) {
            Tab newTab = new Tab("untitled.py");
            newTab.setClosable(true);
            HBox box = new HBox();
            box.getStyleClass().add("editor-content-box");
            if (lineGutterArea != null && editorTextArea != null) {
                box.getChildren().addAll(lineGutterArea, editorTextArea);
            }
            newTab.setContent(box);
            editorTabPane.getTabs().add(newTab);
            activeTab = newTab;
            currentFileName.set("untitled.py");
            currentLanguage.set(Language.PYTHON);
            if (editorTextArea != null) {
                editorTextArea.clear();
            }
        }
    }

    private void updateInitialStatus() {
        if (statusBadgeLabel != null) {
            statusBadgeLabel.setText("IDLE");
            statusBadgeLabel.getStyleClass().setAll("status-pill", "status-idle");
        }
        if (footerStatusLabel != null) {
            footerStatusLabel.setText("Ready");
        }
        if (footerLanguageLabel != null) {
            footerLanguageLabel.setText(currentLanguage.get().displayName());
        }
        if (totalIssuesBadge != null) {
            totalIssuesBadge.setText("0");
        }
        if (diagnosticsCountLabel != null) {
            diagnosticsCountLabel.setText("0 items");
        }
    }

    public void updateLineGutter(String text) {
        int lines = 1;
        if (text != null && !text.isEmpty()) {
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    lines++;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            sb.append(i).append("\n");
        }
        if (lineGutterArea != null) {
            lineGutterArea.setText(sb.toString());
        }
        if (footerLinesLabel != null) {
            footerLinesLabel.setText(lines + " lines");
        }
    }

    /**
     * Loads source code text, filename, and language into the active editor session.
     */
    public void loadSourceCode(String code, String filename, Language language) {
        currentFileName.set(filename);
        currentLanguage.set(language);
        currentFilePath.set(null);
        if (editorTextArea != null) {
            editorTextArea.setText(code);
        }
        if (activeTab != null) {
            activeTab.setText(filename);
        }
        if (footerLanguageLabel != null) {
            footerLanguageLabel.setText(language.displayName());
        }
        if (footerStatusLabel != null) {
            footerStatusLabel.setText("Loaded " + filename);
        }
        updateLineGutter(code);
    }

    // =========================================================================
    // Action Handlers
    // =========================================================================

    @FXML
    public void handleDeepScan(ActionEvent event) {
        runAnalysis();
    }

    @FXML
    public void handleIngestFile(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Ingest Source File");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Source Files (*.java, *.py, *.cpp, *.h)",
                "*.java", "*.py", "*.cpp", "*.cxx", "*.cc", "*.h", "*.hpp"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        Window window = ingestFileButton != null && ingestFileButton.getScene() != null
            ? ingestFileButton.getScene().getWindow() : null;
        File selected = chooser.showOpenDialog(window);
        if (selected != null) {
            ingestFile(selected.toPath());
        }
    }

    @FXML
    public void handleNewFolder(ActionEvent event) {
        if (rootTreeItem != null) {
            int folderCount = rootTreeItem.getChildren().size() + 1;
            TreeItem<String> newFolder = new TreeItem<>("📁 Folder " + folderCount);
            newFolder.setExpanded(true);
            rootTreeItem.getChildren().add(newFolder);
            if (fileTreeView != null) {
                fileTreeView.getSelectionModel().select(newFolder);
            }
            if (footerStatusLabel != null) {
                footerStatusLabel.setText("Created Folder " + folderCount);
            }
        }
    }

    /**
     * Ingests a local file via {@link SourceIngestionService} and updates the editor and analysis state.
     */
    public void ingestFile(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            SourcePayload payload = ingestionService.ingestFile(path);
            currentFilePath.set(path);
            currentFileName.set(path.getFileName().toString());
            currentLanguage.set(payload.language());

            if (editorTextArea != null) {
                editorTextArea.setText(payload.sourceText());
            }
            if (activeTab != null) {
                activeTab.setText(path.getFileName().toString());
            }
            if (footerLanguageLabel != null) {
                footerLanguageLabel.setText(payload.language().displayName());
            }
            if (footerStatusLabel != null) {
                footerStatusLabel.setText("Ingested file: " + path.getFileName());
            }
            updateLineGutter(payload.sourceText());

            addIngestedFileToTree(path, payload.language());

            runAnalysis();
        } catch (IngestionException e) {
            if (footerStatusLabel != null) {
                footerStatusLabel.setText("Ingestion error: " + e.getMessage());
            }
        }
    }

    private void addIngestedFileToTree(Path path, Language language) {
        if (fileTreeView == null || rootTreeItem == null) {
            return;
        }
        String fileName = path.getFileName().toString();
        ingestedFilePaths.put(fileName, path);

        TreeItem<String> targetFolder = null;
        TreeItem<String> selected = fileTreeView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (selected.getParent() == rootTreeItem) {
                targetFolder = selected;
            } else if (selected.getParent() != null && selected.getParent() != rootTreeItem) {
                targetFolder = selected.getParent();
            }
        }
        if (targetFolder == null) {
            if (!rootTreeItem.getChildren().isEmpty()) {
                targetFolder = rootTreeItem.getChildren().get(0);
            } else {
                targetFolder = new TreeItem<>("📁 Workspace Files");
                targetFolder.setExpanded(true);
                rootTreeItem.getChildren().add(targetFolder);
            }
        }
        targetFolder.setExpanded(true);

        for (TreeItem<String> child : targetFolder.getChildren()) {
            if (fileName.equals(child.getValue())) {
                fileTreeView.getSelectionModel().select(child);
                return;
            }
        }

        TreeItem<String> fileItem = new TreeItem<>(fileName, createLanguageIcon(language));
        targetFolder.getChildren().add(fileItem);
        fileTreeView.getSelectionModel().select(fileItem);
    }

    /**
     * Executes syntax analysis asynchronously in a background task via {@link AnalysisOrchestrator}.
     * Returns the executing task for testing and lifecycle tracking.
     */
    public Task<AnalysisResult> runAnalysis() {
        String code = editorTextArea != null ? editorTextArea.getText() : "";
        if (code == null) {
            code = "";
        }
        Language lang = currentLanguage.get();
        if (lang == null) {
            lang = Language.fromFileName(currentFileName.get()).orElse(Language.PYTHON);
        }
        String name = currentFileName.get() != null ? currentFileName.get() : "untitled";
        Path path = currentFilePath.get();

        SourcePayload payload;
        if (path != null) {
            payload = SourcePayload.file(code, name, lang);
        } else {
            payload = SourcePayload.snippet(code, lang);
        }

        analyzing.set(true);
        if (statusBadgeLabel != null) {
            statusBadgeLabel.setText("ANALYZING");
            statusBadgeLabel.getStyleClass().setAll("status-pill", "status-analyzing");
        }
        if (footerStatusLabel != null) {
            footerStatusLabel.setText("Analyzing syntax in background...");
        }

        Task<AnalysisResult> task = createAnalysisTask(payload);

        task.setOnSucceeded(e -> {
            AnalysisResult result = task.getValue();
            applyAnalysisResult(result);
            analyzing.set(false);
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            handleAnalysisFailure(ex);
            analyzing.set(false);
        });

        Thread worker = new Thread(task, "Syntax-Analysis-Worker");
        worker.setDaemon(true);
        worker.start();

        return task;
    }

    /**
     * Creates the background task for running analysis. Package-private/protected for testing.
     */
    public Task<AnalysisResult> createAnalysisTask(SourcePayload payload) {
        return new Task<>() {
            @Override
            protected AnalysisResult call() {
                return orchestrator.analyze(payload);
            }
        };
    }

    /**
     * Applies analysis results to the JavaFX UI components.
     * Guaranteed to run on JavaFX Application Thread.
     */
    public void applyAnalysisResult(AnalysisResult result) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> applyAnalysisResult(result));
            return;
        }

        latestResult.set(result);

        int totalIssues = result.diagnostics().size();
        if (totalIssuesBadge != null) {
            totalIssuesBadge.setText(String.valueOf(totalIssues));
        }

        if (statusBadgeLabel != null) {
            if (result.isPassed()) {
                statusBadgeLabel.setText(AnalysisStatus.PASSED.name());
                statusBadgeLabel.getStyleClass().setAll("status-pill", "status-passed");
            } else {
                statusBadgeLabel.setText(AnalysisStatus.FAILED_SYNTAX_ERRORS.name());
                statusBadgeLabel.getStyleClass().setAll("status-pill", "status-failed");
            }
        }

        if (errorCountLabel != null) {
            errorCountLabel.setText(String.valueOf(result.errorCount()));
        }
        if (warningCountLabel != null) {
            warningCountLabel.setText(String.valueOf(result.warningCount()));
        }
        if (validLinesLabel != null) {
            validLinesLabel.setText(String.valueOf(result.summary().validLines()));
        }
        if (flaggedLinesLabel != null) {
            flaggedLinesLabel.setText(String.valueOf(result.summary().flaggedLines()));
        }
        if (footerLinesLabel != null) {
            footerLinesLabel.setText(result.summary().totalLines() + " lines");
        }
        if (footerTokensLabel != null) {
            footerTokensLabel.setText(result.summary().totalTokens() + " tokens");
        }
        if (footerStatusLabel != null) {
            footerStatusLabel.setText(result.isPassed()
                ? "Scan complete - No syntax errors"
                : "Scan complete - " + totalIssues + " issue(s) detected");
        }

        renderDiagnostics(result);
    }

    private void renderDiagnostics(AnalysisResult result) {
        if (diagnosticsListContainer == null) {
            return;
        }
        diagnosticsListContainer.getChildren().clear();

        int count = result.diagnostics().size();
        if (diagnosticsCountLabel != null) {
            diagnosticsCountLabel.setText(count + (count == 1 ? " item" : " items"));
        }

        if (result.diagnostics().isEmpty()) {
            Label cleanLabel = new Label("✓ No syntax errors found. Source is clean.");
            cleanLabel.getStyleClass().add("diag-description");
            diagnosticsListContainer.getChildren().add(cleanLabel);
            return;
        }

        for (Diagnostic diag : result.diagnostics()) {
            HBox itemBox = new HBox(12);
            itemBox.getStyleClass().add("diagnostic-item");

            int lineNum = diag.location().line();
            Label lineBadge = new Label(lineNum > 0 ? String.valueOf(lineNum) : "--");
            lineBadge.getStyleClass().add("line-number-badge");

            VBox detailsBox = new VBox(3);
            Label titleLabel = new Label(diag.message());
            if (diag.severity() == Severity.ERROR) {
                titleLabel.getStyleClass().add("diag-error-title");
            } else if (diag.severity() == Severity.WARNING) {
                titleLabel.getStyleClass().add("diag-warning-title");
            } else {
                titleLabel.getStyleClass().add("diag-info-title");
            }

            StringBuilder descText = new StringBuilder();
            descText.append(diag.category().displayName());
            if (!diag.code().isBlank()) {
                descText.append(" [").append(diag.code()).append("]");
            }
            if (diag.suggestedFix().isPresent()) {
                descText.append(" • Fix: ").append(diag.suggestedFix().get());
            }

            Label descLabel = new Label(descText.toString());
            descLabel.getStyleClass().add("diag-description");
            descLabel.setWrapText(true);

            detailsBox.getChildren().addAll(titleLabel, descLabel);
            itemBox.getChildren().addAll(lineBadge, detailsBox);

            itemBox.setOnMouseClicked(ev -> jumpToLocation(diag.location()));

            diagnosticsListContainer.getChildren().add(itemBox);
        }
    }

    public void jumpToLocation(SourceLocation location) {
        if (location == null || location.line() <= 0 || editorTextArea == null) {
            return;
        }
        String text = editorTextArea.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        int targetLine = location.line();
        int currentLine = 1;
        int targetOffset = 0;
        for (int i = 0; i < text.length(); i++) {
            if (currentLine == targetLine) {
                targetOffset = i + Math.max(0, location.column() - 1);
                break;
            }
            if (text.charAt(i) == '\n') {
                currentLine++;
            }
        }
        if (targetOffset >= text.length()) {
            targetOffset = Math.max(0, text.length() - 1);
        }
        editorTextArea.positionCaret(targetOffset);
        editorTextArea.requestFocus();
    }

    private void handleAnalysisFailure(Throwable throwable) {
        if (footerStatusLabel != null) {
            footerStatusLabel.setText("Analysis failed: " + (throwable != null ? throwable.getMessage() : "Unknown error"));
        }
        if (statusBadgeLabel != null) {
            statusBadgeLabel.setText(AnalysisStatus.FAILED_SYNTAX_ERRORS.name());
            statusBadgeLabel.getStyleClass().setAll("status-pill", "status-failed");
        }
    }

    // =========================================================================
    // Getters and Setters for programmatic / test access
    // =========================================================================

    public SourceIngestionService getIngestionService() {
        return ingestionService;
    }

    public void setIngestionService(SourceIngestionService ingestionService) {
        this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService must not be null");
    }

    public AnalysisOrchestrator getOrchestrator() {
        return orchestrator;
    }

    public void setOrchestrator(AnalysisOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
    }

    public String getEditorText() {
        return editorTextArea != null ? editorTextArea.getText() : "";
    }

    public void setEditorText(String text) {
        if (editorTextArea != null) {
            editorTextArea.setText(text);
        }
        updateLineGutter(text);
    }

    public Language getCurrentLanguage() {
        return currentLanguage.get();
    }

    public void setCurrentLanguage(Language language) {
        this.currentLanguage.set(language);
    }

    public String getCurrentFileName() {
        return currentFileName.get();
    }

    public void setCurrentFileName(String fileName) {
        this.currentFileName.set(fileName);
    }

    public AnalysisResult getLatestResult() {
        return latestResult.get();
    }

    public boolean isAnalyzing() {
        return analyzing.get();
    }

    public String getTotalIssuesText() {
        return totalIssuesBadge != null ? totalIssuesBadge.getText() : "0";
    }

    public String getStatusBadgeText() {
        return statusBadgeLabel != null ? statusBadgeLabel.getText() : "";
    }

    public int getDiagnosticsCardCount() {
        return diagnosticsListContainer != null ? diagnosticsListContainer.getChildren().size() : 0;
    }

    public TextArea getEditorTextArea() {
        return editorTextArea;
    }

    public TextArea getLineGutterArea() {
        return lineGutterArea;
    }

    public VBox getDiagnosticsListContainer() {
        return diagnosticsListContainer;
    }

    public TextField getSearchField() {
        return searchField;
    }

    public TreeView<String> getFileTreeView() {
        return fileTreeView;
    }

    public Tab getActiveTab() {
        return activeTab;
    }

    public TabPane getEditorTabPane() {
        return editorTabPane;
    }

    public Label getFooterStatusLabel() {
        return footerStatusLabel;
    }

    public Label getFooterLanguageLabel() {
        return footerLanguageLabel;
    }

    public Button getDeepScanButton() {
        return deepScanButton;
    }
}
