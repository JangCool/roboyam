package com.community.holiday.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.holiday.entity.HolidaySource;
import com.community.holiday.exception.HolidayNotEditableException;
import com.community.holiday.exception.HolidayNotFoundException;
import com.community.holiday.repository.master.SiteHolidayStore;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 쉬는 날(사용자 지시 2026-11-05 — 표 하나 {@code site_holidays}) — 쉬는 날 판정({@link #calendar}), 달력 표시용 범위 읽기({@link #days}), 휴일 관리 화면의 해 목록·회사 휴무일 더하기·지우기.
 * 공공데이터 공휴일 받기는 {@code HolidaySyncService}.
 *
 * <p>판정은 부를 때마다 표를 통째로 읽는다(해마다 20줄 안팎) — 받기·회사 휴무일이 바뀌면 서버를 다시 띄우지 않아도 다음 판정부터 쓴다(서버가 여럿이어도 같다).
 * <b>이미 정해진 기한·이미 굳힌 지급일은 다시 계산하지 않는다</b> — 저장된 값은 그대로이고, 아직 정해지지 않은 것만 지금 표를 본다.
 */
@Service
public class SiteHolidayService {

    /** 회사 휴무일 이름 길이 */
    public static final int NAME_MIN = 1;
    public static final int NAME_MAX = 30;
    /** 달력 범위 읽기의 최대 길이(해) */
    public static final int RANGE_MAX_YEARS = 2;
    /** 감사 대상 종류 */
    public static final String AUDIT_TARGET = "SITE_HOLIDAY";

    /** 달력 칸 하나 — 종류 {@code HOLIDAY} 공휴일 · {@code COMPANY} 회사 휴무일 */
    public record Day(LocalDate date, String name, String kind) {
    }

    private final SiteHolidayStore store;
    private final AuditLogService audit;
    private final ObjectProvider<Clock> clocks;

    public SiteHolidayService(SiteHolidayStore store, AuditLogService audit, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.audit = audit;
        this.clocks = clocks;
    }

    /** 쉬는 날 판정 — 토·일 + 표의 모든 날(공휴일·회사 휴무일) */
    public BusinessDayCalendar calendar() {
        return BusinessDayCalendar.of(store.allDates());
    }

    /**
     * 달력 표시용 — {@code from}~{@code to}(양 끝 포함, 최대 {@value #RANGE_MAX_YEARS}년). 사이트(회원 화면)는 공휴일만({@code includeCompany = false} — 회사 휴무일은 이름도 날도 보내지 않는다).
     *
     * @throws ValidationException 범위가 비었거나 뒤집혔거나 {@value #RANGE_MAX_YEARS}년을 넘는다({@code fields.from}·{@code fields.to})
     */
    @Transactional(readOnly = true)
    public List<Day> days(LocalDate from, LocalDate to, boolean includeCompany) {
        checkRange(from, to);
        return store.between(from, to).stream().filter(r -> includeCompany || r.source().publicHoliday())
                .map(r -> new Day(r.date(), r.name(), r.source().publicHoliday() ? "HOLIDAY" : "COMPANY")).toList();
    }

    /** 요청의 날짜 글자({@code YYYY-MM-DD}) → 날짜. 비었거나 읽을 수 없으면 400 {@code fields.<칸>} */
    public static LocalDate date(String field, String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDate.parse(raw.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw ValidationException.of(field, new MessageRef("holiday.range.required"));
        }
    }

    /**
     * 달력 응답의 지문(ETag 값 — 따옴표 없이) — 범위와 내용의 SHA-256 앞 16바이트(hex). 앱 서버가 여럿이어도 같은 내용이면 같은 값(로보티콘 카탈로그와 같은 방식).
     */
    public static String etag(LocalDate from, LocalDate to, List<Day> days) {
        StringBuilder text = new StringBuilder(from + "~" + to);
        days.forEach(d -> text.append('\n').append(d.date()).append('|').append(d.kind()).append('|').append(d.name()));
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash, 0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 범위 검사 */
    public static void checkRange(LocalDate from, LocalDate to) {
        if (from == null) {
            throw ValidationException.of("from", new MessageRef("holiday.range.required"));
        }
        if (to == null) {
            throw ValidationException.of("to", new MessageRef("holiday.range.required"));
        }
        if (to.isBefore(from) || to.isAfter(from.plusYears(RANGE_MAX_YEARS))) {
            throw ValidationException.of("to", new MessageRef("holiday.range.invalid", RANGE_MAX_YEARS));
        }
    }

    /** 휴일 관리 화면 — 그 해의 모든 줄(날짜 차례, 같은 날은 공휴일 먼저) */
    @Transactional(readOnly = true)
    public List<SiteHolidayStore.Row> year(int year) {
        return store.between(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    /**
     * 회사 휴무일 더하기 — 날짜·이름({@value #NAME_MIN}~{@value #NAME_MAX}자, 앞뒤 공백 지움) 필수, 그날 회사 휴무일이 이미 있으면 400 {@code fields.date}. 감사 {@code SITE_HOLIDAY_CREATE}.
     */
    @Transactional
    public SiteHolidayStore.Row addCompanyDay(LocalDate date, String name, long operatorId, String ip) {
        if (date == null) {
            throw ValidationException.of("date", new MessageRef("holiday.date.required"));
        }
        String value = name == null ? "" : name.strip();
        if (value.length() < NAME_MIN) {
            throw ValidationException.of("name", new MessageRef("holiday.name.required"));
        }
        if (value.length() > NAME_MAX) {
            throw ValidationException.of("name", new MessageRef("holiday.name.max", NAME_MAX));
        }
        if (store.operatorDayExists(date)) {
            throw ValidationException.of("date", new MessageRef("holiday.date.duplicate"));
        }
        long id = store.insert(date, value, HolidaySource.OPERATOR, false, operatorId, now());
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.SITE_HOLIDAY_CREATE, AuditResult.SUCCESS).target(AUDIT_TARGET, String.valueOf(id))
                .with("date", date.toString()).with("name", value));
        return store.find(id).orElseThrow();
    }

    /**
     * 회사 휴무일 지우기 — 공공데이터·기본값 줄은 409 {@code HOLIDAY_NOT_EDITABLE}(읽기만), 없으면 404 {@code HOLIDAY_NOT_FOUND}. 감사 {@code SITE_HOLIDAY_DELETE}.
     */
    @Transactional
    public void deleteCompanyDay(long id, long operatorId, String ip) {
        SiteHolidayStore.Row row = store.find(id).orElseThrow(HolidayNotFoundException::new);
        if (row.source() != HolidaySource.OPERATOR) {
            throw new HolidayNotEditableException();
        }
        store.delete(id);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.SITE_HOLIDAY_DELETE, AuditResult.SUCCESS).target(AUDIT_TARGET, String.valueOf(id))
                .with("date", row.date().toString()).with("name", row.name()));
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant().truncatedTo(ChronoUnit.MICROS);
    }
}
