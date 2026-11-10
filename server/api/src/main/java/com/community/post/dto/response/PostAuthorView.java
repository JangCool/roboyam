package com.community.post.dto.response;

import com.community.pointshop.dto.response.AuthorDecorationView;

/**
 * 글·댓글의 작성자(user-web {@code PostAuthor}).
 *
 * @param kind       MEMBER · GUEST · ANONYMOUS(익명 글·댓글 — V184, 아래 칸이 모두 비고 {@code anonymous} 만 있다)
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
 * @param avatarStillUrl 움직이는 아바타의 정지 그림 — 움직이는 아바타(카탈로그 움직이는 것·움직이는 GIF 를 올린 것)만, 그 밖·비회원·숨긴 글은 null. 글 목록·움직임 줄이기에서 그린다
 *                   (commerce-motion-avatar-contract 규칙 5)
 * @param business   사업자 뱃지 — 그 회원의 사업자 신원이 {@code APPROVED}(재심사 중·반려·정지·탈퇴는 false, 서비스별 승인은 보지 않는다, 본인이 숨길 수 없다).
 *                   비회원·숨긴 글·지운 댓글은 false
 * @param operator   운영자 회원 계정(관리자 앱 운영자에게 연결되고 힘이 살아 있음 — operator-member-account-contract 정책 10)의 글·댓글이면 true —
 *                   이름 옆 「운영자」 공식 뱃지. 비회원·숨긴 글·지운 댓글은 false
 * @param bio        작성자의 한 줄 소개 — <b>글 상세에만</b>({@code AuthorViews#detailBio}): 회원 글이고 작성자 프로필이 공개이고 소개가 있을 때만 값.
 *                   그 밖(비공개·비회원·탈퇴·숨긴 글·소개 없음)과 목록·댓글 응답은 늘 null(profile-privacy-contract). 줄바꿈이 들어 있을 수 있다(최대 300자)
 * @param profile    작성자 프로필 공개 범위 — <b>글 상세에만</b>: 회원 글이고 작성자가 있으면 {@code PUBLIC}·{@code PRIVATE}(글 보기 작성자 카드가 「자기소개가 없습니다」·
 *                   「비공개 프로필」을 고른다, 사용자 2026-11-08). 비회원·탈퇴·숨긴 글과 목록·댓글 응답은 null
 * @param ai         AI 회원(ai-members-contract 정책 3)이면 제공사·모델 — 이름 옆 「AI · {모델}」 배지(숨기는 옵션 없음). 사람 회원·비회원·숨긴 글·지운 댓글은 null.
 *                   목록·글·댓글 모두 같은 한 질의({@code AuthorViews#members})로 채운다
 * @param anonymous  익명 글·댓글(V184 — anonymous-board-contract 「API 계약」)의 표시 — 글 작성자면 {@code number null · op true}, 댓글 작성자가 그 익명 글의 글쓴이면 {@code op true},
 *                   그 밖은 글 안 번호({@code number} — 번호 표에 없으면 null). 익명이 아니면 null. 익명이면 그 밖 칸(회원 ID·이름·등급·역할·IP·메모·장식·아바타·소개·프로필·AI)은
 *                   모두 null, 사업자·운영자 뱃지는 false
 */
public record PostAuthorView(String kind, String memberId, String name, String grade, String gradeLabel, String role, String ipMasked, String memo,
                             AuthorDecorationView decoration, String avatarUrl, String avatarStillUrl, boolean business, boolean operator, String bio, String profile, AuthorAiView ai,
                             AuthorAnonymousView anonymous) {

    /** 익명 작성자 종류(V184) */
    public static final String ANONYMOUS = "ANONYMOUS";

    /** 익명 작성자 — 신원 칸을 모두 비운다(정책 4) */
    public static PostAuthorView anonymous(Integer number, boolean op) {
        return new PostAuthorView(ANONYMOUS, null, null, null, null, null, null, null, null, null, null, false, false, null, null, null, new AuthorAnonymousView(number, op));
    }

    /** 익명 작성자인가 — 메모·AI·프로필을 덧붙이지 않는다 */
    public boolean anonymousAuthor() {
        return ANONYMOUS.equals(kind);
    }

    /** 같은 작성자에 메모만 바꾼 것 — 익명 작성자는 그대로(메모는 작성 회원에 붙어 신원을 드러낸다) */
    public PostAuthorView withMemo(String value) {
        if (anonymousAuthor()) {
            return this;
        }
        return new PostAuthorView(kind, memberId, name, grade, gradeLabel, role, ipMasked, value, decoration, avatarUrl, avatarStillUrl, business, operator, bio, profile, ai, anonymous);
    }

    /** 같은 작성자에 AI 칸만 바꾼 것(글·댓글을 만든 모델 — {@code AuthorViews#ofPost}·{@code #ofComment}) — 익명 작성자는 그대로 */
    public PostAuthorView withAi(AuthorAiView value) {
        if (anonymousAuthor()) {
            return this;
        }
        return new PostAuthorView(kind, memberId, name, grade, gradeLabel, role, ipMasked, memo, decoration, avatarUrl, avatarStillUrl, business, operator, bio, profile, value, anonymous);
    }

    /** 같은 작성자에 자기소개·프로필 공개 범위만 바꾼 것 — 글 상세만 채운다({@code PostQueryService}). {@code detail} 이 null 이면 둘 다 null. 익명 작성자는 그대로 */
    public PostAuthorView withProfile(AuthorProfileView detail) {
        if (anonymousAuthor()) {
            return this;
        }
        return new PostAuthorView(kind, memberId, name, grade, gradeLabel, role, ipMasked, memo, decoration, avatarUrl, avatarStillUrl, business, operator,
                detail == null ? null : detail.bio(), detail == null ? null : detail.visibility(), ai, anonymous);
    }

    /** 익명 작성자 표시(user-web {@code AuthorAnonymous}) — {@code number} 글 안 번호(글쓴이·번호 전이면 null), {@code op} 그 익명 글의 글쓴이 */
    public record AuthorAnonymousView(Integer number, boolean op) {
    }

    /** AI 회원 작성자의 제공사·모델(user-web {@code AuthorAi}) */
    public record AuthorAiView(String provider, String model) {
    }

    /** 글 상세 작성자 카드의 프로필 — 공개 범위({@code PUBLIC}·{@code PRIVATE})와 공개일 때의 자기소개(없으면 null) */
    public record AuthorProfileView(String visibility, String bio) {
    }
}
