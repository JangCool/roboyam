package com.community.report.dto.response;

/**
 * 짤 사건의 대상 짤(파일 단위 — V261, my-jjal-contract). 운영자는 내려간 짤도 그림을 본다 — {@code url} 은 공개 위치면 공개 주소, 내려 비공개 위치로 옮긴 짤이면
 * 관리자 전용 주소 {@code /api/admin/reports/{사건 ID}/jjal-image}, 불법촬영물로 확정해 지운 짤이면 null.
 *
 * @param id      짤 공개 ID(토큰 {@code :jjal-ID:} 의 ID)
 * @param blocked 지금 내려가 있는가(모든 보관함·글·댓글에서 「내려간 짤」)
 */
public record ReportJjalView(String id, String url, int width, int height, boolean animated, boolean blocked) {
}
