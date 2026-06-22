package de.ruu.app.pragma.fx.task.graph;

import de.ruu.lib.fx.comp.DefaultFXCView;

public class Graph extends DefaultFXCView<Graph, GraphService, GraphController>
{
    public GraphController getController() { return controller(); }
}
