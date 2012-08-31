/*
 * Copyright 2012 James Moger
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
 */
package org.moxie.ant;

import java.text.MessageFormat;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.util.StringUtils;

public class MainLogger extends DefaultLogger {

    /** Time of the start of the build */
    private long startTime = System.currentTimeMillis();

	public MainLogger() {
		super();
	}
	
    static void throwableMessage(StringBuilder m, Throwable error, boolean verbose) {
        while (error instanceof BuildException) { // #43398
            Throwable cause = error.getCause();
            if (cause == null) {
                break;
            }
            String msg1 = error.toString();
            String msg2 = cause.toString();
            if (msg1.endsWith(msg2)) {
                m.append(msg1.substring(0, msg1.length() - msg2.length()));
                error = cause;
            } else {
                break;
            }
        }
        if (verbose || !(error instanceof BuildException)) {
            m.append(StringUtils.getStackTrace(error));
        } else {
            m.append(error).append(lSep);
        }
    }

    /**
     * Responds to a build being started by just remembering the current time.
     *
     * @param event Ignored.
     */
    public void buildStarted(BuildEvent event) {
        startTime = System.currentTimeMillis();
    }

    /**
     * Prints whether the build succeeded or failed,
     * any errors the occurred during the build, and
     * how long the build took.
     *
     * @param event An event with any relevant extra information.
     *              Must not be <code>null</code>.
     */
    public void buildFinished(BuildEvent event) {
        Throwable error = event.getException();
        StringBuilder message = new StringBuilder();
        if (error == null) {
            message.append(StringUtils.LINE_SEP);
            message.append(getBuildSuccessfulMessage());
        } else {
            message.append(StringUtils.LINE_SEP);
            message.append(getBuildFailedMessage());
            message.append(StringUtils.LINE_SEP);
            throwableMessage(message, error, Project.MSG_VERBOSE <= msgOutputLevel);
        }
        message.append(StringUtils.LINE_SEP);
        message.append("Total time: ");
        message.append(formatTime(System.currentTimeMillis() - startTime));
        
        message.append(StringUtils.LINE_SEP);
        double mb = 1024*1024;
        double total = Runtime.getRuntime().totalMemory()/mb;
        double free = Runtime.getRuntime().freeMemory()/mb;
        double max = Runtime.getRuntime().maxMemory()/mb;
        message.append(MessageFormat.format("Used: {0,number,###,##0}MB (Free: {1,number,###,##0}MB, Heap: {2,number,###,##0}MB)", total-free, free, max));
        message.append(StringUtils.LINE_SEP);

        String msg = message.toString();
        if (error == null) {
            printMessage(msg, out, Project.MSG_VERBOSE);
        } else {
            printMessage(msg, err, Project.MSG_ERR);
        }
        log(msg);
    }
	
}
