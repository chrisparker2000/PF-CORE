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
package de.dal33t.powerfolder.net;

import java.util.Collection;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.message.RelayedMessage;
import de.dal33t.powerfolder.message.RelayedMessage.Type;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.TransferCounter;
import de.dal33t.powerfolder.util.Waiter;

/**
 * Listens for incoming relayed messages and
 * <p>
 * 1) Processes it if destination = myself. = Let RelayedConHandler of Member
 * process the message.
 * <p>
 * 2) Send the message to the destination if connected.
 * <p>
 * TRAC #597.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class RelayedConnectionManager extends PFComponent {

    private static final Logger log = Logger
        .getLogger(RelayedConnectionManager.class.getName());
    private static long nextConnectionId = 0;

    /**
     * Connection handler that are in pending state. Pending means there is a
     * ConnectionHanlder which is not yet connected with it's member (node).
     */
    private Collection<AbstractRelayedConnectionHandler> pendingConHans;
    private RelayFilter relayFilter;
    private Lock pendingConHansLock = new ReentrantLock();
    private TransferCounter counter;
    private boolean printStats;
    private long nRelayedMsgs;

    public RelayedConnectionManager(Controller controller) {
        super(controller);
        pendingConHans = new CopyOnWriteArrayList<AbstractRelayedConnectionHandler>();
        relayFilter = new ServerIsRelayFilter();
        counter = new TransferCounter();
        printStats = false;
    }

    public void start() {
        getController().scheduleAndRepeat(new RelayConnectTask(), 1000L * 20,
            1000L * 20);
    }

    /**
     * Creates and initializes a relayed channel via a relay. The returned
     * ConnectionHandler is in init state.
     * 
     * @param destination
     *            the remote destination to connect to
     * @return the relayed connection handler.
     * @throws ConnectionException
     */
    public ConnectionHandler initRelayedConnectionHandler(MemberInfo destination)
        throws ConnectionException
    {
        if (getController().getMySelf().getInfo().equals(destination)) {
            throw new ConnectionException(
                "Illegal relayed loopback connection detection to myself");
        }
        Member relay = getRelay();
        if (relay == null) {
            throw new ConnectionException(
                "Unable to open relayed connection to " + destination
                    + ". No relay found!");
        }
        if (isFiner()) {
            logFiner("Using relay " + relay + " for connection to "
                + destination + " / " + destination.id);
        }
        if (isFiner()) {
            logFiner("Sending SYN for relayed connection to "
                + destination.nick);
        }
        long connectionId;
        synchronized (RelayedConnectionManager.class) {
            connectionId = nextConnectionId++;
        }

        AbstractRelayedConnectionHandler relHan = getController()
            .getIOProvider().getConnectionHandlerFactory()
            .createRelayedConnectionHandler(destination, connectionId, relay);

        pendingConHansLock.lock();
        pendingConHans.add(relHan);
        pendingConHansLock.unlock();

        if (pendingConHans.size() > 20) {
            logSevere(pendingConHans.size()
                + " PENDING RELAYED CONNECTION HANDLERS found: "
                + pendingConHans);
        }

        RelayedMessage synMsg = new RelayedMessage(Type.SYN, getController()
            .getMySelf().getInfo(), destination, connectionId, null);
        relay.sendMessage(synMsg);
        try {
            waitForAckOrNack(relHan);
            relHan.init();
        } catch (ConnectionException e) {
            relHan.shutdown();
            removePedingRelayedConnectionHandler(relHan);
            throw e;
        }

        return relHan;
    }

    /**
     * Callback from <code>AbstractRelayedConnectionHandler</code> to inform,
     * that the handler is not longer pending (=on shutdown or assigend to his
     * <code>Member</code>).
     * 
     * @param conHan
     */
    public void removePedingRelayedConnectionHandler(
        AbstractRelayedConnectionHandler conHan)
    {
        Reject.ifNull(conHan, "ConnectionHandler is null");
        // logWarning("Removing pend. con han: " + conHan);
        pendingConHansLock.lock();
        pendingConHans.remove(conHan);
        pendingConHansLock.unlock();
    }

    /**
     * Callback method from <code>Member</code>.
     * 
     * @param receivedFrom
     *            the node/relay which relayed the message
     * @param message
     *            the message
     */
    public void handleRelayedMessage(final Member receivedFrom,
        final RelayedMessage message)
    {
        if (getController().getMySelf().getInfo().equals(
            message.getDestination()))
        {
            // This is a message for US!
            processMessageForMySelf(receivedFrom, message);
        } else {
            // Route message to destination member if possible.
            relayMessage(receivedFrom, message);
        }
    }

    /**
     * For TESTS only.
     * 
     * @param relayFilter
     */
    public void setRelayFilter(RelayFilter relayFilter) {
        Reject.ifNull(relayFilter, "RelayFilter");
        this.relayFilter = relayFilter;
    }

    /**
     * @return the relaying node or null if no relay found
     */
    public Member getRelay() {
        if (getController().getNodeManager() == null) {
            logWarning("Not getting relay, NodeManager not created yet");
            return null;
        }
        for (Member node : getController().getNodeManager().getConnectedNodes())
        {
            if (isRelay(node.getInfo())) {
                return node;
            }
        }
        if (isRelay(getController().getMySelf().getInfo())) {
            return getController().getMySelf();
        }
        return null;
    }

    public boolean isRelay(MemberInfo nodeInfo) {
        Reject.ifNull(nodeInfo, "Node info is null");
        Member node = nodeInfo.getNode(getController(), false);
        return node != null && isRelay(node);
    }

    public boolean isRelay(Member node) {
        return relayFilter.isRelay(node);
    }

    public TransferCounter getTransferCounter() {
        return counter;
    }

    // Internal ***************************************************************

    private void relayMessage(final Member receivedFrom,
        final RelayedMessage message)
    {
        Member destinationMember = message.getDestination().getNode(
            getController(), true);
        if (!destinationMember.isCompletelyConnected()) {
            Type type = message.getType().equals(Type.SYN)
                ? Type.NACK
                : Type.EOF;
            RelayedMessage msg = new RelayedMessage(type, message
                .getDestination(), message.getSource(), message
                .getConnectionId(), null);
            receivedFrom.sendMessagesAsynchron(msg);
            if (isFiner()) {
                logFiner("Unable to relay message. "
                    + destinationMember.getNick()
                    + " not connected, sending EOF/NACK. msg: " + message);
            }
            return;
        }
        if (isFiner()) {
            logFiner("Relaying msg to " + destinationMember.getNick()
                + ". msg: " + message);
        }

        if (!printStats) {
            printStats = true;
            logInfo("Acting as relay. Received from " + receivedFrom.getNick()
                + ", msg: " + message);
            getController().scheduleAndRepeat(new TimerTask() {
                @Override
                public void run() {
                    logFine("Relay stats (RelayedCon): " + nRelayedMsgs
                        + " msgs relayed. " + counter);
                }
            }, 10000);
        }

        try {
            destinationMember.sendMessage(message);
            if (message.getPayload() != null) {
                counter.bytesTransferred(message.getPayload().length);
                nRelayedMsgs++;
            }
        } catch (ConnectionException e) {
            log.log(Level.WARNING,
                "Connection broken while relaying message to "
                    + destinationMember.getNick() + ". " + e);
            log.log(Level.FINER, e.toString(), e);
            RelayedMessage eofMsg = new RelayedMessage(Type.EOF, message
                .getDestination(), message.getSource(), message
                .getConnectionId(), null);
            receivedFrom.sendMessagesAsynchron(eofMsg);
        }
    }

    private void processMessageForMySelf(final Member receivedFrom,
        final RelayedMessage message)
    {
        // Deliver to RelayedConnectionHanlder of Remote member
        final AbstractRelayedConnectionHandler peer = resolveRelHan(message);

        switch (message.getType()) {
            case SYN :
                if (isFiner()) {
                    logFiner("SYN received from " + message.getSource().nick);
                }
                if (!getController().getIOProvider()
                    .getConnectionHandlerFactory().useRelayedConnections())
                {
                    RelayedMessage nackMsg = new RelayedMessage(Type.NACK,
                        getController().getMySelf().getInfo(), message
                            .getSource(), message.getConnectionId(), null);
                    receivedFrom.sendMessagesAsynchron(nackMsg);
                    return;
                }
                final AbstractRelayedConnectionHandler relHan = getController()
                    .getIOProvider().getConnectionHandlerFactory()
                    .createRelayedConnectionHandler(message.getSource(),
                        message.getConnectionId(), receivedFrom);

                pendingConHansLock.lock();
                pendingConHans.add(relHan);
                pendingConHansLock.unlock();

                Runnable initializer = new ConnectionInitializer(message,
                    relHan, receivedFrom);
                getController().getIOProvider().startIO(initializer);
                return;
            case ACK :
                if (isFiner()) {
                    logFiner("ACK received from " + message.getSource().nick);
                }
                if (peer != null) {
                    peer.setAckReceived(true);
                }
                return;
            case NACK :
                if (isFiner()) {
                    logFiner("NACK received from " + message.getSource().nick);
                }
                if (peer != null) {
                    peer.setNackReceived(true);
                    peer.shutdownWithMember();
                    removePedingRelayedConnectionHandler(peer);
                }
                return;
            case EOF :
                if (isFiner()) {
                    logFiner("EOF received from " + message.getSource().nick);
                }
                if (peer != null) {
                    peer.shutdownWithMember();
                    removePedingRelayedConnectionHandler(peer);
                }
                return;
        }

        Reject.ifFalse(message.getType().equals(Type.DATA_ZIPPED),
            "Only zipped data allowed");
        if (isFiner()) {
            logFiner("DATA received from " + message.getSource().nick + ": "
                + message);
        }

        // if (!sourceMember.isCompletelyConnected()) {
        // log()
        // .warn("Relayed connection was shutdown to " + sourceMember);
        // RelayedMessage eofMsg = new RelayedMessage(Type.EOF,
        // getController().getMySelf().getInfo(), message.getSource(),
        // null);
        // receivedFrom.sendMessagesAsynchron(eofMsg);
        // }

        if (peer == null) {
            if (isFiner()) {
                logFiner("Got unknown peer, while processing relayed message from "
                    + message.getSource().nick);
            }
            RelayedMessage eofMsg = new RelayedMessage(Type.EOF,
                getController().getMySelf().getInfo(), message.getSource(),
                message.getConnectionId(), null);
            receivedFrom.sendMessagesAsynchron(eofMsg);
            return;
        }

        // Actual relay of message
        peer.receiveRelayedMessage(message);
    }

    private AbstractRelayedConnectionHandler resolveRelHan(
        RelayedMessage message)
    {
        Member sourceMember = message.getSource()
            .getNode(getController(), true);
        ConnectionHandler peer = sourceMember.getPeer();

        if (peer == null) {
            // Search in pending con handlers
            try {
                pendingConHansLock.lock();
                for (AbstractRelayedConnectionHandler relHel : pendingConHans) {
                    if (relHel.getRemote().equals(message.getSource())
                        && (relHel.getConnectionId() == message
                            .getConnectionId()))
                    {
                        // Found in pending!
                        peer = relHel;
                        break;
                    }
                }
            } finally {
                pendingConHansLock.unlock();
            }
        }

        if (peer instanceof AbstractRelayedConnectionHandler) {
            return (AbstractRelayedConnectionHandler) peer;
        }

        if (message.getType().equals(Type.DATA_ZIPPED)
            && sourceMember.isInteresting())
        {
            logWarning("Unable to resolved pending con handler for "
                + message.getSource().nick + ", conId: "
                + message.getConnectionId() + ". Got these: " + pendingConHans
                + ". msg: " + message);
        }
        return null;
    }

    private void waitForAckOrNack(AbstractRelayedConnectionHandler relHan)
        throws ConnectionException
    {
        Waiter waiter = new Waiter(60L * 1000L);
        if (isFiner()) {
            logFiner("Waiting for ack on " + relHan);
        }
        while (!waiter.isTimeout()) {
            if (relHan.isAckReceived()) {
                if (isFiner()) {
                    logFiner("Got ack on " + relHan);
                }
                return;
            }
            if (relHan.isNackReceived()) {
                throw new ConnectionException(
                    "NACK received: Unable to open relayed connection to "
                        + relHan.getRemote().nick);
            }
            try {
                waiter.waitABit();
            } catch (RuntimeException e) {
                throw new ConnectionException("Shutdown", e);
            }

        }
        if (!relHan.isAckReceived()) {
            throw new ConnectionException(
                "Did not receive a ack after 60s from " + relHan);
        }
    }

    // Internal classes *******************************************************

    private final class ConnectionInitializer implements Runnable {
        private final RelayedMessage message;
        private final AbstractRelayedConnectionHandler relHan;
        private final Member receivedFrom;

        private ConnectionInitializer(RelayedMessage message,
            AbstractRelayedConnectionHandler relHan, Member receivedFrom)
        {
            this.message = message;
            this.relHan = relHan;
            this.receivedFrom = receivedFrom;
        }

        public void run() {
            try {
                if (isFiner()) {
                    logFiner("Sending ACK to " + message.getSource().nick);
                }
                RelayedMessage ackMsg = new RelayedMessage(Type.ACK,
                    getController().getMySelf().getInfo(), message.getSource(),
                    relHan.getConnectionId(), null);
                receivedFrom.sendMessagesAsynchron(ackMsg);
                relHan.init();
                getController().getNodeManager().acceptConnection(relHan);
            } catch (ConnectionException e) {
                relHan.shutdown();
                logFine("Unable to accept connection: " + relHan + ". "
                    + e.toString());
                logFiner("ConnectionException", e);
                RelayedMessage nackMsg = new RelayedMessage(Type.NACK,
                    getController().getMySelf().getInfo(), message.getSource(),
                    message.getConnectionId(), null);
                receivedFrom.sendMessagesAsynchron(nackMsg);
            } finally {
                removePedingRelayedConnectionHandler(relHan);
            }
        }
    }

    private class RelayConnectTask extends TimerTask {
        @Override
        public void run() {
            if (!getController().isStarted()) {
                return;
            }
            if (getRelay() != null) {
                return;
            }
            if (isRelay(getController().getMySelf().getInfo())) {
                return;
            }
            if (getController().isLanOnly()) {
                return;
            }
            if (!getController().getNodeManager().isStarted()) {
                return;
            }
            logFiner("Trying to connect to a Relay");
            for (Member canidate : getController().getNodeManager()
                .getNodesAsCollection())
            {
                if (!isRelay(canidate.getInfo())) {
                    continue;
                }
                if (canidate.isConnected()) {
                    continue;
                }
                if (canidate.isConnecting()) {
                    continue;
                }
                logFine("Triing to connect to relay: " + canidate + " id: "
                    + canidate.getId());
                canidate.markForImmediateConnect();
            }
        }
    }

    private class ServerIsRelayFilter implements RelayFilter {

        public final boolean isRelay(Member node) {
            // Default: Server also acts as relay. #1488
            return getController().getOSClient().isServer(node);
        }

    }
}
