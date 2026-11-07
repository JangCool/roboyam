package com.community.notice.dto.request;

/**
 * {@code POST /api/admin/notices} · {@code PUT /api/admin/notices/{id}} — 검사는 서비스가 칸 경로({@code title}·{@code body}·{@code prefixId})로 모은다(공개 글쓰기와 같은 규칙).
 *
 * @param prefixId 말머리 ID(없으면 null — 그 게시판의 켜진 말머리)
 * @param title    제목(1~100자)
 * @param body     서식 HTML(서버가 게시판 에디터 수준으로 정화한다)
 * @param pinned   상단 고정(null 은 false)
 */
public record NoticeSaveRequest(String prefixId, String title, String body, Boolean pinned) {
}
