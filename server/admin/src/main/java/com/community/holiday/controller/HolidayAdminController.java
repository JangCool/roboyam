package com.community.holiday.controller;

import com.community.holiday.dto.HolidayAdminView;
import com.community.holiday.service.HolidaySyncService;
import com.community.holiday.service.SiteHolidayService;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.response.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 휴일 관리 API(설정 › 시스템 › 휴일 관리 {@code /settings/holidays}, 사용자 지시 2026-11-05) — 보기 {@code site.read}, 회사 휴무일 더하기·지우기·「지금 받기」 {@code site.manage}
 * (새 권한 없음 — 사이트 설정 권한). 달력 표시용 범위 읽기는 로그인한 운영자 누구나(모든 화면의 달력이 쓴다 — 회사 휴무일 포함). 응답은 {@code Cache-Control: no-store}.
 *
 * <ul>
 *   <li>{@code GET /calendar?from&to} — {@code [{date, name, kind: HOLIDAY|COMPANY}]}(최대 2년)</li>
 *   <li>{@code GET ?year=} — 그 해 목록 + 마지막 받기 + 인증키 여부</li>
 *   <li>{@code POST} {@code {date, name}} — 회사 휴무일(400 {@code fields.date}·{@code fields.name})</li>
 *   <li>{@code DELETE /{id}} — 회사 휴무일만(404 {@code HOLIDAY_NOT_FOUND} · 409 {@code HOLIDAY_NOT_EDITABLE})</li>
 *   <li>{@code POST /sync} — 공공데이터에서 올해·내년 받기(배치 {@code holiday-sync} 와 같은 서비스, 키 없으면 {@code NO_KEY})</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/holidays")
public class HolidayAdminController {

    static final int YEAR_MIN = 2000;
    static final int YEAR_MAX = 2100;

    private final SiteHolidayService holidays;
    private final HolidaySyncService sync;

    public HolidayAdminController(SiteHolidayService holidays, HolidaySyncService sync) {
        this.holidays = holidays;
        this.sync = sync;
    }

    @AdminAuthenticated
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<SiteHolidayService.Day>>> calendar(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return noStore(holidays.days(SiteHolidayService.date("from", from), SiteHolidayService.date("to", to), true));
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<HolidayAdminView.Year>> year(@RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now(com.community.zconfiguration.time.ServiceTime.ZONE).getYear() : year;
        if (y < YEAR_MIN || y > YEAR_MAX) {
            throw ValidationException.of("year", new MessageRef("holiday.year.invalid"));
        }
        return noStore(view(y));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<HolidayAdminView.Row>> create(@RequestBody(required = false) HolidayAdminView.CreateRequest body, AdminActor actor) {
        LocalDate date = SiteHolidayService.date("date", body == null ? null : body.date());
        return noStore(HolidayAdminView.Row.of(holidays.addCompanyDay(date, body == null ? null : body.name(), actor.operatorId(), actor.ip())));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, AdminActor actor) {
        holidays.deleteCompanyDay(id, actor.operatorId(), actor.ip());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<HolidayAdminView.Sync>> sync(AdminActor actor) {
        sync.sync(actor.operatorId(), actor.ip());
        return noStore(HolidayAdminView.Sync.of(sync.last()));
    }

    private HolidayAdminView.Year view(int year) {
        return new HolidayAdminView.Year(year, holidays.year(year).stream().map(HolidayAdminView.Row::of).toList(), HolidayAdminView.Sync.of(sync.last()),
                sync.keyConfigured());
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
