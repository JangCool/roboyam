package com.community.post.dto.response;

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
 */
public record PostAuthorView(String kind, String memberId, String name, String grade, String gradeLabel, String role, String ipMasked, String memo) {

    /** 메모 칸 없이(null) — 메모를 싣지 않는 응답 */
    public PostAuthorView(String kind, String memberId, String name, String grade, String gradeLabel, String role, String ipMasked) {
        this(kind, memberId, name, grade, gradeLabel, role, ipMasked, null);
    }

    /** 같은 작성자에 메모만 바꾼 것 */
    public PostAuthorView withMemo(String value) {
        return new PostAuthorView(kind, memberId, name, grade, gradeLabel, role, ipMasked, value);
    }
}
