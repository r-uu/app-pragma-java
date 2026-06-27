package de.ruu.app.pragma.fx.task.gantt;

import de.ruu.app.pragma.bean.TaskBean;
import de.ruu.app.pragma.bean.TaskGroupBean;
import de.ruu.app.pragma.client.TaskClient;
import de.ruu.app.pragma.client.TaskGroupClient;
import de.ruu.lib.fx.FXUtil;
import de.ruu.lib.fx.comp.FXCController.DefaultFXCController;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Dependent
class GanttController extends DefaultFXCController<Gantt, GanttService> implements GanttService
{
    private static final Logger log = LoggerFactory.getLogger(GanttController.class);

    private static final DateTimeFormatter DE_FORMAT    = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DAY_FORMAT   = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy");

    // ── top bar ──────────────────────────────────────────────────────────────

    @FXML private VBox vBxForGroup;
    @FXML private HBox hBxForFilter;

    @FXML private ComboBox<TaskGroupBean> cbGroups;
    @FXML private DatePicker              dtPckrStart;
    @FXML private DatePicker              dtPckrEnd;
    @FXML private Button                  btnApply;

    // ── main table ───────────────────────────────────────────────────────────

    @FXML private TreeTableView<TaskBean> ttv;

    // ── detail / edit area ───────────────────────────────────────────────────

    @FXML private DatePicker dtPckrTaskStart;
    @FXML private DatePicker dtPckrTaskEnd;
    @FXML private Button     btnSaveDates;

    // ── injections ───────────────────────────────────────────────────────────

    @Inject private TaskGroupClient taskGroupClient;
    @Inject private TaskClient      taskClient;

    // ── state ────────────────────────────────────────────────────────────────

    private List<TaskBean> currentTasks = List.of();

    // ── initialization ───────────────────────────────────────────────────────

    @Override
    @FXML
    protected void initialize()
    {
        FXUtil.wrapInTitledBorder("group",  vBxForGroup);
        FXUtil.wrapInTitledBorder("filter", hBxForFilter);

        cbGroups.setCellFactory(lv -> groupCell());
        cbGroups.setButtonCell(groupCell());
        cbGroups.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> { if (sel != null) loadGroup(sel); });

        configureDatePicker(dtPckrStart);
        configureDatePicker(dtPckrEnd);
        dtPckrStart.setValue(LocalDate.of(LocalDate.now().getYear(), 1, 1));
        dtPckrEnd  .setValue(LocalDate.of(LocalDate.now().getYear(), 3, 31));

        btnApply.setOnAction(e -> reloadTable());

        ttv.setShowRoot(false);
        ttv.setRoot(new TreeItem<>());
        ttv.setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
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
            currentTasks = taskClient.findAll(group);
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

        // Task name column (fixed)
        TreeTableColumn<TaskBean, String> nameCol = new TreeTableColumn<>("Task");
        nameCol.setPrefWidth(200);
        nameCol.setMinWidth(100);
        nameCol.setResizable(true);
        nameCol.setStyle("-fx-font-weight: normal;");
        nameCol.setCellValueFactory(cdf ->
                new SimpleStringProperty(cdf.getValue().getValue() == null
                        ? "" : cdf.getValue().getValue().name()));
        ttv.getColumns().add(nameCol);

        // Nested month → day columns (analogous to jeerah GanttTableController)
        LocalDate current = start;
        while (!current.isAfter(end))
        {
            YearMonth month = YearMonth.from(current);

            TreeTableColumn<TaskBean, String> monthCol = new TreeTableColumn<>(MONTH_FORMAT.format(current));
            monthCol.setStyle("-fx-font-weight: normal; -fx-alignment: center;");
            monthCol.setResizable(false);
            monthCol.setReorderable(false);
            monthCol.setSortable(false);

            while (!current.isAfter(end) && YearMonth.from(current).equals(month))
            {
                final LocalDate date = current;

                TreeTableColumn<TaskBean, String> dayCol = new TreeTableColumn<>(DAY_FORMAT.format(date));
                dayCol.setPrefWidth(24);
                dayCol.setMinWidth(24);
                dayCol.setMaxWidth(24);
                dayCol.setResizable(false);
                dayCol.setReorderable(false);
                dayCol.setSortable(false);
                dayCol.setStyle("-fx-font-weight: normal; -fx-alignment: center;");

                dayCol.setCellValueFactory(cdf -> {
                    TaskBean task = cdf.getValue().getValue();
                    if (task == null) return new SimpleStringProperty("");
                    LocalDate ps = task.plannedStart().orElse(null);
                    LocalDate pe = task.plannedEnd()  .orElse(null);
                    if (ps != null && pe != null && !date.isBefore(ps) && !date.isAfter(pe))
                        return new SimpleStringProperty("x");
                    return new SimpleStringProperty("");
                });

                dayCol.setCellFactory(col -> new TreeTableCell<>()
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
                            setText("x");
                            setAlignment(Pos.CENTER);
                            setStyle("-fx-background-color: #4a90e2; -fx-text-fill: white;");
                        }
                    }
                });

                monthCol.getColumns().add(dayCol);
                current = current.plusDays(1);
            }

            ttv.getColumns().add(monthCol);
        }

        // Build tree from flat list using parentTask references
        Map<Long, TreeItem<TaskBean>> byId = new HashMap<>();
        for (TaskBean t : currentTasks)
        {
            if (t.id() == null) continue;
            TreeItem<TaskBean> item = new TreeItem<>(t);
            item.setExpanded(true);
            byId.put(t.id(), item);
        }
        for (TaskBean t : currentTasks)
        {
            if (t.id() == null) continue;
            TreeItem<TaskBean> item     = byId.get(t.id());
            Long               parentId = t.parentTask().map(TaskBean::id).orElse(null);
            if (parentId != null && byId.containsKey(parentId))
                byId.get(parentId).getChildren().add(item);
            else
                ttv.getRoot().getChildren().add(item);
        }
    }

    private void onTaskSelected(TreeItem<TaskBean> sel)
    {
        if (sel == null || sel.getValue() == null)
        {
            dtPckrTaskStart.setValue(null);
            dtPckrTaskEnd  .setValue(null);
            btnSaveDates.setDisable(true);
            return;
        }
        TaskBean task = sel.getValue();
        dtPckrTaskStart.setValue(task.plannedStart().orElse(null));
        dtPckrTaskEnd  .setValue(task.plannedEnd()  .orElse(null));
        btnSaveDates.setDisable(task.id() == null);
    }

    private void saveDates()
    {
        TreeItem<TaskBean> sel = ttv.getSelectionModel().getSelectedItem();
        if (sel == null || sel.getValue() == null || sel.getValue().id() == null) return;

        TaskBean task = sel.getValue();
        task.plannedStart(dtPckrTaskStart.getValue());
        task.plannedEnd  (dtPckrTaskEnd  .getValue());

        try
        {
            TaskBean updated = taskClient.update(task);
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

    // ── helpers ──────────────────────────────────────────────────────────────

    private void configureDatePicker(DatePicker dp)
    {
        dp.setConverter(new StringConverter<>()
        {
            @Override public String toString(LocalDate d) { return d != null ? DE_FORMAT.format(d) : ""; }
            @Override public LocalDate fromString(String s)
            {
                try   { return s != null && !s.isEmpty() ? LocalDate.parse(s, DE_FORMAT) : null; }
                catch (DateTimeParseException e) { return null; }
            }
        });
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
}
