/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *	 IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.codewind.intellij.core.cli;

import com.intellij.openapi.progress.ProgressIndicator;
import org.eclipse.codewind.intellij.core.ProcessHelper;
import org.eclipse.codewind.intellij.core.ProcessHelper.ProcessResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class ConnectionUtil {

	private static final String CONNECTIONS_CMD = "connections";
	private static final String[] LIST_CMD = new String[] {CONNECTIONS_CMD, "list"};
	private static final String[] ADD_CMD = new String[] {CONNECTIONS_CMD, "add"};
	private static final String[] REMOVE_CMD = new String[] {CONNECTIONS_CMD, "remove"};
	private static final String[] UPDATE_CMD = new String[] {CONNECTIONS_CMD, "update"};
	
	private static final String LABEL_OPTION = "--label";
	private static final String URL_OPTION = "--url";
	private static final String USERNAME_OPTION = "--username";
	
	private static final String ID_KEY = "id";
	
	public static List<ConnectionInfo> listConnections(ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		ProcessResult result = runConnectionCmd(LIST_CMD, null, null, true, monitor);
		JSONObject resultJson = new JSONObject(result.getOutput());
		return ConnectionInfo.getInfos(resultJson);
	}
	
	public static String addConnection(String name, String url, String username, ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		ProcessResult result = runConnectionCmd(ADD_CMD, new String[] {LABEL_OPTION, name, URL_OPTION, url, USERNAME_OPTION, username}, null, true, monitor);
		JSONObject resultJson = new JSONObject(result.getOutput());
		return resultJson.getString(ID_KEY);
	}
	
	public static void removeConnection(String conid, ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		runConnectionCmd(REMOVE_CMD, new String[] {CLIUtil.CON_ID_OPTION, conid}, null, false, monitor);
	}
	
	public static void updateConnection(String conid, String name, String url, String username, ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		runConnectionCmd(UPDATE_CMD, new String[] {CLIUtil.CON_ID_OPTION, conid, LABEL_OPTION, name, URL_OPTION, url, USERNAME_OPTION, username}, null, false, monitor);
	}
	
	private static ProcessResult runConnectionCmd(String[] command, String[] options, String[] args, boolean checkOutput, ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		monitor.setIndeterminate(true);
		Process process = null;
		try {
			process = CLIUtil.runCWCTL(CLIUtil.GLOBAL_JSON_INSECURE, command, options, args);
			ProcessResult result = ProcessHelper.waitForProcess(process, 500, 60);
			CLIUtil.checkResult(command, result, checkOutput);
			return result;
		} finally {
			if (process != null && process.isAlive()) {
				process.destroy();
			}
		}
	}
}
