package com.community.popup.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 팝업 한 줄(목록·만들기·고치기·노출 응답) — admin-web {@code SitePopupItem} 과 같은 칸. 지금 종류가 아닌 칸도 저장값 그대로 싣는다(종류를 되돌리면 다시 쓰인다).
 *
 * @param order       1부터 연속(저장한 차례 — 공개 사이트에서 넘기는 차례)
 * @param imageFileId 그림 파일 ID · {@code imageUrl} 그 공개 주소(없으면 null)
 * @param devices     {@code PC}·{@code MOBILE}(이 차례)
 * @param status      SHOWING(지금 노출 중) · SCHEDULED(기간 전) · EXPIRED(기간 지남) · OFF(노출 끔) — 앞의 것이 이긴다: OFF → SCHEDULED → EXPIRED → SHOWING
 * @param updatedBy   마지막으로 고친 운영자(없으면 null)
 */
public record SitePopupItemView(String id, int order, String name, String kind, String imageFileId, String imageUrl, String imageAlt, String link, String heading,
                                String body, String buttonLabel, String target, List<String> devices, String hideOption, boolean visible, Instant showFrom,
                                Instant showUntil, String status, Instant updatedAt, SitePopupEditorView updatedBy) {
}
