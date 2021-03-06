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
 * $Id: FolderAdminPermission.java 5581 2008-11-03 03:26:24Z tot $
 */
package de.dal33t.powerfolder.security;

import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.util.Reject;

/**
 * A generic subclass for all permissions that are related to a certain folder.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public abstract class FolderPermission implements Permission {
    private static final long serialVersionUID = 100L;

    public static final String PROPERTYNAME_FOLDER = "folder";

    protected FolderInfo folder;

    protected FolderPermission(FolderInfo foInfo) {
        Reject.ifNull(foInfo, "Folderinfo is null");
        folder = foInfo.intern();
    }

    public abstract String getName();

    public abstract AccessMode getMode();

    public final FolderInfo getFolder() {
        return folder;
    }

    public String getId() {
        return folder.id + "_FP_" + getClass().getSimpleName();
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((folder == null) ? 0 : folder.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof FolderPermission))
            return false;
        final FolderPermission other = (FolderPermission) obj;
        if (folder == null) {
            if (other.folder != null)
                return false;
        } else if (!folder.equals(other.folder))
            return false;
        return true;
    }

    public static FolderPermission get(FolderInfo foInfo, AccessMode mode) {
        if (AccessMode.READ.equals(mode)) {
            return read(foInfo);
        } else if (AccessMode.READ_WRITE.equals(mode)) {
            return readWrite(foInfo);
        } else if (AccessMode.ADMIN.equals(mode)) {
            return admin(foInfo);
        } else if (AccessMode.OWNER.equals(mode)) {
            return owner(foInfo);
        } else {
            // No access / null.
            return null;
        }
    }

    public static FolderReadPermission read(FolderInfo foInfo) {
        return new FolderReadPermission(foInfo);
    }

    public static FolderReadWritePermission readWrite(FolderInfo foInfo) {
        return new FolderReadWritePermission(foInfo);
    }

    public static FolderAdminPermission admin(FolderInfo foInfo) {
        return new FolderAdminPermission(foInfo);
    }

    public static FolderOwnerPermission owner(FolderInfo foInfo) {
        return new FolderOwnerPermission(foInfo);
    }

    public String toString() {
        return getClass().getSimpleName() + " on " + folder;
    }
}
