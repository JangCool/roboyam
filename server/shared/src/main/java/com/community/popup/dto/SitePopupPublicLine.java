package com.community.popup.dto;

import java.time.Instant;
import java.util.List;

/**
 * 공개 팝업 한 장({@link com.community.popup.service.SitePopupReader}) — 공개 응답의 칸 + 노출 기간(공개 서버가 사본을 지금 시각으로 다시 거를 때 쓴다).
 * 지금 종류가 아닌 칸은 null 이다(IMAGE 면 제목·내용·단추, TEXT 면 그림·대체 글).
 */
public record SitePopupPublicLine(long id, String version, String kind, String imageUrl, String imageAlt, String link, String heading, String body,
                                  String buttonLabel, String target, List<String> devices, String hideOption, Instant showFrom, Instant showUntil) {

    /** 노출 기간 안인가(시작 포함·끝 미포함, 비어 있으면 상시) — 노출 켬은 읽을 때 이미 걸렀다 */
    public boolean inPeriod(Instant now) {
        return (showFrom == null || !showFrom.isAfter(now)) && (showUntil == null || showUntil.isAfter(now));
    }
}
