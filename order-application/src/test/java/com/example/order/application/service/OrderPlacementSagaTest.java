package com.example.order.application.service;

import com.example.order.application.domain.*;
import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.in.OrderPlacedResult;
import com.example.order.application.port.in.PlaceOrderCommand;
import com.example.order.application.port.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class OrderPlacementSagaTest {

    private static final long ASYNC_TIMEOUT_SECONDS = 5;

    private OrderRepositoryPort orderRepository;
    private InventoryPort inventoryPort;
    private SagaLogPort sagaLogPort;
    private DomainEventPublisher eventPublisher;
    private WmsPort wmsPort;
    private InventoryConfirmationScheduler confirmationScheduler;
    private TransactionTemplate transactionTemplate;
    private IdempotencyCachePort idempotencyCache;
    private OrderPlacementSaga saga;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepositoryPort.class);
        inventoryPort = mock(InventoryPort.class);
        sagaLogPort = mock(SagaLogPort.class);
        eventPublisher = mock(DomainEventPublisher.class);
        wmsPort = mock(WmsPort.class);
        confirmationScheduler = mock(InventoryConfirmationScheduler.class);
        transactionTemplate = mock(TransactionTemplate.class);
        idempotencyCache = mock(IdempotencyCachePort.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        saga = new OrderPlacementSaga(orderRepository, inventoryPort, sagaLogPort,
                eventPublisher, wmsPort, confirmationScheduler, transactionTemplate, idempotencyCache);
    }

    private PlaceOrderCommand createCommand() {
        return new PlaceOrderCommand("cust-1", List.of(new OrderItem("SKU-1", 2)), "idem-key-1");
    }

    private InventoryReservation createReservation() {
        return new InventoryReservation("resv-123", "SKU-1", 2, "dummy-order-id",
                ReservationStatus.PENDING, Instant.now(), null);
    }

    @Test
    @DisplayName("happy path: should save order, record saga log, and publish WMS event")
    void testPlaceOrderHappyPath() {
        PlaceOrderCommand command = createCommand();
        InventoryReservation reservation = createReservation();

        when(orderRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(inventoryPort.occupy(any(ReservationRequest.class))).thenReturn(reservation);
        when(wmsPort.sendInstruction(any(WmsShipmentInstruction.class)))
                .thenReturn(CompletableFuture.completedFuture(new WmsAck(true, "ack-1")));

        OrderPlacedResult result = saga.placeOrder(command);

        assertNotNull(result.getOrderId());
        assertEquals(OrderStatus.CREATED, result.getStatus());
        verify(orderRepository).save(any(Order.class));
        verify(sagaLogPort).recordStep(anyString(), eq("ORDER_CREATED"), anyString());
        verify(eventPublisher).publish(any(WmsInstructionRequiredEvent.class));
    }

    @Test
    @DisplayName("duplicate idempotency key should throw DuplicateOrderException and not call inventory")
    void testPlaceOrderDuplicateOrderThrows() {
        PlaceOrderCommand command = createCommand();
        Order existingOrder = new Order("ord-existing", "cust-1", List.of(new OrderItem("SKU-1", 2)),
                OrderStatus.CREATED, "idem-key-1", "resv-1", Instant.now());

        when(orderRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(existingOrder));

        assertThrows(DuplicateOrderException.class, () -> saga.placeOrder(command));

        verify(inventoryPort, never()).occupy(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("insufficient inventory should throw InsufficientInventoryException and not save order")
    void testPlaceOrderInsufficientInventoryThrows() {
        PlaceOrderCommand command = createCommand();

        when(orderRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(inventoryPort.occupy(any(ReservationRequest.class))).thenReturn(null);

        InsufficientInventoryException ex = assertThrows(InsufficientInventoryException.class,
                () -> saga.placeOrder(command));
        assertTrue(ex.getMessage().contains("SKU-1"));

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("onWmsRequired with accepted WMS should confirm inventory and update status to WMS_ACKED")
    void testOnWmsAcceptedConfirmsInventory() throws Exception {
        InventoryReservation reservation = createReservation();
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> { latch.countDown(); return null; })
                .when(inventoryPort).confirm(any(ConfirmReservationCommand.class));

        WmsInstructionRequiredEvent event = new WmsInstructionRequiredEvent(
                "ord-1", new WmsShipmentInstruction("ord-1", "resv-123"), reservation);

        when(wmsPort.sendInstruction(any(WmsShipmentInstruction.class)))
                .thenReturn(CompletableFuture.completedFuture(new WmsAck(true, "ack-1")));

        saga.onWmsRequired(event);

        assertTrue(latch.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        verify(inventoryPort).confirm(any(ConfirmReservationCommand.class));
        verify(orderRepository).updateStatus(eq("ord-1"), eq(OrderStatus.WMS_ACKED));
        verify(sagaLogPort).recordStep(eq("ord-1"), eq("WMS_ACKED"), anyString());
    }

    @Test
    @DisplayName("onWmsRequired with rejected WMS should release inventory and update status to REJECTED")
    void testOnWmsRejectedReleasesInventory() throws Exception {
        InventoryReservation reservation = createReservation();
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> { latch.countDown(); return null; })
                .when(inventoryPort).release(anyString());

        WmsInstructionRequiredEvent event = new WmsInstructionRequiredEvent(
                "ord-1", new WmsShipmentInstruction("ord-1", "resv-123"), reservation);

        when(wmsPort.sendInstruction(any(WmsShipmentInstruction.class)))
                .thenReturn(CompletableFuture.completedFuture(new WmsAck(false, "reject-1")));

        saga.onWmsRequired(event);

        assertTrue(latch.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        verify(inventoryPort).release("resv-123");
        verify(orderRepository).updateStatus(eq("ord-1"), eq(OrderStatus.REJECTED));
        verify(sagaLogPort).recordCompensation(eq("ord-1"), eq("resv-123"), anyString());
    }

    @Test
    @DisplayName("onWmsRequired with transport failure should release inventory and update status to REJECTED")
    void testOnWmsTransportFailureReleasesInventory() throws Exception {
        InventoryReservation reservation = createReservation();
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> { latch.countDown(); return null; })
                .when(inventoryPort).release(anyString());

        WmsInstructionRequiredEvent event = new WmsInstructionRequiredEvent(
                "ord-1", new WmsShipmentInstruction("ord-1", "resv-123"), reservation);

        CompletableFuture<WmsAck> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("timeout"));
        when(wmsPort.sendInstruction(any(WmsShipmentInstruction.class))).thenReturn(failedFuture);

        saga.onWmsRequired(event);

        assertTrue(latch.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        verify(inventoryPort).release("resv-123");
        verify(orderRepository).updateStatus(eq("ord-1"), eq(OrderStatus.REJECTED));
        verify(sagaLogPort).recordCompensation(eq("ord-1"), eq("resv-123"), anyString());
    }

    @Test
    @DisplayName("onWmsRequired should call confirmation scheduler after successful WMS instruction")
    void testOnWmsRequiredCallsConfirmationScheduler() throws Exception {
        InventoryReservation reservation = createReservation();
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> { latch.countDown(); return null; })
                .when(confirmationScheduler).scheduleConfirmation(any());

        WmsInstructionRequiredEvent event = new WmsInstructionRequiredEvent(
                "ord-1", new WmsShipmentInstruction("ord-1", "resv-123"), reservation);

        when(wmsPort.sendInstruction(any(WmsShipmentInstruction.class)))
                .thenReturn(CompletableFuture.completedFuture(new WmsAck(true, "ack-1")));

        saga.onWmsRequired(event);

        assertTrue(latch.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        verify(confirmationScheduler).scheduleConfirmation(reservation);
    }

    @Test
    @DisplayName("partial reservation failure should release previously reserved items")
    void testPlaceOrderPartialReservationFailureReleasesSucceededReservations() {
        PlaceOrderCommand command = new PlaceOrderCommand("cust-1",
                List.of(new OrderItem("SKU-1", 2), new OrderItem("SKU-2", 3)), "idem-key-partial");

        InventoryReservation firstReservation = createReservation();
        AtomicInteger callCount = new AtomicInteger(0);

        when(orderRepository.findByIdempotencyKey("idem-key-partial")).thenReturn(Optional.empty());
        when(inventoryPort.occupy(any(ReservationRequest.class))).thenAnswer(invocation -> {
            if (callCount.getAndIncrement() == 0) {
                return firstReservation;
            }
            return null;
        });

        InsufficientInventoryException ex = assertThrows(InsufficientInventoryException.class,
                () -> saga.placeOrder(command));
        assertTrue(ex.getMessage().contains("SKU-2"));

        verify(inventoryPort).release("resv-123");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("release failure during compensation should log to saga log")
    void testReleaseFailureDuringCompensationLogsToSagaLog() throws Exception {
        InventoryReservation reservation = new InventoryReservation("resv-123", "SKU-1", 2, "ord-1",
                ReservationStatus.PENDING, Instant.now(), null);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> { latch.countDown(); return null; })
                .when(sagaLogPort).recordCompensation(eq("ord-1"), eq("resv-123"), contains("Release failed"));

        doThrow(new RuntimeException("release failed"))
                .when(inventoryPort).release(anyString());

        WmsInstructionRequiredEvent event = new WmsInstructionRequiredEvent(
                "ord-1", new WmsShipmentInstruction("ord-1", "resv-123"), reservation);

        when(wmsPort.sendInstruction(any(WmsShipmentInstruction.class)))
                .thenReturn(CompletableFuture.completedFuture(new WmsAck(false, "reject-1")));

        saga.onWmsRequired(event);

        assertTrue(latch.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        verify(sagaLogPort).recordCompensation(eq("ord-1"), eq("resv-123"),
                contains("Release failed"));
    }
}
