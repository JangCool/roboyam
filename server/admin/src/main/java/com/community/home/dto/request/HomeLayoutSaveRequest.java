package com.community.home.dto.request;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * {@code PUT /api/admin/home-layouts/{theme}} — admin-web {@code HomeLayoutSaveRequest}(배치 + 넣고 뺀 결과의 가젯 전체). 검사는 서비스가 칸 경로로 모은다.
 * 가젯 페이지 배치 저장({@code PUT /api/admin/gadget-pages/{id}/layouts/{theme}})도 같은 모양이고, 게시판 레이아웃 저장은 이것에 적용 화면이 더해진다.
 * 가젯마다 {@code {id, type, title, desktop, mobile, style, config}}(gadget-page-contract 「가젯 설정」 2026-10-30):
 * <ul>
 *   <li>저장 가젯·시드 가젯({@code seed-<n>}): {@code title}·{@code desktop}·{@code mobile} 은 보낸 값으로 바꾸고(빠지면 그대로), {@code style}·{@code config} 는 보낸 칸만 덮는다(null 이면 그대로).</li>
 *   <li>새 가젯(모르는 ID — {@code new-<n>}): {@code type}·{@code title} 필수, 처음 꾸미기(테마 기본) 위에 보낸 {@code style}, 처음 설정 위에 보낸 {@code config}.</li>
 * </ul>
 */
public record HomeLayoutSaveRequest(Layout layout, List<Gadget> gadgets) {

    /**
     * @param columns     12 · 24
     * @param mobileOrder 모바일 한 열 순서 — 가젯 ID 목록(저장 · 시드 · {@code new-<n>} ID 모두), null = PC 배치에서 계산(home-news-contract 「모바일 순서」).
     *                    배치 {@code items} 에 없는 ID·겹친 ID 는 버리고(오류 아님) 빈 목록은 null, 새 가젯 ID 는 저장 뒤 실제 ID 로 바꾼다
     */
    public record Layout(Integer columns, List<Item> items, List<String> mobileOrder) {

        /** 모바일 순서 없이 */
        public Layout(Integer columns, List<Item> items) {
            this(columns, items, null);
        }
    }

    public record Item(String gadgetId, Integer x, Integer y, Integer w, Integer h) {
    }

    /**
     * @param style  꾸미기 — 보낸 칸만 검사·저장({@code GadgetPatches.style}), 모르는 칸은 버린다
     * @param config 내용 설정 — 종류마다 표 칸만 검사·저장({@code GadgetPatches.config}), 모르는 칸은 버린다
     */
    public record Gadget(String id, String type, String title, Boolean desktop, Boolean mobile, JsonNode style, JsonNode config) {
    }
}
