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
package de.dal33t.powerfolder.util.test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;

import junit.framework.TestCase;
import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Feature;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderSettings;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.DirectoryInfo;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.net.ConnectionException;
import de.dal33t.powerfolder.util.FileUtils;
import de.dal33t.powerfolder.util.Format;
import de.dal33t.powerfolder.util.PropertiesUtil;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.logging.Loggable;
import de.dal33t.powerfolder.util.logging.LoggingManager;

/**
 * Provides basic testcase-setup with N controllers.
 * <p>
 * After <code>#setUp()</code> is invoked it is ensured, that all controllers
 * are running. There are several utility methods to bring the test into a usual
 * state. To connect two controllers just call
 * <code>{@link #tryToConnect(Controller, Controller)}</code> in
 * <code>{@link #setUp()}</code>.
 * <p>
 * You can access all controllers and do manupulating/testing stuff on them
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.2 $
 */
public abstract class MultipleControllerTestCase extends TestCase {
    private final Map<String, Controller> controllers = new HashMap<String, Controller>();
    private FolderInfo mctFolder;
    private int port = 4000;

    @Override
    protected void setUp() throws Exception {
        System.setProperty("user.home",
            new File("build/test/home").getCanonicalPath());
        Loggable.setLogNickPrefix(true);
        super.setUp();

        for (Controller controller : controllers.values()) {
            if (controller.isStarted()) {
                // Ensure shutdown of controller. Maybe tearDown was not called
                // becaused of previous failing test.
                stopControllers();
                break;
            }
        }

        // Default exception logger
        Thread
            .setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
            {
                public void uncaughtException(Thread t, Throwable e) {
                    System.err.println("Exception in " + t + ": "
                        + e.toString());
                    e.printStackTrace();
                }
            });
        Feature.setupForTests();

        // Cleanup
        TestHelper.cleanTestDir();
        FileUtils.recursiveDelete(new File(Controller.getMiscFilesLocation(),
            "build"));
    }

    @Override
    protected void tearDown() throws Exception {
        LoggingManager.setConsoleLogging(Level.OFF);
        System.out.println("-------------- tearDown -----------------");
        super.tearDown();
        stopControllers();
        mctFolder = null;
    }

    // For subtest ************************************************************

    /**
     * After this method is invoked it is ensured, that the controller is
     * running. the controller gets added to the internal list of controllers.
     * 
     * @param id
     *            the internal id of the controller.
     * @param config
     *            the config to load.
     * @return
     */
    protected Controller startController(String id, String config) {
        Reject.ifTrue(controllers.containsKey(id),
            "Already got controller with id '" + id + "'");
        Controller controller = Controller.createController();
        controller.startConfig(config);
        waitForStart(controller);
        assertNotNull("Connection listener for controller '" + id
            + "' could not be opened", controller.getConnectionListener());
        // triggerAndWaitForInitialMaitenenace(controller);
        ConfigurationEntry.MASS_DELETE_PROTECTION.setValue(controller, false);
        // Clean up on completion.
        controllers.put(id, controller);
        return controller;
    }

    protected Controller startControllerWithDefaultConfig(String id)
        throws IOException
    {
        Reject.ifTrue(controllers.containsKey(id),
            "Already got controller with id '" + id + "'");
        Properties conf = new Properties();
        FileReader r = new FileReader(
            "src/test-resources/ControllerBart.config");
        conf.load(r);
        r.close();
        conf.put("nodeid", "randomstringController" + id);
        conf.put("nick", "Controller" + id);
        conf.put("port", "" + port++);
        File f = new File("build/test/Controller" + id + "/PowerFolder.config");
        assertTrue(f.getParentFile().mkdirs());
        PropertiesUtil.saveConfig(f, conf, "PF Test config");

        return startController(id, "build/test/Controller" + id
            + "/PowerFolder");
    }

    protected Controller getContoller(String id) {
        return controllers.get(id);
    }

    /**
     * Makes both controllers frieds.
     */
    protected static void makeFriends(Controller controllerA,
        Controller controllerB)
    {
        Member contBatA = controllerA.getNodeManager().getNode(
            controllerB.getMySelf().getId());
        if (contBatA == null) {
            contBatA = controllerA.getNodeManager().addNode(
                controllerB.getMySelf().getInfo());
        }
        contBatA.setFriend(true, null);

        Member contAatB = controllerB.getNodeManager().getNode(
            controllerA.getMySelf().getId());
        if (contAatB == null) {
            contAatB = controllerB.getNodeManager().addNode(
                controllerA.getMySelf().getInfo());
        }
        contAatB.setFriend(true, null);
    }

    /**
     * Connects and waits for connection of both controllers
     */
    protected static void connect(final Controller cont1, final Controller cont2)
    {
        if (!tryToConnect(cont1, cont2)) {
            throw new RuntimeException("Unable to connect controller " + cont1
                + " to " + cont2);
        }
    }

    /**
     * Connects and waits for connection of both controllers
     * 
     * @param cont1
     * @param cont2
     * @throws InterruptedException
     * @throws ConnectionException
     */
    protected static boolean tryToConnect(final Controller cont1,
        final Controller cont2)
    {
        Reject.ifTrue(!cont1.isStarted(), "Controller1 not started yet");
        Reject.ifTrue(!cont2.isStarted(), "Controller2 not started yet");

        if (cont1.getNodeManager().getConnectedNodes()
            .contains(cont2.getMySelf()))
        {
            System.out
                .println("NOT connecting, Controllers already connected: "
                    + cont1 + " to " + cont2);
            return true;
        }

        // Connect
        System.out.println("Connecting controllers...");
        System.out.println("Con to: "
            + cont2.getConnectionListener().getAddress());

        try {
            cont1.connect(cont2.getConnectionListener().getAddress());
        } catch (ConnectionException e) {
            e.printStackTrace();
            System.err.println("Unable to connect controller: " + cont1
                + " to " + cont2);
            return false;
        }
        try {
            TestHelper.waitForCondition(20, new ConditionWithMessage() {
                public boolean reached() {
                    Member member2atCon1 = cont1.getNodeManager().getNode(
                        cont2.getMySelf().getId());
                    Member member1atCon2 = cont2.getNodeManager().getNode(
                        cont1.getMySelf().getId());
                    boolean connected = member2atCon1 != null
                        && member1atCon2 != null
                        && member2atCon1.isCompletelyConnected()
                        && member1atCon2.isCompletelyConnected();
                    boolean nodeManagersOK = cont1.getNodeManager()
                        .getConnectedNodes().contains(member2atCon1)
                        && cont2.getNodeManager().getConnectedNodes()
                            .contains(member1atCon2);
                    return connected && nodeManagersOK;
                }

                public String message() {
                    return "Unable to connect controllers: " + cont1 + " and "
                        + cont2;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unable to connect controller: " + cont1
                + " to " + cont2);
            return false;
        }
        System.out.println("Controllers connected");
        return true;
    }

    /**
     * Let the controller join the specified folder.
     * <p>
     * After the method is invoked, it is ensured that the controller joined the
     * folder.
     * 
     * @param foInfo
     *            the folder to join
     * @param baseDir
     *            the local base dir for the controller
     * @param profile
     *            the profile to use
     */
    protected static Folder joinFolder(FolderInfo foInfo, File baseDir,
        Controller controller, SyncProfile profile)
    {
        FolderSettings folderSettings = new FolderSettings(baseDir, profile,
            false, 5);
        return controller.getFolderRepository().createFolder(foInfo,
            folderSettings);
    }

    protected void nSetupControllers(int n) throws IOException {
        for (int i = 0; i < n; i++)
            startControllerWithDefaultConfig("" + i);
    }

    protected Collection<Controller> getControllers() {
        return controllers.values();
    }

    protected void joinNTestFolder(SyncProfile profile) {
        Reject.ifTrue(mctFolder != null, "Reject already setup a testfolder!");
        mctFolder = new FolderInfo("testFolder", UUID.randomUUID().toString());
        for (Entry<String, Controller> e : controllers.entrySet()) {
            joinFolder(mctFolder, new File(TestHelper.getTestDir(),
                "Controller" + e.getKey() + "/testFolder"), e.getValue(),
                profile);
        }
    }

    protected Folder getFolderOf(String id) {
        return getContoller(id).getFolderRepository().getFolder(mctFolder);
    }

    protected Folder getFolderOf(Controller c) {
        return c.getFolderRepository().getFolder(mctFolder);
    }

    protected void setNSyncProfile(SyncProfile profile) {
        for (String id : controllers.keySet()) {
            getFolderOf(id).setSyncProfile(profile);
        }
    }

    /**
     * Set a configuration for all controllers
     */
    protected void setConfigurationEntry(ConfigurationEntry entry, String value)
    {
        for (Controller c : controllers.values()) {
            entry.setValue(c, value);
        }
    }

    protected void connectAll() {
        Controller entries[] = controllers.values().toArray(
            new Controller[controllers.values().size()]);
        for (int i = 0; i < entries.length; i++) {
            for (int j = 0; j < i; j++) {
                tryToConnect(entries[i], entries[j]);
            }
        }
    }

    protected void disconnectAll() {
        final Controller entries[] = controllers.values().toArray(
            new Controller[controllers.values().size()]);
        for (int i = 0; i < entries.length; i++) {
            for (int j = 0; j < i; j++) {
                Member node = entries[j].getNodeManager().getNode(
                    entries[i].getNodeManager().getMySelf().getId());
                if (node == null) {
                    continue;
                }
                node.shutdown();
            }
        }
        TestHelper.waitForCondition(10, new Condition() {
            public boolean reached() {
                for (int i = 0; i < entries.length; i++) {
                    for (int j = 0; j < i; j++) {
                        Member node = entries[j].getNodeManager().getNode(
                            entries[i].getNodeManager().getMySelf().getId());
                        if (node != null && node.isConnected()) {
                            return false;
                        }
                    }
                }
                return true;
            }
        });
        TestHelper.waitMilliSeconds(500);
        System.out.println("All Controllers Disconnected");
    }

    /**
     * Scans a folder and waits for the scan to complete.
     */
    protected synchronized void scanFolder(Folder folder) {
        TestHelper.scanFolder(folder);
    }

    /**
     * Tests if the diskfile matches the fileinfo. Checks name, lenght/size,
     * modification date and the deletion status.
     * 
     * @param diskFile
     *            the diskfile to compare
     * @param fInfo
     *            the fileinfo
     * @param controller
     *            the controller to use.
     */
    protected void assertFileMatch(File diskFile, FileInfo fInfo,
        Controller controller)
    {
        boolean nameMatch = diskFile.getName().equals(fInfo.getFilenameOnly());
        boolean sizeMatch = diskFile.length() == fInfo.getSize();
        boolean fileObjectEquals = diskFile.equals(fInfo.getDiskFile(controller
            .getFolderRepository()));
        boolean deleteStatusMatch = diskFile.exists() == !fInfo.isDeleted();
        boolean lastModifiedMatch = diskFile.lastModified() == fInfo
            .getModifiedDate().getTime();
        boolean isDir = fInfo.isDiretory() && fInfo instanceof DirectoryInfo;
        boolean dirMatch = isDir == diskFile.isDirectory();

        // Skip last modification test when diskfile is deleted.
        boolean matches = dirMatch && nameMatch && sizeMatch
            && (!diskFile.exists() || lastModifiedMatch) && deleteStatusMatch
            && fileObjectEquals;

        assertTrue("FileInfo does not match physical file. \nFileInfo:\n "
            + fInfo.toDetailString() + "\nFile:\n " + diskFile.getName()
            + ", size: " + Format.formatBytes(diskFile.length())
            + ", lastModified: " + new Date(diskFile.lastModified()) + " ("
            + diskFile.lastModified() + ")" + "\n\nWhat matches?:\nName: "
            + nameMatch + "\nSize: " + sizeMatch + "\nlastModifiedMatch: "
            + lastModifiedMatch + "\ndeleteStatus: " + deleteStatusMatch
            + "\ndirMatch: " + dirMatch + "\nFileObjectEquals: "
            + fileObjectEquals, matches);
    }

    /**
     * Tests if the diskfile matches the fileinfo. Checks name and the deletion
     * status.
     * 
     * @param diskFile
     *            the diskfile to compare
     * @param fInfo
     *            the fileinfo
     * @param controller
     *            the controller to use.
     */
    protected void assertDirMatch(File diskFile, FileInfo fInfo,
        Controller controller)
    {
        boolean nameMatch = diskFile.getName().equals(fInfo.getFilenameOnly());
        boolean fileObjectEquals = diskFile.equals(fInfo.getDiskFile(controller
            .getFolderRepository()));
        boolean deleteStatusMatch = diskFile.exists() == !fInfo.isDeleted();
        // Skip directory match if not existing.
        boolean dirMatch = !diskFile.exists()
            || fInfo.isDiretory() == diskFile.isDirectory();

        boolean matches = dirMatch && nameMatch && deleteStatusMatch
            && fileObjectEquals;

        assertTrue("DirectoryInfo does not match physical dir. \nFileInfo:\n "
            + fInfo.toDetailString() + "\nFile:\n " + diskFile.getName()
            + ", size: " + Format.formatBytes(diskFile.length())
            + ", lastModified: " + new Date(diskFile.lastModified()) + " ("
            + diskFile.lastModified() + ")" + "\n\nWhat matches?:\nName: "
            + nameMatch + "\ndeleteStatus: " + deleteStatusMatch
            + "\ndirMatch: " + dirMatch + "\nFileObjectEquals: "
            + fileObjectEquals, matches);
    }

    // Helpers ****************************************************************

    /**
     * Waits for the controller to startup
     * 
     * @param controller
     */
    private static void waitForStart(final Controller controller) {
        TestHelper.waitForCondition(30, new Condition() {
            public boolean reached() {
                return controller.isStarted();
            }
        });
    }

    private void stopControllers() {
        for (String id : controllers.keySet()) {
            final Controller controller = controllers.get(id);
            controller.shutdown();
            int i = 0;
            while (controller.isShuttingDown()) {
                i++;
                if (i > 1000) {
                    System.out.println("Shutdown failed: " + controller);
                    break;
                }
                TestHelper.waitMilliSeconds(100);
            }
            assertFalse(controller.isStarted());
            // add a pause to make sure files can be cleaned before next
            // test.
            TestHelper.waitMilliSeconds(500);
            assertFalse("Shutdown of controller(" + id + ") failed",
                controller.isShuttingDown());
            assertFalse("Shutdown of controller(" + id + ")  failed",
                controller.isStarted());
        }
        controllers.clear();
    }
}
