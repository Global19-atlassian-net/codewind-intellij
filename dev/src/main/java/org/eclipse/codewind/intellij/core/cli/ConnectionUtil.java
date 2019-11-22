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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.intellij.openapi.progress.ProgressIndicator;
import org.eclipse.codewind.intellij.core.Logger;
import org.eclipse.codewind.intellij.core.ProcessHelper;
import org.eclipse.codewind.intellij.core.ProcessHelper.ProcessResult;
import org.json.JSONException;
import org.json.JSONObject;

public class ConnectionUtil {
	

	private static final String CONNECTIONS_CMD = "connections";
	private static final String LIST_OPTION = "list";
	private static final String ADD_OPTION = "add";
	private static final String REMOVE_OPTION = "remove";
	
	private static final String LABEL_OPTION = "--label";
	private static final String URL_OPTION = "--url";
	private static final String USERNAME_OPTION = "--username";
	
	private static final String STATUS_KEY = "status";
	private static final String STATUS_MSG_KEY = "status_message";
	private static final String ID_KEY = "id";
	
	private static final String ERROR_KEY = "error";
	private static final String ERROR_DESCRIPTION_KEY = "error_description";
	
	private static final String STATUS_OK_VALUE = "OK";
	
	public static List<ConnectionInfo> listConnections(ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		ProcessResult result = runConnectionCmd(new String[] {CONNECTIONS_CMD, LIST_OPTION}, null, null, monitor);
		JSONObject resultJson = new JSONObject(result.getOutput());
		return ConnectionInfo.getInfos(resultJson);
	}
	
	public static String addConnection(String name, String url, String username, ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		ProcessResult result = runConnectionCmd(new String[] {CONNECTIONS_CMD, ADD_OPTION}, new String[] {LABEL_OPTION, name, URL_OPTION, url, USERNAME_OPTION, username}, null, monitor);
			JSONObject resultJson = new JSONObject(result.getOutput());
			if (!resultJson.has(STATUS_KEY) || !STATUS_OK_VALUE.equals(resultJson.getString(STATUS_KEY))) {
				String errorMsg = getErrorMsg(resultJson);
				String msg = "Connection add failed for: " + name; //$NON-NLS-1$
				if (errorMsg != null) {
					msg = msg + " with output: " + errorMsg; //$NON-NLS-1$
				}
				Logger.logWarning(msg);
				throw new IOException(msg);
			}
			return resultJson.getString(ID_KEY);
	}
	
	public static void removeConnection(String name, String conid, ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		ProcessResult result = runConnectionCmd(new String[] {CONNECTIONS_CMD, REMOVE_OPTION}, new String[] {CLIUtil.CON_ID_OPTION, conid}, null, monitor);
		JSONObject resultJson = new JSONObject(result.getOutput());
		if (!resultJson.has(STATUS_KEY) || !STATUS_OK_VALUE.equals(resultJson.getString(STATUS_KEY))) {
			String errorMsg = getErrorMsg(resultJson);
			String msg = "Connection remove failed for: " + name; //$NON-NLS-1$
			if (errorMsg != null) {
				msg = msg + " with output: " + errorMsg; //$NON-NLS-1$
			}
			Logger.logWarning(msg);
			throw new IOException(msg);
		}
	}
	
	private static ProcessResult runConnectionCmd(String[] command, String[] options, String[] args, ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		monitor.setIndeterminate(true);
		Process process = null;
		try {
			process = CLIUtil.runCWCTL(new String[] {CLIUtil.INSECURE_OPTION, CLIUtil.JSON_OPTION}, command, options, args);
			ProcessResult result = ProcessHelper.waitForProcess(process, 500, 60);
			if (result.getExitValue() != 0) {
				Logger.logWarning("The " + Arrays.toString(command) + " command with options " + Arrays.toString(options) + " failed with rc: " + result.getExitValue() + " and error: " + result.getErrorMsg()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				throw new IOException(result.getErrorMsg());
			}
			if (result.getOutput() == null || result.getOutput().trim().isEmpty()) {
				// This should not happen
				Logger.logWarning("The " + Arrays.toString(command) + " command had 0 return code but the output is empty"); //$NON-NLS-1$
				throw new IOException("The output from " + Arrays.toString(command) + " is empty."); //$NON-NLS-1$
			}
			return result;
		} finally {
			if (process != null && process.isAlive()) {
				process.destroy();
			}
		}
	}
	
	private static String getErrorMsg(JSONObject resultJson) throws JSONException {
		String errorMsg = null;
		if (resultJson.has(ERROR_DESCRIPTION_KEY)) {
			errorMsg = resultJson.getString(ERROR_DESCRIPTION_KEY);
		} else if (resultJson.has(STATUS_MSG_KEY)) {
			errorMsg = resultJson.getString(STATUS_MSG_KEY);
		}
		return errorMsg;
	}
}
