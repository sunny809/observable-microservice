package com.example.order.application.port.out;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WmsAckTest {

    @Test
    void testAcceptedAck() {
        WmsAck ack = new WmsAck(true, "msg-1");
        assertTrue(ack.isAccepted());
        assertEquals("msg-1", ack.getMessageId());
    }

    @Test
    void testRejectedAck() {
        WmsAck ack = new WmsAck(false, "msg-2");
        assertFalse(ack.isAccepted());
        assertEquals("msg-2", ack.getMessageId());
    }

    @Test
    void testNullMessageIdAccepted() {
        WmsAck ack = new WmsAck(true, null);
        assertTrue(ack.isAccepted());
        assertNull(ack.getMessageId());
    }
}
