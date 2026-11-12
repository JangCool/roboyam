package com.community.payment.repository.master;

import com.community.payment.entity.PaymentOrderEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** {@code PaymentOrderEntity} 넣기({@code persist}) — 읽기와 조건부 고치기는 {@link PaymentOrderStore} 의 QueryDSL */
public interface PaymentOrderRepository extends IrobitRepository<PaymentOrderEntity, Long> {
}
