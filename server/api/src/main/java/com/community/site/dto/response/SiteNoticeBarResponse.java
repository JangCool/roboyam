package com.community.site.dto.response;

import java.util.List;

/**
 * {@code GET /api/site/notice-bar}(비로그인) — 공지 띠(user-web {@code SiteNoticeBarResponse}). 사용자별 내용은 없다.
 *
 * @param version 지금 줄 묶음의 표식({@code off} · {@code b:…} · {@code c:…}) — 공개 사이트는 이 값별로 「닫음」을 기억한다
 * @param items   최신순 — 꺼져 있거나 보일 줄이 없으면 빈 목록(띠를 그리지 않는다)
 */
public record SiteNoticeBarResponse(String version, List<SiteNoticeItemView> items) {
}
