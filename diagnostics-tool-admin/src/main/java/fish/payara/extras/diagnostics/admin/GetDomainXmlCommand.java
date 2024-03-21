package fish.payara.extras.diagnostics.admin;

import com.sun.enterprise.config.serverbeans.Server;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.*;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service(name = "get-domain-xml")
@PerLookup
@CommandLock(CommandLock.LockType.NONE)
@TargetType({CommandTarget.DAS, CommandTarget.STANDALONE_INSTANCE, CommandTarget.CLUSTERED_INSTANCE})
@ExecuteOn(value = {RuntimeType.INSTANCE}, ifNeverStarted = FailurePolicy.Error)
@RestEndpoints({
        @RestEndpoint(configBean = Server.class,
                opType = RestEndpoint.OpType.GET,
                path = "list-certificates",
                description = "List Keys and Certificates in key or trust store",
                params = {
                        @RestParam(name = "target", value = "$parent")
                }
        )
})
public class GetDomainXmlCommand implements AdminCommand {

    @Param(name = "target", optional = true)
    private String target;

    @Inject
    ServerEnvironment serverEnvironment;

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        ActionReport report = adminCommandContext.getActionReport();
        Path domainXMLPath = Paths.get(serverEnvironment.getInstanceRoot().getAbsolutePath(), "config", "domain.xml");
        File domainXml = domainXMLPath.toFile();

        if (!domainXml.exists()) {
            String result = String.format("domain.xml not found at %s", domainXMLPath);
            report.setMessage(result);
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
        }

        report.setMessage("Found at " + domainXMLPath);
        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }
}
