package de.dal33t.powerfolder.ui;

import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

import javax.swing.*;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.event.InvitationReceivedEvent;
import de.dal33t.powerfolder.event.InvitationReceivedHandler;
import de.dal33t.powerfolder.message.Invitation;
import de.dal33t.powerfolder.ui.wizard.FolderInvitationPanel;
import de.dal33t.powerfolder.ui.wizard.PFWizard;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.DialogFactory;
import de.dal33t.powerfolder.util.ui.GenericDialogType;
import de.dal33t.powerfolder.util.os.OSUtil;

/**
 * The default handler when an invitation is received by the
 * FolderRepositoryThis handler shows the user a dialog where he can should to
 * join the folder.
 */
public class InvitationReceivedHandlerDefaultImpl extends PFComponent implements
    InvitationReceivedHandler
{
    public InvitationReceivedHandlerDefaultImpl(Controller controller) {
        super(controller);
    }

    /**
     * Called by the FolderRepository when an invitation is received. Shows a
     * dialog to give the user the option to join the folder. Depending on the
     * flag isProcessSilently and error message is shown if folder is already
     * joined.
     */
    public void invitationReceived(
        InvitationReceivedEvent invitationRecievedEvent)
    {
        final Invitation invitation = invitationRecievedEvent.getInvitation();
        final boolean processSilently = invitationRecievedEvent
            .isProcessSilently();
        final boolean forcePopup = invitationRecievedEvent.isProcessSilently();
        final FolderRepository repository = invitationRecievedEvent
            .getFolderRepository();
        if (invitation == null || invitation.folder == null) {
            throw new NullPointerException("Invitation/Folder is null");
        }
        if (!getController().isUIOpen()) {
            return;
        }

        Runnable worker = new Runnable() {
            public void run() {
                // Check if already on folder
                if (repository.hasJoinedFolder(invitation.folder)) {
                    // Already on folder, show message if not processing
                    // silently
                    if (!processSilently) {
                        // Popup application
                        getController().getUIController().getMainFrame()
                            .getUIComponent().setVisible(true);
                        getController().getUIController().getMainFrame()
                            .getUIComponent().setExtendedState(Frame.NORMAL);

                        DialogFactory.genericDialog(
                                getController().getUIController().getMainFrame().getUIComponent(),
                                Translation.getTranslation("joinfolder.already_joined_title", invitation.folder.name),
                                Translation.getTranslation("joinfolder.already_joined_text", invitation.folder.name),
                                GenericDialogType.WARN);
                    }
                    return;
                }
                final FolderInvitationPanel panel = new FolderInvitationPanel(
                    getController(), invitation);
                final JFrame jFrame = getController().getUIController()
                    .getMainFrame().getUIComponent();
                if (forcePopup
                    || !(OSUtil.isSystraySupported() && !jFrame.isVisible()))
                {
                    // Popup whole application
                    MainFrame mf = getController().getUIController()
                        .getMainFrame();
                    if (mf.isIconifiedOrHidden()) {
                        mf.deiconify();
                    }
                    PFWizard wizard = new PFWizard(getController());
                    wizard.open(panel);
                } else {
                    // Only show systray blinking
                    getController().getUIController().getBlinkManager()
                        .setBlinkingTrayIcon(Icons.ST_NODE);
                    jFrame.addWindowFocusListener(new WindowFocusListener() {
                        public void windowGainedFocus(WindowEvent e) {
                            jFrame.removeWindowFocusListener(this);
                            PFWizard wizard = new PFWizard(getController());
                            wizard.open(panel);
                        }

                        public void windowLostFocus(WindowEvent e) {
                        }
                    });
                }
            }
        };

        // Invoke later
        SwingUtilities.invokeLater(worker);
    }
}
