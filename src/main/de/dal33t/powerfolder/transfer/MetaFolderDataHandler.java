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
 * $Id: MetaDataHandler.java 12163 2010-04-24 02:06:55Z harry $
 */
package de.dal33t.powerfolder.transfer;

import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.light.FileInfo;

/**
 * This class delegates responsibility for processing metaFolder fileInfo
 * 'events' to relevant classes.
 */
public class MetaFolderDataHandler extends PFComponent {

    public MetaFolderDataHandler(Controller controller) {
        super(controller);
    }

    /**
     * Handle file change to metaFolder files.
     *
     * @param fileInfo
     *               metaFolder file info
     */
    public void handleMetaFolderFileInfo(FileInfo fileInfo) {
        if (fileInfo.getFilenameOnly().equals(
                Folder.META_FOLDER_SYNC_PATTERNS_FILE_NAME)) {
            handleMetaFolderSyncPatterns(fileInfo);
        }
    }

    /**
     * Updated sync patterns have been downloaded to the metaFolder.
     * Update the sync patterns in the parent folder.
     *
     * @param fileInfo
     *               fileInfo of the new sync patterns
     */
    private void handleMetaFolderSyncPatterns(FileInfo fileInfo) {
        Folder parentFolder = getController().getFolderRepository()
                .getParentFolder(fileInfo.getFolderInfo());
        parentFolder.handleMetaFolderSyncPatterns(fileInfo);
    }
}