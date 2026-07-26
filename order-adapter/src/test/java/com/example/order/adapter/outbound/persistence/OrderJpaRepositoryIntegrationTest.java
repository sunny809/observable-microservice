package com.order.demo.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OrderJpaRepositoryIntegrationTest {

    private EntityManagerFactory emf;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        emf = Persistence.createEntityManagerFactory("orderTestPU");
        em = emf.createEntityManager();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

    @Test
    void testPersistAndFindById() {
        em.getTransaction().begin();
        OrderEntity entity = new OrderEntity("ord-1", "cust-1", "idem-1", "resv-1",
                "CREATED", LocalDateTime.now());
        entity.setItems("[{\"sku\":\"SKU-1\",\"quantity\":2}]");
        em.persist(entity);
        em.getTransaction().commit();

        em.clear();
        OrderEntity found = em.find(OrderEntity.class, "ord-1");
        assertThat(found).isNotNull();
        assertThat(found.getCustomerId()).isEqualTo("cust-1");
        assertThat(found.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(found.getStatus()).isEqualTo("CREATED");
    }

    @Test
    void testFindByIdempotencyKey() {
        em.getTransaction().begin();
        OrderEntity entity = new OrderEntity("ord-2", "cust-2", "idem-unique", "resv-2",
                "CREATED", LocalDateTime.now());
        em.persist(entity);
        em.getTransaction().commit();

        em.clear();
        List<OrderEntity> results = em.createQuery(
                "SELECT o FROM OrderEntity o WHERE o.idempotencyKey = :key", OrderEntity.class)
                .setParameter("key", "idem-unique")
                .getResultList();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("ord-2");
    }

    @Test
    void testUpdateStatus() {
        em.getTransaction().begin();
        OrderEntity entity = new OrderEntity("ord-3", "cust-3", "idem-3", "resv-3",
                "CREATED", LocalDateTime.now());
        em.persist(entity);
        em.getTransaction().commit();

        em.getTransaction().begin();
        em.createQuery("UPDATE OrderEntity o SET o.status = :status WHERE o.id = :id")
                .setParameter("status", "WMS_ACKED")
                .setParameter("id", "ord-3")
                .executeUpdate();
        em.getTransaction().commit();

        em.clear();
        OrderEntity found = em.find(OrderEntity.class, "ord-3");
        assertThat(found.getStatus()).isEqualTo("WMS_ACKED");
    }
}