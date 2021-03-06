/*
* Copyright 2004 - 2008 Christian Sprajc, Dennis Waldherr. All rights reserved.
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
package de.dal33t.powerfolder.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;

public class AddLicenseHeader {

    /**
     * @param args
     */
    public static void main(String[] args) {
        addLicInfoToDir(new File("."));
    }

    public static void addLicInfoToDir(File dir) {
        File[] javas = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".java");
            }
        });
        for (File file : javas) {
            addLicInfo(file);
        }

        File[] subDirs = dir.listFiles((FileFilter) FileFilterUtils
            .directoryFileFilter());
        for (File subDir : subDirs) {
            addLicInfoToDir(subDir);
        }
    }

    public static void addLicInfo(File f) {
        try {
            if (f.getAbsolutePath().contains("\\jwf\\jwf")) {
                System.out.println("Skip: " + f.getCanonicalPath());
                return;
            }
            if (f.getAbsolutePath().contains("org\\jdesktop\\swinghelper")) {
                System.out.println("Skip: " + f.getCanonicalPath());
                return;
            }
            String content = FileUtils.readFileToString(f, "UTF-8");
            int i = content.indexOf("package");

//            if (i != 693) {
//                System.out.println("Skip: " + f.getCanonicalPath() + ": " + i);
//                return;
//            }
            boolean dennis = content.contains("@author Dennis");
            if (dennis) {
                System.err.println("Dennis: " + f.getCanonicalPath() + ": " + i);
                content = LIC_INFO_DENNIS + content.substring(i, content.length());
            } else {
                System.out.println("Onlyme: " + f.getCanonicalPath() + ": " + i);
                content = LIC_INFO + content.substring(i, content.length());
            }
//           
            // System.out.println(content);
             FileUtils.writeStringToFile(f, content, "UTF-8");
            // throw new RuntimeException();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static final String LIC_INFO = "/*\r\n"
        + "* Copyright 2004 - 2008 Christian Sprajc. All rights reserved.\r\n"
        + "*\r\n"
        + "* This file is part of PowerFolder.\r\n"
        + "*\r\n"
        + "* PowerFolder is free software: you can redistribute it and/or modify\r\n"
        + "* it under the terms of the GNU General Public License as published by\r\n"
        + "* the Free Software Foundation.\r\n"
        + "*\r\n"
        + "* PowerFolder is distributed in the hope that it will be useful,\r\n"
        + "* but WITHOUT ANY WARRANTY; without even the implied warranty of\r\n"
        + "* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\r\n"
        + "* GNU General Public License for more details.\r\n"
        + "*\r\n"
        + "* You should have received a copy of the GNU General Public License\r\n"
        + "* along with PowerFolder. If not, see <http://www.gnu.org/licenses/>.\r\n"
        + "*\r\n" + "* $Id$\r\n" + "*/\r\n";
    
    private static final String LIC_INFO_DENNIS = "/*\r\n"
        + "* Copyright 2004 - 2008 Christian Sprajc, Dennis Waldherr. All rights reserved.\r\n"
        + "*\r\n"
        + "* This file is part of PowerFolder.\r\n"
        + "*\r\n"
        + "* PowerFolder is free software: you can redistribute it and/or modify\r\n"
        + "* it under the terms of the GNU General Public License as published by\r\n"
        + "* the Free Software Foundation.\r\n"
        + "*\r\n"
        + "* PowerFolder is distributed in the hope that it will be useful,\r\n"
        + "* but WITHOUT ANY WARRANTY; without even the implied warranty of\r\n"
        + "* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\r\n"
        + "* GNU General Public License for more details.\r\n"
        + "*\r\n"
        + "* You should have received a copy of the GNU General Public License\r\n"
        + "* along with PowerFolder. If not, see <http://www.gnu.org/licenses/>.\r\n"
        + "*\r\n" + "* $Id$\r\n" + "*/\r\n";
}
