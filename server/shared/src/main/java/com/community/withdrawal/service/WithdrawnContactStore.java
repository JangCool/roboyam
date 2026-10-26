package com.community.withdrawal.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.dto.RevealedContact;
import com.community.withdrawal.dto.WithdrawnContactInfo;
import com.community.withdrawal.exception.WithdrawnContactNotFoundException;
import com.community.withdrawal.repository.master.WithdrawnContactRows;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 회원 연락용 이메일의 <b>분리 보관</b>(commerce.md 17-2 · 17-3) — 결제 이력이 있는 회원이 탈퇴할 때 이메일 하나만 {@link DataCipher}(용도 {@link #EMAIL})로 암호화해
 * {@code withdrawn_member_contacts} 에 둔다. 파기 예정일 = 마지막 거래일 + 5년(설정값), 지나면 정리 배치({@code member-privacy-retention})가 지운다.
 *
 * <ul>
 *   <li>쓰는 탈퇴 기능은 아직 없다(세션 정리 결정 14) — 탈퇴 기능이 탈퇴 트랜잭션 안에서 {@link #store} 를 부른다.</li>
 *   <li><b>값을 푸는 길은 {@link #reveal} 하나</b>다 — 전용 권한(컨트롤러 선언 {@code member.withdrawn.contact}) + 사유를 받아 감사({@code WITHDRAWN_CONTACT_REVEAL}, 사유 포함·이메일 없음)를
 *       <b>같은 트랜잭션에 남긴 뒤에만</b> 복호화한다. 감사를 못 남기면(트랜잭션이 깨지면) 값도 나가지 않는다. 열람 기록은 감사에서 읽는다({@link #reveals}).</li>
 *   <li>일반 회원 조회는 이 표를 읽지 않는다 — 표를 아는 곳은 {@link WithdrawnContactRows}·정리 배치·데이터 키 교체 배치뿐이다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class WithdrawnContactStore {

    /** 암호화 용도 — AAD 로 묶이므로 한 번 정하면 바꾸지 않는다(데이터 키 교체 배치도 이 이름을 쓴다) */
    public static final DataPurpose EMAIL = DataPurpose.of("member.withdrawn-contact.email");

    /** 열람한 값을 화면이 다시 가리기까지(고정 10분 — 화면 확정 결정 4) */
    public static final Duration MASK_AFTER = Duration.ofMinutes(10);

    /** 열람 사유 길이 */
    public static final int REASON_MIN = 5;
    public static final int REASON_MAX = 200;

    /** 열람 기록을 읽는 최대 건수 */
    static final int REVEALS_LIMIT = 50;

    private final WithdrawnContactRows rows;
    private final DataCipher cipher;
    private final AuditLogService audit;
    private final WithdrawalProperties properties;
    private final Clock clock;

    public WithdrawnContactStore(WithdrawnContactRows rows, DataCipher cipher, AuditLogService audit, WithdrawalProperties properties, ObjectProvider<Clock> clocks) {
        this.rows = rows;
        this.cipher = cipher;
        this.audit = audit;
        this.properties = properties;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 탈퇴 회원의 이메일을 암호화해 둔다.
     *
     * @param lastTransactionAt 마지막 거래 시각 — 파기 예정일 = 그 서비스 날짜 기준 + 보관 년수
     * @throws com.community.zconfiguration.exception.CommonException 데이터 키가 없을 때({@code 503 DATA_KEY_MISSING})
     */
    @Transactional
    public void store(long userId, String email, Instant withdrawnAt, Instant lastTransactionAt) {
        rows.upsert(userId, cipher.encrypt(EMAIL, email.strip()), withdrawnAt, purgeAfter(lastTransactionAt));
    }

    /** 파기 예정 시각 — 마지막 거래 시각을 서비스 시간대로 보고 보관 년수를 더한다 */
    public Instant purgeAfter(Instant lastTransactionAt) {
        return lastTransactionAt.atZone(ServiceTime.ZONE).plusYears(properties.contactRetentionYears()).toInstant();
    }

    /** 보관 중인가와 시각(이메일 없음) */
    public Optional<WithdrawnContactInfo> find(long userId) {
        return rows.find(userId).map(row -> new WithdrawnContactInfo(row.withdrawnAt(), row.purgeAfter()));
    }

    /**
     * 연락처를 연다 — 감사(사유 포함, 이메일 없음)를 같은 트랜잭션에 남긴 <b>뒤에</b> 복호화한다.
     *
     * @param reason 열람 사유(5~200자)
     * @throws ValidationException               사유 길이({@code fields.reason})
     * @throws WithdrawnContactNotFoundException 보관한 연락처가 없을 때
     */
    @Transactional
    public RevealedContact reveal(long userId, String reason, long operatorId, String ip) {
        String why = reason == null ? "" : reason.strip();
        if (why.length() < REASON_MIN || why.length() > REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("withdrawnContact.reason.length", REASON_MIN, REASON_MAX));
        }
        WithdrawnContactRows.Row row = rows.find(userId).orElseThrow(WithdrawnContactNotFoundException::new);
        Instant now = clock.instant();
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.WITHDRAWN_CONTACT_REVEAL, AuditResult.SUCCESS).target("MEMBER", Long.toString(userId))
                .with("reason", why));
        return new RevealedContact(cipher.decrypt(EMAIL, row.emailCipher()), now.plus(MASK_AFTER));
    }

    /** 열람 기록(감사에서 — 최근 것부터 50건) */
    public List<WithdrawnContactRows.Reveal> reveals(long userId) {
        return rows.reveals(userId, AuditActions.WITHDRAWN_CONTACT_REVEAL, REVEALS_LIMIT);
    }
}
