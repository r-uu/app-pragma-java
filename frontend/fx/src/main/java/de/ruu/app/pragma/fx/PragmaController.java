package de.ruu.app.pragma.fx;

import de.ruu.app.pragma.fx.task.gantt.Gantt;
import de.ruu.app.pragma.fx.task.graph.Graph;
import de.ruu.app.pragma.fx.task.hierarchy.Hierarchies;
import de.ruu.lib.fx.comp.FXCController.DefaultFXCController;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dependent
class PragmaController extends DefaultFXCController<Pragma, PragmaService> implements PragmaService
{
    private static final Logger log = LoggerFactory.getLogger(PragmaController.class);

    @FXML private AnchorPane paneHierarchies;
    @FXML private AnchorPane paneGantt;
    @FXML private AnchorPane paneGraph;

    @Inject private Hierarchies hierarchies;
    @Inject private Gantt       gantt;
    @Inject private Graph       graph;

    @Override
    @FXML
    protected void initialize()
    {
        embed(paneHierarchies, hierarchies.localRoot());
        embed(paneGantt,       gantt      .localRoot());
        embed(paneGraph,       graph      .localRoot());
    }

    private void embed(AnchorPane pane, javafx.scene.Node node)
    {
        AnchorPane.setTopAnchor   (node, 0.0);
        AnchorPane.setBottomAnchor(node, 0.0);
        AnchorPane.setLeftAnchor  (node, 0.0);
        AnchorPane.setRightAnchor (node, 0.0);
        pane.getChildren().add(node);
    }
}
