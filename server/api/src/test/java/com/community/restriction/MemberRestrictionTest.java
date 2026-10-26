package com.community.restriction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.audit.dto.AuditActions;
import com.community.commerce.CommerceApiTestBase;
import com.community.restriction.dto.request.RestrictionGrant;
import com.community.restriction.dto.response.MemberRestrictionView;
import com.community.restriction.entity.RestrictedFeature;
import com.community.restriction.entity.RestrictionAutoRule;
import com.community.restriction.entity.RestrictionSource;
import com.community.restriction.entity.RestrictionStatus;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.exception.MemberRestrictedException;
import com.community.restriction.exception.MemberWithdrawnException;
import com.community.restriction.exception.RestrictionMemberNotFoundException;
import com.community.restriction.exception.RestrictionNotActiveException;
import com.community.restriction.exception.RestrictionNotFoundException;
import com.community.restriction.service.MemberRestrictionService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 기능 제한 규칙(commerce.md 12-3, 계약 「0-4」 화면 확정 결정 1~3·데이터 규칙)을 실제 PostgreSQL 에서 확인한다 — 막는 것 표·진행 중 판정·부여(운영자/자동)·해제·감사·회원 알림.
 */
class MemberRestrictionTest extends CommerceApiTestBase {

    private static final String REASON = "결제하지 않은 선점이 반복됨";
    private static final String MEMO = "내부 메모 — 회원에게 보이면 안 됨 7f3a";
    private static final String IP = "127.0.0.1";

    @Autowired
    private MemberRestrictionService service;

    private MemberRestrictionView grant(long member, RestrictionType type, Instant until, long operator) {
        return service.grant(member, new RestrictionGrant(type, until, REASON, MEMO), operator, IP);
    }

    private Instant in(Duration duration) {
        return clock.instant().plus(duration);
    }

    @Nested
    class 막는_것 {

        @Test
        void 포인트_동결은_적립_사용_충전을_막고_충전_포인트_환불_신청은_막지_않는다() {
            long member = newMember();
            grant(member, RestrictionType.POINT_FREEZE, in(Duration.ofDays(3)), newOperator());

            for (RestrictedFeature blocked : List.of(RestrictedFeature.POINT_EARN, RestrictedFeature.POINT_USE, RestrictedFeature.CHARGE)) {
                assertThatThrownBy(() -> service.assertAllowed(member, blocked)).isInstanceOf(MemberRestrictedException.class);
            }
            assertThatCode(() -> service.assertAllowed(member, RestrictedFeature.CHARGE_REFUND_REQUEST)).doesNotThrowAnyException();
            assertThatCode(() -> service.assertAllowed(member, RestrictedFeature.GROUP_BUY_JOIN)).doesNotThrowAnyException();
        }

        @Test
        void 공구_참여_제한은_새_참여만_막는다() {
            long member = newMember();
            grant(member, RestrictionType.GROUP_BUY_BAN, in(Duration.ofDays(1)), newOperator());

            assertThatThrownBy(() -> service.assertAllowed(member, RestrictedFeature.GROUP_BUY_JOIN)).isInstanceOf(MemberRestrictedException.class);
            for (RestrictedFeature free : List.of(RestrictedFeature.POINT_EARN, RestrictedFeature.POINT_USE, RestrictedFeature.CHARGE, RestrictedFeature.CHARGE_REFUND_REQUEST)) {
                assertThatCode(() -> service.assertAllowed(member, free)).doesNotThrowAnyException();
            }
        }

        @Test
        void 충전_제한은_새_충전만_막고_기존_포인트_사용_환불은_된다() {
            long member = newMember();
            grant(member, RestrictionType.CHARGE_BAN, null, newOperator());

            assertThatThrownBy(() -> service.assertAllowed(member, RestrictedFeature.CHARGE)).isInstanceOf(MemberRestrictedException.class);
            for (RestrictedFeature free : List.of(RestrictedFeature.POINT_USE, RestrictedFeature.CHARGE_REFUND_REQUEST, RestrictedFeature.POINT_EARN,
                    RestrictedFeature.GROUP_BUY_JOIN)) {
                assertThatCode(() -> service.assertAllowed(member, free)).doesNotThrowAnyException();
            }
        }

        @Test
        void 막히면_403_과_종류_가장_늦은_끝_공개_사유를_싣고_운영_메모는_없다() {
            long member = newMember();
            long operator = newOperator();
            Instant early = in(Duration.ofDays(1));
            Instant late = in(Duration.ofDays(5));
            grant(member, RestrictionType.GROUP_BUY_BAN, early, operator);
            service.grant(member, new RestrictionGrant(RestrictionType.GROUP_BUY_BAN, late, "늦게 끝나는 쪽의 사유", MEMO), operator, IP); // 겹쳐도 막지 않는다(결정 1)

            MemberRestrictedException error = catchThrowableOfType(MemberRestrictedException.class,
                    () -> service.assertAllowed(member, RestrictedFeature.GROUP_BUY_JOIN));
            assertThat(error.getStatus().value()).isEqualTo(403);
            assertThat(error.getErrorCode()).isEqualTo("MEMBER_RESTRICTED");
            @SuppressWarnings("unchecked")
            Map<String, Object> restriction = (Map<String, Object>) error.getExtras().get("restriction");
            assertThat(restriction).containsOnlyKeys("type", "until", "reason");
            assertThat(restriction).containsEntry("type", "GROUP_BUY_BAN").containsEntry("until", late.toString()).containsEntry("reason", "늦게 끝나는 쪽의 사유");
            assertThat(restriction.values()).noneMatch(value -> String.valueOf(value).contains(MEMO));
        }

        @Test
        void 같은_종류에_무기한이_있으면_until_은_null() {
            long member = newMember();
            long operator = newOperator();
            grant(member, RestrictionType.CHARGE_BAN, in(Duration.ofDays(1)), operator);
            grant(member, RestrictionType.CHARGE_BAN, null, operator);

            MemberRestrictedException error = catchThrowableOfType(MemberRestrictedException.class, () -> service.assertAllowed(member, RestrictedFeature.CHARGE));
            @SuppressWarnings("unchecked")
            Map<String, Object> restriction = (Map<String, Object>) error.getExtras().get("restriction");
            assertThat(restriction).containsEntry("until", null);
        }

        @Test
        void 두_종류가_같은_기능을_막으면_더_늦게_끝나는_종류를_알린다() {
            long member = newMember();
            long operator = newOperator();
            grant(member, RestrictionType.POINT_FREEZE, in(Duration.ofDays(2)), operator);
            grant(member, RestrictionType.CHARGE_BAN, in(Duration.ofDays(9)), operator);

            MemberRestrictedException error = catchThrowableOfType(MemberRestrictedException.class, () -> service.assertAllowed(member, RestrictedFeature.CHARGE));
            assertThat(error.getExtras().get("restriction").toString()).contains("CHARGE_BAN");
        }

        @Test
        void 기간이_끝나면_막지_않는다_진행_중_판정은_끝_시각을_넘지_않는다() {
            long member = newMember();
            Instant until = in(Duration.ofHours(2));
            grant(member, RestrictionType.GROUP_BUY_BAN, until, newOperator());

            clock.set(until.minusSeconds(1));
            assertThat(service.hasActive(member, RestrictionType.GROUP_BUY_BAN)).isTrue();
            clock.set(until);
            assertThat(service.hasActive(member, RestrictionType.GROUP_BUY_BAN)).isFalse();
            assertThat(service.hasActive(member)).isFalse();
            assertThatCode(() -> service.assertAllowed(member, RestrictedFeature.GROUP_BUY_JOIN)).doesNotThrowAnyException();
        }
    }

    @Nested
    class 운영자_부여 {

        @Test
        void 부여하면_한_줄과_감사_회원_알림이_남고_알림에는_운영_메모가_없다() {
            long member = newMember();
            long operator = newOperator();
            Instant until = in(Duration.ofDays(7));

            MemberRestrictionView view = grant(member, RestrictionType.POINT_FREEZE, until, operator);

            assertThat(view.status()).isEqualTo(RestrictionStatus.ACTIVE);
            assertThat(view.source()).isEqualTo(RestrictionSource.OPERATOR);
            assertThat(view.reason()).isEqualTo(REASON);
            assertThat(view.adminMemo()).isEqualTo(MEMO);
            assertThat(view.startsAt()).isEqualTo(clock.instant());
            assertThat(view.until()).isEqualTo(until);
            assertThat(view.createdBy().id()).isEqualTo(Long.toString(operator));
            assertThat(view.createdBy().name()).isNotBlank();
            assertThat(view.autoRule()).isNull();

            Map<String, Object> audit = lastAudit("MEMBER", Long.toString(member), AuditActions.MEMBER_RESTRICTION_GRANT);
            assertThat(audit.get("actor_type")).isEqualTo("OPERATOR");
            assertThat(audit.get("actor_id")).isEqualTo(operator);
            assertThat((String) audit.get("detail")).contains("\"source\":\"OPERATOR\"", "\"type\":\"POINT_FREEZE\"", "\"until\":\"" + until + "\"", view.id());

            List<Map<String, Object>> notices = jdbc.queryForList("SELECT type, payload::text AS payload FROM member_notifications WHERE user_id = ?", member);
            assertThat(notices).hasSize(1);
            assertThat(notices.get(0).get("type")).isEqualTo("FEATURE_RESTRICTED");
            String payload = (String) notices.get(0).get("payload");
            assertThat(payload).contains("POINT_FREEZE", REASON, until.toString()).doesNotContain(MEMO).doesNotContain("adminMemo");
        }

        @Test
        void 무기한도_걸_수_있고_감사에는_indefinite() {
            long member = newMember();
            MemberRestrictionView view = grant(member, RestrictionType.CHARGE_BAN, null, newOperator());
            assertThat(view.until()).isNull();
            assertThat((String) lastAudit("MEMBER", Long.toString(member), AuditActions.MEMBER_RESTRICTION_GRANT).get("detail")).contains("\"indefinite\":true");
        }

        @Test
        void 입력_검사는_종류_끝_사유_메모_차례로_모아_400() {
            long member = newMember();
            ValidationException error = catchThrowableOfType(ValidationException.class,
                    () -> service.grant(member, new RestrictionGrant(null, clock.instant(), "짧음", "m".repeat(1001)), newOperator(), IP));
            assertThat(error.getFields()).containsOnlyKeys("type", "until", "reason", "adminMemo");
            assertThat(error.getFields().keySet()).containsExactly("type", "until", "reason", "adminMemo");

            assertThatThrownBy(() -> service.grant(member, new RestrictionGrant(RestrictionType.CHARGE_BAN, null, "r".repeat(201), null), newOperator(), IP))
                    .isInstanceOf(ValidationException.class);
            assertThat(service.list(member)).isEmpty();
        }

        @Test
        void 끝이_지금_이전이면_400() {
            long member = newMember();
            ValidationException error = catchThrowableOfType(ValidationException.class,
                    () -> grant(member, RestrictionType.CHARGE_BAN, clock.instant().minusSeconds(1), newOperator()));
            assertThat(error.getFields()).containsOnlyKeys("until");
        }

        @Test
        void 탈퇴_회원에게는_걸_수_없고_없는_회원은_404() {
            long member = newMember();
            markWithdrawn(member);
            assertThatThrownBy(() -> grant(member, RestrictionType.CHARGE_BAN, null, newOperator())).isInstanceOf(MemberWithdrawnException.class);
            assertThat(service.list(member)).isEmpty();

            assertThatThrownBy(() -> grant(ids.nextId(), RestrictionType.CHARGE_BAN, null, newOperator())).isInstanceOf(RestrictionMemberNotFoundException.class)
                    .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("MEMBER_NOT_FOUND");
        }
    }

    @Nested
    class 회원_알림_문구 {

        @Test
        void 알림함에는_어떤_기능_언제까지_공개_사유가_보이고_운영_메모는_없다() {
            long member = newMember();
            Instant until = Instant.parse("2030-01-02T03:04:00Z"); // 2030-01-02 12:04 KST
            grant(member, RestrictionType.GROUP_BUY_BAN, until, newOperator());

            var item = memberNotifications.list(member, null, 10, null).items().get(0);
            assertThat(item.type()).isEqualTo("FEATURE_RESTRICTED");
            assertThat(item.category()).isEqualTo("system");
            assertThat(item.title()).contains("공동구매 참여", "2030-01-02 12:04");
            assertThat(item.body()).isEqualTo(REASON);
            assertThat(item.toString()).doesNotContain(MEMO);
        }

        @Test
        void 무기한이면_해제될_때까지_자동_부여면_규칙_사유() {
            long member = newMember();
            grant(member, RestrictionType.CHARGE_BAN, null, newOperator());
            assertThat(memberNotifications.list(member, null, 10, null).items().get(0).title()).contains("포인트 충전", "해제될 때까지");

            long auto = newMember();
            service.grantAuto(auto, RestrictionType.GROUP_BUY_BAN, Duration.ofHours(24), RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED);
            assertThat(memberNotifications.list(auto, null, 10, null).items().get(0).body()).contains("24시간");
        }
    }

    @Autowired
    private com.community.membernotification.service.MemberNotificationService memberNotifications;

    @Nested
    class 자동_부여 {

        @Test
        void 공구_선점_반복_만료는_24시간_공구_참여_제한이고_감사는_SYSTEM_이다() {
            long member = newMember();
            MemberRestrictionView view = service.grantAuto(member, RestrictionType.GROUP_BUY_BAN, Duration.ofHours(24), RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED)
                    .orElseThrow();

            assertThat(view.source()).isEqualTo(RestrictionSource.AUTO);
            assertThat(view.autoRule()).isEqualTo(RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED);
            assertThat(view.until()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
            assertThat(view.createdBy()).isNull();
            assertThat(view.reason()).isNotBlank(); // 규칙의 번들 문구
            Map<String, Object> audit = lastAudit("MEMBER", Long.toString(member), AuditActions.MEMBER_RESTRICTION_GRANT);
            assertThat(audit.get("actor_type")).isEqualTo("SYSTEM");
            assertThat(audit.get("actor_id")).isNull();
            assertThat((String) audit.get("detail")).contains("\"source\":\"AUTO\"", "\"autoRule\":\"GROUP_BUY_HOLD_EXPIRED\"");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'FEATURE_RESTRICTED'", Integer.class, member))
                    .isEqualTo(1);
        }

        @Test
        void 같은_종류가_더_늦게까지_진행_중이면_만들지_않는다() {
            long member = newMember();
            grant(member, RestrictionType.GROUP_BUY_BAN, in(Duration.ofDays(3)), newOperator());
            assertThat(service.grantAuto(member, RestrictionType.GROUP_BUY_BAN, Duration.ofHours(24), RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED)).isEmpty();

            long indefinite = newMember();
            grant(indefinite, RestrictionType.GROUP_BUY_BAN, null, newOperator());
            assertThat(service.grantAuto(indefinite, RestrictionType.GROUP_BUY_BAN, Duration.ofHours(24), RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED)).isEmpty();
        }

        @Test
        void 같은_종류가_더_일찍_끝나거나_다른_종류만_있으면_만든다() {
            long member = newMember();
            grant(member, RestrictionType.GROUP_BUY_BAN, in(Duration.ofHours(2)), newOperator());
            grant(member, RestrictionType.CHARGE_BAN, null, newOperator());
            assertThat(service.grantAuto(member, RestrictionType.GROUP_BUY_BAN, Duration.ofHours(24), RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED)).isPresent();
        }

        @Test
        void 탈퇴했거나_없는_회원에게는_만들지_않는다() {
            long member = newMember();
            markWithdrawn(member);
            assertThat(service.grantAuto(member, RestrictionType.GROUP_BUY_BAN, Duration.ofHours(24), RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED)).isEmpty();
            assertThat(service.grantAuto(ids.nextId(), RestrictionType.GROUP_BUY_BAN, Duration.ofHours(24), RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED)).isEmpty();
        }

        @Test
        void 같은_회원의_자동_부여가_동시에_들어와도_하나만_만든다() throws Exception {
            long member = newMember();
            int threads = 4;
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<Future<Optional<MemberRestrictionView>>> results = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    results.add(pool.submit(() -> {
                        go.await();
                        return service.grantAuto(member, RestrictionType.GROUP_BUY_BAN, Duration.ofHours(24), RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED);
                    }));
                }
                go.countDown();
                int made = 0;
                for (Future<Optional<MemberRestrictionView>> result : results) {
                    made += result.get(30, TimeUnit.SECONDS).isPresent() ? 1 : 0;
                }
                assertThat(made).isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member_restrictions WHERE user_id = ?", Integer.class, member)).isEqualTo(1);
        }
    }

    @Nested
    class 해제 {

        @Test
        void 해제하면_사유와_해제한_사람이_남고_감사가_남는다() {
            long member = newMember();
            long operator = newOperator();
            MemberRestrictionView granted = grant(member, RestrictionType.POINT_FREEZE, null, operator);
            clock.advance(Duration.ofMinutes(5));

            MemberRestrictionView lifted = service.lift(member, Long.parseLong(granted.id()), "  오탐으로 해제합니다  ", operator, IP);

            assertThat(lifted.status()).isEqualTo(RestrictionStatus.LIFTED);
            assertThat(lifted.liftReason()).isEqualTo("오탐으로 해제합니다");
            assertThat(lifted.liftedAt()).isEqualTo(clock.instant());
            assertThat(lifted.liftedBy().id()).isEqualTo(Long.toString(operator));
            assertThat(service.hasActive(member)).isFalse();
            assertThat((String) lastAudit("MEMBER", Long.toString(member), AuditActions.MEMBER_RESTRICTION_LIFT).get("detail"))
                    .contains(granted.id(), "\"type\":\"POINT_FREEZE\"", "오탐으로 해제합니다");
        }

        @Test
        void 해제_사유는_5_200자_필수() {
            long member = newMember();
            MemberRestrictionView granted = grant(member, RestrictionType.POINT_FREEZE, null, newOperator());
            for (String reason : new String[] {null, "", "넷글자임", "r".repeat(201)}) {
                ValidationException error = catchThrowableOfType(ValidationException.class,
                        () -> service.lift(member, Long.parseLong(granted.id()), reason, newOperator(), IP));
                assertThat(error.getFields()).containsOnlyKeys("reason");
            }
            assertThat(service.hasActive(member)).isTrue();
        }

        @Test
        void 이미_해제했거나_기간이_끝났으면_409_남의_것이나_없는_것은_404() {
            long member = newMember();
            long operator = newOperator();
            long lifted = Long.parseLong(grant(member, RestrictionType.POINT_FREEZE, null, operator).id());
            service.lift(member, lifted, "첫 번째 해제 사유", operator, IP);
            assertThatThrownBy(() -> service.lift(member, lifted, "두 번째 해제 사유", operator, IP)).isInstanceOf(RestrictionNotActiveException.class);

            long expired = Long.parseLong(grant(member, RestrictionType.CHARGE_BAN, in(Duration.ofHours(1)), operator).id());
            clock.advance(Duration.ofHours(2));
            assertThatThrownBy(() -> service.lift(member, expired, "끝난 제한 해제 시도", operator, IP)).isInstanceOf(RestrictionNotActiveException.class);

            long others = Long.parseLong(grant(newMember(), RestrictionType.CHARGE_BAN, null, operator).id());
            assertThatThrownBy(() -> service.lift(member, others, "남의 제한 해제 시도", operator, IP)).isInstanceOf(RestrictionNotFoundException.class);
            assertThatThrownBy(() -> service.lift(member, ids.nextId(), "없는 제한 해제 시도", operator, IP)).isInstanceOf(RestrictionNotFoundException.class);
        }

        @Test
        void 동시에_해제하면_하나만_이긴다() throws Exception {
            long member = newMember();
            long operator = newOperator();
            long id = Long.parseLong(grant(member, RestrictionType.POINT_FREEZE, null, operator).id());
            int threads = 4;
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<Future<String>> results = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    results.add(pool.submit(() -> {
                        go.await();
                        try {
                            service.lift(member, id, "동시 해제 시험 사유", operator, IP);
                            return "OK";
                        } catch (CommonException e) {
                            return e.getErrorCode();
                        }
                    }));
                }
                go.countDown();
                List<String> outcomes = new ArrayList<>();
                for (Future<String> result : results) {
                    outcomes.add(result.get(30, TimeUnit.SECONDS));
                }
                assertThat(outcomes).containsOnlyOnce("OK");
                assertThat(outcomes.stream().filter(o -> !o.equals("OK"))).allMatch("RESTRICTION_NOT_ACTIVE"::equals);
            } finally {
                pool.shutdownNow();
            }
            assertThat(auditCount("MEMBER", Long.toString(member), AuditActions.MEMBER_RESTRICTION_LIFT)).isEqualTo(1);
        }
    }

    @Nested
    class 목록 {

        @Test
        void 진행_중이_먼저_그다음_최근_것부터이고_상태는_지금으로_판정한다() {
            long member = newMember();
            long operator = newOperator();
            MemberRestrictionView expired = grant(member, RestrictionType.CHARGE_BAN, in(Duration.ofHours(1)), operator);
            clock.advance(Duration.ofMinutes(1));
            MemberRestrictionView active = grant(member, RestrictionType.GROUP_BUY_BAN, null, operator);
            clock.advance(Duration.ofMinutes(1));
            MemberRestrictionView lifted = grant(member, RestrictionType.POINT_FREEZE, null, operator);
            service.lift(member, Long.parseLong(lifted.id()), "목록 시험 해제", operator, IP);
            clock.advance(Duration.ofHours(2));

            List<MemberRestrictionView> list = service.list(member);
            assertThat(list).extracting(MemberRestrictionView::id).containsExactly(active.id(), lifted.id(), expired.id());
            assertThat(list).extracting(MemberRestrictionView::status).containsExactly(RestrictionStatus.ACTIVE, RestrictionStatus.LIFTED, RestrictionStatus.EXPIRED);
            assertThat(service.hasActive(member)).isTrue();
        }
    }
}
