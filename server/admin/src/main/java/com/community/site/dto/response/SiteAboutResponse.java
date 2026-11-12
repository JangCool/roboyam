package com.community.site.dto.response;

import java.time.Instant;

/**
 * 사이트 소개({@code GET·PUT /api/admin/site/about}).
 *
 * @param html      소독된 소개 HTML. 없으면 null(공개 푸터 링크 숨김)
 * @param updatedAt 소개를 마지막으로 바꾼 때. 저장한 적 없으면 null
 * @param publicUrl 사이트 설정의 공개 주소(화면 「공개 페이지 보기」 = 이 값 + {@code /about}). 비어 있으면 null
 */
public record SiteAboutResponse(String html, Instant updatedAt, String publicUrl) {
}
