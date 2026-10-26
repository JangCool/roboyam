package com.community.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.commerce.CommerceApiTestBase;
import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.dto.response.IdentityStartView;
import com.community.identity.dto.response.IdentityStatusView;
import com.community.identity.exception.IdentityAlreadyLinkedException;
import com.community.identity.exception.IdentityAlreadyVerifiedException;
import com.community.identity.exception.IdentityRequiredException;
import com.community.identity.exception.IdentityReverifyBlockedException;
import com.community.identity.exception.IdentityUnderageException;
import com.community.identity.exception.IdentityVerificationExpiredException;
import com.community.identity.exception.IdentityVerificationNotFoundException;
import com.community.identity.repository.master.IdentityStore;
import com.community.identity.service.IdentityVerificationService;
import com.community.withdrawal.service.IdentityWithdrawalRecorder;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 본인인증 규칙(commerce.md 12-1 · 17-2, 계약 「0-4」 데이터·업무 규칙)을 가짜 구현 + 실제 PostgreSQL 로 확인한다 — 발급·확정·1인 1계정·재인증 제한·만료·만 19세.
 */
class IdentityVerificationFlowTest extends CommerceApiTestBase {

    private static final String ADULT_BIRTH = "1990-05-14";

    @Autowired
    private IdentityVerificationService service;
    @Autowired
    private IdentityStore store;
    @Autowired
    private IdentityWithdrawalRecorder recorder;
    @Autowired
    private BlindIndex index;

    private String start(long userId) {
        return service.start(userId).verificationId();
    }

    private IdentityStatusView verify(long userId, String birthDate, String person) {
        return service.confirm(userId, start(userId), new FakeIdentityInput(birthDate, person));
    }

    private String attemptStatus(String id) {
        return store.findAttempt(id).orElseThrow().status();
    }

    private String failureCode(String id) {
        return store.findAttempt(id).orElseThrow().failureCode();
    }

    @Nested
    class 시작 {

        @Test
        void 서버가_인증_번호를_먼저_발급하고_지금_구현을_알린다() {
            long member = newMember();
            IdentityStartView view = service.start(member);

            assertThat(view.provider()).isEqualTo(IdentityProviderKind.FAKE);
            assertThat(view.storeId()).isNull();
            assertThat(view.channelKey()).isNull();
            IdentityStore.Attempt attempt = store.findAttempt(view.verificationId()).orElseThrow();
            assertThat(attempt.userId()).isEqualTo(member);
            assertThat(attempt.status()).isEqualTo("PENDING");
            assertThat(attempt.provider()).isEqualTo(IdentityProviderKind.FAKE);
        }

        @Test
        void 이미_인증한_회원은_다시_시작할_수_없다() {
            long member = newMember();
            verify(member, ADULT_BIRTH, newPerson());

            assertThatThrownBy(() -> service.start(member)).isInstanceOf(IdentityAlreadyVerifiedException.class);
        }
    }

    @Nested
    class 확정 {

        @Test
        void 확정하면_DI_CI_해시만_저장하고_원문은_어디에도_없다() {
            long member = newMember();
            String person = newPerson();
            String id = start(member);

            IdentityStatusView status = service.confirm(member, id, new FakeIdentityInput(ADULT_BIRTH, person));

            assertThat(status.verified()).isTrue();
            assertThat(status.adult()).isTrue();
            assertThat(status.adultFrom()).isEqualTo("2009-05-14");
            assertThat(status.verifiedAt()).isEqualTo(clock.instant());
            Map<String, Object> row = jdbc.queryForMap("SELECT di_hash, ci_hash, provider, provider_tx_id, birth_date::text AS birth FROM identity_verifications WHERE user_id = ?",
                    member);
            assertThat(row.get("di_hash")).isEqualTo(index.hash(IdentityVerificationService.DI, "fake-di:" + person));
            assertThat(row.get("ci_hash")).isEqualTo(index.hash(IdentityVerificationService.CI, "fake-ci:" + person));
            assertThat(row.get("provider")).isEqualTo("FAKE");
            assertThat(row.get("provider_tx_id")).isEqualTo("fake:" + id);
            assertThat(row.get("birth")).isEqualTo(ADULT_BIRTH);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_verifications WHERE di_hash LIKE '%' || ? || '%' OR ci_hash LIKE '%' || ? || '%'", Integer.class,
                    person, person)).isZero();
            assertThat(attemptStatus(id)).isEqualTo("VERIFIED");
            assertThat(service.me(member)).isEqualTo(status);
        }

        @Test
        void 미인증이면_me_는_verified_false_와_나머지_null() {
            assertThat(service.me(newMember())).isEqualTo(new IdentityStatusView(false, null, null, null));
        }

        @Test
        void 남의_인증_번호와_없는_번호는_같은_404() {
            long owner = newMember();
            long other = newMember();
            String id = start(owner);

            assertThatThrownBy(() -> service.confirm(other, id, new FakeIdentityInput(ADULT_BIRTH, newPerson())))
                    .isInstanceOf(IdentityVerificationNotFoundException.class);
            assertThatThrownBy(() -> service.confirm(other, "iv-없는번호", new FakeIdentityInput(ADULT_BIRTH, newPerson())))
                    .isInstanceOf(IdentityVerificationNotFoundException.class);
            assertThat(attemptStatus(id)).isEqualTo("PENDING"); // 남의 요청은 주인의 시도를 건드리지 않는다
        }

        @Test
        void 발급_30분이_지나면_410이고_시도는_실패로_남는다() {
            long member = newMember();
            String id = start(member);
            clock.advance(Duration.ofMinutes(30));

            assertThatThrownBy(() -> service.confirm(member, id, new FakeIdentityInput(ADULT_BIRTH, newPerson())))
                    .isInstanceOf(IdentityVerificationExpiredException.class);
            assertThat(attemptStatus(id)).isEqualTo("FAILED");
            assertThat(failureCode(id)).isEqualTo("IDENTITY_VERIFICATION_EXPIRED");
            assertThat(service.me(member).verified()).isFalse();
        }

        @Test
        void 발급_30분_직전에는_확정된다() {
            long member = newMember();
            String id = start(member);
            clock.advance(Duration.ofMinutes(30).minusSeconds(1));

            assertThat(service.confirm(member, id, new FakeIdentityInput(ADULT_BIRTH, newPerson())).verified()).isTrue();
        }

        @Test
        void 입력이_틀리면_400이고_시도는_그대로라_고쳐서_다시_낼_수_있다() {
            long member = newMember();
            String id = start(member);

            ValidationException error = catchThrowableOfType(ValidationException.class,
                    () -> service.confirm(member, id, new FakeIdentityInput("1990-13-40", " ")));
            assertThat(error.getFields()).containsOnlyKeys("fake.birthDate", "fake.personKey");
            ValidationException future = catchThrowableOfType(ValidationException.class,
                    () -> service.confirm(member, id, new FakeIdentityInput(ServiceTime.today(clock).plusDays(1).toString(), "x".repeat(41))));
            assertThat(future.getFields()).containsOnlyKeys("fake.birthDate", "fake.personKey");
            assertThatThrownBy(() -> service.confirm(member, id, null)).isInstanceOf(ValidationException.class);
            assertThat(attemptStatus(id)).isEqualTo("PENDING");

            assertThat(service.confirm(member, id, new FakeIdentityInput(ADULT_BIRTH, newPerson())).verified()).isTrue();
        }

        @Test
        void 한_번_끝난_인증_번호는_다시_쓸_수_없다() {
            long first = newMember();
            long second = newMember();
            String person = newPerson();
            verify(first, ADULT_BIRTH, person);
            String id = start(second);
            assertThatThrownBy(() -> service.confirm(second, id, new FakeIdentityInput(ADULT_BIRTH, person))).isInstanceOf(IdentityAlreadyLinkedException.class);

            assertThatThrownBy(() -> service.confirm(second, id, new FakeIdentityInput(ADULT_BIRTH, newPerson())))
                    .isInstanceOf(IdentityVerificationExpiredException.class);
        }

        @Test
        void 이미_인증한_회원이_다른_번호로_확정하면_409_이고_그_번호는_실패로_남는다() {
            long member = newMember();
            String first = start(member);
            String second = start(member);
            service.confirm(member, first, new FakeIdentityInput(ADULT_BIRTH, newPerson()));

            assertThatThrownBy(() -> service.confirm(member, second, new FakeIdentityInput(ADULT_BIRTH, newPerson())))
                    .isInstanceOf(IdentityAlreadyVerifiedException.class);
            assertThat(failureCode(second)).isEqualTo("IDENTITY_ALREADY_VERIFIED");
        }
    }

    @Nested
    class 일인_일계정 {

        @Test
        void 같은_사람이_다른_계정에서_인증하면_409_이고_시도는_실패로_남는다() {
            long first = newMember();
            long second = newMember();
            String person = newPerson();
            verify(first, ADULT_BIRTH, person);
            String id = start(second);

            assertThatThrownBy(() -> service.confirm(second, id, new FakeIdentityInput(ADULT_BIRTH, person))).isInstanceOf(IdentityAlreadyLinkedException.class);
            assertThat(failureCode(id)).isEqualTo("IDENTITY_ALREADY_LINKED");
            assertThat(service.me(second).verified()).isFalse();
        }

        @Test
        void DI_만_같아도_CI_만_같아도_다른_계정이면_막는다() {
            String byDi = newPerson();
            String byCi = newPerson();
            // 본인인증 PG 를 바꿔 다른 쪽 값만 받은 경우를 흉내 낸다 — DI 만 있는 줄, CI 만 있는 줄
            jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, ci_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, NULL, ?, 'FAKE', ?, now())",
                    newMember(), index.hash(IdentityVerificationService.DI, "fake-di:" + byDi), LocalDate.parse(ADULT_BIRTH), "tx-" + ids.nextId());
            jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, ci_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, NULL, ?, ?, 'FAKE', ?, now())",
                    newMember(), index.hash(IdentityVerificationService.CI, "fake-ci:" + byCi), LocalDate.parse(ADULT_BIRTH), "tx-" + ids.nextId());

            assertThatThrownBy(() -> verify(newMember(), ADULT_BIRTH, byDi)).isInstanceOf(IdentityAlreadyLinkedException.class);
            assertThatThrownBy(() -> verify(newMember(), ADULT_BIRTH, byCi)).isInstanceOf(IdentityAlreadyLinkedException.class);
        }

        @Test
        void 같은_사람이_두_계정에서_동시에_확정하면_하나만_묶인다() throws Exception {
            String person = newPerson();
            List<Long> members = List.of(newMember(), newMember(), newMember(), newMember());
            List<String> attempts = members.stream().map(IdentityVerificationFlowTest.this::start).toList();
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(members.size());
            try {
                List<Future<String>> results = new ArrayList<>();
                for (int i = 0; i < members.size(); i++) {
                    long member = members.get(i);
                    String id = attempts.get(i);
                    results.add(pool.submit(() -> {
                        go.await();
                        try {
                            service.confirm(member, id, new FakeIdentityInput(ADULT_BIRTH, person));
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
                assertThat(outcomes.stream().filter(o -> !o.equals("OK"))).allMatch("IDENTITY_ALREADY_LINKED"::equals);
            } finally {
                pool.shutdownNow();
            }
            Integer linked = jdbc.queryForObject("SELECT count(*) FROM identity_verifications WHERE di_hash = ?", Integer.class,
                    index.hash(IdentityVerificationService.DI, "fake-di:" + person));
            assertThat(linked).isEqualTo(1);
        }
    }

    @Nested
    class 탈퇴_후_재인증_제한 {

        @Test
        void 탈퇴한_사람은_30일_동안_다시_인증할_수_없고_retryAfter_를_받는다() {
            String person = newPerson();
            long left = newMember();
            verify(left, ADULT_BIRTH, person);
            Instant withdrawnAt = clock.instant();
            assertThat(recorder.recordIdentity(left, withdrawnAt)).isEqualTo(2);
            assertThat(store.findVerification(left)).isEmpty(); // 1인 1계정 묶음이 풀리고 30일 제한으로 바뀐다

            long rejoined = newMember();
            clock.advance(Duration.ofDays(10));
            String id = start(rejoined);
            IdentityReverifyBlockedException blocked = catchThrowableOfType(IdentityReverifyBlockedException.class,
                    () -> service.confirm(rejoined, id, new FakeIdentityInput(ADULT_BIRTH, person)));
            assertThat(blocked.getExtras()).containsEntry("retryAfter", ServiceTime.firstFullDateAfter(withdrawnAt.plus(Duration.ofDays(30))).toString());
            assertThat(failureCode(id)).isEqualTo("IDENTITY_REVERIFY_BLOCKED");

            clock.set(withdrawnAt.plus(Duration.ofDays(30)).plusSeconds(1));
            assertThat(verify(rejoined, ADULT_BIRTH, person).verified()).isTrue();
        }

        @Test
        void DI_나_CI_어느_한쪽_해시만_남아_있어도_막는다() {
            String byCi = newPerson();
            jdbc.update("INSERT INTO identity_withdrawals (id, kind, hash, withdrawn_at, expires_at) VALUES (?, 'IDENTITY_CI', ?, now(), now() + interval '5 days')",
                    ids.nextId(), index.hash(IdentityVerificationService.CI, "fake-ci:" + byCi));

            assertThatThrownBy(() -> verify(newMember(), ADULT_BIRTH, byCi)).isInstanceOf(IdentityReverifyBlockedException.class);
        }

        @Test
        void 다른_종류의_해시는_막지_않는다() {
            String person = newPerson();
            // 같은 해시 값이라도 EMAIL 종류면 본인인증과 상관없다
            jdbc.update("INSERT INTO identity_withdrawals (id, kind, hash, withdrawn_at, expires_at) VALUES (?, 'EMAIL', ?, now(), now() + interval '5 days')",
                    ids.nextId(), index.hash(IdentityVerificationService.DI, "fake-di:" + person));

            assertThat(verify(newMember(), ADULT_BIRTH, person).verified()).isTrue();
        }
    }

    @Nested
    class 만_19세 {

        @Test
        void 미인증이면_403_IDENTITY_REQUIRED() {
            long member = newMember();
            assertThatThrownBy(() -> service.requireAdult(member)).isInstanceOf(IdentityRequiredException.class);
            assertThatThrownBy(() -> service.requireVerified(member)).isInstanceOf(IdentityRequiredException.class);
        }

        @Test
        void 서비스_날짜_기준_만_나이이고_19세_생일이_지나면_다시_인증하지_않아도_통과한다() {
            // 2026-10-24 12:00 KST(03:00 UTC) — 2007-10-25 생은 내일 만 19세
            clock.set(Instant.parse("2026-10-24T03:00:00Z"));
            long member = newMember();
            IdentityStatusView status = verify(member, "2007-10-25", newPerson());
            assertThat(status.adult()).isFalse();
            assertThat(status.adultFrom()).isEqualTo("2026-10-25");

            IdentityUnderageException minor = catchThrowableOfType(IdentityUnderageException.class, () -> service.requireAdult(member));
            assertThat(minor.getExtras()).containsEntry("adultFrom", "2026-10-25");
            service.requireVerified(member); // 나이와 상관없는 곳은 통과

            // UTC 로는 아직 24일이지만 한국 시간으로 25일 0시 — 서비스 날짜로 판정한다
            clock.set(Instant.parse("2026-10-24T15:00:00Z"));
            service.requireAdult(member);
            assertThat(service.me(member).adult()).isTrue();
        }

        @Test
        void 이월_29일생은_평년에_3월_1일부터_성인이다() {
            clock.set(Instant.parse("2027-02-28T05:00:00Z")); // 2027-02-28 KST
            long member = newMember();
            assertThat(verify(member, "2008-02-29", newPerson()).adultFrom()).isEqualTo("2027-03-01");
            assertThatThrownBy(() -> service.requireAdult(member)).isInstanceOf(IdentityUnderageException.class);

            clock.set(Instant.parse("2027-02-28T15:00:00Z")); // 2027-03-01 00:00 KST
            service.requireAdult(member);
        }

        @Test
        void 관리자_요약은_인증_시각_성인_여부_구현만_준다() {
            long member = newMember();
            assertThat(service.summaryOf(member)).isEmpty();
            verify(member, ADULT_BIRTH, newPerson());

            var summary = service.summaryOf(member).orElseThrow();
            assertThat(summary.verified()).isTrue();
            assertThat(summary.adult()).isTrue();
            assertThat(summary.provider()).isEqualTo(IdentityProviderKind.FAKE);
        }
    }
}
