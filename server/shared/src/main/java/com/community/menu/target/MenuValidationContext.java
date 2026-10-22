package com.community.menu.target;

import com.community.menu.dto.MenuBoardRef;
import java.util.Map;
import java.util.Set;

/**
 * 저장 때 대상을 확인하는 재료 — 요청 하나가 시작할 때 읽어 둔 스냅샷이다.
 *
 * @param boards    게시판 ID → 게시판(꺼진 게시판까지 — 꺼진 게시판도 메뉴에 둘 수 있다)
 * @param gradeKeys 등급 키({@code GRADE_MIN} 의 {@code minGrade} 확인)
 * @param placement 그룹의 위치({@code SIDE} 만 모바일 탭·자동 추가)
 */
public record MenuValidationContext(Map<Long, MenuBoardRef> boards, Set<String> gradeKeys, String placement) {
}
