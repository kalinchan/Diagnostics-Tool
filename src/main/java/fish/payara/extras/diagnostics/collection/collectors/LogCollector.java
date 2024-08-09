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

import fish.payara.extras.diagnostics.util.ParamConstants;
import org.glassfish.api.logging.LogLevel;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

public class LogCollector extends FileCollector {

    private Path logPath;
    private String dirSuffix;
    private String logName;

    public LogCollector(Path logPath, String logName) {
        this.logPath = logPath;
        this.logName = logName;
    }

    public LogCollector(Path logPath, String instanceName, String logName) {
        this.logPath = logPath;
        super.setInstanceName(instanceName);
        this.logName = logName;
    }

    public LogCollector(Path logPath, String instanceName, String dirSuffix, String logName) {
        this.logPath = logPath;
        super.setInstanceName(instanceName);
        this.dirSuffix = dirSuffix;
        this.logName = logName;
    }

    @Override
    public int collect() {
        Map<String, Object> params = getParams();
        if (params == null) {
            return 0;
        }
        String outputPathString = (String) params.get(ParamConstants.DIR_PARAM);
        Path outputPath = Paths.get(outputPathString, dirSuffix != null ? dirSuffix : "");

        if (confirmPath(logPath, false) && confirmPath(outputPath, true)) {
            collectLogs(logPath, outputPath.resolve("log"), logName);
        }

        return 0;
    }

    private void collectLogs(Path sourcePath, Path destinationPath, String fileContains) {
        try {
            logger.info(String.format("Collecting %s from %s", logName, (getInstanceName() != null ? getInstanceName() : "server")));
            CopyDirectoryVisitor copyDirectoryVisitor = new CopyDirectoryVisitor(destinationPath, fileContains);
            copyDirectoryVisitor.setInstanceName(getInstanceName());
            Files.walkFileTree(sourcePath, copyDirectoryVisitor);
        } catch (IOException io) {
            logger.log(LogLevel.SEVERE, "Could not copy directory " + sourcePath + " to path " + destinationPath.toString());
            io.printStackTrace();
        }
    }

    private class CopyDirectoryVisitor extends SimpleFileVisitor<Path> {

        private final Path destination;
        private Path path = null;
        private final String fileContains;
        private String instanceName;

        public CopyDirectoryVisitor(Path destination, String fileContains) {
            this.destination = destination;
            this.fileContains = fileContains;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (path == null) {
                this.path = dir;
            }

            if (!Files.exists(destination.resolve(destination))) {
                Files.createDirectories(destination.resolve(destination));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

            Path relativePath = path.relativize(file);
            if (!file.getFileName().toString().contains(fileContains)) {
                return FileVisitResult.CONTINUE;
            }

            if (instanceName != null) {
                String prefix = instanceName + "-";
                if ((prefix + relativePath).startsWith(prefix + instanceName)) {
                    prefix = "";
                }
                Files.copy(file, destination.resolve((prefix + relativePath)));

            } else {
                Files.copy(file, destination.resolve(relativePath));
            }

            return FileVisitResult.CONTINUE;
        }

        public void setInstanceName(String instanceName) {
            this.instanceName = instanceName;
        }
    }
}
