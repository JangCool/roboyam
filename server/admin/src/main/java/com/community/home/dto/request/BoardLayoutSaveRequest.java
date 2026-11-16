package com.community.home.dto.request;

import java.util.List;

/**
 * {@code PUT /api/admin/board-layouts/{theme}} — admin-web {@code BoardLayoutSaveRequest}(board-layout-contract). 배치·가젯은 홈 저장과 같은 모양·같은 규칙
 * ({@link HomeLayoutSaveRequest}) + 고정 칸 「게시판 본문」({@code type: BOARD_BODY}) 꼭 하나 + 적용 화면.
 *
 * @param screens 적용 화면 — {@code list}·{@code post} 둘 다 불리언(빠지면 {@code 400 fields.screens.list|post})
 * @param base    화면이 받은 배치의 {@code updatedAt} — 홈 배치와 같은 규칙({@link HomeLayoutSaveRequest#base()}, 다르면 409 {@code LAYOUT_VERSION_CONFLICT})
 */
public record BoardLayoutSaveRequest(Screens screens, HomeLayoutSaveRequest.Layout layout, List<HomeLayoutSaveRequest.Gadget> gadgets, HomeLayoutSaveRequest.Base base) {

    /** 버전 확인 없이 */
    public BoardLayoutSaveRequest(Screens screens, HomeLayoutSaveRequest.Layout layout, List<HomeLayoutSaveRequest.Gadget> gadgets) {
        this(screens, layout, gadgets, null);
    }

    /** @param list 게시판 목록 {@code /boards/{slug}} · @param post 글 보기 {@code /posts/{id}} */
    public record Screens(Boolean list, Boolean post) {
    }
}
