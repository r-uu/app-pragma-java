package de.ruu.app.pragma.fx.task.graph;

import com.brunomnsilva.smartgraph.graph.DigraphEdgeList;
import com.brunomnsilva.smartgraph.graph.Vertex;
import com.brunomnsilva.smartgraph.graphview.ForceDirectedSpringGravityLayoutStrategy;
import com.brunomnsilva.smartgraph.graphview.SmartCircularSortedPlacementStrategy;
import com.brunomnsilva.smartgraph.graphview.SmartGraphPanel;
import com.brunomnsilva.smartgraph.graphview.SmartGraphProperties;
import de.ruu.app.pragma.client.TaskClient;
import de.ruu.app.pragma.client.TaskGroupClient;
import de.ruu.app.pragma.dto.TaskDto;
import de.ruu.app.pragma.dto.TaskGroupDto;
import de.ruu.lib.fx.comp.FXCController.DefaultFXCController;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Dependent
class GraphController extends DefaultFXCController<Graph, GraphService> implements GraphService
{
    private static final Logger log = LoggerFactory.getLogger(GraphController.class);

    @FXML private ComboBox<TaskGroupDto> cbGroups;
    @FXML private Label                  lblStatus;
    @FXML private AnchorPane             graphContainer;

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
            Platform.runLater(() -> buildGraph(tasks));
        }
        catch (Exception e) { log.error("failed to load group {}", group.name(), e); }
    }

    private void buildGraph(List<TaskDto> tasks)
    {
        DigraphEdgeList<String, String> digraph = new DigraphEdgeList<>();
        Map<Long, Vertex<String>> vertexById = new HashMap<>();

        for (TaskDto task : tasks)
        {
            if (task.id() == null) continue;
            Vertex<String> v = digraph.insertVertex(task.name());
            vertexById.put(task.id(), v);
        }

        int edgeSeq = 0;
        for (TaskDto task : tasks)
        {
            if (task.id() == null) continue;
            Vertex<String> vTask = vertexById.get(task.id());
            if (vTask == null) continue;
            try
            {
                List<TaskDto> preds = taskClient.findPredecessors(task.id());
                for (TaskDto pred : preds)
                {
                    if (pred.id() == null) continue;
                    Vertex<String> vPred = vertexById.get(pred.id());
                    if (vPred == null)
                    {
                        vPred = digraph.insertVertex(pred.name() + "*");
                        vertexById.put(pred.id(), vPred);
                    }
                    try { digraph.insertEdge(vPred, vTask, "e" + edgeSeq++); }
                    catch (Exception ex) { log.warn("duplicate edge skipped", ex); }
                }
            }
            catch (Exception e) { log.warn("failed to load predecessors for {}", task.name(), e); }
        }

        if (lblStatus != null)
            lblStatus.setText(digraph.numVertices() + " tasks, " + digraph.numEdges() + " links");

        URI cssUri = resolveCssUri();
        SmartGraphPanel<String, String> panel;
        if (cssUri != null)
        {
            panel = new SmartGraphPanel<>(
                    digraph,
                    new SmartGraphProperties(),
                    new SmartCircularSortedPlacementStrategy(),
                    cssUri,
                    new ForceDirectedSpringGravityLayoutStrategy<>()
            );
        }
        else
        {
            // fallback: no custom CSS — SmartGraph tries smartgraph.css in working dir
            panel = new SmartGraphPanel<>(digraph, new ForceDirectedSpringGravityLayoutStrategy<>());
        }
        panel.setAutomaticLayout(true);

        AnchorPane.setTopAnchor   (panel, 0.0);
        AnchorPane.setBottomAnchor(panel, 0.0);
        AnchorPane.setLeftAnchor  (panel, 0.0);
        AnchorPane.setRightAnchor (panel, 0.0);
        graphContainer.getChildren().setAll(panel);

        if (panel.getScene() != null)
        {
            panel.init();
        }
        else
        {
            panel.sceneProperty().addListener((obs, old, scene) ->
            {
                if (scene != null) Platform.runLater(() -> panel.init());
            });
        }
    }

    private URI resolveCssUri()
    {
        try
        {
            URL url = GraphController.class.getResource("/smartgraph.css");
            return url != null ? url.toURI() : null;
        }
        catch (URISyntaxException e)
        {
            log.warn("could not resolve smartgraph.css URI", e);
            return null;
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
