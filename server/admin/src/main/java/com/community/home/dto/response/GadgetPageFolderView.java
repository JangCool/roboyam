package com.community.home.dto.response;

import java.util.List;

/**
 * 기준 폴더로 고를 수 있는 폴더 한 개 — admin-web {@code GadgetPageFolder}({@code GET /api/admin/gadget-pages/folders}). 공개 메뉴 main 의 폴더(깊이 무관, 트리 차례).
 *
 * @param label  위 폴더가 있으면 「위 › 이름」
 * @param boards 폴더 아래(하위 폴더까지) 게시판 항목의 게시판(메뉴 차례, 꺼진 것 포함)
 */
public record GadgetPageFolderView(String id, String name, String nameEn, String label, List<GadgetPageView.Board> boards) {
}
