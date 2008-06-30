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

import static de.dal33t.powerfolder.disk.SyncProfile.AUTOMATIC_SYNCHRONIZATION;
import static de.dal33t.powerfolder.ui.wizard.PFWizard.SUCCESS_PANEL;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.BACKUP_ONLINE_STOARGE;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.CREATE_DESKTOP_SHORTCUT;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.FOLDER_LOCAL_BASE;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.SEND_INVIATION_AFTER_ATTRIBUTE;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.SYNC_PROFILE_ATTRIBUTE;

import java.awt.Color;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import jwf.WizardPanel;

import org.apache.commons.lang.StringUtils;

import com.jgoodies.binding.adapter.BasicComponentFactory;
import com.jgoodies.binding.value.ValueHolder;
import com.jgoodies.binding.value.ValueModel;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Constants;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.ui.widget.LinkLabel;
import de.dal33t.powerfolder.util.Help;
import de.dal33t.powerfolder.util.Translation;

public class LoginOnlineStoragePanel extends PFWizardPanel {

    private JTextField usernameField;
    private JPasswordField passwordField;
    private WizardPanel nextPanel;

    private boolean entryRequired;

    private ValueModel setupDefaultModel;
    private JCheckBox setupDefaultCB;

    private File defaultSynchronizedFolder;

    /**
     * @param controller
     * @param folderSetupAfterwards
     *            true if folder setup should shown after correct setup
     */
    public LoginOnlineStoragePanel(Controller controller,
        WizardPanel nextPanel, boolean entryRequired)
    {
        super(controller);
        this.nextPanel = nextPanel;
        this.entryRequired = entryRequired;
    }

    public boolean hasNext() {
        return !entryRequired || !StringUtils.isEmpty(usernameField.getText());
    }

    public boolean validateNext(List list) {
        if (!entryRequired && StringUtils.isEmpty(usernameField.getText())) {
            return true;
        }
        // TODO Move this into worker. Make nicer. Difficult because function
        // returns loginOk.
        boolean loginOk = false;
        try {
            loginOk = getController().getOSClient().login(
                usernameField.getText(),
                new String(passwordField.getPassword())).isValid();
            if (!loginOk) {
                list.add(Translation
                    .getTranslation("online_storage.account_data"));
            }
            // FIXME Use separate account stores for diffrent servers?
            ConfigurationEntry.WEBSERVICE_USERNAME.setValue(getController(),
                usernameField.getText());
            ConfigurationEntry.WEBSERVICE_PASSWORD.setValue(getController(),
                new String(passwordField.getPassword()));
            getController().saveConfig();

        } catch (Exception e) {
            log().error("Problem logging in", e);
            list.add(Translation.getTranslation("online_storage.general_error",
                e.getMessage()));
        }
        return loginOk;
    }

    public WizardPanel next() {

        // Create default
        if ((Boolean) setupDefaultModel.getValue()) {

            // If there is already a default folder for this account, use that
            // for the name.
            FolderInfo accountFolder = getController().getOSClient()
                .getAccount().getDefaultSynchronizedFolder();
            if (accountFolder != null) {
                defaultSynchronizedFolder = new File(getController()
                    .getFolderRepository().getFoldersBasedir(),
                    accountFolder.name);
            }

            // Redirect via folder create of the deafult sync folder.
            FolderCreatePanel fcp = new FolderCreatePanel(getController());
            getWizardContext().setAttribute(CREATE_DESKTOP_SHORTCUT, false);
            getWizardContext().setAttribute(SEND_INVIATION_AFTER_ATTRIBUTE,
                false);
            getWizardContext().setAttribute(SUCCESS_PANEL, nextPanel);
            getWizardContext().setAttribute(SYNC_PROFILE_ATTRIBUTE,
                AUTOMATIC_SYNCHRONIZATION);
            getWizardContext().setAttribute(FOLDER_LOCAL_BASE,
                defaultSynchronizedFolder);
            getWizardContext().setAttribute(BACKUP_ONLINE_STOARGE, true);

            return fcp;
        }
        return nextPanel;
    }

    protected JPanel buildContent() {
        FormLayout layout = new FormLayout(
            "$wlabel, $lcg, $wfield, 0:g",
            "pref, 10dlu, pref, 5dlu, pref, 5dlu, pref, 15dlu, pref, 5dlu, pref");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        builder.addLabel(Translation
            .getTranslation("wizard.webservice.enteraccount"), cc.xyw(1, 1, 4));

        builder.addLabel(Translation
            .getTranslation("wizard.webservice.username"), cc.xy(1, 3));
        builder.add(usernameField, cc.xy(3, 3));

        builder.addLabel(Translation
            .getTranslation("wizard.webservice.password"), cc.xy(1, 5));
        builder.add(passwordField, cc.xy(3, 5));

        builder.add(new LinkLabel(Translation
            .getTranslation("pro.wizard.activation.register_now"),
            Constants.ONLINE_STORAGE_REGISTER_URL), cc.xy(3, 7));

        LinkLabel link = new LinkLabel(Translation
            .getTranslation("wizard.webservice.learnmore"),
            "http://www.powerfolder.com/node/webservice");
        // FIXME This is a hack because of "Fusch!"
        link.setBorder(Borders.createEmptyBorder("0, 1px, 0, 0"));
        builder.add(link, cc.xyw(1, 9, 4));

        if (defaultSynchronizedFolder.exists()) {
            // Hmmm. User has already created this???
            setupDefaultCB.setSelected(false);
        } else {
            builder.add(createSetupDefultPanel(), cc.xyw(1, 11, 4));
        }

        return builder.getPanel();
    }

    private Component createSetupDefultPanel() {
        FormLayout layout = new FormLayout("pref, 3dlu, pref", "pref");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();
        builder.add(setupDefaultCB, cc.xy(1, 1));
        builder.add(Help.createWikiLinkLabel("Default_Folder"), cc.xy(3, 1));
        builder.setOpaque(true);
        builder.setBackground(Color.white);

        return builder.getPanel();
    }

    // UI building ************************************************************

    /**
     * Initalizes all nessesary components
     */
    protected void initComponents() {
        // FIXME Use separate account stores for diffrent servers?
        ValueModel usernameModel = new ValueHolder(
            ConfigurationEntry.WEBSERVICE_USERNAME.getValue(getController()),
            true);
        usernameField = BasicComponentFactory.createTextField(usernameModel);
        passwordField = new JPasswordField(
            ConfigurationEntry.WEBSERVICE_PASSWORD.getValue(getController()));
        updateButtons();
        usernameModel.addValueChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                updateButtons();
            }
        });

        setupDefaultModel = new ValueHolder(true);
        setupDefaultCB = BasicComponentFactory.createCheckBox(
            setupDefaultModel, Translation
                .getTranslation("wizard.login_online_storage.setup_default"));
        setupDefaultCB.setOpaque(true);
        setupDefaultCB.setBackground(Color.white);

        defaultSynchronizedFolder = new File(getController()
            .getFolderRepository().getFoldersBasedir(), Translation
            .getTranslation("wizard.basicsetup.default_folder_name"));
    }

    protected Icon getPicto() {
        return Icons.WEBSERVICE_PICTO;
    }

    protected String getTitle() {
        return Translation.getTranslation("wizard.webservice.login");
    }
}
