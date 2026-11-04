package com.community.dashboard.dto.response.gadget;

import java.time.LocalDate;
import java.util.List;

/** {@code SIGNUP_TREND} — 일별 가입(오래된 차례, 오늘 포함 7|30일 — 게시판 활동 일별 집계의 가입 칸, 없는 날은 0) */
public record DashboardSignupTrendView(List<Day> days) implements DashboardGadgetData {

    public record Day(LocalDate date, long count) {
    }
}
