package com.community.operator.service;

import jakarta.persistence.EntityManager;

/**
 * 운영자의 <b>식별 값(이메일·이름)을 정하는 모든 입구</b>를 서버 여러 대 사이에서 한 번에 하나씩만 지나가게 하는 DB 어드바이저리 잠금(트랜잭션이 끝나면 풀린다).
 *
 * <p>대상 입구: 운영자 바로 추가 · 운영자 이름 수정 · 초대 만들기 · 초대 수락 · 내 정보 닉네임 변경. 이메일·이름은 운영자마다 다른 행에 있고 「직접 추가 ↔ 대기 초대」 처럼
 * 서로 다른 표 사이의 규칙도 있어 행 잠금으로는 경합하는 두 요청을 줄 세울 수 없다. 이 일들은 드물어 전역 잠금이어도 부담이 없다.
 * 유일 인덱스({@link #NAME_INDEX}·이메일 {@code ux_operators_email})는 그래도 새는 경우를 막는 마지막 방어이고, 서비스의 사전 검사가 보통의 거절 문구를 만든다.
 *
 * <p><b>잠금 순서</b>: 이 잠금을 <b>먼저</b> 잡은 뒤 행 잠금(운영자·초대 행)을 잡는다 — 거꾸로 하면 교착이 날 수 있다. 호출하는 쪽은 트랜잭션 안이어야 한다.
 * (옛 이름 {@code ProfileNicknameLock} 과 같은 키라 이름만 바뀌었다.)
 */
public final class OperatorIdentityLock {

    /** 운영자 이름 유일 인덱스(대소문자 무시) — V30__operator_name_unique.sql */
    public static final String NAME_INDEX = "ux_operators_name_lower";

    /** 임의의 고정 키 — 이 앱의 다른 어드바이저리 잠금(7_340_001 기동 맞춤, 7_340_002 배치, 7_340_004 비밀번호 재설정 요청 한도)과 겹치지 않게 정한 값 */
    private static final long KEY = 7_340_003L;

    private OperatorIdentityLock() {
    }

    public static void acquire(EntityManager entityManager) {
        // Native SQL 인 이유: PostgreSQL 의 pg_advisory_xact_lock 은 JPQL·QueryDSL 에 대응 문법이 없다
        // pg_advisory_xact_lock 은 void 를 돌려주므로 바깥 select 로 감싸 값 하나로 읽는다
        entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(?1)) as acquired").setParameter(1, KEY).getSingleResult();
    }

    /** 이 오류(원인 사슬 포함)가 운영자 이름 유일 인덱스를 어긴 것인가(아니면 이메일 같은 다른 제약·다른 오류) */
    public static boolean isNameViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(NAME_INDEX)) {
                return true;
            }
        }
        return false;
    }
}
