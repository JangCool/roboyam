package com.community.payment.repository.master;

import com.community.payment.entity.PaymentWebhookLogEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** {@code PaymentWebhookLogEntity} 넣기({@code persist}) — 읽기는 {@link PaymentAdminQueries} 의 QueryDSL */
public interface PaymentWebhookLogRepository extends RoboyamRepository<PaymentWebhookLogEntity, Long> {
}
