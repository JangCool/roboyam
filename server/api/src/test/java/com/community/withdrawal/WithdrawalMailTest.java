package com.community.withdrawal;

import static com.community.withdrawal.WithdrawalTestSupport.plain;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.mail.service.MailDispatcher;
import com.community.restriction.dto.request.RestrictionGrant;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.service.MemberRestrictionService;
import com.community.withdrawal.batch.WithdrawalAutoCompleteJob;
import com.community.withdrawal.service.WithdrawalService;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/**
 * 탈퇴 메일(member-withdrawal-contract 「메일」) — 메일 서버만 가짜({@link FakeDispatcher}, {@code RequiredMailOutboxSenderTest} 와 같은 방식)로 두고 실제 문구 엔진이 만든 제목·본문을 본다.
 * 완료 메일은 익명화 전 <b>인증한</b> 주소로(재가입 가능일·고객센터·결제 이력이 있을 때만 연락용 이메일 보관 안내, 회원 언어), 진행 안내 메일은 남은 일 목록·예상 완료일. 미인증 주소면 안 간다.
 */
@Import(WithdrawalMailTest.FakeMailConfig.class)
@TestPropertySource(properties = "irobit.withdrawal.support-email=help@test.local")
class WithdrawalMailTest extends CommerceApiTestBase {

    private static final String IP = "127.0.0.1";

    /** 보낸 메일 한 통 */
    record Sent(String to, String subject, String html) {
    }

    /** 보낸 메일을 모으는 가짜 메일 서버(발송 스레드가 넣으므로 동기화) */
    static final class FakeDispatcher extends MailDispatcher {
        private final List<Sent> sent = new ArrayList<>();

        FakeDispatcher() {
            super(null, null, null, null);
        }

        @Override
        public synchronized boolean sendTagged(String to, String taggedSubject, String untaggedSubject, String html) {
            sent.add(new Sent(to, untaggedSubject, html));
            return true;
        }

        synchronized List<Sent> to(String address) {
            return sent.stream().filter(s -> s.to().equals(address)).toList();
        }
    }

    @TestConfiguration
    static class FakeMailConfig {

        @Bean
        @Primary
        FakeDispatcher fakeMailDispatcher() {
            return new FakeDispatcher();
        }
    }

    @Autowired
    private FakeDispatcher mails;
    @Autowired
    private WithdrawalService service;
    @Autowired
    private WithdrawalAutoCompleteJob job;
    @Autowired
    private MemberRestrictionService restrictions;
    @Autowired
    private PasswordEncoder encoder;


    private long member(String language) {
        long id = newMember();
        WithdrawalTestSupport.withPassword(jdbc, encoder, id, clock.instant());
        if (language != null) {
            jdbc.update("INSERT INTO member_display_preferences (user_id, language, zone_id, time_format, date_format) VALUES (?, ?, 'Asia/Seoul', 'H24', 'YMD_DASH')", id,
                    language);
        }
        return id;
    }

    private String emailOf(long userId) {
        return memberFixture.email(userId);
    }

    /** 발송 스레드가 보낼 때까지(최대 5초) 기다린다 */
    private Optional<Sent> awaitMail(String to) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<Sent> found = mails.to(to);
            if (!found.isEmpty()) {
                return Optional.of(found.get(found.size() - 1));
            }
            Thread.sleep(100);
        }
        return Optional.empty();
    }

    private void paidOrder(long userId) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO payment_orders (id, payment_id, user_id, order_type, order_ref_id, order_name, amount, status, paid_amount, paid_at, confirmed_via, expires_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'PAYMENT_TEST', ?, 'test', 1000, 'PAID', 1000, now(), 'COMPLETE_API', now(), now(), now())", id,
                "pay_" + String.format("%032d", id), userId, id);
    }

    @Test
    void 완료_메일은_익명화_전_인증_주소로_가고_재가입_가능일_고객센터_보관_기간을_담는다_결제_이력이_없으면_연락처_보관_안내는_없다() throws Exception {
        long member = member(null);
        String email = emailOf(member);
        Instant now = clock.instant();
        restrictions.grant(member, new RestrictionGrant(RestrictionType.POINT_FREEZE, now.plus(Duration.ofDays(60)), "결제하지 않은 선점이 반복됨", null), newOperator(), IP);

        assertThat(service.request(member, plain(), IP).state()).isEqualTo("COMPLETED");
        assertThat(memberFixture.email(member)).isNull(); // 익명화됐지만 메일은 그 전 주소로

        Sent mail = awaitMail(email).orElseThrow();
        assertThat(mail.subject()).contains("회원 탈퇴가 완료됐어요");
        assertThat(mail.html()).contains(WithdrawalTestSupport.nick(member))
                .contains(ServiceTime.dateOf(now.plus(Duration.ofDays(60))).toString()) // 제재로 늘어난 재가입 가능일
                .contains("help@test.local").contains("5년").doesNotContain("분리 보관").doesNotContain("{{");
    }

    @Test
    void 결제_이력이_있으면_연락용_이메일_분리_보관_안내가_들어가고_영어_설정_회원은_영어로_받는다() throws Exception {
        long member = member("en");
        String email = emailOf(member);
        paidOrder(member);

        assertThat(service.request(member, plain(), IP).hasPaymentHistory()).isTrue();

        Sent mail = awaitMail(email).orElseThrow();
        assertThat(mail.subject()).contains("Your account has been closed");
        assertThat(mail.html()).contains("one contact email is kept encrypted and separately").contains("5 years").contains("help@test.local")
                .contains(ServiceTime.dateOf(clock.instant().plus(Duration.ofDays(30))).toString()).doesNotContain("{{");
    }

    @Test
    void 이메일을_인증하지_않은_회원에게는_완료_메일을_보내지_않는다() throws Exception {
        long member = member(null);
        String email = emailOf(member);
        // SQL 로 남김: 회원 상태(정지·차단·탈퇴)·이메일·인증 칸을 그대로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET email_verified_at = NULL WHERE id = ?", member);

        assertThat(service.request(member, plain(), IP).state()).isEqualTo("COMPLETED");

        Thread.sleep(500);
        assertThat(mails.to(email)).isEmpty();
    }

    @Test
    void 신청_10일_뒤_진행_안내_메일은_남은_일_목록과_예상_완료일을_담고_한_번만_간다() throws Exception {
        long member = member(null);
        String email = emailOf(member);
        long transfer = ids.nextId();
        jdbc.update("INSERT INTO commerce_bank_transfers (id, source_kind, source_id, user_id, amount, status, reason, created_at) VALUES (?, 'GROUP_BUY_REFUND', ?, ?, 10000, "
                + "'AWAITING_ACCOUNT', 'test', now())", transfer, transfer, member);
        assertThat(service.request(member, WithdrawalTestSupport.request(null, "WAIT", null, null, null), IP).state()).isEqualTo("SCHEDULED");
        long withdrawalId = jdbc.queryForObject("SELECT id FROM member_withdrawals WHERE user_id = ?", Long.class, member);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE member_withdrawals SET requested_at = ? WHERE id = ?", ts(clock.instant().minus(Duration.ofDays(11))), withdrawalId);

        WithdrawalAutoCompleteJob.Outcome first = tx.execute(s -> job.process(withdrawalId));
        assertThat(first).isEqualTo(WithdrawalAutoCompleteJob.Outcome.NOTIFIED);

        Sent mail = awaitMail(email).orElseThrow();
        assertThat(mail.subject()).contains("회원 탈퇴가 진행 중이에요");
        assertThat(mail.html()).contains("처리 중인 환불").contains("남은 일이 끝나는 대로").contains("help@test.local").doesNotContain("{{");
        WithdrawalAutoCompleteJob.Outcome again = tx.execute(s -> job.process(withdrawalId));
        assertThat(again).isEqualTo(WithdrawalAutoCompleteJob.Outcome.WAITING);
        Thread.sleep(300);
        assertThat(mails.to(email)).hasSize(1);
    }
}
