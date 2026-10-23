package com.community.notification.dto.request;

import java.util.List;

/**
 * 이벤트 관리의 사용 여부·전달 도구·추가 수신 팀·기본 수신 저장.
 *
 * <ul>
 *   <li>{@code enabled} 는 늘 필요하다(없으면 400 {@code fields.enabled}).</li>
 *   <li>{@code channels}(전달 도구 id: {@code email} · {@code browser} · Slack/Mattermost 도구 id, 서랍은 항상 켜짐이라 보내도 무시)를 <b>보내면</b> 그 목록이 전부다 —
 *       {@code email} 이 있는지가 곧 이메일 전달이고 {@code emailEnabled} 는 무시한다(둘을 따로 두지 않는다). <b>안 보내면</b>(예전 요청) {@code emailEnabled} 가 필요하고 다른 도구는 그대로다.</li>
 *   <li>{@code teamIds}(추가 수신 팀 id)를 <b>보내면</b> 그 목록으로 바꾸고, 안 보내면 그대로다.</li>
 *   <li>{@code defaultRecipients}(「권한자 전원에게 보내기」 — false 면 고른 팀에만)를 <b>보내면</b> 바꾸고, 안 보내면 그대로다(예전 요청 호환). false 는 저장 결과 팀이 하나 이상
 *       있어야 하고(400 {@code fields.teamIds}) 본인 전용·필수 이벤트에는 쓸 수 없다(400 {@code fields.defaultRecipients}).</li>
 * </ul>
 */
public record NotificationEventUpdateRequest(Boolean enabled, Boolean emailEnabled, List<String> channels, List<String> teamIds, Boolean defaultRecipients) {
}
