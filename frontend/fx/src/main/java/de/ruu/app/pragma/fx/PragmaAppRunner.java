package de.ruu.app.pragma.fx;

import de.ruu.lib.fx.comp.FXCAppRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PragmaAppRunner extends FXCAppRunner
{
    private static final Logger log = LoggerFactory.getLogger(PragmaAppRunner.class);

    public static void main(String[] args)
    {
        log.debug("starting {}", PragmaAppRunner.class.getName());
        FXCAppRunner.configureModuleAccessForCDI();
        PragmaAppRunner.class.getModule().addReads(PragmaAppRunner.class.getClassLoader().getUnnamedModule());
        FXCAppRunner.run(PragmaApp.class, args);
        log.debug("finished {}", PragmaAppRunner.class.getName());
    }
}