/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2023-2024 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package fish.payara.extras.diagnostics.asadmin;

import com.sun.enterprise.config.serverbeans.Cluster;
import com.sun.enterprise.config.serverbeans.Clusters;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.Server;
import fish.payara.enterprise.config.serverbeans.DeploymentGroup;
import fish.payara.enterprise.config.serverbeans.DeploymentGroups;
import fish.payara.extras.diagnostics.collection.CollectorService;
import fish.payara.extras.diagnostics.util.ParamConstants;
import fish.payara.extras.diagnostics.util.PropertiesFile;
import fish.payara.extras.diagnostics.util.TargetType;
import org.glassfish.api.Param;
import org.glassfish.api.admin.CommandException;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceLocator;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigParser;

import javax.inject.Inject;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service(name = "collect-diagnostics")
@PerLookup
public class CollectAsadmin extends BaseAsadmin {

    Logger LOGGER = Logger.getLogger(this.getClass().getName());

    @Param(name = ParamConstants.SERVER_LOG_PARAM, optional = true, defaultValue = "true")
    private boolean collectServerLog;

    @Param(name = ParamConstants.DOMAIN_XML_PARAM, optional = true, defaultValue = "true")
    private boolean collectDomainXml;

    @Param(name = ParamConstants.THREAD_DUMP_PARAM, optional = true, defaultValue = "true")
    private boolean collectThreadDump;

    @Param(name = ParamConstants.JVM_REPORT_PARAM, optional = true, defaultValue = "true")
    private boolean collectJvmReport;

    @Param(name = ParamConstants.DOMAIN_NAME_PARAM, optional = true, primary = true, defaultValue = "domain1")
    private String domainName;

    @Param(name = ParamConstants.TARGET_PARAM, optional = true, defaultValue = "domain")
    private String target;

    @Param(name = ParamConstants.HEAP_DUMP_PARAM, optional = true, defaultValue = "true")
    private boolean collectHeapDump;

    private CollectorService collectorService;
    private Domain domain;

    @Inject
    ServiceLocator serviceLocator;


    /**
     * Execute asadmin command Collect.
     * <p>
     * 0 - success
     * 1 - failure
     *
     * @return int
     * @throws CommandException
     */
    @Override
    protected int executeCommand() throws CommandException {
        domain = getDomain();
        TargetType targetType = getTargetType();
        if (targetType == null) {
            LOGGER.info("Target not found!");
            return 1;
        }
        parameterMap = populateParameters(new HashMap<>());

        parameterMap = resolveDir(parameterMap);

        collectorService = new CollectorService(parameterMap, targetType, env, programOpts, target);
        PropertiesFile props = getProperties();
        props.store(DIR_PARAM, (String) parameterMap.get(DIR_PARAM));
        return collectorService.executeCollection();
    }

    @Override
    protected void validate() throws CommandException {
        setDomainName(domainName);
        super.validate();
    }

    public TargetType getTargetType() {
        if (target.equals("domain")) {
            return TargetType.DOMAIN;
        }
        if (getInstancesNames().contains(target)) {
            return TargetType.INSTANCE;
        }

        if (getDeploymentGroups().getDeploymentGroup(target) != null) {
            return TargetType.DEPLOYMENT_GROUP;
        }

        if (getClusters().getCluster(target) != null) {
            return TargetType.CLUSTER;
        }
        return null;
    }

    /**
     * Populates parameters with Parameter options into a map. Overriden method add some more additionaly properties required by the collect command.
     *
     * @param params
     * @return Map<String, Object>
     */
    private Map<String, Object> populateParameters(Map<String, Object> params) {
        //Parameter Options
        params.put(ParamConstants.SERVER_LOG_PARAM, getOption(ParamConstants.SERVER_LOG_PARAM));
        params.put(ParamConstants.DOMAIN_XML_PARAM, getOption(ParamConstants.DOMAIN_XML_PARAM));
        params.put(ParamConstants.THREAD_DUMP_PARAM, getOption(ParamConstants.THREAD_DUMP_PARAM));
        params.put(ParamConstants.JVM_REPORT_PARAM, getOption(ParamConstants.JVM_REPORT_PARAM));
        params.put(ParamConstants.HEAP_DUMP_PARAM, getOption(ParamConstants.HEAP_DUMP_PARAM));
        params.put(ParamConstants.DOMAIN_NAME, getOption(ParamConstants.DOMAIN_NAME));

        //Paths
        params.put(ParamConstants.DOMAIN_XML_FILE_PATH, getDomainXml().getAbsolutePath());
        params.put(ParamConstants.INSTANCES_DOMAIN_XML_PATH, getInstancePaths(PathType.DOMAIN));
        params.put(ParamConstants.INSTANCES_LOG_PATH, getInstancePaths(PathType.LOG));
        params.put(ParamConstants.LOGS_PATH, getDomainRootDir().getPath() + "/logs");

        //Other
        params.put(ParamConstants.INSTANCES_NAMES, getInstancesNames());
        params.put(ParamConstants.STANDALONE_INSTANCES, getStandaloneLocalInstances());
        params.put(ParamConstants.NODES, getNodes());
        params.put(ParamConstants.DEPLOYMENT_GROUPS, getDeploymentGroups().getDeploymentGroup());
        params.put(ParamConstants.CLUSTERS, getClusters().getCluster());
        params.put(ParamConstants.INSTANCE, getInstance(target));
        return params;
    }

    private Domain getDomain() {
        File domainXmlFile = Paths.get(getDomainXml().getAbsolutePath()).toFile();
        ConfigParser configParser = new ConfigParser(serviceLocator);

        try {
            configParser.logUnrecognisedElements(false);
        } catch (NoSuchMethodError noSuchMethodError) {
            LOGGER.log(Level.FINE, "Using a version of ConfigParser that does not support disabling log messages via method",
                    noSuchMethodError);
        }

        URL domainUrl;
        try {
            domainUrl = domainXmlFile.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return configParser.parse(domainUrl).getRoot().createProxy(Domain.class);
    }

    private DeploymentGroups getDeploymentGroups() {
        return domain.getDeploymentGroups();
    }

    private Clusters getClusters() {
        return domain.getClusters();
    }

    private List<Node> getNodes() {
        return domain.getNodes().getNode();
    }

    private List<Server> getInstances() {
        List<Server> instances = new ArrayList<>();
        List<Node> nodes = getNodes();
        for (Node node : nodes) {
            instances.addAll(domain.getInstancesOnNode(node.getName()));

        }
        return instances;
    }

    private List<Server> getStandaloneLocalInstances() {
        List<Server> instances = getInstances();

        for (DeploymentGroup dg : getDeploymentGroups().getDeploymentGroup()) {
            for (Server dgInstance : dg.getInstances()) {
                instances.removeIf(instance -> instance.getName().equals(dgInstance.getName()) && dgInstance.getNodeRef().equals(instance.getNodeRef()));
            }
        }

        for (Cluster cluster : getClusters().getCluster()) {
            for (Server clusterInstance : cluster.getInstances()) {
                instances.removeIf(instance -> instance.getName().equals(clusterInstance.getName()) && clusterInstance.getNodeRef().equals(instance.getNodeRef()));
            }
        }
        return instances;
    }

    public Server getInstance(String instance) {
        for (Server server : getInstances()) {
            if (server.getName().equals(instance)) {
                return server;
            }
        }
        return null;
    }


    private Map<String, Path> getNodePaths() {
        Map<String, Path> nodePaths = new HashMap<>();
        for (Node node : domain.getNodes().getNode()) {
            if (!node.getType().equals("CONFIG")) {
                continue;
            }

            if (!node.isLocal()) {
                continue;
            }

            if (node.getNodeDir() != null) {
                nodePaths.put(node.getName(), Paths.get(node.getNodeDir(), node.getName()));
                continue;
            }
            nodePaths.put(node.getName(), Paths.get(node.getInstallDir().replace("${com.sun.aas.productRoot}", System.getProperty("com.sun.aas.productRoot")), "glassfish", "nodes", node.getName()));
        }
        return nodePaths;
    }

    private Map<String, List<String>> getServersInNodes() {
        Map<String, List<String>> nodesAndServers = new HashMap<>();
        for (Server server : domain.getServers().getServer()) {
            if (server.getConfig().isDas()) {
                continue;
            }

            if (!nodesAndServers.containsKey(server.getNodeRef())) {
                List<String> servers = new ArrayList<>();
                servers.add(server.getName());
                nodesAndServers.put(server.getNodeRef(), servers);
                continue;
            }
            List<String> servers = nodesAndServers.get(server.getNodeRef());
            servers.add(server.getName());
            nodesAndServers.put(server.getNodeRef(), servers);
        }
        return nodesAndServers;
    }

    private String getInstancePaths(PathType pathType) {
        Map<String, Path> nodePaths = getNodePaths();
        Map<String, List<String>> nodesAndServers = getServersInNodes();
        List<Path> instanceXmlPaths = new ArrayList<>();
        for (String nodeName : nodePaths.keySet()) {
            List<String> instances = nodesAndServers.get(nodeName);
            if (instances == null) {
                continue;
            }
            if (pathType == PathType.DOMAIN) {
                instances.forEach(s -> instanceXmlPaths.add(Paths.get(String.valueOf(nodePaths.get(nodeName)), s, "config", "domain.xml")));
                continue;
            }

            if (pathType == PathType.LOG) {
                instances.forEach(s -> instanceXmlPaths.add(Paths.get(String.valueOf(nodePaths.get(nodeName)), s, "logs")));
            }
        }
        return instanceXmlPaths.toString();
    }

    private List<String> getInstancesNames() {
        List<Server> localInstances = getInstances();
        List<String> instanceNames = new ArrayList<>();
        localInstances.forEach(instance -> instanceNames.add(instance.getName()));

        return instanceNames;
    }

    enum PathType {
        DOMAIN, LOG;
    }

}
