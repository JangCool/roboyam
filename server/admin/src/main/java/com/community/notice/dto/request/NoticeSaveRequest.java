package com.community.notice.dto.request;

import java.util.List;

/**
 * {@code POST /api/admin/notices} · {@code PUT /api/admin/notices/{id}} — 검사는 서비스가 칸 경로({@code title}·{@code body}·{@code prefixId}·{@code scope}·
 * {@code excludedBoardIds}·{@code expiresAt})로 모은다(공개 글쓰기와 같은 규칙).
 *
 * @param prefixId              말머리 ID(없으면 null — 그 게시판의 켜진 말머리)
 * @param title                 제목(1~100자)
 * @param body                  서식 HTML(서버가 게시판 에디터 수준으로 정화한다)
 * @param pinned                상단 고정(null 은 false). false 면 아래 전체 공지 칸은 모두 무시하고 전체 공지에서도 내린다
 * @param scope                 고정 범위 {@code NOTICE_ONLY}(공지 게시판에만 — null 도) · {@code ALL}(모든 게시판 맨 위에도 = 전체 공지, V174)
 * @param excludedBoardIds      전체 공지 — 뺄 게시판 ID(일반 · 소모임 게시판, null 은 빈 목록)
 * @param excludeAllGroupBoards 전체 공지 — 소모임 게시판 전체를 뺀다(앞으로 만들 소모임 게시판 포함, null 은 false)
 * @param expiresAt             전체 공지 — 다른 게시판에서 내려갈 시각(UTC ISO, null = 풀 때까지)
 */
public record NoticeSaveRequest(String prefixId, String title, String body, Boolean pinned, String scope, List<String> excludedBoardIds,
                                Boolean excludeAllGroupBoards, String expiresAt) {
}
