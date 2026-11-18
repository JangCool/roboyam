package com.community.adultboard.service;

import com.community.account.service.CurrentPasswordVerifier;
import com.community.adultboard.dto.request.AdultBoardEnabledRequest;
import com.community.adultboard.dto.request.AdultBoardPinRequest;
import com.community.adultboard.dto.request.AdultBoardRelockModeRequest;
import com.community.adultboard.dto.request.AdultBoardUnlockRequest;
import com.community.adultboard.dto.response.AdultBoardStatusResponse;
import com.community.adultboard.entity.AdultBoardAccessEntity;
import com.community.adultboard.exception.AdultBoardDisabledException;
import com.community.adultboard.exception.AdultBoardPinLockedException;
import com.community.adultboard.exception.AdultBoardPinWrongException;
import com.community.adultboard.repository.master.AdultBoardAccessRepository;
import com.community.auth.service.PasswordMatching;
import com.community.identity.service.IdentityVerificationService;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.UserRepository;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.exception.WithdrawalReauthRequiredException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 내 19금 게시판 접근 설정·핀번호({@code /api/me/adult-board…}) — docs/features/adult-board-pin-contract.md. 판정은 {@link AdultBoardGate}.
 *
 * <ul>
 *   <li><b>켜기·핀 바꾸기(다시 정하기)</b>: 입력 검사(400 {@code fields.password|pin}) → 성인 확인(403 {@code IDENTITY_REQUIRED}·{@code IDENTITY_UNDERAGE}) →
 *       본인 확인(비밀번호 계정은 {@link CurrentPasswordVerifier} — 로그인 실패와 같은 횟수·잠금, 401 {@code CURRENT_PASSWORD_WRONG}·429 {@code LOGIN_LOCKED} ·
 *       비밀번호가 없는 회원은 탈퇴와 같은 「최근 {@code irobit.withdrawal.reauth-minutes} 분 안 로그인」, 지났으면 401 {@code REAUTH_REQUIRED}) → 핀 해시 저장
 *       (틀린 횟수·잠금도 푼다) → <b>이 기기는 열림</b>. 핀을 새로 정하면 {@code pinSetAt} 이 바뀌어 다른 기기의 확인은 무효다.</li>
 *   <li><b>끄기</b>: 확인 없이(더 안전한 쪽). 핀 해시·틀린 횟수·잠금을 지우고 다시 묻기 기준만 남긴다.</li>
 *   <li><b>핀 확인</b>({@link #unlock}): 회원 단위로 연속 {@value AdultBoardPinPolicy#MAX_FAILURES}번 틀리면 10분 잠금. 시도는 <b>해시를 견주기 전에</b> 행을 잠그고
 *       틀린 것으로 먼저 세어 확정한다(짧은 독립 트랜잭션) — 요청이 오류로 끝나도 남고, 동시에 여러 요청을 보내도 잠기기 전에 견줘 보는 횟수가 한도를 넘지 않는다.
 *       맞으면 횟수를 0 으로 되돌린다. 한도에 닿은 시도는 틀렸으면 바로 423(잠금 시작), 맞았으면 성공이다.</li>
 * </ul>
 * 해시 계산·비교(느린 연산)는 트랜잭션 밖이다. 핀번호·비밀번호는 로그·감사 기록·응답에 싣지 않는다(회원 자기 설정이라 감사 기록도 남기지 않는다).
 */
@Service
public class AdultBoardAccessService {

    private final AdultBoardAccessRepository settings;
    private final UserRepository users;
    private final IdentityVerificationService identity;
    private final CurrentPasswordVerifier passwords;
    private final PasswordEncoder encoder;
    private final WithdrawalProperties reauth;
    private final AdultBoardUnlocks unlocks;
    private final AdultBoardGate gate;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AdultBoardAccessService(AdultBoardAccessRepository settings, UserRepository users, IdentityVerificationService identity,
                                   CurrentPasswordVerifier passwords, PasswordEncoder encoder, WithdrawalProperties reauth, AdultBoardUnlocks unlocks,
                                   AdultBoardGate gate, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.settings = settings;
        this.users = users;
        this.identity = identity;
        this.passwords = passwords;
        this.encoder = encoder;
        this.reauth = reauth;
        this.unlocks = unlocks;
        this.gate = gate;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 내 설정과 이 기기의 핀 확인 상태 — 읽기만 한다(「보는 동안 연장」을 하지 않는다) */
    public AdultBoardStatusResponse status(long userId) {
        UserEntity user = requireUser(userId);
        return view(user, settings.findById(userId).map(Snapshot::of).orElse(Snapshot.NONE), clock.instant());
    }

    /** 켜기({@code enabled: true} + 본인 확인 + 핀) · 끄기({@code enabled: false}) */
    public AdultBoardStatusResponse setEnabled(long userId, AdultBoardEnabledRequest body) {
        if (body == null || body.enabled() == null) {
            throw new ValidationException(Map.of("enabled", new MessageRef("adultBoard.enabled.required")));
        }
        if (!body.enabled()) {
            return disable(userId);
        }
        return setPin(userId, body.password(), body.pin(), false);
    }

    /** 핀번호 바꾸기·잊었을 때 다시 정하기 — 접근이 꺼져 있으면 409 {@code ADULT_BOARD_DISABLED} */
    public AdultBoardStatusResponse changePin(long userId, AdultBoardPinRequest body) {
        return setPin(userId, body == null ? null : body.password(), body == null ? null : body.pin(), true);
    }

    /** 핀번호를 다시 묻는 기준 — 꺼져 있어도 바꿀 수 있다(값만 남는다). 이 기기의 핀 확인이 살아 있으면 끝 시각을 새 기준으로 다시 잡는다 */
    public AdultBoardStatusResponse setRelockMode(long userId, AdultBoardRelockModeRequest body) {
        String mode = body == null || body.relockMode() == null ? null : body.relockMode().strip();
        if (mode == null || !AdultBoardPinPolicy.RELOCK_MODES.contains(mode)) {
            throw new ValidationException(Map.of("relockMode", new MessageRef("adultBoard.relockMode.invalid")));
        }
        Instant now = clock.instant();
        Snapshot saved = tx.execute(status -> {
            lockUser(userId);
            AdultBoardAccessEntity setting = settings.findById(userId).orElse(null);
            if (setting == null) {
                setting = AdultBoardAccessEntity.create(userId, now);
                setting.changeRelockMode(mode, now);
                settings.persist(setting);
            } else {
                setting.changeRelockMode(mode, now);
            }
            return Snapshot.of(setting);
        });
        // 이 기기의 확인 — FIXED 는 핀을 넣은 때 + 유지 시간(이미 지났으면 바로 잠긴다), IDLE 은 지금 + 유지 시간
        AdultBoardUnlocks.Unlock unlock = unlocks.current();
        if (saved.enabled() && unlock != null && unlock.validFor(userId, saved.pinSetAt(), now)) {
            Instant until = AdultBoardAccessEntity.RELOCK_FIXED.equals(mode) ? unlock.verifiedAt().plus(AdultBoardPinPolicy.UNLOCK_TTL)
                    : now.plus(AdultBoardPinPolicy.UNLOCK_TTL);
            if (until.isAfter(now)) {
                unlocks.store(unlock.until(until));
            } else {
                unlocks.clear();
            }
        }
        gate.forget();
        return view(requireUser(userId), saved, now);
    }

    /**
     * 핀번호 확인 — 맞으면 이 기기가 유지 시간 동안 열린다.
     *
     * @throws ValidationException          숫자 6자리가 아닐 때(400 {@code fields.pin} — 시도로 세지 않는다)
     * @throws AdultBoardDisabledException  접근이 꺼져 있을 때(409)
     * @throws AdultBoardPinLockedException 잠겨 있거나 이번에 틀려 잠겼을 때(423 + {@code lockedUntil}·{@code retryAfterSeconds})
     * @throws AdultBoardPinWrongException  틀렸을 때(400 + {@code attemptsLeft})
     */
    public AdultBoardStatusResponse unlock(long userId, AdultBoardUnlockRequest body) {
        String pin = body == null ? null : body.pin();
        if (!AdultBoardPinPolicy.isPin(pin)) {
            throw new ValidationException(Map.of("pin", new MessageRef("adultBoard.pin.invalid")));
        }
        UserEntity user = requireUser(userId);
        identity.requireAdult(userId);

        // 1) 시도를 먼저 틀린 것으로 세어 확정한다(행 잠금, 짧은 독립 트랜잭션) — 잠겨 있으면 세지 않고 거절
        Attempt attempt = tx.execute(status -> {
            Instant now = clock.instant();
            AdultBoardAccessEntity setting = settings.findWithLockByUserId(userId).orElse(null);
            if (setting == null || !setting.isEnabled()) {
                throw new AdultBoardDisabledException();
            }
            setting.clearIfLockExpired(now); // 잠금이 풀렸으면 처음부터 다시 센다
            if (setting.isLocked(now)) {
                // 잠긴 동안은 맞는 핀도 받지 않고 횟수도 더하지 않는다(이 트랜잭션은 바꾼 것 없이 되돌려진다)
                throw new AdultBoardPinLockedException(setting.getLockedUntil(), AdultBoardPinPolicy.secondsUntil(setting.getLockedUntil(), now));
            }
            setting.fail(now, AdultBoardPinPolicy.MAX_FAILURES, AdultBoardPinPolicy.LOCK_TIME.toSeconds());
            return new Attempt(setting.getPinHash(), setting.getPinSetAt(), setting.getFailedCount(), setting.getLockedUntil());
        });

        // 2) 해시 비교 — 트랜잭션 밖
        if (!PasswordMatching.matches(encoder, pin, attempt.pinHash())) {
            Instant now = clock.instant();
            if (attempt.lockedUntil() != null) {
                throw new AdultBoardPinLockedException(attempt.lockedUntil(), AdultBoardPinPolicy.secondsUntil(attempt.lockedUntil(), now));
            }
            throw new AdultBoardPinWrongException(AdultBoardPinPolicy.MAX_FAILURES - attempt.failedCount());
        }

        // 3) 맞았다 — 횟수·잠금을 지운다. 그 사이 핀이 바뀌었거나 꺼졌으면(다른 기기) 이 확인은 버린다
        Instant now = clock.instant();
        Snapshot saved = tx.execute(status -> {
            AdultBoardAccessEntity setting = settings.findWithLockByUserId(userId).orElse(null);
            if (setting == null || !setting.isEnabled() || !attempt.pinSetAt().equals(setting.getPinSetAt())) {
                throw new AdultBoardDisabledException();
            }
            setting.resetFailures(now);
            return Snapshot.of(setting);
        });
        unlocks.store(new AdultBoardUnlocks.Unlock(userId, now, now.plus(AdultBoardPinPolicy.UNLOCK_TTL), saved.pinSetAt()));
        gate.forget();
        return view(user, saved, now);
    }

    /** 이 기기 지금 잠그기 — 이 세션의 핀 확인만 지운다(다른 기기·설정은 그대로) */
    public AdultBoardStatusResponse lock(long userId) {
        UserEntity user = requireUser(userId);
        unlocks.clear();
        gate.forget();
        return view(user, settings.findById(userId).map(Snapshot::of).orElse(Snapshot.NONE), clock.instant());
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private AdultBoardStatusResponse disable(long userId) {
        Instant now = clock.instant();
        Snapshot saved = tx.execute(status -> {
            AdultBoardAccessEntity setting = settings.findWithLockByUserId(userId).orElse(null);
            if (setting == null) {
                return Snapshot.NONE;
            }
            setting.disable(now);
            return Snapshot.of(setting);
        });
        unlocks.clear();
        gate.forget();
        return view(requireUser(userId), saved, now);
    }

    /**
     * 켜기·핀 바꾸기 공통 — 검사 → 성인 → 본인 확인 → 저장 → 이 기기 열림.
     *
     * @param mustBeEnabled 핀 바꾸기(true)는 켜져 있어야 한다(아니면 409). 켜기(false)는 이미 켜져 있어도 핀을 새로 정한다
     */
    private AdultBoardStatusResponse setPin(long userId, String password, String pin, boolean mustBeEnabled) {
        UserEntity user = requireUser(userId);
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (user.hasPassword() && (password == null || password.isEmpty())) {
            fields.put("password", new MessageRef("adultBoard.password.required"));
        }
        if (!AdultBoardPinPolicy.isPin(pin)) {
            fields.put("pin", new MessageRef("adultBoard.pin.invalid"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        identity.requireAdult(userId); // 본인인증 만 19세 이상만(403 IDENTITY_REQUIRED · IDENTITY_UNDERAGE)
        if (mustBeEnabled && !settings.findById(userId).map(AdultBoardAccessEntity::isEnabled).orElse(false)) {
            throw new AdultBoardDisabledException(); // 본인 확인 앞 — 할 수 없는 일에 비밀번호 시도 횟수를 쓰게 하지 않는다
        }

        // 본인 확인 — 비밀번호 계정은 비밀번호(로그인 실패와 같은 횟수·잠금), 비밀번호가 없는 회원은 최근 로그인(탈퇴와 같은 규칙)
        if (user.hasPassword()) {
            passwords.verify(user, password);
        } else if (!recentlyLoggedIn(user, clock.instant())) {
            throw new WithdrawalReauthRequiredException(); // 401 REAUTH_REQUIRED — 화면은 다시 로그인으로 보낸다(탈퇴와 같은 오류)
        }

        String hash = encoder.encode(pin); // 느린 해시 — 트랜잭션 밖
        // pinSetAt 은 밀리초로 자른다 — 세션에 담은 값과 DB 에서 읽은 값(마이크로초 저장)이 늘 같게
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Snapshot saved = tx.execute(status -> {
            lockUser(userId);
            AdultBoardAccessEntity setting = settings.findById(userId).orElse(null);
            if (setting == null) {
                if (mustBeEnabled) {
                    throw new AdultBoardDisabledException();
                }
                setting = AdultBoardAccessEntity.create(userId, now);
                setting.enable(hash, now);
                settings.persist(setting);
            } else {
                if (mustBeEnabled && !setting.isEnabled()) {
                    throw new AdultBoardDisabledException(); // 본인 확인 사이에 다른 기기가 껐다
                }
                setting.enable(hash, now);
            }
            return Snapshot.of(setting);
        });
        // 방금 비밀번호와 핀을 넣었으므로 이 기기는 열림 — 다른 기기는 pinSetAt 이 달라져 잠긴다
        unlocks.store(new AdultBoardUnlocks.Unlock(userId, now, now.plus(AdultBoardPinPolicy.UNLOCK_TTL), saved.pinSetAt()));
        gate.forget();
        return view(user, saved, now);
    }

    private boolean recentlyLoggedIn(UserEntity user, Instant now) {
        return user.getLastLoginAt() != null && !user.getLastLoginAt().plus(Duration.ofMinutes(reauth.reauthMinutes())).isBefore(now);
    }

    private UserEntity requireUser(long userId) {
        return users.findById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
    }

    /** 회원 행을 잠근다 — 같은 회원의 설정 행 만들기·고치기를 줄 세운다(행이 아직 없을 수 있어 회원 행으로). 탈퇴한 계정이면 401 */
    private void lockUser(long userId) {
        users.findWithLockById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
    }

    private AdultBoardStatusResponse view(UserEntity user, Snapshot s, Instant now) {
        AdultBoardUnlocks.Unlock unlock = s.enabled() ? unlocks.current() : null;
        boolean unlocked = unlock != null && unlock.validFor(user.getId(), s.pinSetAt(), now);
        return new AdultBoardStatusResponse(s.enabled(), s.enabled() ? s.pinSetAt() : null, s.relockMode(), unlocked, unlocked ? unlock.until() : null,
                s.enabled() ? AdultBoardPinPolicy.activeLock(s.lockedUntil(), now) : null, user.hasPassword());
    }

    /** 트랜잭션 밖으로 들고 나오는 설정 값(해시는 싣지 않는다) */
    private record Snapshot(boolean enabled, Instant pinSetAt, String relockMode, Instant lockedUntil) {

        static final Snapshot NONE = new Snapshot(false, null, AdultBoardAccessEntity.RELOCK_IDLE, null);

        static Snapshot of(AdultBoardAccessEntity e) {
            return new Snapshot(e.isEnabled(), e.getPinSetAt(), e.getRelockMode(), e.getLockedUntil());
        }
    }

    /** 세어 둔 시도 하나 — 해시 비교에 쓰고 버린다({@code toString} 에 해시를 싣지 않는다) */
    private record Attempt(String pinHash, Instant pinSetAt, int failedCount, Instant lockedUntil) {

        @Override
        public String toString() {
            return "Attempt[pinHash=(감춤), failedCount=" + failedCount + "]";
        }
    }
}
