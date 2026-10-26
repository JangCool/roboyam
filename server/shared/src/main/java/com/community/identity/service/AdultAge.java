package com.community.identity.service;

import java.time.LocalDate;
import java.time.Month;

/**
 * 만 19세 판정(commerce.md 12-1) — 생년월일과 서비스 날짜(한국 시간의 오늘)로만 정한다. 결제 시점마다 다시 계산하므로 19세 생일이 지나면 다시 인증하지 않아도 통과한다.
 *
 * <p>19번째 생일({@link #adultFrom})부터 성인이다. <b>2월 29일생은 평년이면 3월 1일</b>(민법 기간 계산 — 그 달에 해당 날이 없으면 그 달의 말일이 끝나고 다음 날부터 만 나이가 찬다).
 * {@code LocalDate#plusYears} 는 2월 28일로 내리므로 쓰지 않는다.
 */
public final class AdultAge {

    /** 성인 나이(만) */
    public static final int ADULT_AGE = 19;

    private AdultAge() {
    }

    /** 만 19세가 되는 날 */
    public static LocalDate adultFrom(LocalDate birthDate) {
        int year = birthDate.getYear() + ADULT_AGE;
        if (birthDate.getMonth() == Month.FEBRUARY && birthDate.getDayOfMonth() == 29 && !java.time.Year.isLeap(year)) {
            return LocalDate.of(year, Month.MARCH, 1);
        }
        return LocalDate.of(year, birthDate.getMonth(), birthDate.getDayOfMonth());
    }

    /** {@code today}(서비스 날짜)에 만 19세 이상인가 */
    public static boolean isAdult(LocalDate birthDate, LocalDate today) {
        return !today.isBefore(adultFrom(birthDate));
    }
}
