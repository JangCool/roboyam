package com.community.notification.dto.response;

import java.util.List;

/**
 * 이벤트 관리 화면의 이벤트 한 줄. 정의(영역·심각도·수신 대상·필수·이메일 가능)는 코드에 고정이고 운영자가 바꾸는 것은 사용 여부·전달 도구·추가 수신 팀뿐이다.
 *
 * @param recipientText   누가 받는지 한 줄 설명(요청 언어)
 * @param emailable       이메일로 보낼 수 있는 이벤트인가(false 면 {@code emailEnabled} 는 늘 false)
 * @param emailEnabled    이메일 전달 여부 — {@code channels} 에 {@code email} 이 있는지와 같은 값이다(예전 화면과의 호환)
 * @param featureReady    발행처가 있는가(false 면 「준비 중」 — 설정을 바꿀 수 없다)
 * @param sentLast30Days  최근 30일 동안 만들어진 알림 수(받은 사람마다 한 건씩 센다)
 * @param channels        고른 전달 도구 id(서랍 제외): {@code email} · {@code browser} 가 있으면 앞에, 그다음 Slack/Mattermost 도구 id
 * @param teamIds         추가 수신 팀 id — 이름은 {@code team.read} 가 있어야 팀 목록에서 읽는다
 * @param broadcastAllowed 추가 수신 팀과 공유 채널(Slack · Mattermost)로 보낼 수 있는 이벤트인가(본인에게만 가는 이벤트·필수 이벤트는 false)
 * @param defaultRecipients 「권한자 전원에게 보내기」 — 기본 수신 규칙으로도 보내는가. false 면 고른 팀({@code teamIds})의 팀원에게만 보낸다(받을 팀원이 없으면 서버가 기본 수신으로 되돌린다).
 *                          {@code broadcastAllowed=false} 인 이벤트는 늘 true
 */
public record NotificationEventView(String code, String area, String severity, String name, String description, String recipientText, boolean mandatory,
                                    boolean emailable, boolean enabled, boolean emailEnabled, boolean featureReady, long sentLast30Days, List<String> channels,
                                    List<String> teamIds, boolean broadcastAllowed, boolean defaultRecipients) {
}
