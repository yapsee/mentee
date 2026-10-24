package com.ecom.order.order;

import com.ecom.order.customer.CustomerClient;
import com.ecom.order.exception.BusinessException;
import com.ecom.order.orderline.OrderLineRepository;
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
    @Spy
    private OrderMapper mapper;
    @InjectMocks
    private OrderService service;

    private final OrderRequest request = new OrderRequest("c-1", PaymentMethod.VISA, List.of(
            new PurchaseRequest(1, 2), new PurchaseRequest(7, 1)));

    @Test
    void createOrder_totalsServerPricesAndSavesLines() {
        when(customerClient.existsById("c-1")).thenReturn(true);
        when(productClient.purchaseProducts(request.products())).thenReturn(List.of(
                new PurchaseResponse(1, "Keychron K2", "", new BigDecimal("89.99"), 2),
                new PurchaseResponse(7, "MX Master 3S", "", new BigDecimal("99.99"), 1)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(42);
            return order;
        });

        OrderResponse response = service.createOrder(request);

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("279.97");
        assertThat(saved.getValue().getOrderLines())
                .extracting(line -> line.getProductId() + "x" + line.getQuantity())
                .containsExactly("1x2", "7x1");
        assertThat(saved.getValue().getOrderLines()).allMatch(line -> line.getOrder() == saved.getValue());
        assertThat(response.id()).isEqualTo(42);
        assertThat(response.reference()).matches("ORD-\\d{8}-[0-9A-F]{6}");
    }

    @Test
    void createOrder_unknownCustomer_neverTouchesStock() {
        when(customerClient.existsById("c-1")).thenReturn(false);

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No customer found with id c-1");
        verifyNoInteractions(productClient);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_productRejected_savesNothing() {
        when(customerClient.existsById("c-1")).thenReturn(true);
        when(productClient.purchaseProducts(any())).thenThrow(new BusinessException(
                HttpStatus.CONFLICT, "Insufficient stock", "Not enough stock for 1 product(s)",
                Map.of("shortages", List.of("Keychron K2 (id 1): requested 2, available 0"))));

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(orderRepository, never()).save(any());
    }
}
