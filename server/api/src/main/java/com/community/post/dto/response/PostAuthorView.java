package com.community.post.dto.response;

import com.community.pointshop.dto.response.AuthorDecorationView;

/**
 * 글·댓글의 작성자(user-web {@code PostAuthor}).
 *
 * @param kind       MEMBER · GUEST
 * @param memberId   회원 ID — 비회원 null
 * @param name       회원은 닉네임, 비회원은 입력한 이름(지운 댓글·숨긴 글은 빈 글자)
 * @param grade      회원 등급 키 — 비회원 null
 * @param gradeLabel 등급 이름 — 비회원 null
 * @param role       회원은 USER(게시판 매니저·스텝 표시는 공개하지 않는다), 비회원 null
 * @param ipMasked   비회원의 작성 IP 앞 두 자리(123.45.*.*, IPv6 는 앞 두 묶음) — 회원·IP 를 지운 글은 null
 * @param memo       보는 사람이 이 작성 회원에게 남긴 이용자 메모(나에게만 오는 값) — 없으면·비회원 작성자·비로그인이면 null. 지금은 게시판 글 목록·댓글만 채운다
 *                   (docs/features/member-social-contract.md 「이미 있는 응답에 더할 칸」)
 * @param decoration 장착한 포인트 상점 장식(닉네임 색·프로필 테두리 — 만료된 장착은 빠진다, 둘 다 없으면 null). 비회원·숨긴 글·지운 댓글은 null(커머스 1-2)
 * @param avatarUrl  프로필 아바타 그림 주소 — 회원은 늘 채운다(고르지 않았으면 {@code basic-default}), 비회원·숨긴 글·지운 댓글은 null(커머스 1-2 결정 14)
 * @param business   사업자 뱃지 — 그 회원의 사업자 신원이 {@code APPROVED}(재심사 중·반려·정지·탈퇴는 false, 서비스별 승인은 보지 않는다, 본인이 숨길 수 없다).
 *                   비회원·숨긴 글·지운 댓글은 false
 */
public record PostAuthorView(String kind, String memberId, String name, String grade, String gradeLabel, String role, String ipMasked, String memo,
                             AuthorDecorationView decoration, String avatarUrl, boolean business) {

    /** 같은 작성자에 메모만 바꾼 것 */
    public PostAuthorView withMemo(String value) {
        return new PostAuthorView(kind, memberId, name, grade, gradeLabel, role, ipMasked, value, decoration, avatarUrl, business);
    }
}
