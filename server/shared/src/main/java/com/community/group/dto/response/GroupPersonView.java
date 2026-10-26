package com.community.group.dto.response;

import com.community.pointshop.dto.response.AuthorDecorationView;

/**
 * 소모임 화면의 사람 한 명(모임장·신청자·멤버·처리자) — 닉네임·등급 뱃지(+공개 사이트는 장식·아바타) 외 개인정보는 싣지 않는다.
 * admin-web {@code GroupPerson} 과 같은 칸에 공개 사이트용 {@code decoration}·{@code avatarUrl} 을 더했다(관리자 응답에서는 늘 null).
 *
 * @param id         회원 ID(운영자면 운영자 ID). 탈퇴했으면 null
 * @param nickname   닉네임 — 탈퇴했으면 요청 언어의 「탈퇴한 회원」, 운영자는 운영자 이름
 * @param withdrawn  탈퇴한 회원(또는 지워진 운영자)
 * @param grade      등급 뱃지 — 운영자·모르는 등급·탈퇴면 null
 * @param decoration 장착한 포인트 상점 장식(공개 사이트만, 없으면 null)
 * @param avatarUrl  아바타 그림 주소(공개 사이트만, 탈퇴면 null)
 */
public record GroupPersonView(String id, String nickname, boolean withdrawn, GroupGradeView grade, AuthorDecorationView decoration, String avatarUrl) {

    /**
     * 등급 뱃지(등급 설정의 키·이름·색·아이콘).
     *
     * @param key   등급 키
     * @param label 등급 이름
     * @param color 뱃지 색 이름
     * @param icon  PrimeIcons 클래스(없으면 null)
     */
    public record GroupGradeView(String key, String label, String color, String icon) {
    }
}
