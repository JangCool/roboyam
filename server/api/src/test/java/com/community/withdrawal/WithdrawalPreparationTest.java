package com.community.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.audit.dto.AuditActions;
import com.community.commerce.CommerceApiTestBase;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.service.IdentityVerificationService;
import com.community.withdrawal.dto.RevealedContact;
import com.community.withdrawal.entity.WithdrawalKind;
import com.community.withdrawal.exception.WithdrawnContactNotFoundException;
import com.community.withdrawal.repository.master.WithdrawnContactRows;
import com.community.withdrawal.service.IdentityWithdrawalRecorder;
import com.community.withdrawal.service.WithdrawnContactStore;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 탈퇴 대비(commerce.md 12-1 · 17-2 · 17-3, 계약 「0-4」) — 재가입·재인증 제한 해시 기록(30일)과 탈퇴 회원 연락처의 분리 보관·감사 뒤 열람.
 * 쓰는 탈퇴 기능은 아직 없으므로 서비스를 바로 부른다(세션 정리 결정 14).
 */
class WithdrawalPreparationTest extends CommerceApiTestBase {

    private static final String IP = "127.0.0.1";

    @Autowired
    private IdentityWithdrawalRecorder recorder;
    @Autowired
    private WithdrawnContactStore contacts;
    @Autowired
    private IdentityVerificationService identities;
    @Autowired
    private DataCipher cipher;

    @Nested
    class 재가입_제한_해시 {

        @Test
        void 이메일은_소문자_공백_제거로_정규화한_해시만_30일_남는다() {
            String email = "Rejoin." + ids.nextId() + "@Example.COM";
            Instant withdrawnAt = clock.instant();
            recorder.recordEmail("  " + email.substring(0, 3) + " " + email.substring(3) + "\t", withdrawnAt);

            String hash = recorder.hash(WithdrawalKind.EMAIL, email.toLowerCase());
            Map<String, Object> row = jdbc.queryForMap("SELECT kind, hash, withdrawn_at, expires_at FROM identity_withdrawals WHERE hash = ?", hash);
            assertThat(row.get("kind")).isEqualTo("EMAIL");
            assertThat(((java.sql.Timestamp) row.get("expires_at")).toInstant()).isEqualTo(withdrawnAt.plus(Duration.ofDays(30)));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_withdrawals WHERE hash ILIKE '%example%'", Integer.class)).isZero(); // 원문 없음

            assertThat(recorder.blockedUntil(WithdrawalKind.EMAIL, email.toUpperCase(), clock.instant())).contains(withdrawnAt.plus(Duration.ofDays(30)));
            clock.advance(Duration.ofDays(30));
            assertThat(recorder.blockedUntil(WithdrawalKind.EMAIL, email, clock.instant())).isEmpty();
        }

        @Test
        void 소셜_계정은_공급자_회원_번호로_구분한다() {
            String subject = "sub-" + ids.nextId();
            recorder.recordSocial("kakao", subject, clock.instant());

            assertThat(recorder.blockedUntil(WithdrawalKind.SOCIAL, IdentityWithdrawalRecorder.normalizeSocial("KAKAO ", " " + subject), clock.instant())).isPresent();
            assertThat(recorder.blockedUntil(WithdrawalKind.SOCIAL, IdentityWithdrawalRecorder.normalizeSocial("naver", subject), clock.instant())).isEmpty();
        }

        @Test
        void 본인인증은_DI_CI_해시를_옮기고_인증_줄을_지운다() {
            long member = newMember();
            String person = newPerson();
            identities.confirm(member, identities.start(member).verificationId(), new FakeIdentityInput("1990-05-14", person));
            Map<String, Object> verified = jdbc.queryForMap("SELECT di_hash, ci_hash FROM identity_verifications WHERE user_id = ?", member);

            assertThat(recorder.recordIdentity(member, clock.instant())).isEqualTo(2);

            assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_verifications WHERE user_id = ?", Integer.class, member)).isZero();
            assertThat(jdbc.queryForList("SELECT kind FROM identity_withdrawals WHERE hash IN (?, ?) ORDER BY kind", String.class, verified.get("di_hash"),
                    verified.get("ci_hash"))).containsExactly("IDENTITY_CI", "IDENTITY_DI");
            assertThat(recorder.blockedUntil(WithdrawalKind.IDENTITY_DI, "fake-di:" + person, clock.instant())).isPresent();
            assertThat(recorder.recordIdentity(member, clock.instant())).isZero(); // 인증하지 않은 회원(이미 옮김)이면 아무것도 하지 않는다
        }
    }

    @Nested
    class 탈퇴_회원_연락처 {

        @Test
        void 이메일은_데이터_키로_암호화해_두고_파기_예정일은_마지막_거래일_더하기_5년() {
            long member = newMember();
            String email = "left-" + member + "@test.local";
            Instant lastTransaction = Instant.parse("2026-03-01T05:00:00Z");
            contacts.store(member, email, clock.instant(), lastTransaction);

            Map<String, Object> row = jdbc.queryForMap("SELECT email_cipher, purge_after FROM withdrawn_member_contacts WHERE user_id = ?", member);
            String stored = (String) row.get("email_cipher");
            assertThat(stored).startsWith("d" + cipher.activeVersion() + ":").doesNotContain(email).doesNotContain("left-");
            assertThat(cipher.decrypt(WithdrawnContactStore.EMAIL, stored)).isEqualTo(email);
            assertThat(((java.sql.Timestamp) row.get("purge_after")).toInstant())
                    .isEqualTo(LocalDateTime.of(2031, 3, 1, 14, 0).atZone(ServiceTime.ZONE).toInstant());
            assertThat(contacts.find(member)).get().extracting(info -> info.purgeAfter()).isEqualTo(Instant.parse("2031-03-01T05:00:00Z"));
        }

        @Test
        void 열람은_사유를_받아_감사를_남기고_값과_다시_가릴_시각을_준다() {
            long member = newMember();
            long operator = newOperator();
            String email = "reveal-" + member + "@test.local";
            contacts.store(member, email, clock.instant(), clock.instant());

            RevealedContact revealed = contacts.reveal(member, "  환불 분쟁 연락 확인  ", operator, IP);

            assertThat(revealed.email()).isEqualTo(email);
            assertThat(revealed.maskAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
            assertThat(revealed.toString()).doesNotContain(email);
            Map<String, Object> audit = lastAudit("MEMBER", Long.toString(member), AuditActions.WITHDRAWN_CONTACT_REVEAL);
            assertThat(audit.get("actor_id")).isEqualTo(operator);
            assertThat((String) audit.get("detail")).contains("환불 분쟁 연락 확인").doesNotContain(email).doesNotContain("reveal-");

            List<WithdrawnContactRows.Reveal> reveals = contacts.reveals(member);
            assertThat(reveals).hasSize(1);
            assertThat(reveals.get(0).reason()).isEqualTo("환불 분쟁 연락 확인");
            assertThat(reveals.get(0).operatorName()).isNotBlank();
        }

        @Test
        void 사유가_5_200자가_아니면_400이고_감사도_값도_없다() {
            long member = newMember();
            contacts.store(member, "short-" + member + "@test.local", clock.instant(), clock.instant());
            for (String reason : new String[] {null, "  ", "넷글자임", "r".repeat(201)}) {
                ValidationException error = catchThrowableOfType(ValidationException.class, () -> contacts.reveal(member, reason, newOperator(), IP));
                assertThat(error.getFields()).containsOnlyKeys("reason");
            }
            assertThat(auditCount("MEMBER", Long.toString(member), AuditActions.WITHDRAWN_CONTACT_REVEAL)).isZero();
        }

        @Test
        void 보관한_연락처가_없으면_404_이고_감사도_없다() {
            long member = newMember();
            assertThatThrownBy(() -> contacts.reveal(member, "없는 연락처 열람", newOperator(), IP)).isInstanceOf(WithdrawnContactNotFoundException.class);
            assertThat(contacts.find(member)).isEmpty();
            assertThat(auditCount("MEMBER", Long.toString(member), AuditActions.WITHDRAWN_CONTACT_REVEAL)).isZero();
        }

        @Test
        void 값을_풀지_못하면_열람_감사도_남지_않는다_같은_트랜잭션() {
            long member = newMember();
            // 다른 용도로 잠근 암호문 — 풀리지 않는다(용도가 AAD 로 묶인다)
            jdbc.update("INSERT INTO withdrawn_member_contacts (user_id, email_cipher, withdrawn_at, purge_after) VALUES (?, ?, now(), now() + interval '1 day')", member,
                    cipher.encrypt(com.community.zconfiguration.crypto.DataPurpose.of("other.purpose"), "x@test.local"));

            assertThatThrownBy(() -> contacts.reveal(member, "풀리지 않는 값 열람", newOperator(), IP)).isInstanceOf(IllegalArgumentException.class);
            assertThat(auditCount("MEMBER", Long.toString(member), AuditActions.WITHDRAWN_CONTACT_REVEAL)).isZero();
        }
    }
}
