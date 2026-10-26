package com.ecom.order.order;

import com.ecom.order.customer.CustomerClient;
import com.ecom.order.customer.CustomerResponse;
import com.ecom.order.exception.OrderNotFoundException;
import com.ecom.order.kafka.OrderConfirmation;
import com.ecom.order.kafka.OrderProducer;
import com.ecom.order.orderline.OrderLine;
import com.ecom.order.orderline.OrderLineRepository;
import com.ecom.order.orderline.OrderLineResponse;
import com.ecom.order.payment.PaymentClient;
import com.ecom.order.payment.PaymentRequest;
import com.ecom.order.payment.PaymentResponse;
import com.ecom.order.product.ProductClient;
import com.ecom.order.product.PurchaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;
    private final OrderProducer orderProducer;
    private final OrderMapper mapper;

    /**
     * Not @Transactional on purpose: never hold a database transaction open while waiting on
     * other services over the network. Only the final save is a (short) transaction.
     */
    public OrderResponse createOrder(OrderRequest request) {
        // 1. The customer must exist; we also need their name and email for payment and emails.
        //    404 from customer-service -> BusinessException "Customer not found" (FeignConfig).
        CustomerResponse customer = customerClient.findById(request.customerId());

        // 2. Reserve the stock and get the real prices (product-service, RestClient).
        //    From here on, stock is decremented in ANOTHER database: see docs/days/day-06.md §4.
        List<PurchaseResponse> purchased = productClient.purchaseProducts(request.products());

        // 3. Total from product-service prices, never from the client.
        BigDecimal total = purchased.stream()
                .map(p -> p.price().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Save the order and its lines in one local transaction (cascade).
        Order order = Order.builder()
                .reference(newReference())
                .customerId(request.customerId())
                .paymentMethod(request.paymentMethod())
                .totalAmount(total)
                .build();
        purchased.forEach(p -> order.addLine(OrderLine.builder()
                .productId(p.productId())
                .quantity(p.quantity())
                .build()));
        Order saved;
        try {
            saved = orderRepository.save(order);
        } catch (RuntimeException e) {
            // Stock was already taken in product-service's database and our save failed: the two
            // databases now disagree. No rollback can cross services. Until compensation exists
            // (see day-06.md §4), at least leave an alert with everything needed to fix it by hand.
            log.error("INCONSISTENCY: stock purchased but order NOT saved. customer={}, lines={}. "
                    + "Release this stock manually.", request.customerId(), request.products(), e);
            throw e;
        }
        log.info("Order {} saved for customer {}: {} line(s), total {}",
                saved.getReference(), saved.getCustomerId(), purchased.size(), total);

        // 5. Pay (payment-service, OpenFeign). payment-service publishes PaymentConfirmation itself.
        PaymentResponse payment;
        try {
            payment = paymentClient.requestPayment(new PaymentRequest(
                    total, request.paymentMethod(), saved.getId(), saved.getReference(), customer));
        } catch (RuntimeException e) {
            // The order exists and the stock is taken, but it is not paid. Safe to retry later:
            // payment-service is idempotent on the order reference.
            log.error("INCONSISTENCY: order {} saved but payment FAILED. Retry the payment for this order.",
                    saved.getReference(), e);
            throw e;
        }
        log.info("Order {} paid: payment {} {}", saved.getReference(), payment.reference(), payment.status());

        // 6. Announce it (Kafka, asynchronous). notification-service sends the email from this event.
        orderProducer.sendOrderConfirmation(new OrderConfirmation(
                saved.getReference(), total, request.paymentMethod(), customer, purchased));

        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Integer id) {
        return orderRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<OrderLineResponse> findLinesByOrderId(Integer orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
        return orderLineRepository.findAllByOrderIdOrderById(orderId).stream()
                .map(mapper::toLineResponse)
                .toList();
    }

    /** e.g. ORD-20261018-3F9A1C. Readable for humans (emails, support), unique thanks to the DB constraint. */
    private static String newReference() {
        return "ORD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
