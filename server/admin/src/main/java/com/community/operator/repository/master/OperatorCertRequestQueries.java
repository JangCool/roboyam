package com.community.operator.repository.master;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/**
 * 비밀번호 재설정 승인번호 요청 기록({@code operator_cert_requests})의 줄 세우기 — 한도 확인과 기록 사이의 경합을 <b>키(이메일·IP 해시)마다</b> 트랜잭션 어드바이저리 잠금으로 막는다
 * ({@code PasswordResetRequestLimiter}). 읽기·기록은 엔티티 저장소 {@link OperatorCertRequestRepository} 다. <b>부르는 서비스의 트랜잭션 안에서</b> 부른다(트랜잭션이 끝나면 풀린다).
 */
@Repository
public class OperatorCertRequestQueries {

    /** 어드바이저리 잠금 종류 번호 — 이 앱의 다른 잠금 키(7_340_001 기동 맞춤, 7_340_002 배치, 7_340_003 운영자 식별 값, 7_340_005 소모임 분류)와 겹치지 않게 정한 값 */
    private static final int LOCK_CLASS = 7_340_004;

    private final EntityManager entityManager;

    public OperatorCertRequestQueries(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 이 키의 요청 잠금을 잡는다(트랜잭션 끝까지) — 여러 키를 잡을 때는 부르는 쪽이 키 정렬 순서로 부른다(교착 없음) */
    public void lockKey(String keyHash) {
        // Native SQL 인 이유: pg_advisory_xact_lock 은 JPQL·QueryDSL 에 대응 문법이 없다(void 반환이라 바깥 select 로 감싸 값 하나로 읽는다)
        entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(?1, hashtext(?2))) as acquired")
                .setParameter(1, LOCK_CLASS).setParameter(2, keyHash).getSingleResult();
    }
}
