package com.securesoc.security;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Enforces inbound WebSocket message restrictions.
 *
 * Client-originated SEND and MESSAGE frames are rejected because this
 * application does not support client-to-server STOMP publishing.
 * Server-side publishing through the broker channel remains unaffected.
 *
 * SUBSCRIBE frames require an authenticated Principal. Destination-level
 * authorization and Faculty laboratory scoping are enforced separately.
 */
@Component
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    @Override
    @Nullable
    public Message<?> preSend(
            @NonNull Message<?> message,
            @NonNull MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // Client-originated publishing is not supported by this application.
        // Drop SEND, forged MESSAGE commands, and message-type frames.
        if (isClientPublish(accessor)) {
            return null;
        }

        // Require an authenticated principal for subscriptions.
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                && accessor.getUser() == null) {
            return null;
        }

        return message;
    }

    private static boolean isClientPublish(
            StompHeaderAccessor accessor) {

        StompCommand command = accessor.getCommand();

        return StompCommand.SEND.equals(command)
                || StompCommand.MESSAGE.equals(command)
                || SimpMessageType.MESSAGE.equals(
                        accessor.getMessageType());
    }
}
