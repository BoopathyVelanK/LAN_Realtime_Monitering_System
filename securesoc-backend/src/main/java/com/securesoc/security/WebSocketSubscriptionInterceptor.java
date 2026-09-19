package com.securesoc.security;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Coarse SUBSCRIBE-level gate: drops any STOMP SUBSCRIBE frame that lacks
 * an authenticated Principal. In practice this should never fire -
 * WebSocketAuthHandshakeInterceptor already rejects the handshake itself
 * for any unauthenticated connection attempt, so every session reaching
 * this point already carries a Principal from PrincipalHandshakeHandler -
 * but this is defense-in-depth against any future change to that chain.
 *
 * This is deliberately the ONLY rule enforced here. It does not, and is
 * not meant to, decide which destinations a given role may subscribe to
 * (there is currently exactly one class of destination - each user's own
 * /user/queue/... - and every authenticated user, Admin or Faculty, is
 * allowed to subscribe to their own queues). Per-event Faculty/lab scope
 * is enforced separately and independently at publish time by
 * ScopedWebSocketDelivery: a valid SUBSCRIBE here only ever results in
 * receiving messages a publisher chose to deliver to this specific user -
 * subscribing grants no access by itself.
 */
@Component
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    @Override
    @Nullable
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand()) && accessor.getUser() == null) {
            return null;
        }
        return message;
    }
}
