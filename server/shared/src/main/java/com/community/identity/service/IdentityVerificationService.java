package com.community.identity.service;

import com.community.identity.config.IdentityProperties;
import com.community.identity.dto.IdentityStartInfo;
import com.community.identity.dto.IdentitySummary;
import com.community.identity.dto.VerifiedPerson;
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
import com.community.identity.provider.IdentityVerifier;
import com.community.identity.repository.master.IdentityStore;
import com.community.point.service.PointService;
import com.community.withdrawal.entity.WithdrawalKind;
import com.community.withdrawal.repository.master.IdentityWithdrawalStore;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 본인인증(commerce.md 12-1, docs/features/commerce-foundation-contract.md 「0-4」) — 1인 1계정 · 만 19세 · 탈퇴 후 30일 재인증 제한.
 *
 * <ol>
 *   <li><b>시작</b>({@link #start}): 서버가 인증 번호를 먼저 발급하고({@code identity_verification_attempts}) 화면은 그 번호로 업체 인증을 연다. 이미 인증한 회원은 409.</li>
 *   <li><b>확정</b>({@link #confirm}): 남의 번호·없는 번호는 404, 발급 30분이 지났거나 끝난 번호는 410. 업체에 결과를 다시 묻고(트랜잭션 밖), DI·CI 를 {@link BlindIndex}
 *       (용도 {@link #DI}·{@link #CI})로 해시한 뒤 한 트랜잭션에서 — 재인증 제한(같은 종류·해시가 아직 막혀 있으면 409 + {@code retryAfter}) → 결과 넣기(DI·CI·업체 건 번호가 이미
 *       다른 회원에게 있으면 409 {@code IDENTITY_ALREADY_LINKED} — 동시에 들어와도 유일 제약이 하나만 넣는다) → 시도를 VERIFIED 로.</li>
 *   <li><b>실패도 시도 표에 남는다</b> — 본인인증 성공·실패는 회원 행동이라 감사가 아니라 시도의 {@code status}·{@code failure_code}(거절한 오류 코드)에 남긴다. 거절로 업무 트랜잭션이
 *       되돌려져도 남도록 실패 표시는 <b>따로</b> 확정한다. 입력 오류(400)·설정 없음(503)은 시도를 끝내지 않는다(다시 낼 수 있다).</li>
 *   <li><b>만 19세</b>({@link #requireAdult}): 서비스 날짜(한국 시간) 기준 만 나이, 2월 29일생은 평년에 3월 1일({@link AdultAge}). 결제 직전마다 다시 계산한다.</li>
 * </ol>
 * 원문 CI·DI·이름·휴대폰 번호는 저장하지도 로그에 남기지도 않는다.
 */
@Service
public class IdentityVerificationService {

    private static final Logger log = LoggerFactory.getLogger(IdentityVerificationService.class);

    /** DI 해시의 용도 — 한 번 정하면 바꾸지 않는다(재인증 제한 표의 IDENTITY_DI 해시와 같은 값) */
    public static final DataPurpose DI = DataPurpose.of("identity.di");
    /** CI 해시의 용도 */
    public static final DataPurpose CI = DataPurpose.of("identity.ci");

    /** 시도를 실패로 끝내는 거절 — 이 밖의 오류(입력·설정·키)는 시도를 그대로 둔다 */
    private static final Set<String> FAILING_CODES = Set.of("IDENTITY_ALREADY_LINKED", "IDENTITY_REVERIFY_BLOCKED", "IDENTITY_ALREADY_VERIFIED",
            "IDENTITY_PROVIDER_FAILED", "IDENTITY_VERIFICATION_EXPIRED");

    private final IdentityStore store;
    private final IdentityWithdrawalStore withdrawals;
    private final IdentityVerifier verifier;
    private final IdentityProperties properties;
    private final BlindIndex index;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate writes;
    private final TransactionTemplate separate;
    private final PointService points;
    private final Clock clock;

    public IdentityVerificationService(IdentityStore store, IdentityWithdrawalStore withdrawals, IdentityVerifier verifier, IdentityProperties properties,
                                       BlindIndex index, SnowflakeIdGenerator ids, PlatformTransactionManager transactions, PointService points,
                                       ObjectProvider<Clock> clocks) {
        this.points = points;
        this.store = store;
        this.withdrawals = withdrawals;
        this.verifier = verifier;
        this.properties = properties;
        this.index = index;
        this.ids = ids;
        this.writes = new TransactionTemplate(transactions);
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    /** 내 본인인증 상태 — 미인증이면 {@code verified:false} 와 나머지 null */
    public IdentityStatusView me(long userId) {
        return store.findVerification(userId).map(this::statusOf).orElseGet(IdentityStatusView::unverified);
    }

    /**
     * 인증을 시작한다 — 인증 번호를 발급하고 지금 구현(포트원/가짜)과 포트원 SDK 값을 준다.
     *
     * @throws IdentityAlreadyVerifiedException 이미 인증한 회원
     * @throws com.community.identity.exception.IdentityNotConfiguredException 포트원인데 설정 칸이 비었을 때(503)
     */
    public IdentityStartView start(long userId) {
        if (store.findVerification(userId).isPresent()) {
            throw new IdentityAlreadyVerifiedException();
        }
        index.requireConfigured(); // 결과를 저장할 수 없으면(해시 키 없음 — 503 INDEX_KEY_MISSING) 업체 인증을 시작하지 않는다
        IdentityStartInfo info = verifier.startInfo();
        String id = "iv-" + ids.nextId();
        writes.executeWithoutResult(status -> store.insertAttempt(id, userId, verifier.kind(), clock.instant()));
        return new IdentityStartView(id, verifier.kind(), info.storeId(), info.channelKey());
    }

    /**
     * 인증을 확정한다(규칙은 클래스 설명).
     *
     * @param fake 가짜 구현의 입력(포트원은 읽지 않는다)
     * @return 확정된 상태(me 와 같은 모양)
     */
    public IdentityStatusView confirm(long userId, String verificationId, FakeIdentityInput fake) {
        IdentityStore.Attempt attempt = store.findAttempt(verificationId).filter(a -> a.userId() == userId)
                .orElseThrow(IdentityVerificationNotFoundException::new);
        try {
            if (store.findVerification(userId).isPresent()) {
                throw new IdentityAlreadyVerifiedException();
            }
            Instant now = clock.instant();
            if (!attempt.pending() || attempt.provider() != verifier.kind() || !now.isBefore(attempt.createdAt().plus(properties.verificationTtl()))) {
                throw new IdentityVerificationExpiredException(); // 끝난 번호 · 구현이 바뀐 뒤의 번호 · 30분 지난 번호는 새로 시작해야 한다
            }
            VerifiedPerson person = verifier.verify(attempt.id(), fake); // 트랜잭션 밖 — 외부 호출
            String diHash = person.di() == null ? null : index.hash(DI, person.di());
            String ciHash = person.ci() == null ? null : index.hash(CI, person.ci());
            return writes.execute(status -> settle(userId, attempt, person, diHash, ciHash));
        } catch (CommonException e) {
            if (FAILING_CODES.contains(e.getErrorCode())) {
                fail(attempt, e.getErrorCode());
            }
            throw e;
        }
    }

    /** 한 트랜잭션 — 재인증 제한 → 결과 넣기 → 시도 확정 → 가입 포인트(세이브포인트) */
    private IdentityStatusView settle(long userId, IdentityStore.Attempt attempt, VerifiedPerson person, String diHash, String ciHash) {
        Instant now = clock.instant();
        Optional<Instant> blocked = Stream.of(withdrawals.blockedUntil(WithdrawalKind.IDENTITY_DI, diHash, now),
                withdrawals.blockedUntil(WithdrawalKind.IDENTITY_CI, ciHash, now)).flatMap(Optional::stream).max(Instant::compareTo);
        if (blocked.isPresent()) {
            throw new IdentityReverifyBlockedException(ServiceTime.firstFullDateAfter(blocked.get()));
        }
        if (!store.insertVerification(userId, diHash, ciHash, person.birthDate(), verifier.kind(), person.providerTxId(), now)) {
            if (store.findVerification(userId).isPresent()) {
                throw new IdentityAlreadyVerifiedException(); // 같은 회원의 다른 확정이 먼저 끝났다
            }
            throw new IdentityAlreadyLinkedException(); // DI·CI(또는 업체 건 번호)가 다른 회원에게 있다
        }
        if (!store.finishAttempt(attempt.id(), true, null, now)) {
            throw new IdentityAlreadyVerifiedException(); // 같은 번호의 다른 확정이 먼저 끝냈다 — 되돌린다
        }
        // 가입 포인트(커머스 1-3) — 계정마다 평생 한 번, 세이브포인트라 실패해도 인증은 성공한다. 동결·정상 회원 아님이면 주지 않고 나중에도 주지 않는다
        long signup = points.signup(userId);
        log.info("본인인증 확정 userId={} provider={} signupPoints={}", userId, verifier.kind(), signup);
        return statusOf(store.findVerification(userId).orElseThrow());
    }

    /** 시도를 실패로 끝낸다 — 거절로 업무 트랜잭션이 되돌려져도 남도록 따로 확정한다 */
    private void fail(IdentityStore.Attempt attempt, String code) {
        try {
            separate.executeWithoutResult(status -> store.finishAttempt(attempt.id(), false, code, clock.instant()));
        } catch (RuntimeException e) {
            log.error("본인인증 시도의 실패를 남기지 못했다 원인={}", e.getClass().getSimpleName()); // 거절 응답은 그대로 나간다
        }
    }

    // ── 다른 업무가 부르는 판정 ────────────────────────────────────────────────

    /**
     * 만 19세 이상인 본인인증 회원만 통과(충전·공구 결제 직전 — commerce.md 12-1).
     *
     * @throws IdentityRequiredException 미인증(403)
     * @throws IdentityUnderageException 만 19세 미만(403 + {@code adultFrom})
     */
    public void requireAdult(long userId) {
        IdentityStore.Verification verification = store.findVerification(userId).orElseThrow(IdentityRequiredException::new);
        if (!AdultAge.isAdult(verification.birthDate(), ServiceTime.today(clock))) {
            throw new IdentityUnderageException(AdultAge.adultFrom(verification.birthDate()));
        }
    }

    /**
     * 본인인증한 회원만 통과(가입 포인트·유상 환불 신청처럼 나이와 상관없는 곳).
     *
     * @throws IdentityRequiredException 미인증(403)
     */
    public void requireVerified(long userId) {
        if (store.findVerification(userId).isEmpty()) {
            throw new IdentityRequiredException();
        }
    }

    /** 만 19세가 되는 날(미인증이면 빈 값) */
    public Optional<LocalDate> adultFrom(long userId) {
        return store.findVerification(userId).map(v -> AdultAge.adultFrom(v.birthDate()));
    }

    /** 관리자 회원 상세의 「본인 인증」 구획 — 미인증이면 빈 값(생년월일·해시 없음) */
    public Optional<IdentitySummary> summaryOf(long userId) {
        return store.findVerification(userId)
                .map(v -> new IdentitySummary(true, v.verifiedAt(), AdultAge.isAdult(v.birthDate(), ServiceTime.today(clock)), v.provider()));
    }

    private IdentityStatusView statusOf(IdentityStore.Verification verification) {
        LocalDate adultFrom = AdultAge.adultFrom(verification.birthDate());
        return new IdentityStatusView(true, verification.verifiedAt(), !ServiceTime.today(clock).isBefore(adultFrom), adultFrom.toString());
    }
}
