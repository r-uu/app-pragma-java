package de.ruu.app.pragma.fx.task.graph;

import de.ruu.app.pragma.bean.TaskBean;
import de.ruu.app.pragma.bean.TaskGroupBean;
import de.ruu.app.pragma.client.TaskClient;
import de.ruu.app.pragma.client.TaskGroupClient;
import de.ruu.lib.fx.comp.FXCController.DefaultFXCController;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.Group;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Dependent
class GraphController extends DefaultFXCController<Graph, GraphService> implements GraphService
{
    private static final Logger log = LoggerFactory.getLogger(GraphController.class);

    private static final double NODE_WIDTH  = 160;
    private static final double NODE_HEIGHT =  60;
    private static final double H_GAP      =  40;
    private static final double V_GAP      =  30;
    private static final double PAD        = GraphLayout.PAD;
    private static final double ARC        =  12;
    private static final double ARROW_LEN  =  10;
    private static final double ARROW_ANG  =   0.4; // radians half-angle of arrowhead
    private static final double GRID       =  20;   // snap-to-grid resolution in pixels
    private static final double STEP       = GraphLayout.STEP;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML private ComboBox<TaskGroupBean> cbGroups;
    @FXML private Label                   lblStatus;
    @FXML private ScrollPane              graphContainer;
    @FXML private Button                  btnSaveLayout;
    @FXML private Button                  btnLoadLayout;

    @Inject private TaskGroupClient taskGroupClient;
    @Inject private TaskClient      taskClient;

    /** Task-ID → node; populated after each group load, used for save/load layout. */
    private Map<Long, Group> currentNodeById = new HashMap<>();

    private File lastLayoutFile;

    @Override
    @FXML
    protected void initialize()
    {
        cbGroups.setCellFactory(lv -> groupCell());
        cbGroups.setButtonCell(groupCell());
        cbGroups.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> { if (sel != null) loadGroup(sel); });
        btnSaveLayout.setDisable(true);
        btnLoadLayout.setDisable(true);
        loadGroups();
    }

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
            buildGraph(tasks);
            if (lblStatus != null) lblStatus.setText(tasks.size() + " tasks");
        }
        catch (Exception e)
        {
            log.error("failed to load group {}", group.name(), e);
            if (lblStatus != null) lblStatus.setText("Fehler: " + e.getMessage());
            showError("Gruppe laden", e);
        }
    }

    private void buildGraph(List<TaskBean> tasks)
    {
        Pane canvas = new Pane();
        canvas.setStyle("-fx-background-color: #1e1e2e;");

        Map<Long, TaskBean> byId     = new HashMap<>();
        Map<Long, Group>    nodeById = new HashMap<>();
        List<EdgeSpec>      edges    = new ArrayList<>();

        for (TaskBean task : tasks)
            if (task.id() != null) byId.put(task.id(), task);

        for (TaskBean task : tasks)
        {
            if (task.id() == null) continue;
            Group node = createNode(task);
            nodeById.put(task.id(), node);
            canvas.getChildren().add(node);
        }

        for (TaskBean task : tasks)
        {
            if (task.id() == null) continue;
            task.predecessors().ifPresent(preds ->
            {
                for (TaskBean pred : preds)
                {
                    if (pred.id() == null) continue;
                    Group from = nodeById.get(pred.id());
                    Group to   = nodeById.get(task.id());
                    if (from == null)
                    {
                        // predecessor is from another group — show as ghost node
                        from = createNode(pred);
                        nodeById.put(pred.id(), from);
                        canvas.getChildren().add(from);
                    }
                    if (to != null) edges.add(new EdgeSpec(from, to));
                }
            });
        }

        applyLayout(nodeById, byId);

        // add edges behind nodes
        for (EdgeSpec spec : edges)
            canvas.getChildren().addAll(0, createArrow(spec.from(), spec.to()));

        graphContainer.setContent(new Group(canvas));
        addZoomSupport(canvas);

        currentNodeById = new HashMap<>(nodeById);
        btnSaveLayout.setDisable(currentNodeById.isEmpty());
        btnLoadLayout.setDisable(currentNodeById.isEmpty());
    }

    // ── Node creation ─────────────────────────────────────────────────────────

    private Group createNode(TaskBean task)
    {
        Rectangle rect = new Rectangle(NODE_WIDTH, NODE_HEIGHT);
        rect.setArcWidth (ARC);
        rect.setArcHeight(ARC);
        rect.setFill     (Color.web("#3c5a8a"));
        rect.setStroke   (Color.web("#6699cc"));
        rect.setStrokeWidth(1.5);

        Label lName = new Label(task.name());
        lName.setTextFill(Color.WHITE);
        lName.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        lName.setMaxWidth(NODE_WIDTH - 10);

        String startText = task.plannedStart().map(d -> "von: " + d.format(DATE_FMT)).orElse("");
        String endText   = task.plannedEnd()  .map(d -> "bis: " + d.format(DATE_FMT)).orElse("");
        String dateText  = startText.isEmpty() && endText.isEmpty() ? ""
                         : startText + (startText.isEmpty() || endText.isEmpty() ? "" : "  ") + endText;

        Label lDates = new Label(dateText);
        lDates.setTextFill(Color.LIGHTGRAY);
        lDates.setStyle("-fx-font-size: 9px;");
        lDates.setMaxWidth(NODE_WIDTH - 10);

        VBox box = new VBox(2, lName, lDates);
        box.setPadding(new Insets(6, 6, 6, 8));
        box.setMaxWidth(NODE_WIDTH);

        Group node = new Group(rect, box);
        enableDrag(node);
        return node;
    }

    // ── Dragging with snap-to-grid ─────────────────────────────────────────────

    private void enableDrag(Group node)
    {
        final double[] offset = {0, 0};
        node.setOnMousePressed(e ->
        {
            offset[0] = e.getSceneX() - node.getTranslateX();
            offset[1] = e.getSceneY() - node.getTranslateY();
            node.toFront();
            e.consume();
        });
        node.setOnMouseDragged(e ->
        {
            node.setTranslateX(e.getSceneX() - offset[0]);
            node.setTranslateY(e.getSceneY() - offset[1]);
            e.consume();
        });
        node.setOnMouseReleased(e ->
        {
            node.setTranslateX(snap(node.getTranslateX()));
            node.setTranslateY(snap(node.getTranslateY()));
            e.consume();
        });
    }

    private double snap(double value) { return Math.round(value / GRID) * GRID; }

    // ── Directed edge (predecessor → successor) ────────────────────────────────

    /**
     * Creates a directed arrow from {@code from} to {@code to}.
     * The start point is the right or left center of {@code from} depending on which side
     * faces {@code to}; the end point is the corresponding opposite edge of {@code to}.
     * The arrowhead is placed exactly at the end point.
     */
    private List<javafx.scene.Node> createArrow(Group from, Group to)
    {
        Line    line = new Line();
        Polygon head = new Polygon();
        line.setStroke(Color.web("#aaaaaa"));
        line.setStrokeWidth(1.5);
        head.setFill(Color.web("#aaaaaa"));

        Runnable update = () -> updateArrow(line, head, from, to);

        from.translateXProperty().addListener((o, ov, nv) -> update.run());
        from.translateYProperty().addListener((o, ov, nv) -> update.run());
        to.translateXProperty()  .addListener((o, ov, nv) -> update.run());
        to.translateYProperty()  .addListener((o, ov, nv) -> update.run());

        update.run();
        return List.of(line, head);
    }

    private void updateArrow(Line line, Polygon head, Group from, Group to)
    {
        double fCx = from.getTranslateX() + NODE_WIDTH  / 2.0;
        double tCx = to  .getTranslateX() + NODE_WIDTH  / 2.0;
        double fCy = from.getTranslateY() + NODE_HEIGHT / 2.0;
        double tCy = to  .getTranslateY() + NODE_HEIGHT / 2.0;

        double startX, startY, endX, endY;

        // connect from the horizontal side that faces the target
        if (fCx <= tCx)
        {
            // from is left of (or equal to) to → exit right edge, enter left edge
            startX = from.getTranslateX() + NODE_WIDTH;
            startY = fCy;
            endX   = to.getTranslateX();
            endY   = tCy;
        }
        else
        {
            // from is right of to → exit left edge, enter right edge
            startX = from.getTranslateX();
            startY = fCy;
            endX   = to.getTranslateX() + NODE_WIDTH;
            endY   = tCy;
        }

        line.setStartX(startX);
        line.setStartY(startY);
        line.setEndX  (endX);
        line.setEndY  (endY);

        // arrowhead tip sits exactly at (endX, endY)
        double angle = Math.atan2(endY - startY, endX - startX);
        double lx = endX - Math.cos(angle - ARROW_ANG) * ARROW_LEN;
        double ly = endY - Math.sin(angle - ARROW_ANG) * ARROW_LEN;
        double rx = endX - Math.cos(angle + ARROW_ANG) * ARROW_LEN;
        double ry = endY - Math.sin(angle + ARROW_ANG) * ARROW_LEN;
        head.getPoints().setAll(endX, endY, lx, ly, rx, ry);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    /**
     * Left-to-right layered layout (column = topological depth).
     * Within each column nodes are sorted by the average y-position of their
     * already-placed predecessors, then spread apart to remove overlaps while
     * staying as close as possible to those target positions.
     * This keeps predecessor–successor pairs at approximately the same height.
     */
    private void applyLayout(Map<Long, Group> nodeById, Map<Long, TaskBean> byId)
    {
        Map<Long, Integer>       layer   = computeLayers(byId);
        Map<Integer, List<Long>> columns = new HashMap<>();
        layer.forEach((id, col) -> columns.computeIfAbsent(col, k -> new ArrayList<>()).add(id));

        // ghost nodes (predecessors from other groups) are not in byId → extra column at the right
        Set<Long> ghostIds = new HashSet<>(nodeById.keySet());
        ghostIds.removeAll(byId.keySet());

        int maxCol    = columns.keySet().stream().mapToInt(i -> i).max().orElse(0);
        int colOffset = ghostIds.isEmpty() ? 0 : 1;  // shift all columns right when ghosts exist

        if (!ghostIds.isEmpty())
        {
            double ghostX = snap(PAD);
            int row = 0;
            for (Long gid : ghostIds)
            {
                Group node = nodeById.get(gid);
                if (node != null) { node.setTranslateX(ghostX); node.setTranslateY(snap(PAD + row++ * STEP)); }
            }
        }

        Map<Long, Double> yPos = new HashMap<>();

        for (int col = 0; col <= maxCol; col++)
        {
            List<Long> ids = new ArrayList<>(columns.getOrDefault(col, List.of()));
            if (ids.isEmpty()) continue;

            // sort by average y of predecessors already placed in earlier columns
            ids.sort((a, b) -> Double.compare(avgPredY(a, byId, yPos), avgPredY(b, byId, yPos)));

            // ideal y = average predecessor y (PAD when no predecessor placed yet)
            List<Double> targets = ids.stream().map(id -> avgPredY(id, byId, yPos)).toList();
            List<Double> placed  = resolveOverlaps(targets);

            for (int i = 0; i < ids.size(); i++) yPos.put(ids.get(i), placed.get(i));

            double x = snap(PAD + (col + colOffset) * (NODE_WIDTH + H_GAP));
            for (Long id : ids)
            {
                Group node = nodeById.get(id);
                if (node != null) { node.setTranslateX(x); node.setTranslateY(snap(yPos.get(id))); }
            }
        }
    }

    private double             avgPredY      (Long id, Map<Long, TaskBean> byId, Map<Long, Double> yPos) { return GraphLayout.avgPredY(id, byId, yPos);      }
    private List<Double>       resolveOverlaps(List<Double> targets)                                      { return GraphLayout.resolveOverlaps(targets);        }
    private Map<Long, Integer> computeLayers  (Map<Long, TaskBean> byId)                                  { return GraphLayout.computeLayers(byId);             }

    private record EdgeSpec(Group from, Group to) {}

    // ── Layout persistence ────────────────────────────────────────────────────

    @FXML
    private void saveLayout()
    {
        FileChooser chooser = layoutFileChooser("Layout speichern");
        File file = chooser.showSaveDialog(graphContainer.getScene().getWindow());
        if (file == null) return;

        lastLayoutFile = file;
        Properties props = new Properties();
        currentNodeById.forEach((id, node) ->
            props.setProperty(id.toString(),
                    node.getTranslateX() + "," + node.getTranslateY()));

        try (FileWriter w = new FileWriter(file))
        {
            props.store(w, "pragma graph layout");
        }
        catch (IOException e)
        {
            log.error("failed to save layout to {}", file, e);
            showError("Layout speichern", e);
        }
    }

    @FXML
    private void loadLayout()
    {
        FileChooser chooser = layoutFileChooser("Layout laden");
        if (lastLayoutFile != null) chooser.setInitialDirectory(lastLayoutFile.getParentFile());
        File file = chooser.showOpenDialog(graphContainer.getScene().getWindow());
        if (file == null) return;

        lastLayoutFile = file;
        Properties props = new Properties();
        try (FileReader r = new FileReader(file))
        {
            props.load(r);
        }
        catch (IOException e)
        {
            log.error("failed to load layout from {}", file, e);
            showError("Layout laden", e);
            return;
        }

        int applied = 0;
        for (String key : props.stringPropertyNames())
        {
            try
            {
                Long  id    = Long.parseLong(key.trim());
                Group node  = currentNodeById.get(id);
                if (node == null) continue;
                String[] xy = props.getProperty(key).split(",", 2);
                node.setTranslateX(snap(Double.parseDouble(xy[0].trim())));
                node.setTranslateY(snap(Double.parseDouble(xy[1].trim())));
                applied++;
            }
            catch (NumberFormatException | ArrayIndexOutOfBoundsException ex)
            {
                log.warn("skipping invalid layout entry: {}={}", key, props.getProperty(key));
            }
        }
        if (lblStatus != null) lblStatus.setText(currentNodeById.size() + " tasks  (layout: " + applied + " nodes)");
    }

    private void addZoomSupport(Pane canvas)
    {
        graphContainer.setOnScroll(event -> {
            double factor = event.getDeltaY() > 0 ? 1.1 : 1.0 / 1.1;
            double next = Math.max(0.1, Math.min(canvas.getScaleX() * factor, 5.0));
            canvas.setScaleX(next);
            canvas.setScaleY(next);
            event.consume();
        });
    }

    private FileChooser layoutFileChooser(String title)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new ExtensionFilter("Pragma Graph Layout (*.pgraph)", "*.pgraph"),
                new ExtensionFilter("Alle Dateien", "*.*"));
        if (lastLayoutFile != null)
        {
            chooser.setInitialDirectory(lastLayoutFile.getParentFile());
            chooser.setInitialFileName(lastLayoutFile.getName());
        }
        return chooser;
    }

    private void showError(String title, Exception e)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
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
