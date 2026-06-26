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
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Dependent
class GraphController extends DefaultFXCController<Graph, GraphService> implements GraphService
{
    private static final Logger log = LoggerFactory.getLogger(GraphController.class);

    private static final double NODE_WIDTH  = 160;
    private static final double NODE_HEIGHT =  60;
    private static final double H_GAP       = 40;
    private static final double V_GAP       = 30;
    private static final double ARC         = 12;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML private ComboBox<TaskGroupBean> cbGroups;
    @FXML private Label                   lblStatus;
    @FXML private AnchorPane              graphContainer;

    @Inject private TaskGroupClient taskGroupClient;
    @Inject private TaskClient      taskClient;

    @Override
    @FXML
    protected void initialize()
    {
        cbGroups.setCellFactory(lv -> groupCell());
        cbGroups.setButtonCell(groupCell());
        cbGroups.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> { if (sel != null) loadGroup(sel); });
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
            List<TaskBean> tasks = taskClient.findGroupTasksWithRelated(group.id());
            buildGraph(tasks);
            if (lblStatus != null)
                lblStatus.setText(tasks.size() + " tasks");
        }
        catch (Exception e) { log.error("failed to load group {}", group.name(), e); }
    }

    private void buildGraph(List<TaskBean> tasks)
    {
        Pane canvas = new Pane();
        canvas.setStyle("-fx-background-color: #1e1e2e;");

        Map<Long, TaskBean>  byId       = new HashMap<>();
        Map<Long, Group>     nodeById   = new HashMap<>();
        List<EdgeSpec>       edgeSpecs  = new ArrayList<>();

        for (TaskBean task : tasks)
        {
            if (task.id() == null) continue;
            byId.put(task.id(), task);
        }

        // vertex nodes
        for (TaskBean task : tasks)
        {
            if (task.id() == null) continue;
            Group node = createNode(task);
            nodeById.put(task.id(), node);
            canvas.getChildren().add(node);
        }

        // collect edges
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
                        // predecessor not in group — add it as a ghost node
                        Group ghost = createNode(pred);
                        nodeById.put(pred.id(), ghost);
                        canvas.getChildren().add(ghost);
                        from = ghost;
                    }
                    edgeSpecs.add(new EdgeSpec(from, to));
                }
            });
        }

        // initial layout (topological layers)
        applyLayout(nodeById, byId);

        // draw edges BEHIND nodes
        for (EdgeSpec spec : edgeSpecs)
        {
            List<javafx.scene.Node> arrow = createArrow(spec.from(), spec.to());
            canvas.getChildren().addAll(0, arrow);
        }

        AnchorPane.setTopAnchor   (canvas, 0.0);
        AnchorPane.setBottomAnchor(canvas, 0.0);
        AnchorPane.setLeftAnchor  (canvas, 0.0);
        AnchorPane.setRightAnchor (canvas, 0.0);
        graphContainer.getChildren().setAll(canvas);
    }

    private Group createNode(TaskBean task)
    {
        Rectangle rect = new Rectangle(NODE_WIDTH, NODE_HEIGHT);
        rect.setArcWidth (ARC);
        rect.setArcHeight(ARC);
        rect.setFill  (Color.web("#3c5a8a"));
        rect.setStroke(Color.web("#6699cc"));
        rect.setStrokeWidth(1.5);

        Label lName  = new Label(task.name());
        lName.setTextFill(Color.WHITE);
        lName.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        lName.setMaxWidth(NODE_WIDTH - 10);

        String startText = task.plannedStart()
            .map(d -> "von: " + d.format(DATE_FMT)).orElse("");
        String endText   = task.plannedEnd()
            .map(d -> "bis: " + d.format(DATE_FMT)).orElse("");

        Label lDates = new Label(startText + (startText.isEmpty() || endText.isEmpty() ? "" : "  ") + endText);
        lDates.setTextFill(Color.LIGHTGRAY);
        lDates.setStyle("-fx-font-size: 9px;");
        lDates.setMaxWidth(NODE_WIDTH - 10);

        VBox box = new VBox(2, lName, lDates);
        box.setPadding(new Insets(6, 6, 6, 8));
        box.setMaxWidth(NODE_WIDTH);

        Group group = new Group(rect, box);
        enableDrag(group);
        return group;
    }

    private void enableDrag(Group node)
    {
        final double[] drag = {0, 0};
        node.setOnMousePressed(e ->
        {
            drag[0] = e.getSceneX() - node.getTranslateX();
            drag[1] = e.getSceneY() - node.getTranslateY();
            node.toFront();
        });
        node.setOnMouseDragged(e ->
        {
            node.setTranslateX(e.getSceneX() - drag[0]);
            node.setTranslateY(e.getSceneY() - drag[1]);
        });
    }

    private List<javafx.scene.Node> createArrow(Group from, Group to)
    {
        // bind line endpoints to node centers (translateX/Y + half-size)
        Line line = new Line();
        line.setStroke(Color.web("#aaaaaa"));
        line.setStrokeWidth(1.5);

        line.startXProperty().bind(from.translateXProperty().add(NODE_WIDTH  / 2.0));
        line.startYProperty().bind(from.translateYProperty().add(NODE_HEIGHT / 2.0));
        line.endXProperty  ().bind(to.translateXProperty()  .add(NODE_WIDTH  / 2.0));
        line.endYProperty  ().bind(to.translateYProperty()  .add(NODE_HEIGHT / 2.0));

        Polygon arrowHead = new Polygon();
        arrowHead.setFill(Color.web("#aaaaaa"));

        // update arrowhead when line changes
        Runnable updateHead = () -> updateArrowHead(arrowHead, line);
        line.startXProperty().addListener((o, ov, nv) -> updateHead.run());
        line.startYProperty().addListener((o, ov, nv) -> updateHead.run());
        line.endXProperty  ().addListener((o, ov, nv) -> updateHead.run());
        line.endYProperty  ().addListener((o, ov, nv) -> updateHead.run());

        return List.of(line, arrowHead);
    }

    private void updateArrowHead(Polygon head, Line line)
    {
        double ex = line.getEndX();
        double ey = line.getEndY();
        double sx = line.getStartX();
        double sy = line.getStartY();

        double angle  = Math.atan2(ey - sy, ex - sx);
        double tipLen = 10;
        double tipWid = 5;

        double tipX = ex - Math.cos(angle) * (NODE_HEIGHT / 2.0);
        double tipY = ey - Math.sin(angle) * (NODE_HEIGHT / 2.0);

        double lx = tipX - Math.cos(angle - 0.4) * tipLen;
        double ly = tipY - Math.sin(angle - 0.4) * tipLen;
        double rx = tipX - Math.cos(angle + 0.4) * tipLen;
        double ry = tipY - Math.sin(angle + 0.4) * tipLen;

        head.getPoints().setAll(tipX, tipY, lx, ly, rx, ry);
    }

    /**
     * Assigns x/y positions via topological layering.
     * Nodes with no predecessors go in column 0; each successor goes in max(predecessorLayer)+1.
     */
    private void applyLayout(Map<Long, Group> nodeById, Map<Long, TaskBean> byId)
    {
        Map<Long, Integer> layer  = computeLayers(byId);
        Map<Integer, List<Long>> columns = new HashMap<>();
        layer.forEach((id, col) -> columns.computeIfAbsent(col, k -> new ArrayList<>()).add(id));

        int maxCol = columns.keySet().stream().mapToInt(i -> i).max().orElse(0);
        for (int col = 0; col <= maxCol; col++)
        {
            List<Long> ids = columns.getOrDefault(col, List.of());
            for (int row = 0; row < ids.size(); row++)
            {
                Group node = nodeById.get(ids.get(row));
                if (node == null) continue;
                node.setTranslateX(20 + col * (NODE_WIDTH  + H_GAP));
                node.setTranslateY(20 + row * (NODE_HEIGHT + V_GAP));
            }
        }
    }

    private Map<Long, Integer> computeLayers(Map<Long, TaskBean> byId)
    {
        Map<Long, Integer> inDegree = new HashMap<>();
        Map<Long, List<Long>> successors = new HashMap<>();

        for (TaskBean t : byId.values())
        {
            inDegree.putIfAbsent(t.id(), 0);
            t.predecessors().ifPresent(preds ->
            {
                for (TaskBean pred : preds)
                {
                    if (pred.id() == null) continue;
                    inDegree.merge(t.id(), 1, Integer::sum);
                    successors.computeIfAbsent(pred.id(), k -> new ArrayList<>()).add(t.id());
                }
            });
        }

        Map<Long, Integer> layer = new HashMap<>();
        Queue<Long> queue = new LinkedList<>();
        for (Map.Entry<Long, Integer> e : inDegree.entrySet())
            if (e.getValue() == 0) { queue.add(e.getKey()); layer.put(e.getKey(), 0); }

        Set<Long> visited = new HashSet<>();
        while (!queue.isEmpty())
        {
            Long current = queue.poll();
            if (!visited.add(current)) continue;
            int currentLayer = layer.getOrDefault(current, 0);
            for (Long succId : successors.getOrDefault(current, List.of()))
            {
                int newLayer = currentLayer + 1;
                if (newLayer > layer.getOrDefault(succId, 0))
                    layer.put(succId, newLayer);
                queue.add(succId);
            }
        }

        // any node not yet assigned gets layer 0
        byId.keySet().forEach(id -> layer.putIfAbsent(id, 0));
        return layer;
    }

    private record EdgeSpec(Group from, Group to) {}

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
