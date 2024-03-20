package fish.payara.extras.diagnostics.collection.collectors;

import com.sun.enterprise.admin.cli.Environment;
import com.sun.enterprise.admin.cli.ProgramOptions;
import com.sun.enterprise.admin.cli.remote.RemoteCLICommand;
import fish.payara.extras.diagnostics.collection.Collector;
import fish.payara.extras.diagnostics.util.ParamConstants;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.ParameterMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;

public class HeapDumpCollector implements Collector {

    private Map<String, Object> params;

    private String target;
    private ProgramOptions programOptions;
    private Logger LOGGER = Logger.getLogger(HeapDumpCollector.class.getName());
    private Environment environment;

    private String dirSuffix;

    public HeapDumpCollector(String target, ProgramOptions programOptions, Environment environment) {
        this.target = target;
        this.programOptions = programOptions;
        this.environment = environment;
    }

    public HeapDumpCollector(String target, ProgramOptions programOptions, Environment environment, String dirSuffix) {
        this.target = target;
        this.programOptions = programOptions;
        this.environment = environment;
        this.dirSuffix = dirSuffix;
    }
    @Override
    public int collect() {
        try{
            ParameterMap parameterMap = new ParameterMap();
            if (!target.equals("server")) {
                parameterMap.add("target", target);
            }

            String outputPathString = (String) params.get(ParamConstants.DIR_PARAM);
            Path outputPath = Paths.get(outputPathString, dirSuffix != null ? dirSuffix : "");
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            parameterMap.add("outputDir", outputPath.toString());
            programOptions.updateOptions(parameterMap);
            programOptions.setInteractive(false);
            LOGGER.info("Collecting Heap Dump from " + target);

            RemoteCLICommand remoteCLICommand = new RemoteCLICommand("generate-heap-dump", programOptions, environment);
            String result = remoteCLICommand.executeAndReturnOutput();
            LOGGER.info(result);
            if (result.startsWith("Warning:") && result.contains("seems to be offline; command generate-heap-dump was not replicated to that instance")) {
                LOGGER.warning(target + "is offline! Heap Dump will NOT be collected!");
            }
        } catch (CommandException e) {
            if (e.getMessage().contains("Remote server does not listen for requests on")) {
                LOGGER.warning("Server is offline! Heap Dump will not be collected!");
                return 0;
            }
            if (e.getMessage().contains("has never been started; command generate-heap-dump was not replicated to that instance")) {
                LOGGER.warning(target + " has never been started! Heap Dump will not be collected!");
                return 0;
            }

            if (e.getMessage().contains("Command generate-heap-dump not found.")) {
                LOGGER.warning("This version of Payara does not support heap dump generation.");
                return 0;
            }
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return 0;
    }

    @Override
    public Map<String, Object> getParams() {
        return params;
    }

    @Override
    public void setParams(Map<String, Object> params) {
        if (params != null) {
            this.params = params;
        }
    }
}
