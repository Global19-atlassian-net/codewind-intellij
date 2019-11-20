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
import org.eclipse.codewind.intellij.core.constants.CoreConstants;
import org.eclipse.codewind.intellij.core.constants.ProjectInfo;
import org.eclipse.codewind.intellij.core.cli.CLIUtil;
import org.json.JSONException;
import org.json.JSONObject;

public class ProjectUtil {
	

	private static final String PROJECT_CMD = "project";
	private static final String CREATE_OPTION = "create";
	private static final String BIND_OPTION = "bind";
	
	private static final String URL_OPTION = "--url";
	private static final String NAME_OPTION = "--name";
	private static final String LANGUAGE_OPTION = "--language";
	private static final String TYPE_OPTION = "--type";
	private static final String PATH_OPTION = "--path";

	public static void createProject(String name, String path, String url, String conid, ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		monitor.setIndeterminate(true);
		Process process = null;
		try {
			process = CLIUtil.runCWCTL(new String[] {CLIUtil.INSECURE_OPTION}, new String[] {PROJECT_CMD, CREATE_OPTION}, new String[] {URL_OPTION, url, CLIUtil.CON_ID_OPTION, conid}, new String[] {path});
			ProcessResult result = ProcessHelper.waitForProcess(process, 500, 300);
			if (result.getExitValue() != 0) {
				Logger.logWarning("Project create failed with rc: " + result.getExitValue() + " and error: " + result.getErrorMsg()); //$NON-NLS-1$ //$NON-NLS-2$
				throw new IOException(result.getErrorMsg());
			}
			if (result.getOutput() == null || result.getOutput().trim().isEmpty()) {
				// This should not happen
				Logger.logWarning("Project create had 0 return code but the output is empty"); //$NON-NLS-1$
				throw new IOException("The output from project create is empty."); //$NON-NLS-1$
			}
			JSONObject resultJson = new JSONObject(result.getOutput());
			if (!CoreConstants.VALUE_STATUS_SUCCESS.equals(resultJson.getString(CoreConstants.KEY_STATUS))) {
				String msg = "Project create failed for project: " + name + " with output: " + result.getOutput(); //$NON-NLS-1$ //$NON-NLS-2$
				Logger.logWarning(msg);
				throw new IOException(msg);
			}
		} finally {
			if (process != null && process.isAlive()) {
				process.destroy();
			}
		}
	}
	
	public static void bindProject(String name, String path, String language, String projectType, String conid, ProgressIndicator monitor) throws IOException, TimeoutException {
		monitor.setIndeterminate(true);
		Process process = null;
		try {
			String[] options = new String[] {NAME_OPTION, name, LANGUAGE_OPTION, language, TYPE_OPTION, projectType, PATH_OPTION, path, CLIUtil.CON_ID_OPTION, conid};
			process = CLIUtil.runCWCTL(new String[] {CLIUtil.INSECURE_OPTION}, new String[] {PROJECT_CMD, BIND_OPTION}, options);
			ProcessResult result = ProcessHelper.waitForProcess(process, 500, 300);
			if (result.getExitValue() != 0) {
				Logger.logWarning("Project bind failed with rc: " + result.getExitValue() + " and error: " + result.getErrorMsg()); //$NON-NLS-1$ //$NON-NLS-2$
				throw new IOException(result.getErrorMsg());
			}
		} finally {
			if (process != null && process.isAlive()) {
				process.destroy();
			}
		}
	}
	
	public static ProjectInfo validateProject(String name, String path, String hint, String conid, ProgressIndicator monitor) throws IOException, JSONException, TimeoutException {
		monitor.setIndeterminate(true);
		Process process = null;
		try {
			process = (hint == null) ? 
					CLIUtil.runCWCTL(new String[] {CLIUtil.INSECURE_OPTION}, new String[] {PROJECT_CMD, CREATE_OPTION}, new String[] {CLIUtil.CON_ID_OPTION, conid}, new String[] {path}) :
					CLIUtil.runCWCTL(new String[] {CLIUtil.INSECURE_OPTION}, new String[] {PROJECT_CMD, CREATE_OPTION}, new String[] {TYPE_OPTION, hint, CLIUtil.CON_ID_OPTION, conid}, new String[] {path});
			ProcessResult result = ProcessHelper.waitForProcess(process, 500, 300);
			if (result.getExitValue() != 0) {
				Logger.logWarning("Project validate failed with rc: " + result.getExitValue() + " and error: " + result.getErrorMsg()); //$NON-NLS-1$ //$NON-NLS-2$
				throw new IOException(result.getErrorMsg());
			}
			if (result.getOutput() == null || result.getOutput().trim().isEmpty()) {
				// This should not happen
				Logger.logWarning("Project validate had 0 return code but the output is empty"); //$NON-NLS-1$
				throw new IOException("The output from project validate is empty."); //$NON-NLS-1$
			}
		    
			JSONObject resultJson = new JSONObject(result.getOutput());
			if (CoreConstants.VALUE_STATUS_SUCCESS.equals(resultJson.getString(CoreConstants.KEY_STATUS))) {
				if (resultJson.has(CoreConstants.KEY_RESULT)) {
					JSONObject typeJson = resultJson.getJSONObject(CoreConstants.KEY_RESULT);
					String language = typeJson.getString(CoreConstants.KEY_LANGUAGE);
					String projectType = typeJson.getString(CoreConstants.KEY_PROJECT_TYPE);
					return new ProjectInfo(projectType, language);
				}
			}
			String msg = "Validation failed for project: " + name + " with output: " + result.getOutput(); //$NON-NLS-1$ //$NON-NLS-2$
			Logger.logWarning(msg);
			throw new IOException(msg);
		} finally {
			if (process != null && process.isAlive()) {
				process.destroy();
			}
		}
	}
}
