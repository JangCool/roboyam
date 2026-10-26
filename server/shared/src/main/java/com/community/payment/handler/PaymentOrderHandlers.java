package com.community.payment.handler;

import com.community.payment.dto.PaymentOrderType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 주문 종류 → 처리기. <b>종류마다 처리기는 하나</b> — 둘 이상이면 서버가 뜨지 않는다(기동 검사). 처리기가 없는 종류(아직 도메인이 없는 충전·공구)는 결제를 만들 수 없고
 * ({@code PaymentOrderService.create} 가 거절), 이미 있는 결제의 확정·취소도 처리기 없이는 하지 않는다(설정 오류로 본다 — 예외).
 */
@Component
public class PaymentOrderHandlers {

    private final Map<PaymentOrderType, PaymentOrderHandler> byType;

    public PaymentOrderHandlers(List<PaymentOrderHandler> handlers) {
        Map<PaymentOrderType, PaymentOrderHandler> map = new EnumMap<>(PaymentOrderType.class);
        for (PaymentOrderHandler handler : handlers) {
            if (handler.orderType() == null) {
                throw new IllegalStateException("결제 처리기의 주문 종류가 비었다: " + handler.getClass().getName());
            }
            PaymentOrderHandler previous = map.put(handler.orderType(), handler);
            if (previous != null) {
                throw new IllegalStateException("결제 주문 종류마다 처리기는 하나다: " + handler.orderType());
            }
        }
        this.byType = Collections.unmodifiableMap(map);
    }

    public Optional<PaymentOrderHandler> find(PaymentOrderType type) {
        return Optional.ofNullable(byType.get(type));
    }

    /** 그 종류의 처리기 — 없으면 {@link IllegalStateException}(설정 오류) */
    public PaymentOrderHandler require(PaymentOrderType type) {
        PaymentOrderHandler handler = byType.get(type);
        if (handler == null) {
            throw new IllegalStateException("결제 주문 종류의 처리기가 없다: " + type);
        }
        return handler;
    }
}
