package de.ruu.app.pragma.fx.task.hierarchy;

import de.ruu.app.pragma.client.TaskClient;
import de.ruu.app.pragma.client.TaskGroupClient;
import de.ruu.app.pragma.dto.TaskDto;
import de.ruu.app.pragma.dto.TaskGroupDto;
import de.ruu.lib.fx.comp.FXCController.DefaultFXCController;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Dependent
class HierarchiesController extends DefaultFXCController<Hierarchies, HierarchiesService>
        implements HierarchiesService
{
    private static final Logger log = LoggerFactory.getLogger(HierarchiesController.class);

    // ── top bar ──────────────────────────────────────────────────────────────

    @FXML private ComboBox<TaskGroupDto> cbGroups;

    // ── predecessor panel ────────────────────────────────────────────────────

    @FXML private TreeView<TaskDto> tvPredecessors;
    @FXML private Button            btnAddPred;
    @FXML private Button            btnEditPred;
    @FXML private Button            btnDelPred;
    @FXML private TextField         tfIdPred;
    @FXML private TextField         tfNamePred;

    // ── super/sub panel (center — drives the other two) ──────────────────────

    @FXML private TreeView<TaskDto> tvSuperSub;
    @FXML private Button            btnAddTask;
    @FXML private Button            btnEditTask;
    @FXML private Button            btnDelTask;
    @FXML private TextField         tfIdSuperSub;
    @FXML private TextField         tfNameSuperSub;

    // ── successor panel ──────────────────────────────────────────────────────

    @FXML private TreeView<TaskDto> tvSuccessors;
    @FXML private Button            btnAddSucc;
    @FXML private Button            btnEditSucc;
    @FXML private Button            btnDelSucc;
    @FXML private TextField         tfIdSucc;
    @FXML private TextField         tfNameSucc;

    // ── injections ───────────────────────────────────────────────────────────

    @Inject private TaskGroupClient taskGroupClient;
    @Inject private TaskClient      taskClient;

    /** All tasks across all groups — for "pick task" dialogs. */
    private List<TaskDto> allTasks = List.of();

    // ── initialization ───────────────────────────────────────────────────────

    @Override
    @FXML
    protected void initialize()
    {
        cbGroups.setCellFactory(lv -> groupCell());
        cbGroups.setButtonCell(groupCell());
        cbGroups.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> { if (sel != null) loadGroup(sel); });

        setupTreeView(tvPredecessors);
        setupTreeView(tvSuperSub);
        setupTreeView(tvSuccessors);

        // center selection drives predecessor and successor panels
        tvSuperSub.getSelectionModel().selectedItemProperty()
                  .addListener((obs, old, sel) -> onCenterTaskSelected(sel));

        // side panel selections update their detail forms
        tvPredecessors.getSelectionModel().selectedItemProperty()
                      .addListener((obs, old, sel) -> fillDetail(sel, tfIdPred, tfNamePred));
        tvSuccessors.getSelectionModel().selectedItemProperty()
                    .addListener((obs, old, sel) -> fillDetail(sel, tfIdSucc, tfNameSucc));

        disableAll(true);
        loadGroups();
    }

    // ── data loading ─────────────────────────────────────────────────────────

    private void loadGroups()
    {
        try
        {
            List<TaskGroupDto> groups = taskGroupClient.findAll();
            cbGroups.getItems().setAll(groups);
            if (!groups.isEmpty()) cbGroups.getSelectionModel().selectFirst();
        }
        catch (Exception e) { log.error("failed to load groups", e); }
    }

    private void loadGroup(TaskGroupDto group)
    {
        try
        {
            List<TaskDto> tasks = taskClient.findAll(group.id());
            allTasks = taskClient.findAll(null);
            TreeItem<TaskDto> root = buildSuperSubTree(tasks);
            Platform.runLater(() -> {
                tvSuperSub.setRoot(root);
                clearSidePanels();
                disableAll(false);
                updateButtonStates();
            });
        }
        catch (Exception e) { log.error("failed to load group {}", group.name(), e); }
    }

    private void onCenterTaskSelected(TreeItem<TaskDto> item)
    {
        clearSidePanels();

        if (item == null || item.getValue() == null)
        {
            fillDetail(null, tfIdSuperSub, tfNameSuperSub);
            updateButtonStates();
            return;
        }

        TaskDto task = item.getValue();
        fillDetail(item, tfIdSuperSub, tfNameSuperSub);

        if (task.id() == null) { updateButtonStates(); return; }

        try
        {
            // Predecessors: load recursively, display RTL (visually: pred ← task)
            List<TaskDto> preds = taskClient.findPredecessors(task.id());
            TreeItem<TaskDto> predRoot = new TreeItem<>();
            Set<Long> visited = new HashSet<>();
            visited.add(task.id());
            preds.forEach(p -> predRoot.getChildren().add(buildPredecessorNode(p, visited)));
            tvPredecessors.setRoot(predRoot);

            // Successors: load recursively
            List<TaskDto> succs = taskClient.findSuccessors(task.id());
            TreeItem<TaskDto> succRoot = new TreeItem<>();
            Set<Long> visitedSucc = new HashSet<>();
            visitedSucc.add(task.id());
            succs.forEach(s -> succRoot.getChildren().add(buildSuccessorNode(s, visitedSucc)));
            tvSuccessors.setRoot(succRoot);
        }
        catch (Exception e) { log.error("failed to load neighbours for {}", task.name(), e); }

        updateButtonStates();
    }

    /** Recursively builds predecessor tree; visited guards against cycles. */
    private TreeItem<TaskDto> buildPredecessorNode(TaskDto task, Set<Long> visited)
    {
        TreeItem<TaskDto> item = new TreeItem<>(task);
        item.setExpanded(true);
        if (task.id() != null && !visited.contains(task.id()))
        {
            visited.add(task.id());
            try
            {
                taskClient.findPredecessors(task.id())
                          .forEach(p -> item.getChildren().add(buildPredecessorNode(p, visited)));
            }
            catch (Exception e) { log.warn("failed to load predecessors for {}", task.name(), e); }
            finally { visited.remove(task.id()); }
        }
        return item;
    }

    /** Recursively builds successor tree; visited guards against cycles. */
    private TreeItem<TaskDto> buildSuccessorNode(TaskDto task, Set<Long> visited)
    {
        TreeItem<TaskDto> item = new TreeItem<>(task);
        item.setExpanded(true);
        if (task.id() != null && !visited.contains(task.id()))
        {
            visited.add(task.id());
            try
            {
                taskClient.findSuccessors(task.id())
                          .forEach(s -> item.getChildren().add(buildSuccessorNode(s, visited)));
            }
            catch (Exception e) { log.warn("failed to load successors for {}", task.name(), e); }
            finally { visited.remove(task.id()); }
        }
        return item;
    }

    // ── center panel buttons ─────────────────────────────────────────────────

    @FXML
    private void onAddTask()
    {
        TaskGroupDto group = cbGroups.getSelectionModel().getSelectedItem();
        if (group == null || group.id() == null) return;

        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Aufgabe hinzufügen");
        dlg.setHeaderText("Neue Aufgabe in Gruppe \"" + group.name() + "\"");
        dlg.setContentText("Name:");

        dlg.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).ifPresent(name ->
        {
            try
            {
                taskClient.create(name, group.id());
                reloadCurrentGroup();
            }
            catch (Exception e) { log.error("failed to create task", e); showError("Aufgabe anlegen", e); }
        });
    }

    @FXML
    private void onEditTask()
    {
        TreeItem<TaskDto> item = tvSuperSub.getSelectionModel().getSelectedItem();
        if (item == null) return;
        TaskDto task = item.getValue();
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
                TaskDto updated = taskClient.update(task.id(), task);
                item.setValue(updated);
                fillDetail(item, tfIdSuperSub, tfNameSuperSub);
            }
            catch (Exception e) { log.error("failed to rename task", e); showError("Aufgabe umbenennen", e); }
        });
    }

    @FXML
    private void onDelTask()
    {
        TreeItem<TaskDto> item = tvSuperSub.getSelectionModel().getSelectedItem();
        if (item == null) return;
        TaskDto task = item.getValue();
        if (task == null || task.id() == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Aufgabe \"" + task.name() + "\" löschen?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Aufgabe löschen");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().filter(bt -> bt == ButtonType.OK).isPresent())
        {
            try
            {
                taskClient.delete(task.id());
                reloadCurrentGroup();
            }
            catch (Exception e) { log.error("failed to delete task", e); showError("Aufgabe löschen", e); }
        }
    }

    // ── predecessor panel buttons ─────────────────────────────────────────────

    @FXML
    private void onAddPredecessor()
    {
        TaskDto centerTask = centerSelected();
        if (centerTask == null || centerTask.id() == null) return;

        List<TaskDto> current = collectFromTree(tvPredecessors.getRoot());
        Set<Long> excluded = toIds(current);
        excluded.add(centerTask.id());

        pickTask("Vorgänger hinzufügen", excluded).ifPresent(pred ->
        {
            try
            {
                taskClient.addPredecessor(centerTask.id(), pred.id());
                reloadSidePanels(centerTask);
            }
            catch (Exception e) { log.error("failed to add predecessor", e); showError("Vorgänger hinzufügen", e); }
        });
    }

    @FXML
    private void onEditPredecessor()
    {
        TreeItem<TaskDto> item = tvPredecessors.getSelectionModel().getSelectedItem();
        if (item == null) return;
        editTask(item, tfIdPred, tfNamePred);
    }

    @FXML
    private void onDelPredecessor()
    {
        TaskDto centerTask = centerSelected();
        TreeItem<TaskDto> predItem = tvPredecessors.getSelectionModel().getSelectedItem();
        if (centerTask == null || centerTask.id() == null || predItem == null) return;
        TaskDto pred = predItem.getValue();
        if (pred == null || pred.id() == null) return;

        // Only direct predecessors (root children) can be removed from the center task.
        // Deeper nodes would need their own direct link removed.
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
            taskClient.removePredecessor(centerTask.id(), pred.id());
            reloadSidePanels(centerTask);
        }
        catch (Exception e) { log.error("failed to remove predecessor", e); showError("Vorgänger entfernen", e); }
    }

    // ── successor panel buttons ───────────────────────────────────────────────

    @FXML
    private void onAddSuccessor()
    {
        TaskDto centerTask = centerSelected();
        if (centerTask == null || centerTask.id() == null) return;

        List<TaskDto> current = collectFromTree(tvSuccessors.getRoot());
        Set<Long> excluded = toIds(current);
        excluded.add(centerTask.id());

        pickTask("Nachfolger hinzufügen", excluded).ifPresent(succ ->
        {
            // adding a successor = making centerTask a predecessor of succ
            try
            {
                taskClient.addPredecessor(succ.id(), centerTask.id());
                reloadSidePanels(centerTask);
            }
            catch (Exception e) { log.error("failed to add successor", e); showError("Nachfolger hinzufügen", e); }
        });
    }

    @FXML
    private void onEditSuccessor()
    {
        TreeItem<TaskDto> item = tvSuccessors.getSelectionModel().getSelectedItem();
        if (item == null) return;
        editTask(item, tfIdSucc, tfNameSucc);
    }

    @FXML
    private void onDelSuccessor()
    {
        TaskDto centerTask = centerSelected();
        TreeItem<TaskDto> succItem = tvSuccessors.getSelectionModel().getSelectedItem();
        if (centerTask == null || centerTask.id() == null || succItem == null) return;
        TaskDto succ = succItem.getValue();
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
            taskClient.removePredecessor(succ.id(), centerTask.id());
            reloadSidePanels(centerTask);
        }
        catch (Exception e) { log.error("failed to remove successor", e); showError("Nachfolger entfernen", e); }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void reloadCurrentGroup()
    {
        TaskGroupDto group = cbGroups.getSelectionModel().getSelectedItem();
        if (group != null) loadGroup(group);
    }

    private void reloadSidePanels(TaskDto centerTask)
    {
        // Re-drive from center selection with fresh data
        TreeItem<TaskDto> centerItem = tvSuperSub.getSelectionModel().getSelectedItem();
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
        clearDetail(tfIdPred, tfNamePred);
        clearDetail(tfIdSucc, tfNameSucc);
    }

    private void fillDetail(TreeItem<TaskDto> item, TextField tfId, TextField tfName)
    {
        if (item == null || item.getValue() == null) { clearDetail(tfId, tfName); return; }
        TaskDto t = item.getValue();
        tfId  .setText(t.id()   == null ? "" : t.id().toString());
        tfName.setText(t.name() == null ? "" : t.name());
    }

    private void clearDetail(TextField tfId, TextField tfName)
    {
        tfId  .clear();
        tfName.clear();
    }

    private void editTask(TreeItem<TaskDto> item, TextField tfId, TextField tfName)
    {
        TaskDto task = item.getValue();
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
                TaskDto updated = taskClient.update(task.id(), task);
                item.setValue(updated);
                fillDetail(item, tfId, tfName);
            }
            catch (Exception e) { log.error("failed to rename task", e); showError("Aufgabe umbenennen", e); }
        });
    }

    private TaskDto centerSelected()
    {
        TreeItem<TaskDto> item = tvSuperSub.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private Optional<TaskDto> pickTask(String title, Set<Long> excludedIds)
    {
        List<TaskDto> choices = allTasks.stream()
                                        .filter(t -> t.id() != null && !excludedIds.contains(t.id()))
                                        .toList();
        if (choices.isEmpty()) return Optional.empty();

        Map<String, TaskDto> byLabel = new LinkedHashMap<>();
        for (TaskDto t : choices)
        {
            String group = t.taskGroup().map(TaskGroupDto::name).orElse("?");
            byLabel.put(group + " / " + t.name(), t);
        }
        List<String> labels = List.copyOf(byLabel.keySet());
        ChoiceDialog<String> dlg = new ChoiceDialog<>(labels.get(0), labels);
        dlg.setTitle(title);
        dlg.setHeaderText(null);
        dlg.setContentText("Aufgabe:");
        return dlg.showAndWait().map(byLabel::get);
    }

    /** Collects all task values from a tree (all levels). */
    private List<TaskDto> collectFromTree(TreeItem<TaskDto> root)
    {
        if (root == null) return List.of();
        java.util.List<TaskDto> result = new java.util.ArrayList<>();
        collectRecursive(root, result);
        return result;
    }

    private void collectRecursive(TreeItem<TaskDto> item, java.util.List<TaskDto> acc)
    {
        if (item.getValue() != null) acc.add(item.getValue());
        item.getChildren().forEach(child -> collectRecursive(child, acc));
    }

    private Set<Long> toIds(List<TaskDto> tasks)
    {
        Set<Long> ids = new HashSet<>();
        tasks.stream().filter(t -> t.id() != null).map(TaskDto::id).forEach(ids::add);
        return ids;
    }

    /** Builds the super/sub tree from a flat task list using parentTask references. */
    private TreeItem<TaskDto> buildSuperSubTree(List<TaskDto> tasks)
    {
        TreeItem<TaskDto>       root = new TreeItem<>();
        Map<Long, TreeItem<TaskDto>> byId = new HashMap<>();

        for (TaskDto task : tasks)
        {
            if (task.id() == null) continue;
            TreeItem<TaskDto> item = new TreeItem<>(task);
            item.setExpanded(true);
            byId.put(task.id(), item);
        }
        for (TaskDto task : tasks)
        {
            if (task.id() == null) continue;
            TreeItem<TaskDto> item     = byId.get(task.id());
            Long              parentId = task.parentTask().map(TaskDto::id).orElse(null);
            if (parentId != null && byId.containsKey(parentId))
                byId.get(parentId).getChildren().add(item);
            else
                root.getChildren().add(item);
        }
        return root;
    }

    private void setupTreeView(TreeView<TaskDto> tv)
    {
        tv.setCellFactory(v -> new TreeCell<>()
        {
            @Override protected void updateItem(TaskDto item, boolean empty)
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
    }

    private ListCell<TaskGroupDto> groupCell()
    {
        return new ListCell<>()
        {
            @Override protected void updateItem(TaskGroupDto item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        };
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
