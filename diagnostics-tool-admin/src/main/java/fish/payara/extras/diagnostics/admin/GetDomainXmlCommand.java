package fish.payara.extras.diagnostics.admin;

import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.FailurePolicy;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;

@Service(name = "get-domain-xml")
@PerLookup
@CommandLock(CommandLock.LockType.NONE)
@TargetType({CommandTarget.DAS, CommandTarget.STANDALONE_INSTANCE, CommandTarget.CLUSTERED_INSTANCE})
@ExecuteOn(value = {RuntimeType.INSTANCE}, ifNeverStarted = FailurePolicy.Error)
public class GetDomainXmlCommand implements AdminCommand {

    @Param(name = "target", optional = true)
    private String target;

    @Inject
    ServerEnvironment serverEnvironment;

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        ActionReport report = adminCommandContext.getActionReport();
        report.setMessage(serverEnvironment.getInstanceRoot().toString());
        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }
}
