package io.github.fiftieshousewife.airgap.agent;

import java.net.SocketAddress;

/** Where the SCTP send hook sends its calls. Loaded by the bootstrap class loader, like {@link Gate}. */
public final class SctpGate {

    private SctpGate() {
    }

    /**
     * An SCTP message is being sent, which may make an association with the address it names.
     *
     * @param messageInfo the message's {@code MessageInfo}
     */
    public static void sctpSend(final Object messageInfo) {
        Gate.stopIfProbing(Kind.TCP);
        if (messageInfo == null || Gate.DECIDING.get() != null) {
            return;
        }
        final Object address;
        Gate.DECIDING.set(Boolean.TRUE);
        try {
            address = messageInfo.getClass().getMethod("address").invoke(messageInfo);
        } catch (ReflectiveOperationException | RuntimeException notAMessage) {
            return;
        } finally {
            Gate.DECIDING.remove();
        }
        if (address instanceof SocketAddress) {
            Gate.tcp((SocketAddress) address);
        }
    }
}
