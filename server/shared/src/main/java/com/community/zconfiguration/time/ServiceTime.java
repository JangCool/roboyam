package com.community.zconfiguration.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 서비스 날짜의 시간대 — 커머스의 「날짜」 기준(만 나이 · 재인증 가능일 · 하루 한도 · 소멸일 · 청약철회 기한)은 <b>한국 시간</b>이다(docs/domain/commerce.md 3절 「공통 규칙」 —
 * 시각은 UTC 로 저장하고 날짜 판단만 KST, 커머스는 공용 KST 유틸 하나를 쓴다). 화면 표시 시간대(사이트 표시 설정)와는 따로다 — 운영자가 표시 시간대를 바꿔도 업무 날짜는 바뀌지 않는다.
 */
public final class ServiceTime {

    /** 서비스 날짜의 시간대 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private ServiceTime() {
    }

    /** 그 시계의 서비스 날짜(오늘) */
    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    /** 그 순간의 서비스 날짜 */
    public static LocalDate dateOf(Instant at) {
        return LocalDate.ofInstant(at, ZONE);
    }

    /**
     * 그 순간이 지난 뒤 <b>하루 종일</b> 쓸 수 있는 첫 서비스 날짜 — 0시 정각이면 그날, 아니면 다음 날(올림). 「YYYY-MM-DD 부터 다시 할 수 있어요」에 쓴다(그날 언제 해도 된다).
     */
    public static LocalDate firstFullDateAfter(Instant at) {
        ZonedDateTime local = at.atZone(ZONE);
        LocalDate date = local.toLocalDate();
        return local.toLocalTime().equals(LocalTime.MIDNIGHT) ? date : date.plusDays(1);
    }
}
