package com.community.analytics.collect;

import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.crypto.SecretCipher;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

/** 방문 수집 시험 도우미 — 설정 사본을 고정값으로, 해시 키는 시험용 새 키 */
final class AnalyticsTestSupport {

    static final BlindIndex INDEX = new BlindIndex(SecretCipher.generateKey());
    static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(913);
    private static final SecureRandom RANDOM = new SecureRandom();

    static final StatsCollection ALL_ON = new StatsCollection(true, true, true, 3, true, true, true, true);

    private AnalyticsTestSupport() {
    }

    /** 고정 설정 사본(시험 중 바꿀 수 있다) */
    static StatsCollectionSettings settings(AtomicReference<StatsCollection> ref) {
        return new StatsCollectionSettings(null, null) {
            @Override
            public StatsCollection current() {
                return ref.get();
            }
        };
    }

    static StatsCollectionSettings settings(StatsCollection value) {
        return settings(new AtomicReference<>(value));
    }

    /** 방문자 해시 모양(16진 64자)의 무작위 값 */
    static String hash() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
