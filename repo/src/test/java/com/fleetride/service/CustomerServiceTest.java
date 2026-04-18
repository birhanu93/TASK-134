package com.fleetride.service;

import com.fleetride.domain.Customer;
import com.fleetride.repository.InMemoryCustomerRepository;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CustomerServiceTest {

    @Test
    void createAndFindMaskAndDelete() {
        InMemoryCustomerRepository repo = new InMemoryCustomerRepository();
        EncryptionService enc = new EncryptionService("key");
        AtomicInteger n = new AtomicInteger();
        CustomerService svc = new CustomerService(repo, enc, () -> "c-" + n.incrementAndGet());
        Customer c = svc.create("Alice", "555-0100", "4111111111111234");
        assertEquals("Alice", c.name());
        assertEquals("c-1", c.id());
        assertNotNull(c.encryptedPaymentToken());
        assertEquals("************1234", svc.maskedPaymentToken(c));

        Customer noToken = svc.create("Bob", "555-0200", null);
        assertNull(noToken.encryptedPaymentToken());
        assertNull(svc.maskedPaymentToken(noToken));

        assertTrue(svc.find(c.id()).isPresent());
        assertEquals(2, svc.list().size());
        svc.delete(c.id());
        assertFalse(svc.find(c.id()).isPresent());
    }
}
