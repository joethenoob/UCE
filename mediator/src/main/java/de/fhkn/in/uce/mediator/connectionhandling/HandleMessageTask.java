/*
 * Copyright (c) 2012 Alexander Diener,
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.fhkn.in.uce.mediator.connectionhandling;

import java.io.IOException;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhkn.in.uce.plugininterface.mediator.HandleMessage;
import de.fhkn.in.uce.plugininterface.message.NATAttributeTypeDecoder;
import de.fhkn.in.uce.stun.attribute.ErrorCode.STUNErrorCode;
import de.fhkn.in.uce.stun.header.STUNMessageMethod;
import de.fhkn.in.uce.stun.message.Message;
import de.fhkn.in.uce.stun.message.MessageReader;
import de.fhkn.in.uce.stun.message.MessageWriter;

public final class HandleMessageTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(HandleMessageTask.class);
    private final MessageReader messageReader;
    private final MessageWriter messageWriter;
    private final Socket socket;
    private final HandleMessage registerMessageHandler;
    private final HandleMessage deregisterMessageHandler;
    private final HandleMessage keepAliveMessageHandler;
    private final HandleMessage connectionRequestMessageHandler;

    public HandleMessageTask(final Socket socket) throws IOException {
        this.messageReader = MessageReader
                .createMessageReaderWithCustomAttributeTypeDecoder(new NATAttributeTypeDecoder());
        this.messageWriter = new MessageWriter(socket.getOutputStream());
        this.socket = socket;
        this.registerMessageHandler = new DefaultRegisterHandling();
        this.deregisterMessageHandler = new DefaultDeregisterHandling();
        this.keepAliveMessageHandler = new DefaultKeepAliveHandling();
        this.connectionRequestMessageHandler = new ConnectionRequestHandling();
    }

    @Override
    public void run() {
        while (this.socket.isConnected()) {
            try {
                final Message inMessage = this.receiveMessage(this.socket);
                this.handleMessage(inMessage);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private Message receiveMessage(final Socket socket) throws Exception {
        return this.messageReader.readSTUNMessage(socket.getInputStream());
    }

    private void handleMessage(final Message toHandle) throws Exception {
        try {
            if (toHandle.isMethod(STUNMessageMethod.REGISTER)) {
                this.handleRegisterMessage(toHandle);
            } else if (toHandle.isMethod(STUNMessageMethod.KEEP_ALIVE)) {
                this.handleKeepAliveMessae(toHandle);
            } else if (toHandle.isMethod(STUNMessageMethod.CONNECTION_REQUEST)) {
                this.handleConnectionRequestMessage(toHandle);
            } else if (toHandle.isMethod(STUNMessageMethod.DEREGISTER)) {
                this.handleDeregisterMessage(toHandle);
            } else {
                logger.error("Unknown message method {}", toHandle.getMessageMethod().encode()); //$NON-NLS-1$
            }
        } catch (final Exception e) {
            final String errorMessage = "Exception while handling message"; //$NON-NLS-1$
            logger.error(errorMessage, e);
            // TODO examine cause of the error to send correct error code
            this.sendFailureResponse(toHandle, STUNErrorCode.SERVER_ERROR, e.getMessage());
            throw e;
        }
        this.sendSuccessResponse(toHandle);

    }

    private void handleRegisterMessage(final Message registerMessage) throws Exception {
        this.registerMessageHandler.handleMessage(registerMessage, this.socket);
    }

    private void handleDeregisterMessage(final Message deregisterMessage) throws Exception {
        this.deregisterMessageHandler.handleMessage(deregisterMessage, this.socket);
    }

    private void handleKeepAliveMessae(final Message keepAliveMessage) throws Exception {
        this.keepAliveMessageHandler.handleMessage(keepAliveMessage, this.socket);
    }

    private void handleConnectionRequestMessage(final Message connectionRequestMessage) throws Exception {
        this.connectionRequestMessageHandler.handleMessage(connectionRequestMessage, this.socket);
    }

    private void sendSuccessResponse(final Message toRespond) throws Exception {
        final Message successResponse = toRespond.buildSuccessResponse();
        this.messageWriter.writeMessage(successResponse);
    }

    private void sendFailureResponse(final Message toRespond, final STUNErrorCode errorCode, final String errorReason)
            throws Exception {
        final Message failureResponse = toRespond.buildFailureResponse(errorCode, errorReason);
        this.messageWriter.writeMessage(failureResponse);
    }
}