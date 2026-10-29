package com.community.home.dto.request;

import java.util.List;

/**
 * {@code PUT /api/admin/home-layouts/{theme}} — admin-web {@code HomeLayoutSaveRequest}(배치 + 넣고 뺀 결과의 가젯 전체). 검사는 서비스가 칸 경로로 모은다.
 * 제목·노출·꾸미기·설정은 서버 값을 쓰고, 새 가젯(모르는 ID — {@code new-<n>})만 {@code type}·{@code title} 로 만든다. 테마 기본 배치의 ID({@code seed-<n>})는 그 시드 그대로 만든다.
 * 예외: 내용 설정 {@code config}(⚙ — 공구 · 글 목록 · 갤러리 · 게시판 카드, gadget-page-contract 「가젯 설정」)는 보내면 그 칸으로 바꾼다(없으면 그대로).
 * 가젯 페이지 배치 저장({@code PUT /api/admin/gadget-pages/{id}/layouts/{theme}})도 같은 모양이다.
 */
public record HomeLayoutSaveRequest(Layout layout, List<Gadget> gadgets) {

    /** @param columns 12 · 24 */
    public record Layout(Integer columns, List<Item> items) {
    }

    public record Item(String gadgetId, Integer x, Integer y, Integer w, Integer h) {
    }

    /** {@code desktop}·{@code mobile} 은 받아도 쓰지 않는다(이 화면에서 고치지 않는다). {@code config} 는 ⚙ 설정이 있는 종류만 쓴다(다른 종류는 무시) */
    public record Gadget(String id, String type, String title, Config config) {
    }

    /**
     * 내용 설정 — 종류마다 쓰는 칸만 본다(검사는 {@code GadgetLayoutWriter}).
     *
     * @param count      보일 개수(공구 1~10 · 글 목록 1~30 · 갤러리 1~24)
     * @param sort       공구 {@code DEADLINE}·{@code NEWEST}·{@code POPULAR} · 글 목록 {@code LATEST}·{@code POPULAR}·{@code VIEWS}·{@code LIKES}·{@code COMMENTS}
     * @param boardScope {@code ALL} · {@code FOLDER}(가젯 페이지만) · {@code PICK}
     * @param boardIds   {@code PICK} 의 게시판 slug 1~20개
     * @param periodTabs 글 목록 인기 순의 기간 탭
     * @param perBoard   게시판 카드의 카드마다 글 수 1~10
     * @param cardHead   게시판 카드의 카드 머리(로고·이름·「게시판 가기」) 보이기 — 빠지면 켬
     */
    public record Config(Integer count, String sort, String boardScope, List<String> boardIds, Boolean periodTabs, Integer perBoard, Boolean cardHead) {
    }
}
