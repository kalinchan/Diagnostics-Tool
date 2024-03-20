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

package fish.payara.extras.diagnostics.collection.collectors;

import com.sun.enterprise.admin.cli.Environment;
import com.sun.enterprise.admin.cli.ProgramOptions;
import com.sun.enterprise.admin.cli.remote.RemoteCLICommand;
import fish.payara.extras.diagnostics.collection.Collector;
import fish.payara.extras.diagnostics.util.JvmCollectionType;
import fish.payara.extras.diagnostics.util.ParamConstants;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.ParameterMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;

public class JVMCollector implements Collector {

    private Map<String, Object> params;
    private final Environment environment;
    private final ProgramOptions programOptions;
    private JvmCollectionType jvmCollectionType;
    private String target;
    private String dirSuffix;
    private Logger LOGGER = Logger.getLogger(JVMCollector.class.getName());

    public JVMCollector(Environment environment, ProgramOptions programOptions, String target, JvmCollectionType jvmCollectionType) {
        this.environment = environment;
        this.programOptions = programOptions;
        this.jvmCollectionType = jvmCollectionType;
        this.target = target;
    }

    public JVMCollector(Environment environment, ProgramOptions programOptions, String target, JvmCollectionType jvmCollectionType, String dirSuffix) {
        this.environment = environment;
        this.programOptions = programOptions;
        this.jvmCollectionType = jvmCollectionType;
        this.target = target;
        this.dirSuffix = dirSuffix;
    }

    @Override
    public int collect() {
        if (collectReport(target)) {
            return 0;
        }
        return 1;
    }

    private boolean writeToFile(String text, String fileName) {
        String outputPathString = (String) params.get(ParamConstants.DIR_PARAM);
        Path outputPath = Paths.get(outputPathString, dirSuffix != null ? dirSuffix : "");
        String suffix = jvmCollectionType == JvmCollectionType.JVM_REPORT ? "-jvm-report.txt" : "-thread-dump.txt";
        byte[] textBytes = text.getBytes();
        try {
            Files.write(Paths.get(outputPath + "/" + fileName + suffix), textBytes);
        } catch (IOException e) {
            return false;
        }
        return true;
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

    private boolean collectReport(String target) {
        try {
            ParameterMap parameterMap = new ParameterMap();
            parameterMap.add("target", target);
            parameterMap.add("type", jvmCollectionType.value);
            programOptions.updateOptions(parameterMap);
            LOGGER.info("Collecting " + (jvmCollectionType == JvmCollectionType.JVM_REPORT ? "jvm report" : "thread dump") + " from " + target);

            RemoteCLICommand remoteCLICommand = new RemoteCLICommand("generate-jvm-report", programOptions, environment);
            String result = remoteCLICommand.executeAndReturnOutput();

            if (result.startsWith("Warning:") && result.contains("seems to be offline; command generate-jvm-report was not replicated to that instance")) {
                System.out.printf("%s is offline! JVM %s will NOT be collected!%n", target, jvmCollectionType.value);
                return true;
            }
            return writeToFile(result, target);
        } catch (CommandException e) {
            if (e.getMessage().contains("Remote server does not listen for requests on")) {
                System.out.printf("Server is offline! JVM %s will NOT be collected!%n", jvmCollectionType.value);
                return true;
            }

            if (e.getMessage().contains("has never been started")) {
                System.out.printf("%s has not been started! JVM %s can not be collected!%n", target, jvmCollectionType.value);

                return true;
            }
            throw new RuntimeException(e);
        }
    }
}
