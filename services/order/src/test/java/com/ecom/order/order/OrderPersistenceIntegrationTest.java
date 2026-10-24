package com.ecom.order.order;

import com.ecom.order.TestcontainersConfiguration;
import com.ecom.order.customer.CustomerClient;
import com.ecom.order.customer.CustomerResponse;
import com.ecom.order.kafka.OrderProducer;
import com.ecom.order.payment.PaymentClient;
import com.ecom.order.payment.PaymentResponse;
import com.ecom.order.orderline.OrderLineResponse;
import com.ecom.order.product.ProductClient;
import com.ecom.order.product.PurchaseRequest;
import com.ecom.order.product.PurchaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Real PostgreSQL + Flyway; the other services and Kafka are mocked. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderPersistenceIntegrationTest {

    @Autowired
    private OrderService service;

    @MockitoBean
    private CustomerClient customerClient;

    @MockitoBean
    private ProductClient productClient;

    @MockitoBean
    private PaymentClient paymentClient;

    @MockitoBean
    private OrderProducer orderProducer;

    @Test
    void createdOrder_isStoredWithLinesAndAuditDate() {
        when(customerClient.findById("c-9")).thenReturn(new CustomerResponse("c-9", "Ada", "Lovelace", "ada@example.com"));
        when(paymentClient.requestPayment(any())).thenReturn(new PaymentResponse(1, "PAY-1", "SUCCEEDED"));
        when(productClient.purchaseProducts(any())).thenReturn(List.of(
                new PurchaseResponse(4, "Dell U2723QE", "", new BigDecimal("579.00"), 1),
                new PurchaseResponse(10, "Sony WH-1000XM5", "", new BigDecimal("349.99"), 2)));

        OrderResponse created = service.createOrder(new OrderRequest("c-9", PaymentMethod.PAYPAL,
                List.of(new PurchaseRequest(4, 1), new PurchaseRequest(10, 2))));

        OrderResponse reloaded = service.findById(created.id());
        assertThat(reloaded.totalAmount()).isEqualByComparingTo("1278.98");
        assertThat(reloaded.createdDate()).isNotNull();
        assertThat(service.findLinesByOrderId(created.id()))
                .extracting(OrderLineResponse::productId, OrderLineResponse::quantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(4, 1),
                        org.assertj.core.groups.Tuple.tuple(10, 2));
    }
}
