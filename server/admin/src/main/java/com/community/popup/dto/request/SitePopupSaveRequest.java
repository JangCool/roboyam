package com.community.popup.dto.request;

import java.time.Instant;
import java.util.List;

/**
 * {@code POST /api/admin/site-popups}(만들기) · {@code PUT /api/admin/site-popups/{id}}(고치기 — 모든 칸을 보낸다). admin-web {@code SitePopupSaveRequest} 와 같은 칸.
 *
 * @param name        관리용 이름(필수 1~40자, 방문자에게 안 보인다)
 * @param kind        {@code IMAGE} · {@code TEXT}
 * @param imageFileId IMAGE 의 그림 — {@code POST /api/admin/files}(purpose SITE_POPUP_IMAGE)로 올린 파일 ID. null 이면 그림 없음(고칠 때 null 이면 지운다), 지금 그림과 같으면 그대로
 * @param imageAlt    그림 대체 글(선택 100자)
 * @param link        누르면 갈 곳(선택 500자, {@code /…} 또는 {@code http(s)://})
 * @param heading     TEXT 제목(선택 40자) · {@code body} 내용(TEXT 필수 1~1000자, 줄바꿈 유지) · {@code buttonLabel} 단추 글자(선택 20자)
 * @param target      {@code HOME} · {@code ALL}
 * @param devices     {@code PC}·{@code MOBILE} 하나 이상
 * @param hideOption  {@code TODAY} · {@code WEEK} · {@code NEVER} · {@code NONE}
 * @param visible     노출 켬 — 만들 때 비우면 true, 고칠 때 비우면 그대로
 * @param showFrom    노출 시작(포함, 비우면 바로) · {@code showUntil} 노출 끝(미포함, 비우면 상시 — 시작보다 늦어야)
 */
public record SitePopupSaveRequest(String name, String kind, String imageFileId, String imageAlt, String link, String heading, String body, String buttonLabel,
                                   String target, List<String> devices, String hideOption, Boolean visible, Instant showFrom, Instant showUntil) {

    /** 칸을 하나도 보내지 않은 본문 */
    public static SitePopupSaveRequest empty() {
        return new SitePopupSaveRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
