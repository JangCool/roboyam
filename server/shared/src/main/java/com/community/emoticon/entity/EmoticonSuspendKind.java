package com.community.emoticon.entity;

/**
 * 판매 중단의 종류(V147 {@code emoticon_packs.suspend_kind} — 판매 중단이면 늘 있다). 후속 결정 6(2026-11-05 「탈퇴 작가의 팩은 새 판매만 멈춰」).
 * <ul>
 *   <li>{@link #OPERATOR} 운영자 판단 — 「다시 판매」로 되살린다.</li>
 *   <li>{@link #CREATOR_WITHDRAWN} 작가 탈퇴 — 탈퇴 처리가 시스템으로 멈췄다. 다시 판매·가격 바꾸기·새 판 승인으로 되살리지 못한다({@code 409 EMOTICON_PACK_CREATOR_WITHDRAWN},
 *       DB 트리거가 마지막 그물).</li>
 * </ul>
 * 어느 쪽이든 이미 가진 사람은 계속 쓴다(카탈로그에 남는다 · 셀프 환불 규칙 그대로).
 */
public enum EmoticonSuspendKind {
    OPERATOR,
    CREATOR_WITHDRAWN
}
