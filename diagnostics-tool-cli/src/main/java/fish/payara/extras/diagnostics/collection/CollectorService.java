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

package fish.payara.extras.diagnostics.collection;

import com.sun.enterprise.admin.cli.Environment;
import com.sun.enterprise.admin.cli.ProgramOptions;
import com.sun.enterprise.config.serverbeans.Cluster;
import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.Server;
import fish.payara.enterprise.config.serverbeans.DeploymentGroup;
import fish.payara.extras.diagnostics.collection.collectors.DomainXmlCollector;
import fish.payara.extras.diagnostics.collection.collectors.HeapDumpCollector;
import fish.payara.extras.diagnostics.collection.collectors.JVMCollector;
import fish.payara.extras.diagnostics.collection.collectors.LogCollector;
import fish.payara.extras.diagnostics.util.JvmCollectionType;
import fish.payara.extras.diagnostics.util.ParamConstants;
import fish.payara.extras.diagnostics.util.TargetType;
import org.glassfish.api.logging.LogLevel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static fish.payara.extras.diagnostics.util.ParamConstants.*;

public class CollectorService {
    Logger logger = Logger.getLogger(this.getClass().getName());
    private TargetType targetType;
    private String target;

    private Environment environment;

    private ProgramOptions programOptions;
    private Boolean domainXml;
    private Boolean serverLog;
    private Boolean threadDump;
    private Boolean jvmReport;
    private Boolean heapDump;

    Map<String, Object> parameterMap;

    public CollectorService(Map<String, Object> params, TargetType targetType, Environment environment, ProgramOptions programOptions, String target) {
        this.targetType = targetType;
        this.parameterMap = params;
        this.target = target;
        this.environment = environment;
        this.programOptions = programOptions;
        init();
    }

    private void init() {
        domainXml = true;
        serverLog = true;
        threadDump = true;
        jvmReport = true;
        heapDump = true;

        if (parameterMap != null) {
            domainXml = parameterMap.get(DOMAIN_XML_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(DOMAIN_XML_PARAM));
            serverLog = parameterMap.get(SERVER_LOG_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(SERVER_LOG_PARAM));
            threadDump = parameterMap.get(THREAD_DUMP_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(THREAD_DUMP_PARAM));
            jvmReport = parameterMap.get(JVM_REPORT_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(JVM_REPORT_PARAM));
            heapDump = parameterMap.get(HEAP_DUMP_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(HEAP_DUMP_PARAM));
        }
    }


    /**
     * Executes collection of all specified collectors.
     * <p>
     * 0 - Success
     * 1 - Failure
     *
     * @return int
     */
    public int executeCollection() {
        List<Collector> activeCollectors = getActiveCollectors(parameterMap);

        int result = 0;
        if (!activeCollectors.isEmpty()) {
            for (Collector collector : activeCollectors) {
                collector.setParams(parameterMap);
                result = collector.collect();
                if (result != 0) {
                    return result;
                }
            }
        }

        File file = new File((String) parameterMap.get(ParamConstants.DIR_PARAM));

        try (FileOutputStream fileOutputStream = new FileOutputStream(file.getAbsolutePath() + ".zip")) {
            ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
            compressDirectory(file, file.getName(), zipOutputStream);
            zipOutputStream.close();

            cleanUpDirectory(file);
        } catch (IOException e) {
            logger.log(LogLevel.SEVERE, "Could not zip collected files.");
            return 1;
        }

        //Save output to properties, to make uplaod seamless for the user

        return result;
    }


    /**
     * Compresses the collected directory into a zip folder.
     *
     * @param file
     * @param fileName
     * @param zipOutputStream
     * @throws IOException
     */
    private void compressDirectory(File file, String fileName, ZipOutputStream zipOutputStream) throws IOException {
        if (file.isHidden()) {
            return;
        }

        if (file.isDirectory()) {

            zipOutputStream.putNextEntry(fileName.endsWith(File.separator) ? new ZipEntry(fileName) : new ZipEntry(fileName + File.separator));

            File[] children = file.listFiles();
            for (File childFile : children) {
                compressDirectory(childFile, fileName + File.separator + childFile.getName(), zipOutputStream);
            }
            return;
        }

        FileInputStream fileInputStream = new FileInputStream(file);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOutputStream.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fileInputStream.read(bytes)) >= 0) {
            zipOutputStream.write(bytes, 0, length);
        }

        fileInputStream.close();
    }


    /**
     * Removes specified directory.
     *
     * @param dir
     * @throws IOException
     */
    private void cleanUpDirectory(File dir) throws IOException {
        Path path = dir.toPath();
        try (Stream<Path> walk = Files.walk(path)) {
            walk
                    .sorted(Comparator.reverseOrder())
                    .forEach(x -> deleteDirectory(x));
        }
    }


    /**
     * Utility method for directory cleanup. Removes files at specified path.
     *
     * @param path
     */
    private void deleteDirectory(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            logger.log(LogLevel.INFO, "Could not cleanup directory at {0}", path.toString());
        }
    }


    /**
     * Returns list of collectors which are enabled from user parameters.
     *
     * @param parameterMap
     * @return List&lt;Collector&gt;
     */
    public List<Collector> getActiveCollectors(Map<String, Object> parameterMap) {
        List<Collector> activeCollectors = new ArrayList<>();

        if (parameterMap == null) {
            return activeCollectors;
        }

        if (targetType == TargetType.DOMAIN) {

            if (domainXml) {
                Path domainXmlPath = Paths.get((String) parameterMap.get(DOMAIN_XML_FILE_PATH));
                activeCollectors.add(new DomainXmlCollector(domainXmlPath));
            }
            if (serverLog) {
                Path domainLogPath = Paths.get((String) parameterMap.get(LOGS_PATH));
                activeCollectors.add(new LogCollector(domainLogPath));
            }

            if (jvmReport) {
                activeCollectors.add(new JVMCollector(environment, programOptions, "server", JvmCollectionType.JVM_REPORT));
            }

            if (threadDump) {
                activeCollectors.add(new JVMCollector(environment, programOptions, "server", JvmCollectionType.THREAD_DUMP));
            }

            if (heapDump) {
                activeCollectors.add(new HeapDumpCollector("server", programOptions, environment));
            }

            addInstanceCollectors(activeCollectors, (List<Server>) parameterMap.get(STANDALONE_INSTANCES), "");

            for (DeploymentGroup deploymentGroup : (List<DeploymentGroup>) parameterMap.get(DEPLOYMENT_GROUPS)) {
                addInstanceCollectors(activeCollectors, deploymentGroup.getInstances(), deploymentGroup.getName());
            }

            for (Cluster cluster : (List<Cluster>) parameterMap.get(CLUSTERS)) {
                addInstanceCollectors(activeCollectors, cluster.getInstances(), cluster.getName());
            }

        }

        if (targetType == TargetType.INSTANCE) {
            if (parameterMap.get(INSTANCE) != null) {
                List<Server> servers = new ArrayList<>();
                servers.add((Server) parameterMap.get(INSTANCE));
                addInstanceCollectors(activeCollectors, servers, "");
            }
        }

        if (targetType == TargetType.DEPLOYMENT_GROUP) {
            for (DeploymentGroup deploymentGroup : (List<DeploymentGroup>) parameterMap.get(DEPLOYMENT_GROUPS)) {
                if (deploymentGroup.getName().equals(target)) {
                    addInstanceCollectors(activeCollectors, deploymentGroup.getInstances(), deploymentGroup.getName());
                }
            }
        }

        if (targetType == TargetType.CLUSTER) {
            for (Cluster cluster : (List<Cluster>) parameterMap.get(CLUSTERS)) {
                if (cluster.getName().equals(target)) {
                    addInstanceCollectors(activeCollectors, cluster.getInstances(), cluster.getName());
                }
            }
        }
        return activeCollectors;
    }

    private void addInstanceCollectors(List<Collector> activeCollectors, List<Server> serversList, String dirSuffix) {
        HashMap<String, Path> nodePaths = new HashMap<>();

        for (Node node : (List<Node>) parameterMap.get(NODES)) {
            if (node.getNodeDir() != null) {
                nodePaths.put(node.getName(), Paths.get(node.getNodeDir(), node.getName()));
                continue;
            }
            nodePaths.put(node.getName(), Paths.get(node.getInstallDir().replace("${com.sun.aas.productRoot}", System.getProperty("com.sun.aas.productRoot")), "glassfish", "nodes", node.getName()));
        }
        for (Server server : serversList) {
            String finalDirSuffix = Paths.get(dirSuffix, server.getName()).toString();
            if (domainXml) {
                activeCollectors.add(new DomainXmlCollector(Paths.get(nodePaths.get(server.getNodeRef()).toString(), server.getName(), "config", "domain.xml"), server.getName(), finalDirSuffix));
            }
            if (serverLog) {
                activeCollectors.add(new LogCollector(Paths.get(nodePaths.get(server.getNodeRef()).toString(), server.getName(), "logs"), server.getName(), finalDirSuffix));
            }

            if (jvmReport) {
                activeCollectors.add(new JVMCollector(environment, programOptions, server.getName(), JvmCollectionType.JVM_REPORT, finalDirSuffix));
            }

            if (threadDump) {
                activeCollectors.add(new JVMCollector(environment, programOptions, server.getName(), JvmCollectionType.THREAD_DUMP, finalDirSuffix));
            }
            if (heapDump) {
                activeCollectors.add(new HeapDumpCollector(server.getName(), programOptions, environment, finalDirSuffix));
            }
        }
    }
}
