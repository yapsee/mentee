package com.ecom.order.order;

import com.ecom.order.customer.CustomerClient;
import com.ecom.order.customer.CustomerResponse;
import com.ecom.order.exception.BusinessException;
import com.ecom.order.kafka.OrderConfirmation;
import com.ecom.order.kafka.OrderProducer;
import com.ecom.order.orderline.OrderLineRepository;
import com.ecom.order.payment.PaymentClient;
import com.ecom.order.payment.PaymentRequest;
import com.ecom.order.payment.PaymentResponse;
import com.ecom.order.product.ProductClient;
import com.ecom.order.product.PurchaseRequest;
import com.ecom.order.product.PurchaseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderLineRepository orderLineRepository;
    @Mock
    private CustomerClient customerClient;
    @Mock
    private ProductClient productClient;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private OrderProducer orderProducer;
    @Spy
    private OrderMapper mapper;
    @InjectMocks
    private OrderService service;

    private final CustomerResponse ada = new CustomerResponse("c-1", "Ada", "Lovelace", "ada@example.com");
    private final OrderRequest request = new OrderRequest("c-1", PaymentMethod.VISA, List.of(
            new PurchaseRequest(1, 2), new PurchaseRequest(7, 1)));
    private final List<PurchaseResponse> purchased = List.of(
            new PurchaseResponse(1, "Keychron K2", "", new BigDecimal("89.99"), 2),
            new PurchaseResponse(7, "MX Master 3S", "", new BigDecimal("99.99"), 1));

    private void orderSavesWithId42() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(42);
            return order;
        });
    }

    @Test
    void createOrder_savesPaysAndPublishes() {
        when(customerClient.findById("c-1")).thenReturn(ada);
        when(productClient.purchaseProducts(request.products())).thenReturn(purchased);
        orderSavesWithId42();
        when(paymentClient.requestPayment(any())).thenReturn(new PaymentResponse(7, "PAY-1", "SUCCEEDED"));

        OrderResponse response = service.createOrder(request);

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("279.97");
        assertThat(saved.getValue().getOrderLines())
                .extracting(line -> line.getProductId() + "x" + line.getQuantity())
                .containsExactly("1x2", "7x1");
        assertThat(response.reference()).matches("ORD-\\d{8}-[0-9A-F]{6}");

        ArgumentCaptor<PaymentRequest> payment = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient).requestPayment(payment.capture());
        assertThat(payment.getValue().orderId()).isEqualTo(42);
        assertThat(payment.getValue().amount()).isEqualByComparingTo("279.97");
        assertThat(payment.getValue().customer().email()).isEqualTo("ada@example.com");

        ArgumentCaptor<OrderConfirmation> event = ArgumentCaptor.forClass(OrderConfirmation.class);
        verify(orderProducer).sendOrderConfirmation(event.capture());
        assertThat(event.getValue().orderReference()).isEqualTo(response.reference());
        assertThat(event.getValue().customer()).isEqualTo(ada);
        assertThat(event.getValue().products()).isEqualTo(purchased);
    }

    @Test
    void unknownCustomer_neverTouchesStock() {
        when(customerClient.findById("c-1")).thenThrow(
                new BusinessException(HttpStatus.NOT_FOUND, "Customer not found", "No customer found with id c-1"));

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No customer found with id c-1");
        verifyNoInteractions(productClient, paymentClient, orderProducer);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void productRejected_savesNothingPaysNothing() {
        when(customerClient.findById("c-1")).thenReturn(ada);
        when(productClient.purchaseProducts(any())).thenThrow(new BusinessException(
                HttpStatus.CONFLICT, "Insufficient stock", "Not enough stock for 1 product(s)",
                Map.of("shortages", List.of("Keychron K2 (id 1): requested 2, available 0"))));

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(paymentClient, orderProducer);
    }

    @Test
    void paymentFails_noConfirmationIsPublished() {
        when(customerClient.findById("c-1")).thenReturn(ada);
        when(productClient.purchaseProducts(any())).thenReturn(purchased);
        orderSavesWithId42();
        when(paymentClient.requestPayment(any())).thenThrow(new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", "payment-service is not reachable"));

        assertThatThrownBy(() -> service.createOrder(request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(orderProducer);
    }
}
