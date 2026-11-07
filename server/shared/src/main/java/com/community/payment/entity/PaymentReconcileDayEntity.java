package com.community.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 대사한 날 한 줄 — shared V97(상태 목록은 V98)의 {@code payment_reconcile_days}(KST 날짜가 키). 줄이 없는 날은 「아직 안 함」이다.
 * 쓰기는 같은 날을 다시 대사하면 줄을 덮어쓰는 {@code INSERT … ON CONFLICT (day) DO UPDATE}(네이티브)뿐이고, 읽기는 저장소의 QueryDSL 이다.
 */
@Entity
@Table(name = "payment_reconcile_days")
public class PaymentReconcileDayEntity {

    /** 칸 이름 {@code day} 는 HQL 의 날짜 함수 이름과 겹쳐 속성 이름을 달리 둔다 */
    @Id
    @Column(name = "day")
    private LocalDate reconcileDay;

    /** RUNNING · DONE · MISMATCH · FAILED — {@code PaymentReconcileDayStore.Status} 이름 */
    private String status;
    private Integer checkedCount;
    private Integer issueCount;
    private Long runId;
    private Instant finishedAt;

    protected PaymentReconcileDayEntity() {
    }
}
