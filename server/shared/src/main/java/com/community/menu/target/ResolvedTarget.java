package com.community.menu.target;

import com.community.menu.dto.MenuBoardRef;
import java.util.Map;

/**
 * 연결 대상이 지금 어디로 가고 무엇이라 불리는가 — 공개 응답 항목의 재료.
 *
 * @param path           이동할 주소(사이트 안 경로 또는 {@code https://} 주소)
 * @param matchPrefix    그 아래 주소도 이 항목이 밝다
 * @param defaultLabel   항목 이름이 비었을 때 쓰는 이름(게시판 이름·앱 화면 기본 이름). 따라갈 이름이 없는 유형은 null
 * @param defaultNameEn  그 영문명(없으면 null)
 * @param newTab         새 탭으로 연다
 * @param board          {@code BOARD} 항목의 게시판(그 밖에는 null)
 * @param extra          공개 응답에 그대로 실리는 추가 칸(예: {@code URL} 의 {@code external}). 없으면 null
 */
public record ResolvedTarget(String path, boolean matchPrefix, String defaultLabel, String defaultNameEn, boolean newTab, MenuBoardRef board,
                             Map<String, Object> extra) {
}
