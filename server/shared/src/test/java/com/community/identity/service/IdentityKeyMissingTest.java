package com.community.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.identity.config.IdentityProperties;
import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.provider.FakeIdentityVerifier;
import com.community.identity.repository.master.IdentityStore;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 해시 키({@code ROBOYAM_INDEX_KEY})가 없으면 본인인증은 503 {@code INDEX_KEY_MISSING} — 시작부터 막아 결과를 저장할 수 없는 업체 인증을 열지 않고, 확정에서 걸려도 시도를 끝내지 않는다
 * (키를 넣고 다시 낼 수 있다). 계약 「0-1」(키가 없으면 앱은 뜨고 그 요청만 503).
 */
class IdentityKeyMissingTest {

    private final List<String> finished = new ArrayList<>();
    private final Instant now = Instant.parse("2026-10-24T03:00:00Z");

    private final IdentityStore store = new IdentityStore(new DriverManagerDataSource()) {
        @Override
        public Optional<Verification> findVerification(long userId) {
            return Optional.empty();
        }

        @Override
        public Optional<Attempt> findAttempt(String id) {
            return Optional.of(new Attempt(id, 1L, IdentityProviderKind.FAKE, "PENDING", null, now, null));
        }

        @Override
        public boolean finishAttempt(String id, boolean verified, String failureCode, Instant at) {
            finished.add(id);
            return true;
        }
    };

    private IdentityVerificationService service() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("clock", Clock.fixed(now, ZoneOffset.UTC));
        return new IdentityVerificationService(store, null, new FakeIdentityVerifier(Clock.fixed(now, ZoneOffset.UTC)),
                new IdentityProperties(IdentityProviderKind.FAKE, Duration.ofMinutes(30)), new BlindIndex(""), new SnowflakeIdGenerator(5),
                new DataSourceTransactionManager(new DriverManagerDataSource()), beans.getBeanProvider(Clock.class));
    }

    @Test
    void 해시_키가_없으면_시작은_503_이고_번호를_발급하지_않는다() {
        assertThatThrownBy(() -> service().start(1L)).isInstanceOf(CommonException.class).extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo("INDEX_KEY_MISSING");
    }

    @Test
    void 확정에서_해시_키가_없으면_503_이고_시도는_그대로다() {
        assertThatThrownBy(() -> service().confirm(1L, "iv-1", new FakeIdentityInput("1990-05-14", "p"))).isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("INDEX_KEY_MISSING");
        assertThat(finished).isEmpty();
    }
}
