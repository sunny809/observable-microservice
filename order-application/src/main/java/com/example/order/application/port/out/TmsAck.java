package com.example.order.application.port.out;

/**
 * Acknowledgment from the TMS service after receiving a dispatch instruction.
 *
 * <p>Indicates whether the TMS accepted or rejected the instruction.
 *
 * @see TmsPort#sendInstruction
 */
public class TmsAck {
    private final boolean accepted;
    private final String messageId;

    public TmsAck(boolean accepted, String messageId) {
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
