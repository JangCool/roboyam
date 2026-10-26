package com.community.group.service;

import jakarta.persistence.EntityManager;

/**
 * 소모임 분류 표({@code group_categories}·{@code group_category_boards})를 바꾸는 모든 입구를 서버 여러 대 사이에서 한 번에 하나씩만 지나가게 하는
 * DB 어드바이저리 잠금(트랜잭션이 끝나면 풀린다). 입구: 분류 만들기·저장·순서·삭제, 분류를 고른 게시판 만들기.
 *
 * <p>분류 저장은 여러 분류의 할당 행을 한꺼번에 옮기고(다른 분류에서 빠지는 게시판), 만들기는 아직 없는 행의 이름을 겨루므로 행 잠금으로는 줄을 세울 수 없다.
 * 이 일들은 드물어 전역 잠금이어도 부담이 없다. 「다른 관리자가 먼저 고쳤어요」({@code updatedAt}) 비교도 이 잠금 <b>안에서</b> 한다.
 *
 * <p><b>잠금 순서</b>: 이 잠금을 다른 행 잠금보다 <b>먼저</b> 잡는다. 호출하는 쪽은 트랜잭션 안이어야 한다.
 */
public final class GroupCategoryLock {

    /** 임의의 고정 키 — 이 앱의 다른 어드바이저리 잠금(7_340_001 기동 맞춤, 7_340_002 배치, 7_340_003 운영자 식별 값, 7_340_004 비밀번호 재설정 요청 한도)과 겹치지 않게 정한 값 */
    private static final long KEY = 7_340_005L;

    private GroupCategoryLock() {
    }

    public static void acquire(EntityManager entityManager) {
        // Native SQL 인 이유: PostgreSQL 의 pg_advisory_xact_lock 은 JPQL·QueryDSL 에 대응 문법이 없다
        // pg_advisory_xact_lock 은 void 를 돌려주므로 바깥 select 로 감싸 값 하나로 읽는다
        entityManager.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(?1)) as acquired").setParameter(1, KEY).getSingleResult();
    }
}
