/*
 * Copyright 2004 - 2008 Christian Sprajc. All rights reserved.
 *
 * This file is part of PowerFolder.
 *
 * PowerFolder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 *
 * PowerFolder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PowerFolder. If not, see <http://www.gnu.org/licenses/>.
 *
 * $Id: ProblemsTab.java 5457 2008-10-17 14:25:41Z harry $
 */
package de.dal33t.powerfolder.ui.information.folder.problems;

import de.dal33t.powerfolder.ui.PFUIComponent;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.ui.util.UIUtil;
import de.dal33t.powerfolder.ui.util.Help;
import de.dal33t.powerfolder.util.BrowserLauncher;
import de.dal33t.powerfolder.util.StringUtils;
import de.dal33t.powerfolder.ui.action.BaseAction;
import de.dal33t.powerfolder.disk.problem.Problem;
import de.dal33t.powerfolder.disk.problem.ResolvableProblem;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.light.FolderInfo;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.builder.ButtonBarBuilder;

import java.util.List;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

public class ProblemsTab extends PFUIComponent {

    private JPanel uiComponent;
    private JScrollPane scrollPane;

    private MyOpenProblemAction openProblemAction;
    private MyClearProblemAction clearProblemAction;
    private MyResolveProblemAction resolveProblemAction;

    private FolderInfo folderInfo;
    private ProblemsTable problemsTable;
    private ProblemsTableModel problemsTableModel;
    private Problem selectedProblem;

    public ProblemsTab(Controller controller) {
        super(controller);
        problemsTableModel = new ProblemsTableModel(controller);
        problemsTable = new ProblemsTable(problemsTableModel, controller);
        problemsTable.getSelectionModel().setSelectionMode(
            ListSelectionModel.SINGLE_SELECTION);
        problemsTable.getSelectionModel().addListSelectionListener(
            new MySelectionListener());
    }

    /**
     * Gets the ui component
     * 
     * @return
     */
    public JPanel getUIComponent() {
        if (uiComponent == null) {
            initialize();
            buildUIComponent();
            enableOnSelection();
        }
        return uiComponent;
    }

    private void initialize() {
        openProblemAction = new MyOpenProblemAction(getController());
        clearProblemAction = new MyClearProblemAction(getController());
        resolveProblemAction = new MyResolveProblemAction(getController());

        scrollPane = new JScrollPane(problemsTable);

        // Whitestrip
        UIUtil.whiteStripTable(problemsTable);
        UIUtil.removeBorder(scrollPane);
        UIUtil.setZeroHeight(scrollPane);
    }

    /**
     * Bulds the ui component.
     */
    private void buildUIComponent() {
        FormLayout layout = new FormLayout("3dlu, fill:pref:grow, 3dlu",
            "3dlu, pref, 3dlu, pref , 3dlu, fill:0:grow, 3dlu");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();
        builder.add(createToolBar(), cc.xy(2, 2));
        builder.addSeparator(null, cc.xyw(1, 4, 3));
        builder.add(scrollPane, cc.xy(2, 6));

        uiComponent = builder.getPanel();
    }

    private Component createToolBar() {
        ButtonBarBuilder bar = ButtonBarBuilder.createLeftToRightBuilder();
        JButton openBtn = new JButton(openProblemAction);
        openBtn.setIcon(null);
        bar.addGridded(openBtn);
        bar.addRelatedGap();
        JButton clearBtn = new JButton(clearProblemAction);
        clearBtn.setIcon(null);
        bar.addGridded(clearBtn);
        bar.addRelatedGap();
        JButton resolveBtn = new JButton(resolveProblemAction);
        resolveBtn.setIcon(null);
        bar.addGridded(resolveBtn);
        return bar.getPanel();

    }

    public void setFolderInfo(FolderInfo folderInfo) {
        this.folderInfo = folderInfo;
        selectedProblem = null;
        if (problemsTableModel.getRowCount() > 0) {
            problemsTable.getSelectionModel().setSelectionInterval(0, 0);
        } else {
            problemsTable.getSelectionModel().removeIndexInterval(0,
                problemsTableModel.getRowCount());
        }
    }

    /**
     * Display problems.
     * 
     * @param problemList
     */
    public void updateProblems(List<Problem> problemList) {
        problemsTableModel.updateProblems(problemList);
        enableOnSelection();
    }

    /**
     * Enable the invite action on the table selection.
     */
    private void enableOnSelection() {
        getUIComponent();
        int selectedRow = problemsTable.getSelectedRow();
        if (selectedRow >= 0) {
            selectedProblem = (Problem) problemsTableModel.getValueAt(
                problemsTable.getSelectedRow(), 0);
            openProblemAction.setEnabled(true);
            resolveProblemAction
                .setEnabled(selectedProblem instanceof ResolvableProblem);
            logFiner("Selected row: " + problemsTable.getSelectedRow()
                + ". Problem: " + selectedProblem);
        } else {
            selectedProblem = null;
            openProblemAction.setEnabled(false);
            resolveProblemAction.setEnabled(false);
        }
    }

    // /////////////////
    // Inner Classes //
    // /////////////////

    private class MyOpenProblemAction extends BaseAction {

        private MyOpenProblemAction(Controller controller) {
            super("action_open_problem", controller);
        }

        public void actionPerformed(ActionEvent e) {
            int selectedRow = problemsTable.getSelectedRow();
            Problem problem = (Problem) problemsTableModel.getValueAt(
                selectedRow, 0);
            String wikiArticleURL = Help.getWikiArticleURL(getController(),
                problem.getWikiLinkKey());
            if (StringUtils.isNotBlank(wikiArticleURL)) {
                try {
                    BrowserLauncher.openURL(wikiArticleURL);
                } catch (IOException e1) {
                    logSevere("IOException", e1);
                }
            }
        }
    }

    private class MyClearProblemAction extends BaseAction {
        MyClearProblemAction(Controller controller) {
            super("action_clear_problem", controller);
        }

        public void actionPerformed(ActionEvent e) {
            Folder folder = getController().getFolderRepository().getFolder(
                folderInfo);
            if (folder != null) {
                if (selectedProblem == null) {
                    folder.removeAllProblems();
                } else {
                    folder.removeProblem(selectedProblem);
                }
            }
        }
    }

    private class MyResolveProblemAction extends BaseAction {
        MyResolveProblemAction(Controller controller) {
            super("action_resolve_problem", controller);
        }

        public void actionPerformed(ActionEvent e) {
            if (selectedProblem != null
                && selectedProblem instanceof ResolvableProblem)
            {
                ResolvableProblem resolvableProblem = (ResolvableProblem) selectedProblem;
                SwingUtilities.invokeLater(resolvableProblem
                    .resolution(getController()));
            } else {
                logSevere("Tried to resolve a non-resolvable problem "
                    + (selectedProblem == null ? null : selectedProblem
                        .getClass().getName()));
            }
        }
    }

    /**
     * Class to detect table selection changes.
     */
    private class MySelectionListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent e) {
            enableOnSelection();
        }
    }
}
