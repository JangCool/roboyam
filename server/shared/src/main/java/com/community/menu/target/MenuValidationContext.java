package com.community.menu.target;

import com.community.menu.dto.MenuBoardRef;
import com.community.menu.dto.MenuPageRef;
import java.util.Map;
import java.util.Set;

/**
 * 저장 때 대상을 확인하는 재료 — 요청 하나가 시작할 때 읽어 둔 스냅샷이다.
 *
 * @param boards    게시판 ID → 게시판(꺼진 게시판까지 — 꺼진 게시판도 메뉴에 둘 수 있다)
 * @param gradeKeys 등급 키({@code GRADE_MIN} 의 {@code minGrade} 확인)
 * @param placement 그룹의 위치({@code SIDE} 만 모바일 탭·자동 추가)
 * @param pages     가젯 페이지 ID → 페이지({@code GADGET_PAGE} 확인 — 넘기지 않으면 비어 있어 그 유형은 저장되지 않는다)
 */
public record MenuValidationContext(Map<Long, MenuBoardRef> boards, Set<String> gradeKeys, String placement, Map<Long, MenuPageRef> pages) {

    public MenuValidationContext {
        pages = pages == null ? Map.of() : pages;
    }

    /** 가젯 페이지 없이(설치 프리셋 등) */
    public MenuValidationContext(Map<Long, MenuBoardRef> boards, Set<String> gradeKeys, String placement) {
        this(boards, gradeKeys, placement, Map.of());
    }
}
