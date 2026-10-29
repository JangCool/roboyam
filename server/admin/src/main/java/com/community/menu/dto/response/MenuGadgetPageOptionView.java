package com.community.menu.dto.response;

/**
 * 고를 수 있는 가젯 페이지 — admin-web {@code MenuGadgetPageOption}(연결 유형 {@code GADGET_PAGE} 의 {@code targetId} = {@code id}).
 *
 * @param key 영문 주소(공개 주소 {@code /p/{key}})
 */
public record MenuGadgetPageOptionView(String id, String key, String name, String nameEn) {
}
