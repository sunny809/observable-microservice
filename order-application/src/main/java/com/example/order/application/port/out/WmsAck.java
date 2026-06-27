package com.example.order.application.port.out;

/**
 * Acknowledgment from the WMS service after receiving a shipment instruction.
 *
 * <p>Indicates whether the WMS accepted or rejected the instruction.
 *
 * @see com.example.order.application.port.out.WmsPort#sendInstruction
 */
public class WmsAck {
    private final boolean accepted;
    private final String messageId;

    public WmsAck(boolean accepted, String messageId) {
        this.accepted = accepted;
        this.messageId = messageId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getMessageId() {
        return messageId;
    }
}
