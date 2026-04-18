package com.fleetride.service;

import com.fleetride.domain.Customer;
import com.fleetride.repository.CustomerRepository;

import java.util.List;
import java.util.Optional;

public final class CustomerService {
    private final CustomerRepository repo;
    private final EncryptionService encryption;
    private final IdGenerator ids;

    public CustomerService(CustomerRepository repo, EncryptionService encryption, IdGenerator ids) {
        this.repo = repo;
        this.encryption = encryption;
        this.ids = ids;
    }

    public Customer create(String name, String phone, String rawPaymentToken) {
        String encrypted = rawPaymentToken == null ? null : encryption.encrypt(rawPaymentToken);
        Customer c = new Customer(ids.next(), name, phone, encrypted);
        repo.save(c);
        return c;
    }

    public Optional<Customer> find(String id) { return repo.findById(id); }

    public List<Customer> list() { return repo.findAll(); }

    public String maskedPaymentToken(Customer c) {
        if (c.encryptedPaymentToken() == null) return null;
        String raw = encryption.decrypt(c.encryptedPaymentToken());
        return MaskingUtil.maskLast4(raw);
    }

    public void delete(String id) { repo.delete(id); }
}
