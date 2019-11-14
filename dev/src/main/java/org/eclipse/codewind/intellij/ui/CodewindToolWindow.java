/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.codewind.intellij.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.eclipse.codewind.intellij.core.CodewindApplication;
import org.eclipse.codewind.intellij.core.CoreUtil;
import org.eclipse.codewind.intellij.core.cli.InstallStatus;
import org.eclipse.codewind.intellij.core.connection.ConnectionManager;
import org.eclipse.codewind.intellij.core.connection.LocalConnection;
import org.eclipse.codewind.intellij.core.connection.RemoteConnection;
import org.eclipse.codewind.intellij.ui.actions.OpenIdeaProjectAction;
import org.eclipse.codewind.intellij.ui.actions.StartCodewindAction;
import org.eclipse.codewind.intellij.ui.actions.StopCodewindAction;
import org.eclipse.codewind.intellij.ui.tree.CodewindTreeModel;
import org.eclipse.codewind.intellij.ui.tree.CodewindTreeNodeCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

public class CodewindToolWindow extends JBPanel<CodewindToolWindow> {

    private CodewindTreeModel treeModel;
    private Tree tree;

    // TODO remove this
    private final AnAction debugAction;

    private final AnAction startCodewindAction;
    private final AnAction stopCodewindAction;

    private final AnAction openIdeaProjectAction;

    public CodewindToolWindow() {
        treeModel = new CodewindTreeModel();
        tree = new Tree(treeModel);
        tree.setCellRenderer(new CodewindTreeNodeCellRenderer());

        startCodewindAction = new StartCodewindAction(this::expandLocalTree);
        stopCodewindAction = new StopCodewindAction(this::expandLocalTree);

        openIdeaProjectAction = new OpenIdeaProjectAction();

        debugAction = new AnAction("* debug *") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                System.out.println("*** in debugAction");
                String[] ids = ActionManager.getInstance().getActionIds("");
                Arrays.stream(ids).sorted().forEach(System.out::println);
            }
        };

        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2)
                    handleDoubleClick(e);
            }
        });
        tree.addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(Component component, int x, int y) {
                handlePopup(component, x, y);
            }
        });

        this.setLayout(new BorderLayout());
        this.add(new JBScrollPane(tree), BorderLayout.CENTER);
    }

    public void init() {
        CoreUtil.runAsync(() -> {
            ConnectionManager manager = ConnectionManager.getManager();
            CoreUtil.setUpdateHandler(treeModel);
            treeModel.setRoot(manager);
            treeModel.updateAll();
        });
    }

    public void expandLocalTree() {
        // Expand the tree for the local connection
        Object root = treeModel.getRoot();
        Object child = treeModel.getChild(root, 0);
        if (child == null) {
            tree.expandPath(new TreePath(root));
        } else {
            tree.expandPath(new TreePath(new Object[]{root, child}));
        }
    }

    private void handleDoubleClick(MouseEvent e) {
        TreePath treePath = tree.getSelectionPath();
        if (treePath == null)
            return;
        Object node = treePath.getLastPathComponent();
        if (node instanceof LocalConnection) {
            InstallStatus status = ((LocalConnection) node).getInstallStatus();
            if (status.isInstalled() && !status.isStarted()) {
                AnActionEvent actionEvent = AnActionEvent.createFromInputEvent(e, ActionPlaces.POPUP,
                        null, DataContext.EMPTY_CONTEXT, true, false);
                startCodewindAction.actionPerformed(actionEvent);
            }
        }
    }

    private void handlePopup(Component component, int x, int y) {
        TreePath treePath = tree.getSelectionPath();
        if (treePath == null) {
            return;
        }
        Object node = treePath.getLastPathComponent();
        if (node instanceof LocalConnection) {
            LocalConnection connection = (LocalConnection) node;
            handleLocalConnectionPopup((LocalConnection) node, component, x, y);
            return;
        }
        if (node instanceof RemoteConnection) {
            handleRemoteConnectionPopup((RemoteConnection) node, component, x, y);
            return;
        }
        if (node instanceof CodewindApplication) {
            handleApplicationPopup((CodewindApplication) node, component, x, y);
            return;
        }
    }

    private void handleLocalConnectionPopup(LocalConnection connection, Component component, int x, int y) {
        DefaultActionGroup actions = new DefaultActionGroup("CodewindGroup", true);

        InstallStatus status = connection.getInstallStatus();
        if (status.isStarted()) {
            actions.add(stopCodewindAction);
        } else if (status.isInstalled()) {
            actions.add(startCodewindAction);
        }

        // TODO remove this
        // actions.add(debugAction);

        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("CodewindTree", actions);
        popupMenu.getComponent().show(component, x, y);
    }

    private void handleRemoteConnectionPopup(RemoteConnection connection, Component component, int x, int y) {
        // TODO implement this
    }

    private void handleApplicationPopup(CodewindApplication application, Component component, int x, int y) {
        DefaultActionGroup actions = new DefaultActionGroup("CodewindApplicationGroup", true);
        actions.add(openIdeaProjectAction);
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("CodewindTree", actions);
        popupMenu.getComponent().show(component, x, y);
    }
}
