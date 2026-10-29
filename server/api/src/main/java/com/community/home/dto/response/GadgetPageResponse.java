package com.community.home.dto.response;

import com.community.home.dto.HomeGridLayout;
import java.util.List;

/**
 * {@code GET /api/pages/{key}} — user-web {@code GadgetPageResponse}. 배치·가젯은 홈 가젯 응답({@link HomeGadgetsResponse})과 같은 모양 + 페이지 머리.
 *
 * @param theme 이 배치가 어느 테마의 것인가(지금 사이트 테마)
 */
public record GadgetPageResponse(Page page, String theme, HomeGridLayout layout, List<HomeGadgetView> gadgets) {

    /**
     * 페이지 머리
     *
     * @param key        영문 주소
     * @param nameEn     영문 이름 — 없으면 null
     * @param writeBoard 글쓰기에서 골라 둘 게시판 slug(기준 폴더의 첫 켜진 게시판) — 없으면 null
     */
    public record Page(String key, String name, String nameEn, String description, boolean writeButton, String writeBoard) {
    }
}
