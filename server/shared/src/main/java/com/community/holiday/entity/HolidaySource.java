package com.community.holiday.entity;

/**
 * 쉬는 날 줄의 출처({@code site_holidays.source}, shared V150).
 *
 * <ul>
 *   <li>{@link #PUBLIC_DATA} — 공공데이터 특일 정보(한국천문연구원 {@code getRestDeInfo})에서 받은 공휴일. 받을 때마다 그 해가 응답과 같아진다({@code HolidaySyncService}).</li>
 *   <li>{@link #SEED} — 공식 자료로 마이그레이션이 넣은 기본값(V146 → V150). 그 해를 공공데이터에서 처음 받으면 지운다.</li>
 *   <li>{@link #OPERATOR} — 운영자가 넣은 회사 휴무일(휴일 관리 화면). 받기가 절대 건드리지 않는다. 사이트(회원 화면)에는 보내지 않는다.</li>
 * </ul>
 */
public enum HolidaySource {
    PUBLIC_DATA, SEED, OPERATOR;

    /** 공휴일(회사 휴무일이 아님)인가 */
    public boolean publicHoliday() {
        return this != OPERATOR;
    }
}
