package com.community.hotdeal.dto.response;

/**
 * 사업자 핫딜 글의 사업자(user-web {@code PostDeal.business}) — 상호 스냅샷. 회원 글은 null.
 *
 * @param applicationId 그 글의 신청 건 — <b>보는 사람이 그 사업자(작성자)일 때만</b>(글 보기 딜 카드의 [일시 정지]·「중단·환불은 신청 건에서 ›」, V127). 그 밖 null
 */
public record DealBusinessView(String name, String applicationId) {

    public DealBusinessView(String name) {
        this(name, null);
    }
}
