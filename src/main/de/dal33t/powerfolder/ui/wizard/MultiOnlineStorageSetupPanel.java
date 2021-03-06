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
 * $Id$
 */
package de.dal33t.powerfolder.ui.wizard;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import jwf.WizardPanel;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Feature;
import de.dal33t.powerfolder.PreferencesEntry;
import de.dal33t.powerfolder.clientserver.ServerClient;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.ui.action.BaseAction;
import de.dal33t.powerfolder.ui.dialog.DialogFactory;
import de.dal33t.powerfolder.ui.dialog.GenericDialogType;
import de.dal33t.powerfolder.ui.panel.SyncProfileSelectorPanel;
import de.dal33t.powerfolder.ui.util.Icons;
import de.dal33t.powerfolder.ui.widget.ActionLabel;
import de.dal33t.powerfolder.ui.widget.ActivityVisualizationWorker;
import de.dal33t.powerfolder.ui.widget.JButtonMini;
import de.dal33t.powerfolder.util.FileUtils;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.UserDirectories;
import de.dal33t.powerfolder.util.UserDirectory;
import de.dal33t.powerfolder.util.WebDAV;
import de.dal33t.powerfolder.util.os.OSUtil;

/**
 * Class to do sync profile configuration for OS joins.
 * 
 * @author <a href="mailto:harry@powerfolder.com">Harry Glasgow</a>
 * @version $Revision: 1.11 $
 */
public class MultiOnlineStorageSetupPanel extends PFWizardPanel {

    private Map<FolderInfo, SyncProfile> folderProfileMap;
    private Map<FolderInfo, File> folderLocalBaseMap;
    private JComboBox folderInfoCombo;
    private JTextField folderInfoField;
    private DefaultComboBoxModel folderInfoComboModel;
    private SyncProfileSelectorPanel syncProfileSelectorPanel;
    private JCheckBox manualSyncCB;
    private boolean changingSelection;

    private JTextField localFolderField;
    private JButton localFolderButton;

    private ActionLabel mountAsWebDavLabel;
    private ServerClient serverClient;
    private Date lastFetch;
    private String webDAVURL;

    /**
     * Constuctor
     * 
     * @param controller
     */
    public MultiOnlineStorageSetupPanel(Controller controller) {
        super(controller);
        serverClient = controller.getOSClient();
    }

    public boolean hasNext() {
        return true;
    }

    public WizardPanel next() {

        List<FolderCreateItem> folderCreateItems =
                new ArrayList<FolderCreateItem>();

        for (Entry<FolderInfo, SyncProfile> entry : folderProfileMap.entrySet()) {
            SyncProfile sp;
            if (PreferencesEntry.EXPERT_MODE.getValueBoolean(getController())) {
                sp = entry.getValue();
            } else {
                // Non-expert mode - choose between manual and default.
                if (manualSyncCB.isSelected()) {
                    sp = SyncProfile.MANUAL_SYNCHRONIZATION;
                } else {
                    sp = SyncProfile.AUTOMATIC_SYNCHRONIZATION;
                }
            }

            File localBase = folderLocalBaseMap.get(entry.getKey());
            FolderCreateItem fci = new FolderCreateItem(localBase);
            fci.setSyncProfile(sp);
            fci.setFolderInfo(entry.getKey());
            folderCreateItems.add(fci);
        }

        getWizardContext().setAttribute(
            WizardContextAttributes.FOLDER_CREATE_ITEMS, folderCreateItems);

        return new FolderCreatePanel(getController());
    }

    protected JPanel buildContent() {
        FormLayout layout = new FormLayout(
            "right:pref, 3dlu, 140dlu, 3dlu, pref, pref:grow",
            "pref, 6dlu, pref, 6dlu, pref, 30dlu, pref");

        PanelBuilder builder = new PanelBuilder(layout);
        builder.setBorder(createFewContentBorder());
        CellConstraints cc = new CellConstraints();

        builder.addLabel(Translation.getTranslation("general.folder"),
            cc.xy(1, 1));

        // folderInfoCombo & folderInfoField share the same slot.
        builder.add(folderInfoCombo, cc.xy(3, 1));
        builder.add(folderInfoField, cc.xy(3, 1));

        builder.add(new JLabel(Translation.getTranslation(
                "wizard.multi_online_storage_setup.local_folder_location")),
                cc.xy(1, 3));
        builder.add(localFolderField, cc.xy(3, 3));
        builder.add(localFolderButton, cc.xy(5, 3));

        // manualSyncCB is disabled for Luna (6.0) - #2726.
        if (PreferencesEntry.EXPERT_MODE.getValueBoolean(getController())) {
            builder
                .add(
                    new JLabel(Translation
                        .getTranslation("general.transfer_mode")), cc.xy(1, 5));
            JPanel p = (JPanel) syncProfileSelectorPanel.getUIComponent();
            p.setOpaque(false);
            builder.add(p, cc.xyw(3, 5, 4));
        } else {
            // Create it anyway, so we do not get NPEs elsewhere.
            syncProfileSelectorPanel.getUIComponent();
            if (Feature.MANUAL_SYNC_CB.isEnabled()) {
                builder.add(manualSyncCB, cc.xyw(3, 5, 4));
            }
        }

        builder.add(mountAsWebDavLabel.getUIComponent(), cc.xy(3, 7));

        return builder.getPanel();
    }

    /**
     * Initializes all necessary components
     */
    protected void initComponents() {

        localFolderField = new JTextField();
        localFolderButton = new JButtonMini(
            Icons.getIconById(Icons.DIRECTORY),
            Translation
                .getTranslation("wizard.multi_online_storage_setup.select_directory"));
        MyActionListener myActionListener = new MyActionListener();
        localFolderButton.addActionListener(myActionListener);

        // For non-experts - just choose between auto sync and manual.
        manualSyncCB = new JCheckBox(Translation.getTranslation(
                "transfer_mode.manual_synchronization.name"));

        syncProfileSelectorPanel = new SyncProfileSelectorPanel(getController());
        syncProfileSelectorPanel
            .addModelValueChangeListener(new MyPropertyValueChangeListener());

        folderInfoComboModel = new DefaultComboBoxModel();
        folderInfoCombo = new JComboBox(folderInfoComboModel);

        folderInfoCombo.addItemListener(new MyItemListener());
        folderInfoField = new JTextField();
        folderInfoField.setEditable(false);

        mountAsWebDavLabel = new ActionLabel(getController(),
            new MyMountAsWebDavAction(getController()));
        mountAsWebDavLabel.setVisible(serverClient.supportsWebDAV()
            && OSUtil.isWindowsSystem());
    }

    /**
     * Build map of foInfo and syncProfs
     */
    @SuppressWarnings("unchecked")
    public void afterDisplay() {
        boolean showAppData = PreferencesEntry.EXPERT_MODE
            .getValueBoolean(getController());
        Map<String, UserDirectory> userDirs = UserDirectories
            .getUserDirectoriesFiltered(getController(), showAppData);

        folderProfileMap = new HashMap<FolderInfo, SyncProfile>();
        folderLocalBaseMap = new HashMap<FolderInfo, File>();
        String folderBasedirString = getController().getFolderRepository()
            .getFoldersBasedirString();

        List<FolderInfo> folderInfoList = (List<FolderInfo>) getWizardContext()
            .getAttribute(WizardContextAttributes.FOLDER_INFOS);

        // If we have just one folder info, display as text field,
        // BUT still have the combo, as this is linked to the maps.
        if (folderInfoList.size() == 1) {
            folderInfoField.setText(folderInfoList.get(0).getName());
            folderInfoCombo.setVisible(false);
        } else {
            folderInfoField.setVisible(false);
        }

        for (FolderInfo folderInfo : folderInfoList) {
            folderProfileMap.put(folderInfo,
                SyncProfile.AUTOMATIC_SYNCHRONIZATION);
            // Suggest user dir.
            File dirSuggestion;
            if (userDirs.get(folderInfo.name) == null) {
                dirSuggestion = new File(folderBasedirString,
                    FileUtils.removeInvalidFilenameChars(folderInfo.name));
            } else {
                dirSuggestion = userDirs.get(folderInfo.name).getDirectory();
            }
            folderLocalBaseMap.put(folderInfo, dirSuggestion);
            folderInfoComboModel.addElement(folderInfo.name);
        }
    }

    protected String getTitle() {
        return Translation
            .getTranslation("wizard.multi_online_storage_setup.title");
    }

    /**
     * Update name and profile fields when base selection changes.
     */
    private void folderInfoComboSelectionChange() {
        changingSelection = true;

        Object selectedItem = folderInfoCombo.getSelectedItem();
        FolderInfo selectedFolderInfo = null;
        for (FolderInfo folderInfo : folderProfileMap.keySet()) {
            if (folderInfo.name.equals(selectedItem)) {
                selectedFolderInfo = folderInfo;
                break;
            }
        }
        if (selectedFolderInfo != null) {
            localFolderField.setText(folderLocalBaseMap.get(selectedFolderInfo)
                .getAbsolutePath());
            syncProfileSelectorPanel.setSyncProfile(
                folderProfileMap.get(selectedFolderInfo), false);
        }

        changingSelection = false;
    }

    private void syncProfileSelectorPanelChange() {
        if (!changingSelection) {
            Object selectedItem = folderInfoCombo.getSelectedItem();
            FolderInfo selectedFolderInfo = null;
            for (FolderInfo folderInfo : folderProfileMap.keySet()) {
                if (folderInfo.name.equals(selectedItem)) {
                    selectedFolderInfo = folderInfo;
                    break;
                }
            }
            if (selectedFolderInfo != null) {
                folderProfileMap.put(selectedFolderInfo,
                    syncProfileSelectorPanel.getSyncProfile());
            }
        }
    }

    private synchronized void createWebDAVURL() {
        if (!serverClient.isConnected() || !serverClient.isLoggedIn()) {
            return;
        }
        // Cache 10 secs.
        if (lastFetch == null
            || lastFetch.before(new Date(
                System.currentTimeMillis() - 1000L * 10)))
        {
            FolderInfo fi = null;
            if (folderLocalBaseMap.size() == 1) {
                fi = folderLocalBaseMap.keySet().iterator().next();
            } else {
                int index = folderInfoCombo.getSelectedIndex();
                int pointer = 0;
                for (FolderInfo folderInfo : folderProfileMap.keySet()) {
                    if (pointer++ == index) {
                        fi = folderInfo;
                        break;
                    }
                }
            }
            if (fi != null) {
                webDAVURL = serverClient.getFolderService()
                    .getWebDAVURL(fi);
            }
            lastFetch = new Date();
        }
    }

    

    private void closeWizard() {
        JDialog diag = getWizardDialog();
        diag.setVisible(false);
        diag.dispose();
    }

    private JDialog getWizardDialog() {
        return (JDialog) getWizardContext().getAttribute(
            WizardContextAttributes.DIALOG_ATTRIBUTE);
    }
    
    /**
     * Create a WebDAV connection to this folder. Should be something like 'net
     * use * "https://my.powerfolder.com/webdav/afolder"
     * /User:bob@powerfolder.com pazzword'
     */
    private void createWebdavConnection() {
        ActivityVisualizationWorker worker = new ActivityVisualizationWorker(
            getController().getUIController())
        {
            protected String getTitle() {
                return Translation
                    .getTranslation("exp_folder_view.webdav_title");
            }

            protected String getWorkingText() {
                return Translation
                    .getTranslation("exp_folder_view.webdav_working_text");
            }

            public Object construct() throws Throwable {
                try {
                    createWebDAVURL();
                    return WebDAV.createConnection(serverClient, webDAVURL);
                } catch (Exception e) {
                    // Looks like the link failed, badly :-(
                    return 'N' + e.getMessage();
                }
            }

            public void finished() {

                // See what happened.
                String result = (String) get();
                if (result != null) {
                    if (result.startsWith("Y")) {
                        String[] parts = result.substring(1).split("\\s");
                        for (final String part : parts) {
                            if (part.length() == 2 && part.charAt(1) == ':') {
                                // Probably the new drive name, so open it.
                                // Probably the new drive name, so open it.
                                getController().getIOProvider().startIO(
                                    new Runnable() {
                                        public void run() {
                                            FileUtils.openFile(new File(part));
                                        }
                                    });

                                closeWizard();
                                break;
                            }
                        }
                    } else if (result.startsWith("N")) {
                        DialogFactory
                            .genericDialog(
                                getController(),
                                Translation
                                    .getTranslation("exp_folder_view.webdav_failure_title"),
                                Translation.getTranslation(
                                    "exp_folder_view.webdav_failure_text",
                                    result.substring(1)),
                                GenericDialogType.ERROR);
                    }
                }
            }
        };
        worker.start();
    }

    // ////////////////
    // Inner classes //
    // ////////////////

    private class MyItemListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            folderInfoComboSelectionChange();
        }
    }

    private class MyPropertyValueChangeListener implements
        PropertyChangeListener
    {

        public void propertyChange(PropertyChangeEvent evt) {
            syncProfileSelectorPanelChange();
        }
    }

    @SuppressWarnings("unchecked")
    private void configureLocalFolder() {
        Object selectedItem = folderInfoCombo.getSelectedItem();
        FolderInfo selectedFolderInfo = null;
        for (FolderInfo folderInfo : folderLocalBaseMap.keySet()) {
            if (folderInfo.name.equals(selectedItem)) {
                selectedFolderInfo = folderInfo;
                break;
            }
        }
        if (selectedFolderInfo != null) {
            List<File> files = DialogFactory.chooseDirectory(getController().getUIController(),
                    folderLocalBaseMap.get(selectedFolderInfo), false);
            if (!files.isEmpty()) {
                File file = files.get(0);
                List<FolderInfo> folderInfoList = (List<FolderInfo>) getWizardContext()
                        .getAttribute(WizardContextAttributes.FOLDER_INFOS);
                if (folderInfoList != null && folderInfoList.size() == 1) {
                    String folderName = folderInfoList.get(0).getName();
                    if (!file.getName().equals(folderName)) {
                        // Help the user by appending the folder name to the path.
                        file = new File(file, folderName);
                    }
                }
                localFolderField.setText(file.getAbsolutePath());
                folderLocalBaseMap.put(selectedFolderInfo, file);
            }
        }
    }

    private class MyActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (e.getSource().equals(localFolderButton)) {
                configureLocalFolder();
            }
        }
    }

    private class MyMountAsWebDavAction extends BaseAction {

        private MyMountAsWebDavAction(Controller controller) {
            super("action_webdav", controller);
        }

        public void actionPerformed(ActionEvent e) {
            createWebdavConnection();
        }

    }
}