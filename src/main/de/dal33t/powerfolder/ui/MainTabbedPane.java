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
 * $Id: MainTabbedPane.java 5495 2008-10-24 04:59:13Z harry $
 */
package de.dal33t.powerfolder.ui;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFUIComponent;
import de.dal33t.powerfolder.util.Translation;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * This is the main tabbed pain component in the PowerFolder GUI.
 */
public class MainTabbedPane extends PFUIComponent {

    private static final int HOME_INDEX = 0;
    private static final int FOLDERS_INDEX = 1;
    private static final int COMPUTERS_INDEX = 2;

    /**
     * The main tabbed pain.
     */
    private JTabbedPane uiComponent;

    /**
     * Constructor. Creates the main tabbed pane.
     *
     * @param controller
     */
    public MainTabbedPane(Controller controller) {
        super(controller);
    }

    /**
     * @return the ui main tabbed pane.
     */
    public JTabbedPane getUIComponent() {
        
        if (uiComponent == null) {
            // Initalize components
            initComponents();
        }

        uiComponent.add(Translation.getTranslation("main_tabbed_pane.home.name"),
                new JPanel());
        String key = Translation.getTranslation("main_tabbed_pane.home.key");
        uiComponent.setMnemonicAt(HOME_INDEX,
                (int) Character.toUpperCase(key.charAt(0)));
        uiComponent.setToolTipTextAt(HOME_INDEX,
                Translation.getTranslation("main_tabbed_pane.home.description"));

        uiComponent.add(Translation.getTranslation("main_tabbed_pane.folders.name"),
                new JPanel());
        key = Translation.getTranslation("main_tabbed_pane.folders.key");
        uiComponent.setMnemonicAt(FOLDERS_INDEX,
                (int) Character.toUpperCase(key.charAt(0)));
        uiComponent.setToolTipTextAt(FOLDERS_INDEX,
                Translation.getTranslation("main_tabbed_pane.folders.description"));

        uiComponent.add(Translation.getTranslation("main_tabbed_pane.computers.name"),
                new JPanel());
        key = Translation.getTranslation("main_tabbed_pane.computers.key");
        uiComponent.setMnemonicAt(COMPUTERS_INDEX, 
                (int) Character.toUpperCase(key.charAt(0)));
        uiComponent.setToolTipTextAt(COMPUTERS_INDEX,
                Translation.getTranslation("main_tabbed_pane.computers.description"));

        return uiComponent;
    }

    /**
     * Initialize the comonents of the pane.
     */
    private void initComponents() {
        uiComponent = new JTabbedPane();
    }

}
