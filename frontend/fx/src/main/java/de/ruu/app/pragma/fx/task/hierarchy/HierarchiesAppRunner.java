package de.ruu.app.pragma.fx.task.hierarchy;

import de.ruu.lib.fx.comp.FXCAppRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HierarchiesAppRunner extends FXCAppRunner
{
    private static final Logger log = LoggerFactory.getLogger(HierarchiesAppRunner.class);

    public static void main(String[] args)
    {
        log.debug("starting {}", HierarchiesAppRunner.class.getName());
        FXCAppRunner.configureModuleAccessForCDI();
        HierarchiesAppRunner.addReadsUnnamedModule();
        FXCAppRunner.run(HierarchiesApp.class, args);
        log.debug("finished {}", HierarchiesAppRunner.class.getName());
    }

    public static void addReadsUnnamedModule()
    {
        HierarchiesAppRunner.class.getModule()
                .addReads(HierarchiesAppRunner.class.getClassLoader().getUnnamedModule());
    }
}
