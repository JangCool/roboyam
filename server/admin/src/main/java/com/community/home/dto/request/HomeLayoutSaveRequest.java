package com.community.home.dto.request;

import java.util.List;

/**
 * {@code PUT /api/admin/home-layouts/{theme}} — admin-web {@code HomeLayoutSaveRequest}(배치 + 넣고 뺀 결과의 가젯 전체). 검사는 서비스가 칸 경로로 모은다.
 * 제목·노출·꾸미기·설정은 서버 값을 쓰고, 새 가젯(모르는 ID — {@code new-<n>})만 {@code type}·{@code title} 로 만든다. 테마 기본 배치의 ID({@code seed-<n>})는 그 시드 그대로 만든다.
 * 예외: 공구 가젯({@code GROUP_BUY})의 {@code config}(개수·정렬, 2026-10-29)는 보내면 그 값으로 바꾼다(없으면 그대로).
 */
public record HomeLayoutSaveRequest(Layout layout, List<Gadget> gadgets) {

    /** @param columns 12 · 24 */
    public record Layout(Integer columns, List<Item> items) {
    }

    public record Item(String gadgetId, Integer x, Integer y, Integer w, Integer h) {
    }

    /** {@code desktop}·{@code mobile} 은 받아도 쓰지 않는다(이 화면에서 고치지 않는다). {@code config} 는 공구 가젯만 쓴다(다른 종류는 무시) */
    public record Gadget(String id, String type, String title, Config config) {
    }

    /**
     * 공구 가젯 설정
     *
     * @param count 보일 개수 1~10
     * @param sort  {@code DEADLINE} · {@code NEWEST} · {@code POPULAR}
     */
    public record Config(Integer count, String sort) {
    }
}
