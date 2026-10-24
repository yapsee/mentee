package com.ecom.order.order;

import com.ecom.order.orderline.OrderLine;
import com.ecom.order.orderline.OrderLineResponse;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getReference(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.getCustomerId(),
                order.getCreatedDate()
        );
    }

    public OrderLineResponse toLineResponse(OrderLine line) {
        return new OrderLineResponse(line.getId(), line.getProductId(), line.getQuantity());
    }
}
