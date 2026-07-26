package com.order.demo.adapter.outbound.wms;

import com.order.demo.application.port.out.WmsAck;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WmsAckResponseTest {

    @Test
    void testToDomainMapsAcceptedTrue() {
        WmsAckResponse response = new WmsAckResponse();
        response.setAccepted(true);
        response.setMessageId("msg-1");

        WmsAck ack = response.toDomain();
        assertTrue(ack.isAccepted());
        assertEquals("msg-1", ack.getMessageId());
    }

    @Test
    void testToDomainMapsAcceptedFalse() {
        WmsAckResponse response = new WmsAckResponse();
        response.setAccepted(false);
        response.setMessageId("msg-2");

        WmsAck ack = response.toDomain();
        assertFalse(ack.isAccepted());
        assertEquals("msg-2", ack.getMessageId());
    }

    @Test
    void testNoArgConstructorDefaults() {
        WmsAckResponse response = new WmsAckResponse();
        assertFalse(response.isAccepted());
        assertNull(response.getMessageId());
    }
}