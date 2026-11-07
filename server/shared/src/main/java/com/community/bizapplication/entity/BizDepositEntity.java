package com.community.bizapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 수동 결제 입금 확인 — shared V112 {@code biz_deposits}(신청 건 하나에 한 줄, PK = 신청 건). 쓰기는 저장소의 upsert(네이티브 {@code ON CONFLICT}) 하나라 만들기 메서드가 없다 —
 * 읽기·질의용 매핑이다. 입금자 이름은 개인정보라 {@code toString} 을 두지 않는다.
 */
@Entity
@Table(name = "biz_deposits")
public class BizDepositEntity {

    @Id
    private Long applicationId;

    private Long amount;
    private LocalDate depositedOn;
    private String depositorName;
    private String mismatchReason;
    private Long confirmedBy;
    private Instant confirmedAt;

    protected BizDepositEntity() {
    }
}
