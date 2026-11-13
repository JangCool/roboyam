package com.community.menu.dto;

import com.community.home.entity.GadgetPageEntity;

/**
 * 메뉴가 보는 가젯 페이지 요약 — 연결 유형 {@code GADGET_PAGE} 의 대상 확인·기본 이름·주소({@code /p/{key}}) 재료(menu-contract 「가젯 페이지 연결」).
 *
 * @param key        영문 주소
 * @param systemPage 시스템 페이지 값({@code TIPS}) — 운영자가 만든 페이지는 null
 */
public record MenuPageRef(long id, String key, String name, String nameEn, String systemPage) {

    /** 공개 주소 — 시스템 페이지 TIPS 는 {@code /tips}, 그 밖은 {@code /p/{key}}(tips-contract 「/tips 레이아웃 편집」) */
    public String path() {
        return GadgetPageEntity.publicPath(systemPage, key);
    }
}
