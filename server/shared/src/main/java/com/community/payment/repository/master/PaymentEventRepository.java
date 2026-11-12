package com.community.payment.repository.master;

import com.community.payment.entity.PaymentEventEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** {@code PaymentEventEntity} 넣기({@code persist}) — 읽기는 {@link PaymentEventStore}·{@link PaymentAdminQueries} 의 QueryDSL */
public interface PaymentEventRepository extends IrobitRepository<PaymentEventEntity, Long> {
}
