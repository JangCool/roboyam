package com.community.menu.dto.response;

/**
 * 고를 수 있는 가젯 페이지 — admin-web {@code MenuGadgetPageOption}(연결 유형 {@code GADGET_PAGE} 의 {@code targetId} = {@code id}).
 *
 * @param key        영문 주소
 * @param path       공개 주소 — 시스템 페이지 TIPS 는 {@code /tips}, 그 밖은 {@code /p/{key}}(tips-contract 「/tips 레이아웃 편집」)
 * @param systemPage 시스템 페이지 값({@code TIPS}) — 운영자가 만든 페이지는 null
 */
public record MenuGadgetPageOptionView(String id, String key, String name, String nameEn, String path, String systemPage) {
}
