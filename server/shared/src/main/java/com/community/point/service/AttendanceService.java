package com.community.point.service;

import com.community.point.dto.response.AttendanceMonthView;
import com.community.point.dto.response.AttendanceResultView;
import com.community.point.dto.response.AttendanceTodayView;
import com.community.point.exception.AttendanceAlreadyDoneException;
import com.community.point.exception.AttendanceBlockedException;
import com.community.point.repository.master.PointAttendanceStore;
import com.community.point.repository.master.PointAttendanceStore.MemberState;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출석체크(계약 「1-3 출석체크」 — community {@code AttendanceService}·{@code docs/api-point.md} 「출석」을 로보얌에 맞춘 것).
 *
 * <ul>
 *   <li><b>출석</b>({@link #attend}): 자격 — 회원(없거나 탈퇴 401) → 정지·영구 정지 {@code 403 ACCOUNT_RESTRICTED} → 이메일 인증 전 {@code 403 EMAIL_NOT_VERIFIED}
 *       (포인트 상점 구매·프로필 아바타와 같은 판정과 차례. 사업자 회원도 같은 규칙 — 포인트 적립처럼 따로 막지 않는다). 하루(한국 시간 — 앱 시계) 한 번: 출석 줄 넣기가 한 문장
 *       {@code insert … on conflict do nothing} 이라 같은 날 두 번(동시에도) 눌러도 하나만 성공하고 나머지는 {@code 409 ATTENDANCE_ALREADY_DONE}. 연속 일수 = 어제 줄의 연속 일수 + 1
 *       (없으면 1). 포인트는 {@link PointService#attendance}(출석 포인트 → 연속 보너스, 각각 세이브포인트 — 동결·한도·실패여도 출석은 남는다).</li>
 *   <li><b>달력</b>({@link #month}): {@code month} 는 정확히 {@code YYYY-MM}(없으면 이번 달), 틀리면 {@code 400 VALIDATION fields.month}. 미래의 달은 {@code days: []}.
 *       {@code streak} = 오늘(없으면 어제) 줄의 연속 일수, 둘 다 없으면 0. {@code today} 는 달과 상관없이 오늘.</li>
 * </ul>
 */
@Service
public class AttendanceService {

    private static final Pattern MONTH = Pattern.compile("^\\d{4}-\\d{2}$");

    private final PointAttendanceStore store;
    private final PointService points;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public AttendanceService(PointAttendanceStore store, PointService points, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.points = points;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 오늘 출석한다 */
    @Transactional
    public AttendanceResultView attend(long userId) {
        MemberState state = store.member(userId).filter(s -> !s.gone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (state.suspended()) {
            throw new AttendanceBlockedException(AttendanceBlockedException.ACCOUNT_RESTRICTED);
        }
        if (!state.emailVerified()) {
            throw new AttendanceBlockedException(AttendanceBlockedException.EMAIL_NOT_VERIFIED);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDate today = ServiceTime.dateOf(now);
        int streak = store.streakOn(userId, today.minusDays(1)).map(previous -> previous + 1).orElse(1);
        long id = ids.nextId();
        if (!store.insert(id, userId, today, streak, now)) {
            throw new AttendanceAlreadyDoneException();
        }
        PointService.AttendanceEarned earned = points.attendance(userId, id, streak);
        return new AttendanceResultView(today.toString(), earned.earned(), earned.streakBonus(), streak, store.balance(userId));
    }

    /** 한 달의 출석 달력 */
    @Transactional(readOnly = true)
    public AttendanceMonthView month(long userId, String monthText) {
        LocalDate today = ServiceTime.today(clock);
        YearMonth month = parse(monthText, today);
        List<Integer> days = month.isAfter(YearMonth.from(today)) ? List.of()
                : store.days(userId, month.atDay(1), month.plusMonths(1).atDay(1)).stream().map(LocalDate::getDayOfMonth).toList();
        Integer todayStreak = store.streakOn(userId, today).orElse(null);
        int streak = todayStreak != null ? todayStreak : store.streakOn(userId, today.minusDays(1)).orElse(0);
        return new AttendanceMonthView(month.toString(), days, days.size(), streak, new AttendanceTodayView(today.toString(), todayStreak != null));
    }

    /** 오늘 출석했나 */
    @Transactional(readOnly = true)
    public boolean attendedToday(long userId) {
        return store.streakOn(userId, ServiceTime.today(clock)).isPresent();
    }

    private static YearMonth parse(String text, LocalDate today) {
        if (text == null || text.isBlank()) {
            return YearMonth.from(today);
        }
        if (!MONTH.matcher(text).matches()) {
            throw ValidationException.of("month", new MessageRef("attendance.month.invalid"));
        }
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException e) {
            throw ValidationException.of("month", new MessageRef("attendance.month.invalid"));
        }
    }
}
