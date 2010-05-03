/*
 * Copyright 2004 - 2010 Christian Sprajc. All rights reserved.
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
package de.dal33t.powerfolder.ui.dialog;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.ButtonBarFactory;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.clientserver.ServerClientListener;
import de.dal33t.powerfolder.clientserver.ServerClientEvent;
import de.dal33t.powerfolder.clientserver.ServerClient;
import de.dal33t.powerfolder.ui.action.BaseAction;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.BaseDialog;

/**
 * Panel displayed when wanting to link a synchronized directory to an online
 * folder.
 *
 * @author <a href="mailto:harry@powerfolder.com">Harry Glasgow</a>
 * @version $Revision: 4.2 $
 */
public class LinkFolderOnlineDialog extends BaseDialog {

    private ServerClientListener listener;

    private Action linkAction;
    private JButton linkButton;
    private JButton cancelButton;
    private DefaultComboBoxModel folderListModel;
    private JComboBox folderList;
    private final AtomicBoolean populated = new AtomicBoolean();

    /**
     * Contructor when used on choosen folder
     *
     * @param controller
     * @param fileName
     */
    public LinkFolderOnlineDialog(Controller controller, String fileName) {
        super(controller, true);
        listener = new MyServerClientListener();
        getController().getOSClient().addListener(listener);
    }

    /**
     * Initalizes all ui components
     */
    private void initComponents() {

        folderListModel = new DefaultComboBoxModel();
        folderList = new JComboBox(folderListModel);
        folderListModel.addElement("-- " + Translation.getTranslation(
                "dialog.link_folder_online.select_text") + " --");
        folderList.addItemListener(new MyItemListener());

        linkAction = new MyLinkAction(getController());
        linkButton = new JButton(linkAction);
        cancelButton = createCancelButton(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
        populateOnlineFolders();
    }

    public String getTitle() {
        return Translation.getTranslation("link_folder.dialog.title");
    }

    protected Icon getIcon() {
        return null;
    }

    protected JComponent getContent() {
        initComponents();

        FormLayout layout = new FormLayout(
            "pref, 3dlu, max(140dlu;pref):grow", "pref");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        int row = 1;
        builder.addLabel(Translation.getTranslation("general.synchonisation"),
            cc.xy(1, row));
        builder.add(folderList, cc.xy(3, row));
        return builder.getPanel();
    }

    protected Component getButtonBar() {
        return ButtonBarFactory.buildCenteredBar(linkButton, cancelButton);
    }

    protected JButton getDefaultButton() {
        return linkButton;
    }

    private void link() {
        close();
    }

    protected void finalize() throws Throwable {

        // Detatch listener.
        if (listener != null) {
            getController().getOSClient().removeListener(listener);
        }
        super.finalize();
    }

    private class MyLinkAction extends BaseAction {

        MyLinkAction(Controller controller) {
            super("action_link_directory", controller);
        }

        public void actionPerformed(ActionEvent e) {
            link();
        }
    }

    private void populateOnlineFolders() {
        ServerClient client = getController().getOSClient();
        // Synchronize, so multiple events do not cause flickering.
        synchronized (populated) {
            boolean popped = populated.get();
            if (client != null && client.isConnected() && client.isLoggedIn()) {
                if (!popped) {
                    folderListModel.removeAllElements();
                    folderListModel.addElement("-- " + Translation.getTranslation(
                            "dialog.link_folder_online.select_text") + " --");
                    Collection<FolderInfo> localFolders =
                            getController().getFolderRepository().getJoinedFolderInfos();
                    Collection<FolderInfo> onlineFolders =
                            client.getAccountFolders();
                    for (FolderInfo onlineFolder : onlineFolders) {
                        if (!localFolders.contains(onlineFolder)) {
                            folderListModel.addElement(onlineFolder.getName());
                        }
                    }
                    populated.set(true);
                }
            } else {
                if (popped) {
                    folderListModel.removeAllElements();
                    folderListModel.addElement("-- " + Translation.getTranslation(
                            "dialog.link_folder_online.select_text") + " --");
                    populated.set(false);
                }
            }

            enableLinkAction();
        }
    }

    private void enableLinkAction() {
        linkAction.setEnabled(folderList.getSelectedIndex() > 0);
    }

    // ////////////////
    // Inner classes //
    // ////////////////

    private class MyServerClientListener implements ServerClientListener {

        public void accountUpdated(ServerClientEvent event) {
            populateOnlineFolders();
        }

        public void login(ServerClientEvent event) {
            populateOnlineFolders();
        }

        public void serverConnected(ServerClientEvent event) {
            populateOnlineFolders();
        }

        public void serverDisconnected(ServerClientEvent event) {
            populateOnlineFolders();
        }

        public boolean fireInEventDispatchThread() {
            return true;
        }
    }

    private class MyItemListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            enableLinkAction();
        }
    }
}