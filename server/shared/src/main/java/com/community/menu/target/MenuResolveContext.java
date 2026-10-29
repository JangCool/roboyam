package com.community.menu.target;

import com.community.menu.dto.MenuBoardRef;
import com.community.menu.dto.MenuPageRef;
import com.community.menu.dto.MenuViewer;
import java.util.Map;

/**
 * 리졸버가 대상을 해석할 때 보는 재료 — 요청 하나가 시작할 때 읽어 둔 스냅샷이다(리졸버는 DB 를 다시 읽지 않는다).
 *
 * @param boards       게시판 ID → 게시판(꺼진 것 포함)
 * @param gradeLevels  등급 키 → 서열({@code GRADE_MIN} 판정)
 * @param salesEnabled 공동구매 판매 스위치({@code MenuSales})
 * @param viewer       보는 사람(관리자 상태 계산만 할 때는 null)
 * @param pages        가젯 페이지 ID → 페이지({@code GADGET_PAGE} — 넘기지 않으면 비어 있어 그 연결은 TARGET_MISSING)
 */
public record MenuResolveContext(Map<Long, MenuBoardRef> boards, Map<String, Integer> gradeLevels, boolean salesEnabled, MenuViewer viewer,
                                 Map<Long, MenuPageRef> pages) {

    public MenuResolveContext {
        pages = pages == null ? Map.of() : pages;
    }

    /** 가젯 페이지 없이 */
    public MenuResolveContext(Map<Long, MenuBoardRef> boards, Map<String, Integer> gradeLevels, boolean salesEnabled, MenuViewer viewer) {
        this(boards, gradeLevels, salesEnabled, viewer, Map.of());
    }

    public MenuPageRef page(String targetId) {
        Long id = parseId(targetId);
        return id == null ? null : pages.get(id);
    }

    public MenuBoardRef board(String targetId) {
        Long id = parseId(targetId);
        return id == null ? null : boards.get(id);
    }

    /** 숫자 ID 글자 → 값. 숫자가 아니면 null */
    public static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
