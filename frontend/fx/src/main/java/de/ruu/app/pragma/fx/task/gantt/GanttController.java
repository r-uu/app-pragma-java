package de.ruu.app.pragma.fx.task.gantt;

import de.ruu.app.pragma.client.TaskClient;
import de.ruu.app.pragma.client.TaskGroupClient;
import de.ruu.app.pragma.dto.TaskDto;
import de.ruu.app.pragma.dto.TaskGroupDto;
import de.ruu.lib.fx.comp.FXCController.DefaultFXCController;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Dependent
class GanttController extends DefaultFXCController<Gantt, GanttService> implements GanttService
{
    private static final Logger log = LoggerFactory.getLogger(GanttController.class);

    // ── top bar ──────────────────────────────────────────────────────────────

    @FXML private ComboBox<TaskGroupDto> cbGroups;
    @FXML private DatePicker             dtPckrStart;
    @FXML private DatePicker             dtPckrEnd;
    @FXML private Button                 btnApply;

    // ── main table ───────────────────────────────────────────────────────────

    @FXML private TreeTableView<TaskDto> ttv;

    // ── detail / edit area ───────────────────────────────────────────────────

    @FXML private DatePicker dtPckrTaskStart;
    @FXML private DatePicker dtPckrTaskEnd;
    @FXML private Button     btnSaveDates;

    // ── injections ───────────────────────────────────────────────────────────

    @Inject private TaskGroupClient taskGroupClient;
    @Inject private TaskClient      taskClient;

    // ── state ────────────────────────────────────────────────────────────────

    private List<TaskDto> currentTasks = List.of();

    // ── initialization ───────────────────────────────────────────────────────

    @Override
    @FXML
    protected void initialize()
    {
        cbGroups.setCellFactory(lv -> groupCell());
        cbGroups.setButtonCell(groupCell());
        cbGroups.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> { if (sel != null) loadGroup(sel); });

        dtPckrStart.setValue(LocalDate.now().minusMonths(1));
        dtPckrEnd  .setValue(LocalDate.now().plusMonths(2));

        btnApply.setOnAction(e -> reloadTable());

        ttv.setShowRoot(false);
        ttv.setRoot(new TreeItem<>());
        ttv.getSelectionModel().selectedItemProperty()
           .addListener((obs, old, sel) -> onTaskSelected(sel));

        btnSaveDates.setDisable(true);
        btnSaveDates.setOnAction(e -> saveDates());

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
            currentTasks = taskClient.findAll(group.id());
            Platform.runLater(this::reloadTable);
        }
        catch (Exception e) { log.error("failed to load group {}", group.name(), e); }
    }

    private void reloadTable()
    {
        LocalDate start = dtPckrStart.getValue();
        LocalDate end   = dtPckrEnd  .getValue();
        if (start == null || end == null || !end.isAfter(start)) return;

        ttv.getColumns().clear();
        ttv.getRoot().getChildren().clear();

        // Name column (fixed)
        TreeTableColumn<TaskDto, String> nameCol = new TreeTableColumn<>("task");
        nameCol.setPrefWidth(200);
        nameCol.setMinWidth(100);
        nameCol.setResizable(true);
        nameCol.setCellValueFactory(cdf ->
                new SimpleStringProperty(cdf.getValue().getValue() == null
                        ? "" : cdf.getValue().getValue().name()));
        ttv.getColumns().add(nameCol);

        // One column per day in [start, end)
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1))
        {
            final LocalDate date = d;
            String header = d.getDayOfMonth() == 1
                    ? d.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    : String.valueOf(d.getDayOfMonth());
            TreeTableColumn<TaskDto, String> dayCol = new TreeTableColumn<>(header);
            dayCol.setPrefWidth(28);
            dayCol.setMinWidth(20);
            dayCol.setResizable(false);
            dayCol.setCellValueFactory(cdf -> {
                TaskDto task = cdf.getValue().getValue();
                if (task == null) return new SimpleStringProperty("");
                LocalDate ps = task.plannedStart().orElse(null);
                LocalDate pe = task.plannedEnd()  .orElse(null);
                if (ps != null && pe != null && !date.isBefore(ps) && !date.isAfter(pe))
                    return new SimpleStringProperty("■");
                return new SimpleStringProperty("");
            });
            dayCol.setCellFactory(col -> new javafx.scene.control.TreeTableCell<>()
            {
                @Override protected void updateItem(String item, boolean empty)
                {
                    super.updateItem(item, empty);
                    if (empty || item == null || item.isEmpty())
                    {
                        setText(null);
                        setStyle("");
                    }
                    else
                    {
                        setText(null);
                        setStyle("-fx-background-color: #4a90d9;");
                    }
                }
            });
            ttv.getColumns().add(dayCol);
        }

        // Build tree from flat list using parentTask references
        Map<Long, TreeItem<TaskDto>> byId = new HashMap<>();
        for (TaskDto t : currentTasks)
        {
            if (t.id() == null) continue;
            TreeItem<TaskDto> item = new TreeItem<>(t);
            item.setExpanded(true);
            byId.put(t.id(), item);
        }
        for (TaskDto t : currentTasks)
        {
            if (t.id() == null) continue;
            TreeItem<TaskDto> item = byId.get(t.id());
            Long parentId = t.parentTask().map(TaskDto::id).orElse(null);
            if (parentId != null && byId.containsKey(parentId))
                byId.get(parentId).getChildren().add(item);
            else
                ttv.getRoot().getChildren().add(item);
        }
    }

    private void onTaskSelected(TreeItem<TaskDto> sel)
    {
        if (sel == null || sel.getValue() == null)
        {
            dtPckrTaskStart.setValue(null);
            dtPckrTaskEnd  .setValue(null);
            btnSaveDates.setDisable(true);
            return;
        }
        TaskDto task = sel.getValue();
        dtPckrTaskStart.setValue(task.plannedStart().orElse(null));
        dtPckrTaskEnd  .setValue(task.plannedEnd()  .orElse(null));
        btnSaveDates.setDisable(task.id() == null);
    }

    private void saveDates()
    {
        TreeItem<TaskDto> sel = ttv.getSelectionModel().getSelectedItem();
        if (sel == null || sel.getValue() == null || sel.getValue().id() == null) return;

        TaskDto task = sel.getValue();
        task.plannedStart(dtPckrTaskStart.getValue());
        task.plannedEnd  (dtPckrTaskEnd  .getValue());

        try
        {
            TaskDto updated = taskClient.update(task.id(), task);
            // update in currentTasks list
            currentTasks = new ArrayList<>(currentTasks);
            currentTasks.replaceAll(t -> t.id() != null && t.id().equals(updated.id()) ? updated : t);
            sel.setValue(updated);
            reloadTable();
        }
        catch (Exception e)
        {
            log.error("failed to save dates", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            alert.setTitle("Datumfelder speichern");
            alert.setHeaderText(null);
            alert.showAndWait();
        }
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
}
