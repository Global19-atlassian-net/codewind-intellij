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

package org.eclipse.codewind.intellij.core;

import com.intellij.openapi.progress.ProgressIndicator;
import org.eclipse.codewind.intellij.core.PlatformUtil.OperatingSystem;
import org.eclipse.codewind.intellij.core.ProcessHelper.ProcessResult;
import org.eclipse.codewind.intellij.core.connection.ConnectionManager;
import org.eclipse.codewind.intellij.core.connection.LocalConnection;
import org.eclipse.codewind.intellij.core.constants.CoreConstants;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class InstallUtil {

    public static final String STOP_APP_CONTAINERS_PREFSKEY = "stopAppContainers";
    public static final String STOP_APP_CONTAINERS_ALWAYS = "stopAppContainersAlways";
    public static final String STOP_APP_CONTAINERS_NEVER = "stopAppContainersNever";
    public static final String STOP_APP_CONTAINERS_PROMPT = "stopAppContainersPrompt";
    public static final String STOP_APP_CONTAINERS_DEFAULT = STOP_APP_CONTAINERS_PROMPT;

    public static final int INSTALL_TIMEOUT_DEFAULT = 300;
    public static final int UNINSTALL_TIMEOUT_DEFAULT = 60;
    public static final int START_TIMEOUT_DEFAULT = 60;
    public static final int STOP_TIMEOUT_DEFAULT = 300;

    private static final Map<OperatingSystem, String> installMap = new HashMap<OperatingSystem, String>();
    private static final Map<OperatingSystem, String> appsodyMap = new HashMap<OperatingSystem, String>();

    static {
        installMap.put(OperatingSystem.LINUX, "cwctl/linux/cwctl");
        installMap.put(OperatingSystem.MAC, "cwctl/darwin/cwctl");
        installMap.put(OperatingSystem.WINDOWS, "cwctl/windows/cwctl.exe");
    }

    static {
        appsodyMap.put(OperatingSystem.LINUX, "cwctl/linux/appsody");
        appsodyMap.put(OperatingSystem.MAC, "cwctl/darwin/appsody");
        appsodyMap.put(OperatingSystem.WINDOWS, "cwctl/windows/appsody.exe");
    }

    private static final InstallOperation codewindInstall = new InstallOperation("Codewind", installMap);
    private static final InstallOperation appsodyInstall = new InstallOperation("Appsody", appsodyMap);

    private static final InstallOperation[] installOperations = {codewindInstall, appsodyInstall};


    private static final String INSTALLER_DIR = ".codewind-intellij";
    private static final String INSTALL_CMD = "install";
    private static final String START_CMD = "start";
    private static final String STOP_CMD = "stop";
    private static final String STOP_ALL_CMD = "stop-all";
    private static final String STATUS_CMD = "status";
    private static final String REMOVE_CMD = "remove";
    private static final String PROJECT_CMD = "project";


    private static final String INSTALL_VERSION_PROPERTIES = "install-version.properties";
    private static final String INSTALL_VERSION_KEY = "install-version";
    private static final String INSTALL_VERSION;

    static {
        String version;
        try (InputStream stream = InstallUtil.class.getClassLoader().getResourceAsStream(INSTALL_VERSION_PROPERTIES)) {
            Properties properties = new Properties();
            properties.load(stream);
            version = properties.getProperty(INSTALL_VERSION_KEY);
        } catch (Exception e) {
            Logger.logWarning("Reading version from \"" + INSTALL_VERSION_PROPERTIES + " file failed, defaulting to \"latest\": ", e);
            version = CoreConstants.VERSION_LATEST;
        }
        INSTALL_VERSION = version;
    }

    private static final String TAG_OPTION = "-t";
    private static final String JSON_OPTION = "-j";
    private static final String URL_OPTION = "--url";

    public static final String STATUS_KEY = "status";
    public static final String URL_KEY = "url";

    public static InstallStatus getInstallStatus() throws IOException, JSONException, TimeoutException {
        ProcessResult result = statusCodewind();
        if (result.getExitValue() != 0) {
            String error = result.getError();
            if (error == null || error.isEmpty()) {
                error = result.getOutput();
            }
            String msg = "Installer status command failed with rc: " + result.getExitValue() + " and error: " + error;  //$NON-NLS-1$ //$NON-NLS-2$
            Logger.logWarning(msg);
            throw new IOException(msg);
        }
        JSONObject status = new JSONObject(result.getOutput());
        return new InstallStatus(status);
    }

    public static ProcessResult startCodewind(String version, ProgressIndicator indicator) throws IOException, TimeoutException, JSONException {
        indicator.setIndeterminate(true);
        ProcessResult result = runInstallerProcess(START_CMD, TAG_OPTION, version);
        ConnectionManager.getManager().getLocalConnection().connect();
        return result;
    }

    public static ProcessResult stopCodewind(ProgressIndicator indicator) throws IOException, TimeoutException {
        indicator.setIndeterminate(true);

        // Close the local connection, then yield to give it the chance to close.
        // If there's an exception, log it and continue to stop Codewind
        try {
            ConnectionManager.getManager().getLocalConnection().close();
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                // ignore
            }
        } catch (Exception e) {
            Logger.logWarning("Error closing socket", e);
        }

        return runInstallerProcess(STOP_ALL_CMD);
    }

    private static ProcessResult runInstallerProcess(String cmd, String... options) throws IOException, TimeoutException {
        Process process = null;
        try {
            LocalConnection.InstallerStatus status;
            switch (cmd) {
                case START_CMD:
                    status = LocalConnection.InstallerStatus.STARTING;
                    break;
                case STOP_ALL_CMD:
                    status = LocalConnection.InstallerStatus.STOPPING;
                    break;
                default:
                    throw new AssertionError("Unrecognized cwctl command: " + cmd);
            }
            ConnectionManager.getManager().getLocalConnection().setInstallerStatus(status);
            process = runInstaller(cmd, options);
            return ProcessHelper.waitForProcess(process, 500, 120);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
            ConnectionManager.getManager().getLocalConnection().refreshInstallStatus();
            ConnectionManager.getManager().getLocalConnection().setInstallerStatus(null);
        }
    }

    private static ProcessResult statusCodewind() throws IOException, TimeoutException {
        Process process = null;
        try {
            process = runInstaller(STATUS_CMD, JSON_OPTION);
            ProcessResult result = ProcessHelper.waitForProcess(process, 500, 120);
            return result;
        } catch (Throwable t) {
            t.printStackTrace();
            if (t instanceof RuntimeException)
                throw (RuntimeException) t;
            if (t instanceof IOException)
                throw (IOException) t;
            if (t instanceof TimeoutException)
                throw (TimeoutException) t;
            throw new RuntimeException(t);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    public static Process runInstaller(String cmd, String... options) throws IOException {
        // Install prerequistes
        int len = installOperations.length;
        for (int i = 0; i < len; i++) {
            if (installOperations[i] != null)
                installOperations[i].setInstallPath(getInstallerExecutable(installOperations[i]));
        }

        List<String> cmdList = new ArrayList<String>();
        cmdList.add(codewindInstall.getInstallPath());
        cmdList.add(cmd);
        if (options != null) {
            for (String option : options) {
                cmdList.add(option);
            }
        }
        String[] command = cmdList.toArray(new String[cmdList.size()]);
        ProcessBuilder builder = new ProcessBuilder(command);
        if (PlatformUtil.getOS() == PlatformUtil.OperatingSystem.MAC) {
            String pathVar = System.getenv("PATH");
            pathVar = "/usr/local/bin:" + pathVar;
            Map<String, String> env = builder.environment();
            env.put("PATH", pathVar);
        }
        return builder.start();
    }

    public static String getInstallerExecutable(InstallOperation operation) throws IOException {
        String installPath = operation.getInstallPath();
        if (installPath != null && (new File(installPath).exists())) {
            return installPath;
        }

        // Get the current platform and choose the correct executable path
        OperatingSystem os = PlatformUtil.getOS(System.getProperty("os.name"));

        Map<OperatingSystem, String> osPathMap = operation.getOSPathMap();
        if (osPathMap == null) {
            String msg = "Failed to get the list of operating specific paths for installing the executable " + operation.getInstallName();
            Logger.logWarning(msg);
            throw new IOException(msg);
        }

        String relPath = osPathMap.get(os);
        if (relPath == null) {
            String msg = "Failed to get the relative path for the install executable " + operation.getInstallName();
            Logger.logWarning(msg);
            throw new IOException(msg);
        }

        // Get the executable path
        String installerDir = getInstallerDir();
        String execName = relPath.substring(relPath.lastIndexOf('/') + 1);
        String execPath = installerDir + File.separator + execName;

        // Make the installer directory
        if (!FileUtil.makeDir(installerDir)) {
            String msg = "Failed to make the directory for the installer utility: " + installerDir;
            Logger.logWarning(msg);
            throw new IOException(msg);
        }

        // Copy the executable over
        try (InputStream stream = InstallUtil.class.getClassLoader().getResourceAsStream(relPath)) {
            if (stream == null) {
                throw new FileNotFoundException(relPath);
            }
            FileUtil.copyFile(stream, execPath);
            if (PlatformUtil.getOS() != PlatformUtil.OperatingSystem.WINDOWS) {
                Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxr-xr-x");
                File file = new File(execPath);
                Files.setPosixFilePermissions(file.toPath(), permissions);
            }
            return execPath;
        }
    }

    private static String getInstallerDir() {
        Path path = Paths.get(System.getProperty("user.home"), INSTALLER_DIR);
        return path.toString();
    }

    public static String getVersion() {
        return INSTALL_VERSION;
    }
}
