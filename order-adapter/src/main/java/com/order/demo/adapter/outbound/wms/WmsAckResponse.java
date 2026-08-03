package com.order.demo.adapter.outbound.wms;

import com.order.demo.application.port.out.WmsAck;

/**
 * Adapter-layer DTO for deserializing WMS API responses.
 * Keeps Jackson concern out of the domain layer.
 */
public class WmsAckResponse {
    private boolean accepted;
    private String messageId;

    // No-arg constructor for Jackson
    public WmsAckResponse() {
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public WmsAck toDomain() {
        return new WmsAck(accepted, messageId);
    }
}
