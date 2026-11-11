package com.community.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.account.exception.AccountCurrentPasswordWrongException;
import com.community.analytics.store.VisitPeriods;
import com.community.audit.dto.AuditActions;
import com.community.auth.dto.request.SignupRequest;
import com.community.auth.exception.AuthRejoinBlockedException;
import com.community.auth.service.MeService;
import com.community.auth.service.SignupService;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.groupbuy.repository.master.GroupBuyMemberStore;
import com.community.point.entity.PointKind;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.point.service.PointService;
import com.community.profile.exception.ProfileNotFoundException;
import com.community.profile.service.UserProfileService;
import com.community.restriction.dto.request.RestrictionGrant;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.service.MemberRestrictionService;
import com.community.social.entity.SocialProvider;
import com.community.sociallogin.dto.PendingSignup;
import com.community.sociallogin.service.SocialAccountWriter;
import com.community.testsupport.PointInvariants;
import com.community.withdrawal.batch.WithdrawalAutoCompleteJob;
import com.community.withdrawal.dto.request.WithdrawalRequest;
import com.community.withdrawal.dto.response.WithdrawalResult;
import com.community.withdrawal.dto.response.WithdrawalView;
import com.community.withdrawal.entity.WithdrawalKind;
import com.community.withdrawal.exception.WithdrawalAlreadyScheduledException;
import com.community.withdrawal.exception.WithdrawalNotScheduledException;
import com.community.withdrawal.exception.WithdrawalReauthRequiredException;
import com.community.withdrawal.service.IdentityWithdrawalRecorder;
import com.community.withdrawal.service.WithdrawalService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원 탈퇴(docs/features/member-withdrawal-contract.md, commerce.md 17절 · 18절 P28·P29·P30·P30b~P30e) — 실제 PostgreSQL 에서 신청·즉시 완료·예약·배치 자동 완료·취소·
 * 재가입 제한·모임장 처리·탈퇴 진행 중 차단을 확인한다. 공구 주문 행은 공구 전체(상품·공구·차수)가 있어야 만들 수 있어, 「남은 일」은 같은 판정을 타는 송금 대기 환불
 * ({@code commerce_bank_transfers})로 대신한다.
 */
class WithdrawalFlowTest extends CommerceApiTestBase {

    private static final String IP = "127.0.0.1";
    private static final String PASSWORD = "Withdraw1!pass";

    @Autowired
    private WithdrawalService service;
    @Autowired
    private WithdrawalAutoCompleteJob job;
    @Autowired
    private PointService points;
    @Autowired
    private PointLedgerWriter ledger;
    @Autowired
    private MemberRestrictionService restrictions;
    @Autowired
    private SignupService signup;
    @Autowired
    private SocialAccountWriter social;
    @Autowired
    private IdentityWithdrawalRecorder recorder;
    @Autowired
    private BoardActorResolver actors;
    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private GroupBuyMemberStore groupBuyMembers;
    @Autowired
    private MeService me;
    @Autowired
    private UserProfileService profiles;
    @Autowired
    private PasswordEncoder encoder;

    // ── 재료 ───────────────────────────────────────────────────────────────

    /** 비밀번호·인증 메일·프로필이 있는 정상 회원(방금 로그인) */
    private long memberWithPassword() {
        long id = newMember();
        // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET password_hash = ?, email_verified_at = now(), last_login_at = ? WHERE id = ?", encoder.encode(PASSWORD), ts(clock.instant()), id);
        memberFixture.profile(id, nick(id));
        return id;
    }

    /** 소셜 전용 회원(비밀번호 없음) — 마지막 로그인 {@code lastLogin} */
    private long socialOnlyMember(Instant lastLogin) {
        long id = newMember();
        // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET email_verified_at = now(), last_login_at = ? WHERE id = ?", ts(lastLogin), id);
        memberFixture.profile(id, nick(id));
        jdbc.update("INSERT INTO user_identities (id, user_id, provider, provider_user_id, email, created_at) VALUES (?, ?, 'KAKAO', ?, NULL, now())", ids.nextId(), id,
                "sub-" + id);
        return id;
    }

    private static String nick(long id) {
        return "w" + Long.toString(id % 100_000_000_000L, 36);
    }

    private String emailOf(long userId) {
        return memberFixture.email(userId);
    }

    private WithdrawalRequest request(String paidChoice, String deliveredChoice, String bizChoice, List<WithdrawalRequest.GroupChoice> groups,
                                      WithdrawalRequest.ForfeitConsent consent) {
        return new WithdrawalRequest(PASSWORD, "NOT_USING", "  더 쓰지 않아요  ", paidChoice, deliveredChoice, bizChoice, groups, consent);
    }

    private WithdrawalRequest plain() {
        return request(null, null, null, null, null);
    }

    /** 유상 충전을 흉내 낸다(3단계 충전 기능이 아직 없다 — shared PointTestKit.chargePaid 와 같은 순서). 유상 묶음 ID */
    private long chargePaid(long userId, long amount) {
        return tx.execute(status -> {
            Instant now = clock.instant();
            PointLedgerWriter.Balance balance = ledger.lockBalance(userId, now);
            long line = ids.nextId();
            ledger.insertEntry(line, userId, PointKind.EARN, PointReason.CHARGE.name(), (int) amount, 0, amount, balance.free() + balance.paid() + amount, null, null, null, null,
                    now);
            long lot = ids.nextId();
            ledger.insertLot(new PointLedgerWriter.NewLot(lot, userId, PointLotKind.PAID, amount, null, PointReason.CHARGE.name(), null, null, line, null, now));
            ledger.insertEntryLot(line, lot, amount);
            ledger.saveBalance(userId, balance.free(), balance.paid() + amount, balance.debt(), now);
            return lot;
        });
    }

    /** 송금 대기 환불(남은 일 — 17-1 「송금 대기 환불」) */
    private long pendingBankTransfer(long userId) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO commerce_bank_transfers (id, source_kind, source_id, user_id, amount, status, reason, created_at) VALUES (?, 'GROUP_BUY_REFUND', ?, ?, 10000, "
                + "'AWAITING_ACCOUNT', 'test', now())", id, id, userId);
        return id;
    }

    private void finishBankTransfer(long id) {
        jdbc.update("UPDATE commerce_bank_transfers SET status = 'SENT', sent_at = now(), account_erased_at = now() WHERE id = ?", id);
    }

    private void paidOrder(long userId, Instant paidAt) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, paid_amount, paid_at, confirmed_via, expires_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'PAYMENT_TEST', ?, 'test', 1000, 'PAID', 1000, ?, 'COMPLETE_API', now(), now(), now())", id, "pay_" + String.format("%032d", id), userId, id,
                ts(paidAt));
    }

    private WithdrawalAutoCompleteJob.Outcome process(long withdrawalId) {
        return tx.execute(s -> job.process(withdrawalId));
    }

    private String status(long userId) {
        return memberFixture.status(userId);
    }

    private Map<String, Object> withdrawalRow(long userId) {
        return jdbc.queryForMap("SELECT * FROM member_withdrawals WHERE user_id = ? ORDER BY id DESC LIMIT 1", userId);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    // ── 시나리오 ─────────────────────────────────────────────────────────────

    @Nested
    class 즉시_완료 {

        @Test
        void P30e_결제_이력_없는_회원은_즉시_완료되고_무상_원장은_남고_잔액은_0_연락처는_저장하지_않는다() {
            long member = memberWithPassword();
            String email = emailOf(member);
            long operator = newOperator();
            tx.executeWithoutResult(s -> points.adjust(member, 300, "시험 적립", operator));
            Instant now = clock.instant();

            WithdrawalResult result = service.request(member, plain(), IP);

            assertThat(result.state()).isEqualTo("COMPLETED");
            assertThat(result.hasPaymentHistory()).isFalse();
            assertThat(result.retentionYears()).isEqualTo(5);
            assertThat(result.rejoinAvailableAt()).isEqualTo(now.plus(Duration.ofDays(30)));
            Map<String, Object> user = jdbc.queryForMap("SELECT status, email, password_hash, deleted_at, last_login_at FROM users WHERE id = ?", member);
            assertThat(user.get("status")).isEqualTo("WITHDRAWN");
            assertThat(user.get("email")).isNull();
            assertThat(user.get("password_hash")).isNull();
            assertThat(user.get("deleted_at")).isNotNull();
            assertThat(user.get("last_login_at")).isNull();
            assertThat(memberFixture.nickname(member)).isNull();
            // 무상 원장은 남고(적립 + 포기), 잔액은 0, 불변식 성립(D4)
            assertThat(count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isEqualTo(2);
            assertThat(count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'WITHDRAW_FORFEIT' AND free_delta = -300", member)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT balance FROM point_balances WHERE user_id = ?", Long.class, member)).isZero();
            assertThat(PointInvariants.violations(jdbc, member)).isEmpty();
            // 연락용 이메일은 저장하지 않고, 재가입 제한 해시(30일)는 남는다
            assertThat(count("SELECT count(*) FROM withdrawn_member_contacts WHERE user_id = ?", member)).isZero();
            assertThat(recorder.blockedUntil(WithdrawalKind.EMAIL, email, now)).contains(now.plus(Duration.ofDays(30)));
            assertThat(withdrawalRow(member).get("status")).isEqualTo("COMPLETED");
            assertThat(withdrawalRow(member).get("reason_note")).isEqualTo("더 쓰지 않아요");
            assertThat(auditCount("MEMBER", Long.toString(member), AuditActions.MEMBER_WITHDRAW_REQUEST)).isEqualTo(1);
            assertThat(auditCount("MEMBER", Long.toString(member), AuditActions.MEMBER_WITHDRAW)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM spring_session WHERE principal_name = ?", Long.toString(member))).isZero();
        }

        @Test
        void 탈퇴가_완료되면_그_회원의_방문_통계_주_월_순방문자_표시를_지우고_다른_회원_표시와_숫자_표는_남긴다() {
            // site-analytics-contract 「3단계」 결정 ④ — 회원 표시는 탈퇴 파기 대상(WithdrawalCompletionService → AnalyticsMemberMarks), 숫자 표는 사람을 가리키지 않아 남긴다.
            // 분석 표는 이 앱이 같은 DB 에 이관한다(roboyam.schema.analytics). 기간은 다른 시험과 겹치지 않는 먼 미래(2089년)
            long member = memberWithPassword();
            long other = newMember();
            LocalDate week = VisitPeriods.weekStart(LocalDate.of(2089, 4, 12));
            jdbc.queryForObject("SELECT analytics_ensure_period_mark_partitions(ARRAY['2089-04-12'::date, '2089-05-03'::date])", Integer.class);
            for (long id : List.of(member, other)) {
                jdbc.update("INSERT INTO analytics_member_week_marks (period_start, member_id) VALUES (?, ?)", java.sql.Date.valueOf(week), id);
                jdbc.update("INSERT INTO analytics_member_month_marks (period_start, member_id) VALUES ('2089-04-01', ?), ('2089-05-01', ?)", id, id);
            }
            jdbc.update("INSERT INTO analytics_unique_counts (audience, period_kind, period_start, day, added) VALUES ('MEMBER', 'MONTH', '2089-04-01', '2089-04-12', 2) "
                    + "ON CONFLICT (audience, period_kind, period_start, day) DO UPDATE SET added = EXCLUDED.added");
            try {
                assertThat(service.request(member, plain(), IP).state()).isEqualTo("COMPLETED");

                assertThat(count("SELECT count(*) FROM analytics_member_week_marks WHERE member_id = ?", member)).isZero();
                assertThat(count("SELECT count(*) FROM analytics_member_month_marks WHERE member_id = ?", member)).isZero();
                assertThat(count("SELECT count(*) FROM analytics_member_week_marks WHERE member_id = ?", other)).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM analytics_member_month_marks WHERE member_id = ?", other)).isEqualTo(2);
                assertThat(count("SELECT added FROM analytics_unique_counts WHERE audience = 'MEMBER' AND period_kind = 'MONTH' AND period_start = '2089-04-01' "
                        + "AND day = '2089-04-12'")).as("숫자 표는 남는다").isEqualTo(2);
            } finally {
                jdbc.update("DELETE FROM analytics_member_week_marks WHERE member_id = ANY (?::bigint[])", "{" + member + "," + other + "}");
                jdbc.update("DELETE FROM analytics_member_month_marks WHERE member_id = ANY (?::bigint[])", "{" + member + "," + other + "}");
                jdbc.update("DELETE FROM analytics_unique_counts WHERE period_start = '2089-04-01' AND day = '2089-04-12'");
            }
        }

        @Test
        void P28_충전_포인트를_포기하고_탈퇴하면_동의_금액_시각이_남고_유상_무상_모두_WITHDRAW_FORFEIT() {
            long member = memberWithPassword();
            tx.executeWithoutResult(s -> points.adjust(member, 100, "시험 적립", newOperator()));
            chargePaid(member, 1000);

            WithdrawalView view = service.view(member);
            assertThat(view.money()).isNotNull();
            assertThat(view.money().paidPoints()).isEqualTo(1000);
            assertThat(view.money().fee()).isZero(); // 충전 7일 안 — 수수료 없음
            assertThat(view.money().refundEstimate()).isEqualTo(1000);

            WithdrawalResult result = service.request(member, request("FORFEIT", null, null, null, new WithdrawalRequest.ForfeitConsent(1000L, 1000L, null)), IP);

            assertThat(result.state()).isEqualTo("COMPLETED");
            Map<String, Object> row = withdrawalRow(member);
            assertThat(((Number) row.get("forfeit_paid_points")).longValue()).isEqualTo(1000);
            assertThat(((Number) row.get("forfeit_refund_estimate")).longValue()).isEqualTo(1000);
            assertThat(row.get("forfeit_consented_at")).isNotNull();
            assertThat(count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'WITHDRAW_FORFEIT' AND paid_delta = -1000", member)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'WITHDRAW_FORFEIT' AND free_delta = -100", member)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT paid_balance + free_balance FROM point_balances WHERE user_id = ?", Long.class, member)).isZero();
            assertThat(PointInvariants.violations(jdbc, member)).isEmpty();
            assertThat(status(member)).isEqualTo("WITHDRAWN");
            Map<String, Object> audit = lastAudit("MEMBER", Long.toString(member), AuditActions.MEMBER_WITHDRAW);
            assertThat(audit).isNotNull();
            assertThat(String.valueOf(audit.get("detail"))).contains("\"forfeitPaidPoints\":1000").contains("\"forfeitedPaid\":1000");
        }

        @Test
        void 포기_동의_금액이_서버_계산과_다르면_400_forfeitConsent() {
            long member = memberWithPassword();
            chargePaid(member, 1000);

            ValidationException e = catchThrowableOfType(ValidationException.class,
                    () -> service.request(member, request("FORFEIT", null, null, null, new WithdrawalRequest.ForfeitConsent(900L, 900L, null)), IP));

            assertThat(e).isNotNull();
            assertThat(e.getFields()).containsKey("forfeitConsent");
            assertThat(status(member)).isEqualTo("ACTIVE");
        }

        @Test
        void P30d_결제_이력이_있으면_연락용_이메일을_분리_보관하고_파기일은_마지막_거래_더하기_5년() {
            long member = memberWithPassword();
            String email = emailOf(member);
            paidOrder(member, Instant.parse("2026-03-01T05:00:00Z"));

            WithdrawalView view = service.view(member);
            assertThat(view.remains().hasPaymentHistory()).isTrue();
            WithdrawalResult result = service.request(member, request(null, "NOW", null, null, null), IP);

            assertThat(result.state()).isEqualTo("COMPLETED");
            assertThat(result.hasPaymentHistory()).isTrue();
            Map<String, Object> contact = jdbc.queryForMap("SELECT email_cipher, purge_after FROM withdrawn_member_contacts WHERE user_id = ?", member);
            assertThat(String.valueOf(contact.get("email_cipher"))).startsWith("d").doesNotContain(email);
            assertThat(((Timestamp) contact.get("purge_after")).toInstant()).isEqualTo(Instant.parse("2031-03-01T05:00:00Z"));
            assertThat(status(member)).isEqualTo("WITHDRAWN");
        }

        @Test
        void P30_기능_제한_중_탈퇴는_되고_재가입_제한은_제한_끝까지_무기한은_상한() {
            long member = memberWithPassword();
            String email = emailOf(member);
            Instant now = clock.instant();
            restrictions.grant(member, new RestrictionGrant(RestrictionType.POINT_FREEZE, now.plus(Duration.ofDays(60)), "결제하지 않은 선점이 반복됨", null), newOperator(), IP);

            WithdrawalView view = service.view(member);
            assertThat(view.rejoin().extendedByRestriction()).isTrue();
            assertThat(view.rejoin().availableAt()).isEqualTo(now.plus(Duration.ofDays(60)));
            assertThat(view.rejoin().restrictionReason()).isEqualTo("결제하지 않은 선점이 반복됨");

            WithdrawalResult result = service.request(member, plain(), IP);
            assertThat(result.state()).isEqualTo("COMPLETED");
            assertThat(result.rejoinAvailableAt()).isEqualTo(now.plus(Duration.ofDays(60)));
            assertThat(recorder.blockedUntil(WithdrawalKind.EMAIL, email, now)).contains(now.plus(Duration.ofDays(60)));

            long banned = memberWithPassword();
            restrictions.grant(banned, new RestrictionGrant(RestrictionType.GROUP_BUY_BAN, null, "무기한 제한 시험", null), newOperator(), IP);
            assertThat(service.view(banned).rejoin().availableAt()).isEqualTo(now.plus(Duration.ofDays(365)));
            assertThat(service.request(banned, plain(), IP).rejoinAvailableAt()).isEqualTo(now.plus(Duration.ofDays(365)));
        }
    }

    @Nested
    class 재가입_제한 {

        @Test
        void P29_탈퇴_뒤_같은_이메일로_가입하면_REJOIN_BLOCKED_소셜_계정도_같다() {
            long member = socialOnlyMember(clock.instant());
            String email = emailOf(member);
            assertThat(service.request(member, new WithdrawalRequest(null, null, null, null, null, null, null, null), IP).state()).isEqualTo("COMPLETED");

            AuthRejoinBlockedException byEmail = catchThrowableOfType(AuthRejoinBlockedException.class,
                    () -> signup.signup(new SignupRequest(email, "Rejoin1!pass", nick(ids.nextId()), true, true, true)));
            assertThat(byEmail).isNotNull();
            assertThat(byEmail.getErrorCode()).isEqualTo("REJOIN_BLOCKED");
            assertThat(byEmail.getRetryAfterSeconds()).isBetween(Duration.ofDays(29).toSeconds(), Duration.ofDays(30).toSeconds());
            assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isZero();

            PendingSignup pending = new PendingSignup(SocialProvider.KAKAO, "sub-" + member, null, false, "x", null, clock.instant().plus(Duration.ofMinutes(10)));
            assertThatThrownBy(() -> social.createMember(pending, nick(ids.nextId()))).isInstanceOf(AuthRejoinBlockedException.class);
        }
    }

    @Nested
    class 탈퇴_예약 {

        @Test
        void P30b_남은_일이_있으면_진행_중이_되고_공개_노출과_새_활동이_막히며_끝나면_배치가_완료한다() {
            long member = memberWithPassword();
            long transfer = pendingBankTransfer(member);

            WithdrawalResult result = service.request(member, plain(), IP);

            assertThat(result.state()).isEqualTo("SCHEDULED");
            assertThat(result.pending()).extracting(WithdrawalView.PendingItem::code).containsExactly("PAYMENT_CANCEL");
            assertThat(status(member)).isEqualTo("WITHDRAWING");
            assertThat(me.of(member)).get().extracting(r -> r.withdrawalScheduled()).isEqualTo(true);
            assertThat(actors.resolve(member, catalog.snapshot()).restricted()).isTrue(); // 글·댓글·쪽지·소모임·핫딜 쓰기 차단
            assertThat(groupBuyMembers.state(member)).get().extracting(s -> s.suspended()).isEqualTo(true); // 공구 참여·포인트샵 구매 차단
            assertThatThrownBy(() -> profiles.profile(Long.toString(member), null)).isInstanceOf(ProfileNotFoundException.class); // 공개 프로필에서 빠짐
            assertThat(memberFixture.nickname(member)).isEqualTo(nick(member)); // 완료 전 닉네임 그대로
            assertThatThrownBy(() -> service.request(member, plain(), IP)).isInstanceOf(WithdrawalAlreadyScheduledException.class);
            WithdrawalView view = service.view(member);
            assertThat(view.state()).isEqualTo("SCHEDULED");
            assertThat(view.scheduled()).isNotNull();
            assertThat(view.scheduled().pending()).hasSize(1);

            long withdrawalId = ((Number) withdrawalRow(member).get("id")).longValue();
            assertThat(process(withdrawalId)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.WAITING);
            assertThat(status(member)).isEqualTo("WITHDRAWING");

            finishBankTransfer(transfer);
            assertThat(process(withdrawalId)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.COMPLETED);
            assertThat(status(member)).isEqualTo("WITHDRAWN");
            assertThat(memberFixture.nickname(member)).isNull();
            assertThat(withdrawalRow(member).get("status")).isEqualTo("COMPLETED");
            Map<String, Object> audit = lastAudit("MEMBER", Long.toString(member), AuditActions.MEMBER_WITHDRAW);
            assertThat(audit.get("actor_type")).isEqualTo("SYSTEM");
            assertThat(process(withdrawalId)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.GONE);
        }

        @Test
        void P30c_진행_중_취소하면_정상_회원으로_돌아가고_데이터는_그대로() {
            long member = memberWithPassword();
            tx.executeWithoutResult(s -> points.adjust(member, 50, "시험 적립", newOperator()));
            pendingBankTransfer(member);
            assertThat(service.request(member, plain(), IP).state()).isEqualTo("SCHEDULED");

            service.cancel(member, IP);

            assertThat(status(member)).isEqualTo("ACTIVE");
            assertThat(withdrawalRow(member).get("status")).isEqualTo("CANCELED");
            assertThat(memberFixture.nickname(member)).isEqualTo(nick(member));
            assertThat(jdbc.queryForObject("SELECT balance FROM point_balances WHERE user_id = ?", Long.class, member)).isEqualTo(50);
            assertThat(actors.resolve(member, catalog.snapshot()).restricted()).isFalse();
            assertThat(auditCount("MEMBER", Long.toString(member), AuditActions.MEMBER_WITHDRAW_CANCEL)).isEqualTo(1);
            assertThatThrownBy(() -> service.cancel(member, IP)).isInstanceOf(WithdrawalNotScheduledException.class);
            assertThat(service.view(member).state()).isEqualTo("NONE");
        }

        @Test
        void 신청_10일_뒤에도_남아_있으면_진행_상태_메일을_한_번만_표시한다() {
            long member = memberWithPassword();
            pendingBankTransfer(member);
            assertThat(service.request(member, plain(), IP).state()).isEqualTo("SCHEDULED");
            long withdrawalId = ((Number) withdrawalRow(member).get("id")).longValue();
            // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
            jdbc.update("UPDATE member_withdrawals SET requested_at = ? WHERE id = ?", ts(clock.instant().minus(Duration.ofDays(11))), withdrawalId);

            assertThat(process(withdrawalId)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.NOTIFIED);
            assertThat(withdrawalRow(member).get("progress_notified_at")).isNotNull();
            assertThat(process(withdrawalId)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.WAITING);
        }
    }

    @Nested
    class 본인_확인 {

        @Test
        void 비밀번호가_틀리면_CURRENT_PASSWORD_WRONG_이고_상태는_그대로() {
            long member = memberWithPassword();
            WithdrawalRequest wrong = new WithdrawalRequest("nope", null, null, null, null, null, null, null);
            assertThatThrownBy(() -> service.request(member, wrong, IP)).isInstanceOf(AccountCurrentPasswordWrongException.class);
            assertThat(status(member)).isEqualTo("ACTIVE");
            WithdrawalRequest missing = new WithdrawalRequest(null, null, null, null, null, null, null, null);
            ValidationException e = catchThrowableOfType(ValidationException.class, () -> service.request(member, missing, IP));
            assertThat(e.getFields()).containsKey("password");
        }

        @Test
        void 소셜_전용_회원은_최근_10분_안_로그인이_아니면_REAUTH_REQUIRED() {
            long stale = socialOnlyMember(clock.instant().minus(Duration.ofMinutes(20)));
            assertThat(service.view(stale).verification().method()).isEqualTo("RECENT_LOGIN");
            assertThat(service.view(stale).verification().recentLoginOk()).isFalse();
            WithdrawalRequest body = new WithdrawalRequest(null, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> service.request(stale, body, IP)).isInstanceOf(WithdrawalReauthRequiredException.class);

            long fresh = socialOnlyMember(clock.instant().minus(Duration.ofMinutes(5)));
            assertThat(service.request(fresh, body, IP).state()).isEqualTo("COMPLETED");
            assertThat(count("SELECT count(*) FROM user_identities WHERE user_id = ?", fresh)).isZero();
            assertThat(recorder.blockedUntil(WithdrawalKind.SOCIAL, IdentityWithdrawalRecorder.normalizeSocial("KAKAO", "sub-" + fresh), clock.instant())).isPresent();
        }
    }

    @Nested
    class 모임장 {

        private long board() {
            long id = ids.nextId();
            boardFixture.board(id, "wd" + Long.toString(id, 36), "탈퇴 시험 " + (id % 1000), 990);
            return id;
        }

        private long group(long boardId, long leader, long... members) {
            long id = ids.nextId();
            jdbc.update("INSERT INTO groups (id, name, name_key, description, board_id, join_type, capacity, status, leader_id, creator_id, member_count, requested_at, opened_at) "
                    + "VALUES (?, ?, ?, '시험', ?, 'OPEN', 50, 'ACTIVE', ?, ?, ?, now(), now())", id, "g" + Long.toString(id, 36), "g" + Long.toString(id, 36), boardId, leader, leader,
                    1 + members.length);
            jdbc.update("INSERT INTO group_members (group_id, user_id, role, status, joined_at) VALUES (?, ?, 'LEADER', 'ACTIVE', now())", id, leader);
            int i = 0;
            for (long m : members) {
                jdbc.update("INSERT INTO group_members (group_id, user_id, role, status, joined_at) VALUES (?, ?, 'MEMBER', 'ACTIVE', now() + ?::interval)", id, m, (i++) + " seconds");
            }
            return id;
        }

        private void manager(long groupId, long userId) {
            jdbc.update("UPDATE group_members SET role = 'MANAGER', manager_perms = 1, role_changed_at = now() WHERE group_id = ? AND user_id = ?", groupId, userId);
        }

        @Test
        void AUTO는_매니저_다음_가장_오래된_멤버에게_넘기고_멤버가_없으면_해산_TRANSFER_DISSOLVE_는_고른_대로() {
            long leader = memberWithPassword();
            long m1 = memberWithPassword();
            long m2 = memberWithPassword();
            long m3 = memberWithPassword();
            long m4 = memberWithPassword();
            long boardId = board();
            long auto = group(boardId, leader, m1, m2);
            manager(auto, m2);
            long transfer = group(boardId, leader, m3);
            long empty = group(boardId, leader);
            long dissolve = group(boardId, leader, m4);

            WithdrawalView view = service.view(leader);
            assertThat(view.groupsLed()).hasSize(4);
            WithdrawalView.GroupLed autoLed = view.groupsLed().stream().filter(g -> g.id().equals(Long.toString(auto))).findFirst().orElseThrow();
            assertThat(autoLed.autoDelegate().id()).isEqualTo(Long.toString(m2)); // 매니저 먼저
            assertThat(autoLed.candidates()).extracting(WithdrawalView.GroupCandidate::role).containsExactly("MANAGER", "MEMBER");
            WithdrawalView.GroupLed emptyLed = view.groupsLed().stream().filter(g -> g.id().equals(Long.toString(empty))).findFirst().orElseThrow();
            assertThat(emptyLed.autoDelegate()).isNull();

            // 후보가 아닌 사람에게 넘기기 → 400 groupChoices
            ValidationException bad = catchThrowableOfType(ValidationException.class, () -> service.request(leader, request(null, null, null,
                    List.of(new WithdrawalRequest.GroupChoice(Long.toString(transfer), "TRANSFER", Long.toString(m1))), null), IP));
            assertThat(bad.getFields()).containsKey("groupChoices");

            WithdrawalResult result = service.request(leader, request(null, null, null, List.of(
                    new WithdrawalRequest.GroupChoice(Long.toString(transfer), "TRANSFER", Long.toString(m3)),
                    new WithdrawalRequest.GroupChoice(Long.toString(dissolve), "DISSOLVE", null)), null), IP);

            assertThat(result.state()).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForMap("SELECT status, leader_id FROM groups WHERE id = ?", auto)).containsEntry("status", "ACTIVE").containsEntry("leader_id", m2);
            assertThat(jdbc.queryForObject("SELECT role FROM group_members WHERE group_id = ? AND user_id = ?", String.class, auto, m2)).isEqualTo("LEADER");
            assertThat(jdbc.queryForMap("SELECT status, leader_id FROM groups WHERE id = ?", transfer)).containsEntry("status", "ACTIVE").containsEntry("leader_id", m3);
            assertThat(jdbc.queryForMap("SELECT status, dissolve_penalty_until FROM groups WHERE id = ?", empty)).containsEntry("status", "DELETED")
                    .containsEntry("dissolve_penalty_until", null); // 탈퇴로 인한 해산 — 패널티 없음
            assertThat(jdbc.queryForObject("SELECT status FROM groups WHERE id = ?", String.class, dissolve)).isEqualTo("DELETED");
            // 탈퇴한 모임장의 멤버십 줄은 모두 사라지고 멤버 수가 맞다
            assertThat(count("SELECT count(*) FROM group_members WHERE user_id = ?", leader)).isZero();
            assertThat(jdbc.queryForObject("SELECT member_count FROM groups WHERE id = ?", Integer.class, auto)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT member_count FROM groups WHERE id = ?", Integer.class, transfer)).isEqualTo(1);
        }

        @Test
        void 진행_중에는_모임이_그대로이고_취소하면_모임장을_유지한다() {
            long leader = memberWithPassword();
            long m1 = memberWithPassword();
            long boardId = board();
            long transfer = group(boardId, leader, m1);
            long dissolve = group(boardId, leader);
            pendingBankTransfer(leader);

            WithdrawalResult result = service.request(leader, request(null, null, null, List.of(
                    new WithdrawalRequest.GroupChoice(Long.toString(transfer), "TRANSFER", Long.toString(m1)),
                    new WithdrawalRequest.GroupChoice(Long.toString(dissolve), "DISSOLVE", null)), null), IP);

            assertThat(result.state()).isEqualTo("SCHEDULED");
            // 신청만으로는 모임장·소모임이 바뀌지 않는다(선택은 저장만)
            assertThat(jdbc.queryForMap("SELECT status, leader_id FROM groups WHERE id = ?", transfer)).containsEntry("status", "ACTIVE").containsEntry("leader_id", leader);
            assertThat(jdbc.queryForMap("SELECT status, leader_id FROM groups WHERE id = ?", dissolve)).containsEntry("status", "ACTIVE").containsEntry("leader_id", leader);
            assertThat(jdbc.queryForObject("SELECT role FROM group_members WHERE group_id = ? AND user_id = ?", String.class, transfer, leader)).isEqualTo("LEADER");
            assertThat(String.valueOf(withdrawalRow(leader).get("group_choices"))).contains("TRANSFER").contains("DISSOLVE");

            service.cancel(leader, IP);

            assertThat(status(leader)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT leader_id FROM groups WHERE id = ?", Long.class, transfer)).isEqualTo(leader);
            assertThat(jdbc.queryForObject("SELECT status FROM groups WHERE id = ?", String.class, dissolve)).isEqualTo("ACTIVE");
            assertThat(count("SELECT count(*) FROM group_members WHERE user_id = ? AND status = 'ACTIVE'", leader)).isEqualTo(2);
        }

        @Test
        void 완료_때_TRANSFER_대상이_멤버가_아니면_AUTO_로_떨어지고_신청_뒤_새로_맡은_모임도_AUTO() {
            long leader = memberWithPassword();
            long m1 = memberWithPassword();
            long m2 = memberWithPassword();
            long other = memberWithPassword();
            long boardId = board();
            long transfer = group(boardId, leader, m1, m2);
            long later = group(boardId, other, leader); // 신청 뒤에 넘겨받을 모임
            long bank = pendingBankTransfer(leader);
            assertThat(service.request(leader, request(null, null, null,
                    List.of(new WithdrawalRequest.GroupChoice(Long.toString(transfer), "TRANSFER", Long.toString(m1))), null), IP).state()).isEqualTo("SCHEDULED");

            // 그 사이 m1 이 나가고, other 가 leader 에게 모임장을 넘겼다
            jdbc.update("UPDATE group_members SET status = 'LEFT', left_at = now() WHERE group_id = ? AND user_id = ?", transfer, m1);
            jdbc.update("UPDATE groups SET member_count = member_count - 1 WHERE id = ?", transfer);
            jdbc.update("UPDATE groups SET leader_id = ? WHERE id = ?", leader, later);
            jdbc.update("UPDATE group_members SET role = 'LEADER' WHERE group_id = ? AND user_id = ?", later, leader);
            jdbc.update("UPDATE group_members SET role = 'MEMBER' WHERE group_id = ? AND user_id = ?", later, other);

            finishBankTransfer(bank);
            long withdrawalId = ((Number) withdrawalRow(leader).get("id")).longValue();
            assertThat(process(withdrawalId)).isEqualTo(WithdrawalAutoCompleteJob.Outcome.COMPLETED);

            assertThat(status(leader)).isEqualTo("WITHDRAWN");
            assertThat(jdbc.queryForMap("SELECT status, leader_id FROM groups WHERE id = ?", transfer)).containsEntry("status", "ACTIVE").containsEntry("leader_id", m2); // AUTO
            assertThat(jdbc.queryForMap("SELECT status, leader_id FROM groups WHERE id = ?", later)).containsEntry("status", "ACTIVE").containsEntry("leader_id", other); // AUTO
            assertThat(count("SELECT count(*) FROM group_members WHERE user_id = ?", leader)).isZero();
        }
    }
}
