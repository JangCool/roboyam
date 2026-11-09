package com.community.cache.definition;

/** 회원별 처리 — 공용(누가 봐도 같은 값) · 공용 + 회원 덧입힘(공용 부분만 캐시하고 차단·메모·내 상태·메뉴 거름은 꺼낸 뒤 보는 사람 기준으로 덧입힌다) */
public enum CacheMemberMode {
    SHARED, SHARED_WITH_MEMBER_OVERLAY
}
