package com.community.portone.config;

import com.community.portone.dto.PortOneSettingKey;
import com.community.portone.repository.master.PortOneSettingStore;
import com.community.zconfiguration.crypto.SecretEncryptor;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜰 때(api·admin 모두) local 시드({@link PortOneSeedProperties})로 포트원 설정을 채운다 — 재설치(DB 를 새로 만듦) 뒤에도 등록된 상태로 뜨게.
 *
 * <ul>
 *   <li><b>표가 비어 있을 때만</b> 넣는다 — 관리자 화면에서 한 번이라도 저장하거나 지운 칸이 있으면 손대지 않는다(화면에서 바꾼 값을 서버 재기동이 덮지 않게).</li>
 *   <li>dev·prod 프로필에서는 넣지 않는다(시드 값이 있어도 WARN 만). 설정 비밀값 키({@code ROBOYAM_CRYPTO_KEY})가 없거나 시드가 꺼져 있어도(시험) 넣지 않는다.</li>
 *   <li>감사는 남기지 않는다(운영자가 바꾼 것이 아니다 — 바꾼 사람 없음). 로그에는 칸 수만.</li>
 * </ul>
 */
@Component
public class PortOneLocalSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PortOneLocalSeeder.class);

    static final Profiles GUARDED = Profiles.of("dev", "prod");

    private final PortOneSeedProperties seed;
    private final PortOneSettingStore store;
    private final SecretEncryptor encryptor;
    private final Environment environment;
    private final Clock clock;

    public PortOneLocalSeeder(PortOneSeedProperties seed, PortOneSettingStore store, SecretEncryptor encryptor, Environment environment, ObjectProvider<Clock> clocks) {
        this.seed = seed;
        this.store = store;
        this.encryptor = encryptor;
        this.environment = environment;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** @return 넣은 칸 수 */
    int seed() {
        Map<PortOneSettingKey, String> values = seed.values();
        if (values.isEmpty()) {
            return 0;
        }
        if (environment.acceptsProfiles(GUARDED)) {
            log.warn("dev·prod 프로필에서는 포트원 시드(roboyam.portone.seed)를 넣지 않는다 — 관리자 화면에서 저장하세요");
            return 0;
        }
        if (!encryptor.isConfigured()) {
            log.warn("설정 비밀값 키(ROBOYAM_CRYPTO_KEY)가 없어 포트원 시드를 넣지 않았다");
            return 0;
        }
        if (!store.findAll().isEmpty()) {
            return 0;
        }
        int seeded = 0;
        for (Map.Entry<PortOneSettingKey, String> entry : values.entrySet()) {
            seeded += store.seedIfAbsent(entry.getKey().key(), encryptor.encrypt(entry.getValue()), clock.instant()) ? 1 : 0;
        }
        if (seeded > 0) {
            log.info("포트원 설정 {}칸을 local 시드로 채웠다", seeded);
        }
        return seeded;
    }
}
