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

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.ui.Icons;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.FOLDER_LOCAL_BASES;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.IdGenerator;
import de.dal33t.powerfolder.util.ui.DialogFactory;
import de.dal33t.powerfolder.util.ui.GenericDialogType;
import jwf.WizardPanel;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

/**
 * Class to do folder creations for optional specified FolderCreateItems.
 *
 * @author <a href="mailto:harry@powerfolder.com">Harry Glasgow</a>
 * @version $Revision: 1.11 $
 */
public class MultiFolderSetupPanel extends PFWizardPanel {

    private List<FolderCreateItem> folderCreateItems;
    private JComboBox localBaseCombo;
    private DefaultComboBoxModel localBaseComboModel;

    private FolderCreateItem selectedItem;

    private JTextField nameField;

    /**
     * Constuctor
     *
     * @param controller
     * @param folderName
     *            the recommended folder name.
     */
    public MultiFolderSetupPanel(Controller controller) {
        super(controller);

    }

    /**
     * Can procede if an invitation exists.
     */
    public boolean hasNext() {
        return true;
    }

    public boolean validateNext(List<String> errors) {

        // Check that all folders have names.
        for (FolderCreateItem folderCreateItem : folderCreateItems) {
            if (folderCreateItem.getFolderInfo().name == null ||
                    folderCreateItem.getFolderInfo().name.length() == 0) {
                DialogFactory.genericDialog(getController(),
                        Translation.getTranslation(
                                "wizard.multi_folder_setup.no_name.title"),
                        Translation.getTranslation(
                                "wizard.multi_folder_setup.no_name.text",
                                folderCreateItem.getLocalBase().getAbsolutePath()),
                        GenericDialogType.ERROR);
                return false;
            }
        }
        return true;
    }

    public WizardPanel next() {

//        // Set FolderInfo
//        FolderInfo folderInfo = new FolderInfo(folderNameTextField.getText()
//            .trim(), '[' + IdGenerator.makeId() + ']');
//        getWizardContext().setAttribute(FOLDERINFO_ATTRIBUTE, folderInfo);
//
//        // Set sync profile
//        getWizardContext().setAttribute(SYNC_PROFILE_ATTRIBUTE,
//            syncProfileSelectorPanel.getSyncProfile());
//
//        // Setup choose disk location panel
//        getWizardContext().setAttribute(PROMPT_TEXT_ATTRIBUTE,
//            Translation.getTranslation("wizard.what_to_do.invite.select_local"));
//
//        // Setup sucess panel of this wizard path
//        TextPanelPanel successPanel = new TextPanelPanel(getController(),
//            Translation.getTranslation("wizard.setup_success"), Translation
//                .getTranslation("wizard.success_join"));
//        getWizardContext().setAttribute(PFWizard.SUCCESS_PANEL, successPanel);
//
//        getWizardContext().setAttribute(SAVE_INVITE_LOCALLY,
//            Boolean.TRUE);
//
        return new FolderCreatePanel(getController());
    }

    protected JPanel buildContent() {
        FormLayout layout = new FormLayout("right:pref, 3dlu, 140dlu, pref:grow",
            "pref, 3dlu, pref, 3dlu, pref");

        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        builder.addLabel(Translation.getTranslation("general.directory"),
                cc.xy(1, 1));
        builder.add(localBaseCombo, cc.xy(3, 1));

        builder.addLabel(Translation.getTranslation("general.folder_name"),
                cc.xy(1, 3));
        builder.add(nameField, cc.xy(3, 3));

        return builder.getPanel();
    }

    /**
     * Initializes all necessary components
     */
    protected void initComponents() {

        KeyListener myKeyListener = new MyKeyListener();

        folderCreateItems = new ArrayList<FolderCreateItem>();

        localBaseComboModel = new DefaultComboBoxModel();
        localBaseCombo = new JComboBox(localBaseComboModel);

        nameField = new JTextField();
        nameField.addKeyListener(myKeyListener);

        localBaseCombo.addItemListener(new MyItemListener());

        getWizardContext().setAttribute(PFWizard.PICTO_ICON,
            Icons.FILE_SHARING_PICTO);

    }

    public void afterDisplay() {
        localBaseComboModel.removeAllElements();
        Object attribute = getWizardContext().getAttribute(FOLDER_LOCAL_BASES);
        if (attribute != null && attribute instanceof List) {
            List list = (List) attribute;
            for (Object o : list) {
                if (o instanceof FolderCreateItem) {
                    FolderCreateItem item = (FolderCreateItem) o;

                    // Create folder info if none exists.
                    if (item.getFolderInfo() == null) {
                        createFolderInfo(item);
                    }
                    folderCreateItems.add(item);
                    localBaseComboModel.addElement(item.getLocalBase().getAbsolutePath());
                }
            }
        }

        updateOnSelection();
    }

    protected JComponent getPictoComponent() {
        return new JLabel(getContextPicto());
    }

    protected String getTitle() {
        return Translation.getTranslation("wizard.multi_folder_setup.title");
    }

    private void updateOnSelection() {
        String dirName = (String) localBaseComboModel.getSelectedItem();
        for (FolderCreateItem item : folderCreateItems) {
            if (item.getLocalBase().getAbsolutePath().equals(dirName)) {
                FolderInfo folderInfo = item.getFolderInfo();
                nameField.setText(folderInfo.name);
                selectedItem = item;
                break;
            }
        }
    }

    /**
     * Create folder info now if none exists, and assign to item.
     *
     * @param item
     * @return
     */
    private void createFolderInfo(FolderCreateItem item) {
        // Default sync folder has user name...
        String name = getController().getMySelf().getInfo().nick + '-'
            + item.getLocalBase().getName();
        FolderInfo folderInfo = new FolderInfo(name,
                '[' + IdGenerator.makeId() + ']');
        item.setFolderInfo(folderInfo);
    }

    private class MyItemListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            updateOnSelection();
        }
    }

    private class MyKeyListener implements KeyListener {
        public void keyTyped(KeyEvent e) {
        }

        public void keyPressed(KeyEvent e) {
        }

        public void keyReleased(KeyEvent e) {
            if (selectedItem != null) {
                selectedItem.getFolderInfo().name = nameField.getText();
            }
        }
    }
}