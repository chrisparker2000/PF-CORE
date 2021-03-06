package de.dal33t.powerfolder.test.transfer;

import java.io.File;
import java.io.IOException;

import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.transfer.DownloadManager;
import de.dal33t.powerfolder.util.test.ConditionWithMessage;
import de.dal33t.powerfolder.util.test.TestHelper;
import de.dal33t.powerfolder.util.test.TwoControllerTestCase;

/**
 * Primary because of #1399
 * 
 * @author Christian Sprajc
 * @version $Revision$
 */
public class DownloadPersistenceTest extends TwoControllerTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        connectBartAndLisa();
        joinTestFolder(SyncProfile.AUTOMATIC_SYNCHRONIZATION);
    }

    public void testStoreCompletedDownloadsMultiple() throws Exception {
        for (int i = 0; i < 5; i++) {
            testStoreCompletedDownloads();
            tearDown();
            setUp();
        }
    }

    public void testStoreCompletedDownloads() throws IOException {
        final int nFiles = 10;
        for (int i = 0; i < nFiles; i++) {
            TestHelper.createRandomFile(getFolderAtBart().getLocalBase());
        }
        scanFolder(getFolderAtBart());
        TestHelper.waitForCondition(nFiles, new ConditionWithMessage() {
            public boolean reached() {
                return getContollerLisa().getTransferManager()
                    .getCompletedDownloadsCollection().size() == nFiles;
            }

            public String message() {
                return "Completed downloads at lisa: "
                    + getContollerLisa().getTransferManager()
                        .getCompletedDownloadsCollection().size()
                    + ". Expected: " + nFiles;
            }
        });

        for (FileInfo f : getFolderAtLisa().getKnownFiles()) {
            assertEquals(0, f.getVersion());
        }

        getContollerLisa().shutdown();

        for (DownloadManager dlManager : getContollerLisa()
            .getTransferManager().getCompletedDownloadsCollection())
        {
            assertTrue(dlManager.getTempFile() == null);
            assertTrue("Got state on completed download: "
                + dlManager.getState().getState().toString(),
                dlManager.isCompleted());
        }

        startControllerLisa();
        connectBartAndLisa();

        TestHelper.waitMilliSeconds(2500);

        for (FileInfo f : getFolderAtLisa().getKnownFiles()) {
            assertEquals(0, f.getVersion());
        }

        for (FileInfo f : getFolderAtBart().getKnownFiles()) {
            assertEquals(0, f.getVersion());
        }

        assertEquals("Invalid number of completed downloads: "
            + getContollerLisa().getTransferManager()
                .getCompletedDownloadsCollection(), nFiles, getContollerLisa()
            .getTransferManager().getCompletedDownloadsCollection().size());

        for (DownloadManager dlManager : getContollerLisa()
            .getTransferManager().getCompletedDownloadsCollection())
        {
            assertFalse("Tempfile existing for completed download: "
                + dlManager.getTempFile(), dlManager.getTempFile().exists());
            assertTrue(
                "Unable to access temp file: " + dlManager.getTempFile(),
                dlManager.getTempFile().createNewFile());
        }

        File[] files = getFolderAtBart().getLocalBase().listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isFile()) {
                TestHelper.changeFile(file);
            }
        }
        scanFolder(getFolderAtBart());
        TestHelper.waitForCondition(nFiles * 100, new ConditionWithMessage() {
            public boolean reached() {
                return getContollerLisa().getTransferManager()
                    .getCompletedDownloadsCollection().size() == nFiles * 2;
            }

            public String message() {
                return "Completed dls at lisa: "
                    + getContollerLisa().getTransferManager()
                        .getCompletedDownloadsCollection().size();
            }
        });

        scanFolder(getFolderAtBart());
        for (FileInfo fInfo : getFolderAtBart().getKnownFiles()) {
            assertFileMatch(
                fInfo.getDiskFile(getContollerBart().getFolderRepository()),
                fInfo, getContollerBart());
            assertEquals(1, fInfo.getVersion());
            assertTrue(fInfo.getSize() > 0);
        }
        scanFolder(getFolderAtLisa());
        for (FileInfo fInfo : getFolderAtLisa().getKnownFiles()) {
            assertFileMatch(
                fInfo.getDiskFile(getContollerLisa().getFolderRepository()),
                fInfo, getContollerLisa());
            assertEquals(1, fInfo.getVersion());
            assertTrue(fInfo.getSize() > 0);
        }
    }

}
