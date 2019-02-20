/*
 * Copyright (c) 2019. Kin-Hong Wong. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==============================================================================
 */

package com.easymobo.openlabeler.tensorflow;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ContainerLogs
{
    private DockerClient dockerClient;
    private String containerId;
    private int lastLogTime;

    private static final  Logger LOG = Logger.getLogger(ContainerLogs.class.getCanonicalName());

    public ContainerLogs(DockerClient dockerClient, String containerId) {
        this.dockerClient = dockerClient;
        this.containerId = containerId;
        this.lastLogTime = (int) (System.currentTimeMillis() / 1000);
    }

    public List<String> getDockerLogs() {

        final List<String> logs = new ArrayList<>();

        LogContainerCmd logContainerCmd = dockerClient.logContainerCmd(containerId);
        logContainerCmd.withStdOut(true).withStdErr(true);
        logContainerCmd.withSince( lastLogTime );  // UNIX timestamp (integer) to filter logs. Specifying a timestamp will only output log-entries since that timestamp.
        // logContainerCmd.withTail(4);  // get only the last 4 log entries

        logContainerCmd.withTimestamps(true);

        try {
            logContainerCmd.exec(new LogContainerResultCallback() {
                @Override
                public void onNext(Frame item) {
                    logs.add(item.toString());
                }
            }).awaitCompletion();
        } catch (InterruptedException e) {
            LOG.severe("Interrupted Exception!" + e.getMessage());
        }

        lastLogTime = (int) (System.currentTimeMillis() / 1000) + 5;  // assumes at least a 5 second wait between calls to getDockerLogs

        return logs;
    }
}
