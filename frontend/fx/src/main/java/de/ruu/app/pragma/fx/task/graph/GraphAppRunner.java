package de.ruu.app.pragma.fx.task.graph;

import de.ruu.lib.fx.comp.FXCAppRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphAppRunner extends FXCAppRunner
{
    private static final Logger log = LoggerFactory.getLogger(GraphAppRunner.class);

    public static void main(String[] args)
    {
        log.debug("starting {}", GraphAppRunner.class.getName());
        FXCAppRunner.configureModuleAccessForCDI();
        GraphAppRunner.class.getModule().addReads(GraphAppRunner.class.getClassLoader().getUnnamedModule());
        FXCAppRunner.run(GraphApp.class, args);
        log.debug("finished {}", GraphAppRunner.class.getName());
    }
}
