package com.example.order.application.port.out;

public class WmsAck {
    private boolean accepted;
    private String messageId;

    public WmsAck() {
        this.accepted = false;
        this.messageId = null;
    }

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
