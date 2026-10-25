package com.community.identity.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.dto.VerifiedPerson;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 가짜 본인인증(local·시험 전용) — 같은 사람 번호 = 같은 사람, 입력 검사는 칸 경로 {@code fake.*}(계약 「0-4」 화면) */
class FakeIdentityVerifierTest {

    /** 2026-10-24 23:30 KST(14:30 UTC) */
    private final FakeIdentityVerifier verifier = new FakeIdentityVerifier(Clock.fixed(Instant.parse("2026-10-24T14:30:00Z"), ZoneOffset.UTC));

    @Test
    void 같은_사람_번호는_같은_CI_DI_이고_다른_번호는_다르다() {
        VerifiedPerson a = verifier.verify("iv-1", new FakeIdentityInput("1990-05-14", " kim "));
        VerifiedPerson b = verifier.verify("iv-2", new FakeIdentityInput("1990-05-14", "kim"));
        VerifiedPerson c = verifier.verify("iv-3", new FakeIdentityInput("1990-05-14", "lee"));

        assertThat(a.ci()).isEqualTo(b.ci()).isNotEqualTo(c.ci());
        assertThat(a.di()).isEqualTo(b.di()).isNotEqualTo(c.di());
        assertThat(a.birthDate()).isEqualTo(LocalDate.of(1990, 5, 14));
        assertThat(a.providerTxId()).isEqualTo("fake:iv-1");
        assertThat(verifier.kind()).isEqualTo(IdentityProviderKind.FAKE);
        assertThat(verifier.startInfo().storeId()).isNull();
    }

    @Test
    void 생년월일은_YYYY_MM_DD_로_서비스_날짜_오늘까지() {
        assertThat(verifier.verify("iv", new FakeIdentityInput("2026-10-24", "p")).birthDate()).isEqualTo(LocalDate.of(2026, 10, 24)); // KST 오늘
        for (String bad : new String[] {"2026-10-25", "1990-5-14", "1990-02-30", "19900514", "1899-12-31", "abc"}) {
            ValidationException error = catchThrowableOfType(ValidationException.class, () -> verifier.verify("iv", new FakeIdentityInput(bad, "p")));
            assertThat(error.getFields()).as(bad).containsOnlyKeys("fake.birthDate");
        }
    }

    @Test
    void 빈_입력과_40자를_넘는_사람_번호는_칸별로_400() {
        assertThat(catchThrowableOfType(ValidationException.class, () -> verifier.verify("iv", null)).getFields()).containsOnlyKeys("fake.birthDate", "fake.personKey");
        assertThat(catchThrowableOfType(ValidationException.class, () -> verifier.verify("iv", new FakeIdentityInput(" ", "  "))).getFields())
                .containsOnlyKeys("fake.birthDate", "fake.personKey");
        assertThat(catchThrowableOfType(ValidationException.class, () -> verifier.verify("iv", new FakeIdentityInput("1990-05-14", "x".repeat(41)))).getFields())
                .containsOnlyKeys("fake.personKey");
        assertThat(verifier.verify("iv", new FakeIdentityInput("1990-05-14", "x".repeat(40))).ci()).endsWith("x".repeat(40));
    }
}
