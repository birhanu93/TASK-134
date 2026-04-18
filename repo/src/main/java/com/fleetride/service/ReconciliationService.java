package com.fleetride.service;

import com.fleetride.domain.Order;
import com.fleetride.domain.Payment;
import com.fleetride.repository.OrderRepository;
import com.fleetride.repository.PaymentRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ReconciliationService {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentService paymentService;

    public ReconciliationService(OrderRepository orders, PaymentRepository payments,
                                 PaymentService paymentService) {
        this.orders = orders;
        this.payments = payments;
        this.paymentService = paymentService;
    }

    public Path exportCsv(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("order_id,state,fare_total,cancel_fee,total_paid,balance");
        for (Order o : orders.findAll()) {
            String total = o.fare() == null ? "0.00" : o.fare().total().amount().toPlainString();
            String paid = paymentService.totalPaid(o.id()).amount().toPlainString();
            String bal = paymentService.balanceFor(o).amount().toPlainString();
            lines.add(String.join(",",
                    escape(o.id()),
                    o.state().name(),
                    total,
                    o.cancellationFee().amount().toPlainString(),
                    paid,
                    bal));
        }
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    public Path exportPaymentsCsv(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("payment_id,order_id,kind,tender,amount,recorded_at");
        for (Payment p : payments.findAll()) {
            lines.add(String.join(",",
                    escape(p.id()),
                    escape(p.orderId()),
                    p.kind().name(),
                    p.tender().name(),
                    p.amount().amount().toPlainString(),
                    ISO.format(p.recordedAt())));
        }
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    private String escape(String s) {
        if (s.contains(",") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
