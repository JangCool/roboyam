package com.community.jjal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * irobit.jjal.* — 내 짤 한도(docs/features/my-jjal-contract.md 「규칙(가정 — 구현 때 값은 설정으로)」). 화면 {@code UtilJjal} 의 값과 같게 둔다(판정은 서버).
 *
 * @param maxBytes          올리는 파일 하나의 최대 용량(바이트, 기본 2MB) — 넘으면 413 {@code JJAL_TOO_LARGE}
 * @param maxSide           저장본 긴 변(px, 기본 300) — 머리의 가로·세로가 넘으면 디코딩 전에 400 {@code fields.file}
 * @param maxFrames         움짤 프레임 수 상한(기본 100)
 * @param maxDurationMillis 움짤 재생 시간 상한(ms, 기본 10초 — 프레임 지연 합, 브라우저처럼 10ms 이하 지연은 100ms 로 센다)
 * @param boxLimit          한 회원 보관함 상한(올린 것 + 담은 것 + 내려간 것, 기본 50)
 * @param perMinute         1분에 올릴 수 있는 수(기본 10, DB 에서 센다 — 서버 여러 대에서도 같다)
 * @param perDay            최근 24시간에 올릴 수 있는 수(기본 50)
 * @param postTokens        글 본문 하나에 넣을 수 있는 짤 토큰 수(기본 5)
 * @param commentTokens     댓글 하나에 넣을 수 있는 짤 토큰 수(기본 1)
 * @param orphanHours       보관함 줄도 쓰는 글·댓글도 없는 짤 파일을 정리 배치가 지우기 전 두는 시간(만든 뒤, 기본 24시간)
 * @param uploadLogDays     올리기 속도 기록을 남기는 날(기본 2일)
 */
@ConfigurationProperties(prefix = "irobit.jjal")
public record JjalProperties(@DefaultValue("2097152") long maxBytes, @DefaultValue("300") int maxSide, @DefaultValue("100") int maxFrames,
                             @DefaultValue("10000") long maxDurationMillis, @DefaultValue("50") int boxLimit, @DefaultValue("10") int perMinute,
                             @DefaultValue("50") int perDay, @DefaultValue("5") int postTokens, @DefaultValue("1") int commentTokens,
                             @DefaultValue("24") int orphanHours, @DefaultValue("2") int uploadLogDays) {
}
