package com.community.post.dto.response;

/**
 * 본문 짤 토큰 {@code :jjal-ID:} 하나의 표시 정보 — 관리자 글·신고 서랍. 공개 짤 조회({@code GET /api/jjals} 의 {@code JjalView})와 같은 칸·같은 판정에서
 * 「담기」 판정({@code collectable})만 뺐다(관리자 화면에는 담기가 없다).
 *
 * @param id       짤 공개 ID(토큰의 ID)
 * @param url      그림 주소 — 내려간 짤(신고·운영자 내림 · 공개 위치로 옮기기 전 · 불법촬영물로 지움)이면 null(「내려간 짤」 자리). 공개 사이트와 같다
 * @param width    그림 너비(px)
 * @param height   그림 높이(px)
 * @param animated 움짤(여러 장 GIF)
 * @param blocked  내려간 짤 — {@code url} 이 null 인 까닭
 */
public record AdminBodyJjalView(String id, String url, int width, int height, boolean animated, boolean blocked) {
}
