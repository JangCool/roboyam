package com.community.holiday.controller;

import com.community.holiday.service.SiteHolidayService;
import com.community.zconfiguration.response.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 공휴일(사용자 지시 2026-11-05 — 모든 날짜 달력에 공휴일 표시) — 비회원도 읽는다({@code SecurityConfig} 공개 GET).
 *
 * <ul>
 *   <li>{@code GET /api/holidays?from=YYYY-MM-DD&to=YYYY-MM-DD} — 범위(최대 2년) 안의 <b>공휴일만</b> {@code [{date, name, kind: HOLIDAY}]}(날짜 차례 — 같은 날 둘이면 두 줄).
 *       회사 휴무일은 날도 이름도 보내지 않는다(관리자만 본다). 범위가 비었거나 뒤집혔거나 2년을 넘으면 400 {@code fields.from}·{@code fields.to}.</li>
 *   <li>{@code Cache-Control: public, max-age=300} + {@code ETag}(내용의 지문, 같으면 304) — 로보티콘 카탈로그와 같은 방식.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/holidays")
public class PublicHolidayController {

    public static final String CACHE = "public, max-age=300";

    private final SiteHolidayService holidays;

    public PublicHolidayController(SiteHolidayService holidays) {
        this.holidays = holidays;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SiteHolidayService.Day>>> days(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        LocalDate start = SiteHolidayService.date("from", from);
        LocalDate end = SiteHolidayService.date("to", to);
        List<SiteHolidayService.Day> days = holidays.days(start, end, false);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, CACHE).eTag("\"" + SiteHolidayService.etag(start, end, days) + "\"").body(ApiResponse.success(days));
    }
}
