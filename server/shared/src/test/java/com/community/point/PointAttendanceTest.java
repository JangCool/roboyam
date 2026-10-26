package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.point.dto.response.AttendanceMonthView;
import com.community.point.dto.response.AttendanceResultView;
import com.community.point.exception.AttendanceAlreadyDoneException;
import com.community.point.exception.AttendanceBlockedException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 출석체크(계약 「1-3 출석체크」) — 하루 한 번(동시 두 번이면 하나만, 나머지 409) · 연속 보너스(배수 날만, 끊기면 1부터) · 동결이면 출석만 남고 포인트 0 · 자격(정지·이메일 인증) ·
 * 달력(월 형식·미래 달·today·연속 일수) · 하루 총 한도에 든다. 날짜는 앱 시계(한국 시간)로 옮겨 가며 본다. 불변식은 시험마다.
 */
class PointAttendanceTest extends PointTestBase {

    /** 한국 시간 그날 정오 */
    private static Instant noon(LocalDate day) {
        return day.atTime(12, 0).atZone(ServiceTime.ZONE).toInstant();
    }

    private long verifiedMember() {
        long id = kit.newMember();
        kit.jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        return id;
    }

    private AttendanceResultView attend(long member) {
        return kit.inTx(() -> kit.attendance.attend(member));
    }

    private AttendanceMonthView month(long member, String month) {
        return kit.inTx(() -> kit.attendance.month(member, month));
    }

    @Test
    void 하루에_한_번이고_두_번째는_409이며_포인트도_한_번이다() {
        long member = verifiedMember();
        AttendanceResultView first = attend(member);
        assertThat(first.date()).isEqualTo(ServiceTime.dateOf(kit.clock.instant()).toString());
        assertThat(first.earned()).isEqualTo(10);
        assertThat(first.streakBonus()).isZero();
        assertThat(first.streak()).isEqualTo(1);
        assertThat(first.balance()).isEqualTo(10);

        assertThatThrownBy(() -> attend(member)).isInstanceOf(AttendanceAlreadyDoneException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("ATTENDANCE_ALREADY_DONE");
        assertThat(kit.count("SELECT count(*) FROM point_attendances WHERE user_id = ?", member)).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'ATTENDANCE'", member)).isEqualTo(1);
        long attendanceId = kit.jdbc.queryForObject("SELECT id FROM point_attendances WHERE user_id = ?", Long.class, member);
        assertThat(kit.jdbc.queryForMap("SELECT ref_type, ref_id FROM point_ledger WHERE user_id = ? AND reason = 'ATTENDANCE'", member))
                .containsEntry("ref_type", "ATTENDANCE").containsEntry("ref_id", attendanceId);
        kit.assertInvariants(member);
    }

    @Test
    void 동시에_두_번_누르면_하나만_성공하고_나머지는_409() throws Exception {
        long member = verifiedMember();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        attend(member);
                        return "OK";
                    } catch (AttendanceAlreadyDoneException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            assertThat(results).containsOnlyOnce("OK").filteredOn(r -> !r.equals("OK")).containsOnly("ATTENDANCE_ALREADY_DONE").hasSize(3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(kit.count("SELECT count(*) FROM point_attendances WHERE user_id = ?", member)).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isEqualTo(1);
        kit.assertInvariants(member);
    }

    @Test
    void 연속_보너스는_연속_일수가_배수인_날만이고_끊기면_1부터_다시_센다() {
        long member = verifiedMember();
        LocalDate day1 = ServiceTime.dateOf(kit.clock.instant()).minusDays(20);
        List<long[]> results = new ArrayList<>(); // {streak, earned, bonus}
        kit.withSettings(Map.of("attendance_streak_days", 3, "attendance_streak_bonus", 20), () -> {
            for (int d : new int[] {0, 1, 2, 3, 4, 5, 7, 8, 9}) { // 7일째(인덱스 6)를 건너뛴다
                kit.clock.set(noon(day1.plusDays(d)));
                AttendanceResultView r = attend(member);
                results.add(new long[] {r.streak(), r.earned(), r.streakBonus()});
            }
        });
        assertThat(results).extracting(r -> r[0]).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 1L, 2L, 3L);
        assertThat(results).extracting(r -> r[2]).containsExactly(0L, 0L, 20L, 0L, 0L, 20L, 0L, 0L, 20L);
        assertThat(results).extracting(r -> r[1]).containsOnly(10L);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'ATTENDANCE_STREAK' AND ref_type = 'ATTENDANCE'", member))
                .isEqualTo(3);
        kit.assertInvariants(member);
    }

    @Test
    void 연속_보너스_일수나_양이_0이면_끈다() {
        long member = verifiedMember();
        LocalDate day1 = ServiceTime.dateOf(kit.clock.instant()).minusDays(30);
        kit.withSettings(Map.of("attendance_streak_days", 1, "attendance_streak_bonus", 0), () -> {
            kit.clock.set(noon(day1));
            assertThat(attend(member).streakBonus()).isZero();
        });
        kit.withSettings(Map.of("attendance_streak_days", 0, "attendance_streak_bonus", 20), () -> {
            kit.clock.set(noon(day1.plusDays(1)));
            assertThat(attend(member).streakBonus()).isZero();
        });
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'ATTENDANCE_STREAK'", member)).isZero();
    }

    @Test
    void 동결_중이면_출석은_남고_포인트만_0이다() {
        long member = verifiedMember();
        kit.freeze(member);
        AttendanceResultView r = attend(member);
        assertThat(r.earned()).isZero();
        assertThat(r.streakBonus()).isZero();
        assertThat(r.streak()).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM point_attendances WHERE user_id = ?", member)).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isZero();
        assertThat(kit.inTx(() -> kit.attendance.attendedToday(member))).isTrue();
    }

    @Test
    void 출석_포인트가_0이면_출석만_남는다() {
        long member = verifiedMember();
        kit.withSettings(Map.of("attendance_points", 0), () -> assertThat(attend(member).earned()).isZero());
        assertThat(kit.count("SELECT count(*) FROM point_attendances WHERE user_id = ?", member)).isEqualTo(1);
    }

    @Test
    void 출석과_연속_보너스는_하루_총_한도에_들고_걸치면_남은_만큼만() {
        long member = verifiedMember();
        kit.withSettings(Map.of("attendance_streak_days", 1, "attendance_streak_bonus", 20, "daily_total_cap", 25), () -> {
            AttendanceResultView r = attend(member);
            assertThat(r.earned()).isEqualTo(10);
            assertThat(r.streakBonus()).as("10 + 20 > 25 — 남은 15만").isEqualTo(15);
            assertThat(kit.inTx(() -> kit.points.earnedToday(member))).isEqualTo(25);
        });
        kit.assertInvariants(member);
    }

    @Test
    void 정지_회원은_403_ACCOUNT_RESTRICTED_이메일_인증_전은_403_EMAIL_NOT_VERIFIED_이고_출석이_남지_않는다() {
        long suspended = verifiedMember();
        kit.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        assertThatThrownBy(() -> attend(suspended)).isInstanceOf(AttendanceBlockedException.class).extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo("ACCOUNT_RESTRICTED");
        long banned = verifiedMember();
        kit.jdbc.update("UPDATE users SET status = 'BANNED' WHERE id = ?", banned);
        assertThatThrownBy(() -> attend(banned)).extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("ACCOUNT_RESTRICTED");
        long unverified = kit.newMember();
        assertThatThrownBy(() -> attend(unverified)).isInstanceOf(AttendanceBlockedException.class).extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo("EMAIL_NOT_VERIFIED");
        long withdrawn = verifiedMember();
        kit.jdbc.update("UPDATE users SET status = 'WITHDRAWN', email = NULL WHERE id = ?", withdrawn);
        assertThatThrownBy(() -> attend(withdrawn)).extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(kit.count("SELECT count(*) FROM point_attendances WHERE user_id IN (?, ?, ?, ?)", suspended, banned, unverified, withdrawn)).isZero();
    }

    @Test
    void 달력은_그_달의_출석일과_연속_일수와_오늘을_준다() {
        long member = verifiedMember();
        LocalDate today = LocalDate.of(2026, 9, 3);
        for (LocalDate day : List.of(LocalDate.of(2026, 8, 30), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2))) {
            kit.clock.set(noon(day));
            attend(member);
        }
        kit.clock.set(noon(today));

        AttendanceMonthView sep = month(member, "2026-09");
        assertThat(sep.month()).isEqualTo("2026-09");
        assertThat(sep.days()).containsExactly(1, 2);
        assertThat(sep.monthCount()).isEqualTo(2);
        assertThat(sep.streak()).as("오늘은 아직 — 어제까지 이어진 4일").isEqualTo(4);
        assertThat(sep.today().date()).isEqualTo("2026-09-03");
        assertThat(sep.today().attended()).isFalse();

        assertThat(month(member, null).month()).as("없으면 이번 달").isEqualTo("2026-09");
        assertThat(month(member, "2026-08").days()).containsExactly(30, 31);
        assertThat(month(member, "2026-10").days()).as("미래의 달").isEmpty();
        assertThat(month(member, "2026-10").today().date()).as("today 는 달과 무관").isEqualTo("2026-09-03");

        attend(member);
        AttendanceMonthView after = month(member, "2026-08");
        assertThat(after.streak()).isEqualTo(5);
        assertThat(after.today().attended()).isTrue();

        kit.clock.set(noon(today.plusDays(2))); // 하루 건너뜀
        assertThat(month(member, null).streak()).as("끊겼으면 0").isZero();
        assertThat(YearMonth.parse(month(member, null).month())).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void 달_형식이_틀리면_400_fields_month() {
        long member = verifiedMember();
        for (String bad : List.of("2026-1", "2026-13", "26-10", "2026/10", "abcd-ef", " 2026-10", "2026-10-01")) {
            assertThatThrownBy(() -> month(member, bad)).as(bad).isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsKey("month"));
        }
    }

    @Test
    void 출석_줄은_같은_회원_같은_날_하나뿐이다_DB_유일_제약() {
        long member = verifiedMember();
        attend(member);
        LocalDate today = ServiceTime.dateOf(kit.clock.instant());
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO point_attendances (id, user_id, day, streak) VALUES (?, ?, ?, 1)", kit.ids.nextId(), member,
                java.sql.Date.valueOf(today))).hasMessageContaining("ux_point_attendances_user_day");
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO point_attendances (id, user_id, day, streak) VALUES (?, ?, ?, 0)", kit.ids.nextId(), member,
                java.sql.Date.valueOf(today.minusDays(100)))).hasMessageContaining("ck_point_attendances_streak");
    }
}
