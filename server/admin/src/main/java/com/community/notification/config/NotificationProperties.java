package com.community.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.notification.* — 관리자 알림의 수치(docs/features/notification-contract.md 가정). 모두 설정값이다.
 *
 * @param watcherEnabled      이 서버가 배치 실패·종료 알림 감시를 하는가(끄면 이 서버는 감시하지 않는다 — 서버 여러 대 중 한 대만 둬도 된다. 여럿이 켜져 있어도 중복 알림은 없다)
 * @param batchWatchSeconds   감시 주기(초) — 배치가 끝난 뒤 알림까지 최대 이만큼 늦는다. 값을 바꾸려면 재기동
 * @param batchWatchMaxAgeHours 끝난 지 이 시간이 넘은 줄은 알리지 않고 처리만 한다(감시가 오래 멈췄다 돌아왔을 때 옛 실패가 한꺼번에 알림이 되지 않게)
 * @param channelTestPerMinute 알림 도구 연결 시험을 운영자 한 명이 1분에 할 수 있는 횟수(이 서버 안에서 센다)
 */
@ConfigurationProperties(prefix = "roboyam.notification")
public record NotificationProperties(@DefaultValue("true") boolean watcherEnabled, @DefaultValue("30") int batchWatchSeconds,
                                     @DefaultValue("24") int batchWatchMaxAgeHours, @DefaultValue("6") int channelTestPerMinute) {
}
