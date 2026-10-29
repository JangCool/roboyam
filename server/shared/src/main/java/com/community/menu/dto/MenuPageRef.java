package com.community.menu.dto;

/**
 * 메뉴가 보는 가젯 페이지 요약 — 연결 유형 {@code GADGET_PAGE} 의 대상 확인·기본 이름·주소({@code /p/{key}}) 재료(menu-contract 「가젯 페이지 연결」).
 *
 * @param key 영문 주소
 */
public record MenuPageRef(long id, String key, String name, String nameEn) {
}
