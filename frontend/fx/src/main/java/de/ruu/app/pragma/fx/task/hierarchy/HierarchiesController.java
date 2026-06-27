package de.ruu.app.pragma.fx.task.hierarchy;

import de.ruu.app.pragma.bean.TaskBean;
import de.ruu.app.pragma.bean.TaskGroupBean;
import de.ruu.app.pragma.client.TaskClient;
import de.ruu.app.pragma.client.TaskGroupClient;
import de.ruu.lib.fx.comp.FXCController.DefaultFXCController;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import de.ruu.lib.fx.control.autocomplete.textfield.TextFieldAutoCompleteClearableWithArrowButton;
import de.ruu.lib.fx.control.autocomplete.textfield.TextFieldAutoCompleteClearableWithArrowButtonBuilder;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Dependent
class HierarchiesController extends DefaultFXCController<Hierarchies, HierarchiesService>
        implements HierarchiesService
{
    private static final Logger log = LoggerFactory.getLogger(HierarchiesController.class);

    // ── top bar ──────────────────────────────────────────────────────────────

    @FXML private ComboBox<TaskGroupBean> cbGroups;

    // ── predecessor panel ────────────────────────────────────────────────────

    @FXML private TreeView<TaskBean> tvPredecessors;
    @FXML private Button             btnAddPred;
    @FXML private Button             btnEditPred;
    @FXML private Button             btnDelPred;
    @FXML private TextField          tfIdPred;
    @FXML private TextField          tfNamePred;
    @FXML private DatePicker         dpPlannedStartPred;
    @FXML private DatePicker         dpPlannedEndPred;
    @FXML private TextArea           taDescPred;
    @FXML private CheckBox           cbClosedPred;
    @FXML private Button             btnSaveDatesPred;

    // ── super/sub panel (center — drives the other two) ──────────────────────

    @FXML private TreeView<TaskBean> tvSuperSub;
    @FXML private Button             btnAddTask;
    @FXML private Button             btnEditTask;
    @FXML private Button             btnDelTask;
    @FXML private TextField          tfIdSuperSub;
    @FXML private TextField          tfNameSuperSub;
    @FXML private DatePicker         dpPlannedStartSuperSub;
    @FXML private DatePicker         dpPlannedEndSuperSub;
    @FXML private TextArea           taDescSuperSub;
    @FXML private CheckBox           cbClosedSuperSub;
    @FXML private Button             btnSaveDatesSuperSub;

    // ── successor panel ──────────────────────────────────────────────────────

    @FXML private TreeView<TaskBean> tvSuccessors;
    @FXML private Button             btnAddSucc;
    @FXML private Button             btnEditSucc;
    @FXML private Button             btnDelSucc;
    @FXML private TextField          tfIdSucc;
    @FXML private TextField          tfNameSucc;
    @FXML private DatePicker         dpPlannedStartSucc;
    @FXML private DatePicker         dpPlannedEndSucc;
    @FXML private TextArea           taDescSucc;
    @FXML private CheckBox           cbClosedSucc;
    @FXML private Button             btnSaveDatesSucc;

    // ── injections ───────────────────────────────────────────────────────────

    @Inject private TaskGroupClient taskGroupClient;
    @Inject private TaskClient      taskClient;

    /** All tasks of the current group, keyed by ID; populated by loadGroup() for client-side graph traversal. */
    private Map<Long, TaskBean> taskByIdCache = new HashMap<>();

    /** True when a user-initiated field change has not yet been saved. */
    private boolean dirty       = false;
    /** True while we are programmatically filling form fields — suppresses dirty tracking. */
    private boolean updating    = false;
    /** True while we are programmatically reverting a selection — prevents listener re-entry. */
    private boolean handlingNav = false;

    // ── initialization ───────────────────────────────────────────────────────

    @Override
    @FXML
    protected void initialize()
    {
        cbGroups.setCellFactory(lv -> groupCell());
        cbGroups.setButtonCell(groupCell());
        cbGroups.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    if (handlingNav || sel == null) return;
                    if (!confirmDiscardChanges()) {
                        handlingNav = true;
                        cbGroups.getSelectionModel().select(old);
                        handlingNav = false;
                        return;
                    }
                    dirty = false;
                    loadGroup(sel);
                });

        setupTreeView(tvPredecessors);
        setupTreeView(tvSuperSub);
        setupTreeView(tvSuccessors);

        // center selection drives predecessor and successor panels
        tvSuperSub.getSelectionModel().selectedItemProperty()
                  .addListener((obs, old, sel) -> {
                      if (handlingNav) return;
                      if (!confirmDiscardChanges()) {
                          handlingNav = true;
                          tvSuperSub.getSelectionModel().select(old);
                          handlingNav = false;
                          return;
                      }
                      dirty = false;
                      onCenterTaskSelected(sel);
                  });

        // side panel selections update their detail forms
        tvPredecessors.getSelectionModel().selectedItemProperty()
                      .addListener((obs, old, sel) -> fillDetail(sel, tfIdPred, tfNamePred,
                                                                  dpPlannedStartPred, dpPlannedEndPred,
                                                                  taDescPred, cbClosedPred,
                                                                  btnSaveDatesPred));
        tvSuccessors.getSelectionModel().selectedItemProperty()
                    .addListener((obs, old, sel) -> fillDetail(sel, tfIdSucc, tfNameSucc,
                                                               dpPlannedStartSucc, dpPlannedEndSucc,
                                                               taDescSucc, cbClosedSucc,
                                                               btnSaveDatesSucc));

        btnSaveDatesPred    .setOnAction(e -> saveDates(tvPredecessors));
        btnSaveDatesSuperSub.setOnAction(e -> saveDates(tvSuperSub));
        btnSaveDatesSucc    .setOnAction(e -> saveDates(tvSuccessors));

        // Dirty-state tracking: any user edit in any panel sets the flag
        java.util.stream.Stream.of(
                dpPlannedStartSuperSub.valueProperty(),
                dpPlannedEndSuperSub  .valueProperty(),
                dpPlannedStartPred    .valueProperty(),
                dpPlannedEndPred      .valueProperty(),
                dpPlannedStartSucc    .valueProperty(),
                dpPlannedEndSucc      .valueProperty())
            .forEach(p -> p.addListener((obs, o, n) -> { if (!updating) dirty = true; }));
        java.util.stream.Stream.of(
                taDescSuperSub.textProperty(),
                taDescPred    .textProperty(),
                taDescSucc    .textProperty())
            .forEach(p -> p.addListener((obs, o, n) -> { if (!updating) dirty = true; }));
        java.util.stream.Stream.of(
                cbClosedSuperSub.selectedProperty(),
                cbClosedPred    .selectedProperty(),
                cbClosedSucc    .selectedProperty())
            .forEach(p -> p.addListener((obs, o, n) -> { if (!updating) dirty = true; }));

        disableAll(true);
        loadGroups();
    }

    // ── data loading ─────────────────────────────────────────────────────────

    private void loadGroups()
    {
        try
        {
            List<TaskGroupBean> groups = taskGroupClient.findAll();
            cbGroups.getItems().setAll(groups);
            if (!groups.isEmpty()) cbGroups.getSelectionModel().selectFirst();
        }
        catch (Exception e) { log.error("failed to load groups", e); }
    }

    private void loadGroup(TaskGroupBean group)
    {
        try
        {
            List<TaskBean> tasks = taskClient.findGroupTasksWithRelated(group);
            taskByIdCache = tasks.stream()
                .filter(t -> t.id() != null)
                .collect(java.util.stream.Collectors.toMap(TaskBean::id, t -> t));
            TreeItem<TaskBean> root = buildSuperSubTree(tasks);
            Platform.runLater(() -> {
                handlingNav = true;
                tvSuperSub.setRoot(root);
                handlingNav = false;
                clearSidePanels();
                disableAll(false);
                updateButtonStates();
            });
        }
        catch (Exception e) { log.error("failed to load group {}", group.name(), e); }
    }

    private void onCenterTaskSelected(TreeItem<TaskBean> item)
    {
        clearSidePanels();

        if (item == null || item.getValue() == null)
        {
            fillDetail(null, tfIdSuperSub, tfNameSuperSub,
                       dpPlannedStartSuperSub, dpPlannedEndSuperSub,
                       taDescSuperSub, cbClosedSuperSub, btnSaveDatesSuperSub);
            updateButtonStates();
            return;
        }

        TaskBean task = item.getValue();
        fillDetail(item, tfIdSuperSub, tfNameSuperSub,
                   dpPlannedStartSuperSub, dpPlannedEndSuperSub,
                   taDescSuperSub, cbClosedSuperSub, btnSaveDatesSuperSub);

        if (task.id() == null) { updateButtonStates(); return; }

        try
        {
            // Use the cached task (loaded with relations) to avoid HTTP calls
            TaskBean cached = taskByIdCache.getOrDefault(task.id(), task);

            List<TaskBean> preds = cached.predecessors()
                .<List<TaskBean>>map(java.util.ArrayList::new)
                .orElseGet(() -> taskClient.findPredecessors(task));
            TreeItem<TaskBean> predRoot = new TreeItem<>();
            Set<Long> visited = new HashSet<>();
            visited.add(task.id());
            preds.forEach(p -> predRoot.getChildren().add(buildPredecessorNode(p, visited)));
            tvPredecessors.setRoot(predRoot);

            List<TaskBean> succs = cached.successors()
                .<List<TaskBean>>map(java.util.ArrayList::new)
                .orElseGet(() -> taskClient.findSuccessors(task));
            TreeItem<TaskBean> succRoot = new TreeItem<>();
            Set<Long> visitedSucc = new HashSet<>();
            visitedSucc.add(task.id());
            succs.forEach(s -> succRoot.getChildren().add(buildSuccessorNode(s, visitedSucc)));
            tvSuccessors.setRoot(succRoot);
        }
        catch (Exception e) { log.error("failed to load neighbours for {}", task.name(), e); }

        updateButtonStates();
    }

    /** Recursively builds predecessor tree from the in-memory cache; visited guards against cycles. */
    private TreeItem<TaskBean> buildPredecessorNode(TaskBean task, Set<Long> visited)
    {
        TaskBean resolved = task.id() != null ? taskByIdCache.getOrDefault(task.id(), task) : task;
        TreeItem<TaskBean> item = new TreeItem<>(resolved);
        item.setExpanded(true);
        if (resolved.id() != null && !visited.contains(resolved.id()))
        {
            visited.add(resolved.id());
            try
            {
                resolved.predecessors().ifPresent(preds ->
                    preds.forEach(p -> item.getChildren().add(buildPredecessorNode(p, visited))));
            }
            finally { visited.remove(resolved.id()); }
        }
        return item;
    }

    /** Recursively builds successor tree from the in-memory cache; visited guards against cycles. */
    private TreeItem<TaskBean> buildSuccessorNode(TaskBean task, Set<Long> visited)
    {
        TaskBean resolved = task.id() != null ? taskByIdCache.getOrDefault(task.id(), task) : task;
        TreeItem<TaskBean> item = new TreeItem<>(resolved);
        item.setExpanded(true);
        if (resolved.id() != null && !visited.contains(resolved.id()))
        {
            visited.add(resolved.id());
            try
            {
                resolved.successors().ifPresent(succs ->
                    succs.forEach(s -> item.getChildren().add(buildSuccessorNode(s, visited))));
            }
            finally { visited.remove(resolved.id()); }
        }
        return item;
    }

    // ── center panel buttons ─────────────────────────────────────────────────

    @FXML
    private void onAddTask()
    {
        TaskGroupBean group = cbGroups.getSelectionModel().getSelectedItem();
        if (group == null || group.id() == null) return;

        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Aufgabe hinzufügen");
        dlg.setHeaderText("Neue Aufgabe in Gruppe \"" + group.name() + "\"");
        dlg.setContentText("Name:");

        dlg.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).ifPresent(name ->
        {
            try
            {
                taskClient.create(new TaskBean(group, name));
                reloadCurrentGroup();
            }
            catch (Exception e) { log.error("failed to create task", e); showError("Aufgabe anlegen", e); }
        });
    }

    @FXML
    private void onEditTask()
    {
        TreeItem<TaskBean> item = tvSuperSub.getSelectionModel().getSelectedItem();
        if (item == null) return;
        TaskBean task = item.getValue();
        if (task == null || task.id() == null) return;

        TextInputDialog dlg = new TextInputDialog(task.name());
        dlg.setTitle("Aufgabe umbenennen");
        dlg.setHeaderText(null);
        dlg.setContentText("Name:");

        dlg.showAndWait().map(String::trim).filter(s -> !s.isEmpty() && !s.equals(task.name())).ifPresent(name ->
        {
            try
            {
                task.name(name);
                TaskBean updated = taskClient.update(task);
                item.setValue(updated);
                fillDetail(item, tfIdSuperSub, tfNameSuperSub,
                           dpPlannedStartSuperSub, dpPlannedEndSuperSub,
                           taDescSuperSub, cbClosedSuperSub, btnSaveDatesSuperSub);
            }
            catch (Exception e) { log.error("failed to rename task", e); showError("Aufgabe umbenennen", e); }
        });
    }

    @FXML
    private void onDelTask()
    {
        TreeItem<TaskBean> item = tvSuperSub.getSelectionModel().getSelectedItem();
        if (item == null) return;
        TaskBean task = item.getValue();
        if (task == null || task.id() == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Aufgabe \"" + task.name() + "\" löschen?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Aufgabe löschen");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().filter(bt -> bt == ButtonType.OK).isPresent())
        {
            try
            {
                taskClient.delete(task);
                reloadCurrentGroup();
            }
            catch (Exception e) { log.error("failed to delete task", e); showError("Aufgabe löschen", e); }
        }
    }

    // ── predecessor panel buttons ─────────────────────────────────────────────

    @FXML
    private void onAddPredecessor()
    {
        TaskBean centerTask = centerSelected();
        if (centerTask == null || centerTask.id() == null) return;

        List<TaskBean> current = collectFromTree(tvPredecessors.getRoot());
        Set<Long> excluded = toIds(current);
        excluded.add(centerTask.id());

        pickTask("Vorgänger hinzufügen", excluded).ifPresent(pred ->
        {
            try
            {
                taskClient.addPredecessor(centerTask, pred);
                reloadSidePanels(centerTask);
            }
            catch (Exception e) { log.error("failed to add predecessor", e); showError("Vorgänger hinzufügen", e); }
        });
    }

    @FXML
    private void onEditPredecessor()
    {
        TreeItem<TaskBean> item = tvPredecessors.getSelectionModel().getSelectedItem();
        if (item == null) return;
        editTask(item, tfIdPred, tfNamePred, dpPlannedStartPred, dpPlannedEndPred, taDescPred, cbClosedPred, btnSaveDatesPred);
    }

    @FXML
    private void onDelPredecessor()
    {
        TaskBean centerTask = centerSelected();
        TreeItem<TaskBean> predItem = tvPredecessors.getSelectionModel().getSelectedItem();
        if (centerTask == null || centerTask.id() == null || predItem == null) return;
        TaskBean pred = predItem.getValue();
        if (pred == null || pred.id() == null) return;

        boolean isDirect = tvPredecessors.getRoot() != null
                && tvPredecessors.getRoot().getChildren().contains(predItem);
        if (!isDirect)
        {
            showInfo("Vorgänger entfernen",
                    "Nur direkte Vorgänger der gewählten Aufgabe können hier entfernt werden.");
            return;
        }

        try
        {
            taskClient.removePredecessor(centerTask, pred);
            reloadSidePanels(centerTask);
        }
        catch (Exception e) { log.error("failed to remove predecessor", e); showError("Vorgänger entfernen", e); }
    }

    // ── successor panel buttons ───────────────────────────────────────────────

    @FXML
    private void onAddSuccessor()
    {
        TaskBean centerTask = centerSelected();
        if (centerTask == null || centerTask.id() == null) return;

        List<TaskBean> current = collectFromTree(tvSuccessors.getRoot());
        Set<Long> excluded = toIds(current);
        excluded.add(centerTask.id());

        pickTask("Nachfolger hinzufügen", excluded).ifPresent(succ ->
        {
            try
            {
                taskClient.addPredecessor(succ, centerTask);
                reloadSidePanels(centerTask);
            }
            catch (Exception e) { log.error("failed to add successor", e); showError("Nachfolger hinzufügen", e); }
        });
    }

    @FXML
    private void onEditSuccessor()
    {
        TreeItem<TaskBean> item = tvSuccessors.getSelectionModel().getSelectedItem();
        if (item == null) return;
        editTask(item, tfIdSucc, tfNameSucc, dpPlannedStartSucc, dpPlannedEndSucc, taDescSucc, cbClosedSucc, btnSaveDatesSucc);
    }

    @FXML
    private void onDelSuccessor()
    {
        TaskBean centerTask = centerSelected();
        TreeItem<TaskBean> succItem = tvSuccessors.getSelectionModel().getSelectedItem();
        if (centerTask == null || centerTask.id() == null || succItem == null) return;
        TaskBean succ = succItem.getValue();
        if (succ == null || succ.id() == null) return;

        boolean isDirect = tvSuccessors.getRoot() != null
                && tvSuccessors.getRoot().getChildren().contains(succItem);
        if (!isDirect)
        {
            showInfo("Nachfolger entfernen",
                    "Nur direkte Nachfolger der gewählten Aufgabe können hier entfernt werden.");
            return;
        }

        try
        {
            taskClient.removePredecessor(succ, centerTask);
            reloadSidePanels(centerTask);
        }
        catch (Exception e) { log.error("failed to remove successor", e); showError("Nachfolger entfernen", e); }
    }

    // ── save task data ────────────────────────────────────────────────────────

    private void saveDates(TreeView<TaskBean> tv)
    {
        TreeItem<TaskBean> sel = tv.getSelectionModel().getSelectedItem();
        if (sel == null || sel.getValue() == null || sel.getValue().id() == null) return;

        TaskBean task = sel.getValue();
        DatePicker dpStart;
        DatePicker dpEnd;
        TextArea   taDesc;
        CheckBox   cbClosed;
        if (tv == tvPredecessors)  { dpStart = dpPlannedStartPred;     dpEnd = dpPlannedEndPred;     taDesc = taDescPred;     cbClosed = cbClosedPred;     }
        else if (tv == tvSuperSub) { dpStart = dpPlannedStartSuperSub; dpEnd = dpPlannedEndSuperSub; taDesc = taDescSuperSub; cbClosed = cbClosedSuperSub; }
        else                       { dpStart = dpPlannedStartSucc;     dpEnd = dpPlannedEndSucc;     taDesc = taDescSucc;     cbClosed = cbClosedSucc;     }

        task.plannedStart(dpStart.getValue());
        task.plannedEnd  (dpEnd  .getValue());
        task.description (taDesc.getText().isBlank() ? null : taDesc.getText());
        task.closed      (cbClosed.isSelected());

        try
        {
            TaskBean updated = taskClient.update(task);
            dirty = false;
            sel.setValue(updated);
        }
        catch (Exception e) { log.error("failed to save task data for {}", task.name(), e); showError("Speichern", e); }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void reloadCurrentGroup()
    {
        TaskGroupBean group = cbGroups.getSelectionModel().getSelectedItem();
        if (group != null) loadGroup(group);
    }

    private void reloadSidePanels(TaskBean centerTask)
    {
        TreeItem<TaskBean> centerItem = tvSuperSub.getSelectionModel().getSelectedItem();
        if (centerItem != null && centerItem.getValue() != null
                && centerTask.id() != null
                && centerTask.id().equals(centerItem.getValue().id()))
        {
            onCenterTaskSelected(centerItem);
        }
    }

    private void clearSidePanels()
    {
        tvPredecessors.setRoot(new TreeItem<>());
        tvSuccessors.setRoot(new TreeItem<>());
        clearDetail(tfIdPred, tfNamePred, dpPlannedStartPred, dpPlannedEndPred, taDescPred, cbClosedPred, btnSaveDatesPred);
        clearDetail(tfIdSucc, tfNameSucc, dpPlannedStartSucc, dpPlannedEndSucc, taDescSucc, cbClosedSucc, btnSaveDatesSucc);
    }

    private void fillDetail(TreeItem<TaskBean> item,
                            TextField tfId, TextField tfName,
                            DatePicker dpStart, DatePicker dpEnd,
                            TextArea taDesc, CheckBox cbClosed,
                            Button btnSave)
    {
        updating = true;
        try
        {
            if (item == null || item.getValue() == null)
            {
                clearDetailFields(tfId, tfName, dpStart, dpEnd, taDesc, cbClosed, btnSave);
                return;
            }
            TaskBean t = item.getValue();
            tfId    .setText(t.id()   == null ? "" : t.id().toString());
            tfName  .setText(t.name() == null ? "" : t.name());
            dpStart .setValue(t.plannedStart().orElse(null));
            dpEnd   .setValue(t.plannedEnd()  .orElse(null));
            taDesc  .setText(t.description().orElse(""));
            cbClosed.setSelected(t.closed());
            btnSave .setDisable(t.id() == null);
        }
        finally
        {
            updating = false;
            dirty    = false;
        }
    }

    private void clearDetail(TextField tfId, TextField tfName,
                             DatePicker dpStart, DatePicker dpEnd,
                             TextArea taDesc, CheckBox cbClosed,
                             Button btnSave)
    {
        updating = true;
        try   { clearDetailFields(tfId, tfName, dpStart, dpEnd, taDesc, cbClosed, btnSave); }
        finally { updating = false; }
    }

    private void clearDetailFields(TextField tfId, TextField tfName,
                                   DatePicker dpStart, DatePicker dpEnd,
                                   TextArea taDesc, CheckBox cbClosed,
                                   Button btnSave)
    {
        tfId    .clear();
        tfName  .clear();
        dpStart .setValue(null);
        dpEnd   .setValue(null);
        taDesc  .clear();
        cbClosed.setSelected(false);
        btnSave .setDisable(true);
    }

    private void editTask(TreeItem<TaskBean> item,
                          TextField tfId, TextField tfName,
                          DatePicker dpStart, DatePicker dpEnd,
                          TextArea taDesc, CheckBox cbClosed,
                          Button btnSave)
    {
        TaskBean task = item.getValue();
        if (task == null || task.id() == null) return;

        TextInputDialog dlg = new TextInputDialog(task.name());
        dlg.setTitle("Aufgabe umbenennen");
        dlg.setHeaderText(null);
        dlg.setContentText("Name:");

        dlg.showAndWait().map(String::trim).filter(s -> !s.isEmpty() && !s.equals(task.name())).ifPresent(name ->
        {
            try
            {
                task.name(name);
                TaskBean updated = taskClient.update(task);
                item.setValue(updated);
                fillDetail(item, tfId, tfName, dpStart, dpEnd, taDesc, cbClosed, btnSave);
            }
            catch (Exception e) { log.error("failed to rename task", e); showError("Aufgabe umbenennen", e); }
        });
    }

    private TaskBean centerSelected()
    {
        TreeItem<TaskBean> item = tvSuperSub.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private Optional<TaskBean> pickTask(String title, Set<Long> excludedIds)
    {
        List<TaskBean> choices = taskClient.findAll().stream()
                .filter(t -> t.id() != null && !excludedIds.contains(t.id()))
                .sorted(Comparator.<TaskBean, String>comparing(t -> t.taskGroup().name())
                                  .thenComparing(t -> t.name()))
                .toList();
        if (choices.isEmpty()) return Optional.empty();

        Function<TaskBean, String> label = t -> t.taskGroup().name() + " / " + t.name();

        TextFieldAutoCompleteClearableWithArrowButton<TaskBean> field =
                TextFieldAutoCompleteClearableWithArrowButtonBuilder.<TaskBean>create()
                        .items(choices)
                        .suggestionFilter((t, text) -> label.apply(t).toLowerCase().contains(text.toLowerCase()))
                        .comparator(Comparator.comparing(label))
                        .textProvider(label)
                        .prompt("Aufgabe wählen oder tippen …")
                        .build();
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);

        Dialog<TaskBean> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.setHeaderText(null);
        dlg.getDialogPane().setContent(field);
        dlg.getDialogPane().setPrefWidth(420);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> bt == ButtonType.OK ? field.valueProperty().get() : null);

        return dlg.showAndWait().filter(t -> t != null);
    }

    /** Collects all task values from a tree (all levels). */
    private List<TaskBean> collectFromTree(TreeItem<TaskBean> root)
    {
        if (root == null) return List.of();
        java.util.List<TaskBean> result = new java.util.ArrayList<>();
        collectRecursive(root, result);
        return result;
    }

    private void collectRecursive(TreeItem<TaskBean> item, java.util.List<TaskBean> acc)
    {
        if (item.getValue() != null) acc.add(item.getValue());
        item.getChildren().forEach(child -> collectRecursive(child, acc));
    }

    private Set<Long> toIds(List<TaskBean> tasks)
    {
        Set<Long> ids = new HashSet<>();
        tasks.stream().filter(t -> t.id() != null).map(TaskBean::id).forEach(ids::add);
        return ids;
    }

    /** Builds the super/sub tree from a flat task list using parentTask references. */
    private TreeItem<TaskBean> buildSuperSubTree(List<TaskBean> tasks)
    {
        TreeItem<TaskBean>            root = new TreeItem<>();
        Map<Long, TreeItem<TaskBean>> byId = new HashMap<>();

        for (TaskBean task : tasks)
        {
            if (task.id() == null) continue;
            TreeItem<TaskBean> item = new TreeItem<>(task);
            item.setExpanded(true);
            byId.put(task.id(), item);
        }
        for (TaskBean task : tasks)
        {
            if (task.id() == null) continue;
            TreeItem<TaskBean> item     = byId.get(task.id());
            Long               parentId = task.parentTask().map(TaskBean::id).orElse(null);
            if (parentId != null && byId.containsKey(parentId))
                byId.get(parentId).getChildren().add(item);
            else
                root.getChildren().add(item);
        }
        sortTreeItems(root);
        return root;
    }

    private void sortTreeItems(TreeItem<TaskBean> parent)
    {
        parent.getChildren().sort(java.util.Comparator.comparing(
                item -> item.getValue() == null ? "" : item.getValue().name().toLowerCase()));
        parent.getChildren().forEach(this::sortTreeItems);
    }

    private void setupTreeView(TreeView<TaskBean> tv)
    {
        tv.setCellFactory(v -> new TreeCell<>()
        {
            @Override protected void updateItem(TaskBean item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        tv.setShowRoot(false);
        tv.setRoot(new TreeItem<>());
    }

    private void disableAll(boolean disabled)
    {
        btnAddTask.setDisable(disabled);
        btnEditTask.setDisable(disabled);
        btnDelTask.setDisable(disabled);
        btnAddPred.setDisable(disabled);
        btnEditPred.setDisable(disabled);
        btnDelPred.setDisable(disabled);
        btnAddSucc.setDisable(disabled);
        btnEditSucc.setDisable(disabled);
        btnDelSucc.setDisable(disabled);
        btnSaveDatesPred    .setDisable(disabled);
        btnSaveDatesSuperSub.setDisable(disabled);
        btnSaveDatesSucc    .setDisable(disabled);
    }

    private void updateButtonStates()
    {
        boolean noCenterTask   = centerSelected() == null;
        boolean noPredSelected = tvPredecessors.getSelectionModel().getSelectedItem() == null;
        boolean noSuccSelected = tvSuccessors.getSelectionModel().getSelectedItem() == null;
        boolean noCenterSel    = tvSuperSub.getSelectionModel().getSelectedItem() == null;

        btnAddTask .setDisable(false);
        btnEditTask.setDisable(noCenterSel);
        btnDelTask .setDisable(noCenterSel);

        btnAddPred .setDisable(noCenterTask);
        btnEditPred.setDisable(noPredSelected);
        btnDelPred .setDisable(noCenterTask || noPredSelected);

        btnAddSucc .setDisable(noCenterTask);
        btnEditSucc.setDisable(noSuccSelected);
        btnDelSucc .setDisable(noCenterTask || noSuccSelected);

        btnSaveDatesSuperSub.setDisable(noCenterSel);
        btnSaveDatesPred    .setDisable(noPredSelected);
        btnSaveDatesSucc    .setDisable(noSuccSelected);
    }

    private ListCell<TaskGroupBean> groupCell()
    {
        return new ListCell<>()
        {
            @Override protected void updateItem(TaskGroupBean item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        };
    }

    private boolean confirmDiscardChanges()
    {
        if (!dirty) return true;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Es gibt ungespeicherte Änderungen. Wirklich verwerfen?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Ungespeicherte Änderungen");
        confirm.setHeaderText(null);
        return confirm.showAndWait().filter(bt -> bt == ButtonType.OK).isPresent();
    }

    private void showError(String title, Exception e)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
