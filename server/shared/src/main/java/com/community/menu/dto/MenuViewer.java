package com.community.menu.dto;

/**
 * 메뉴를 보는 사람 — 공개 응답({@code GET /api/menu})은 로그인 상태에서, 관리자 미리보기는 요청에서 만든다.
 *
 * @param kind       {@code GUEST}(비로그인) · {@code MEMBER}(로그인 회원) · {@code STAFF}(운영진 — 공개 사이트에서는 게시판 매니저·스텝을 맡은 회원)
 * @param gradeLevel 회원 등급의 서열(1 이 기본 등급). 비로그인·모르는 등급이면 null — {@code GRADE_MIN} 판정에만 쓴다(운영진은 등급과 무관하게 본다)
 */
public record MenuViewer(String kind, Integer gradeLevel) {

    public static final String GUEST = "GUEST";
    public static final String MEMBER = "MEMBER";
    public static final String STAFF = "STAFF";

    public static MenuViewer guest() {
        return new MenuViewer(GUEST, null);
    }

    public boolean isGuest() {
        return GUEST.equals(kind);
    }

    public boolean isStaff() {
        return STAFF.equals(kind);
    }
}
