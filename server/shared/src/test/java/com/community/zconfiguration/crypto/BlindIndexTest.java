package com.community.zconfiguration.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.zconfiguration.exception.CommonException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** 중복 확인 해시({@link BlindIndex}) — 같은 값·같은 용도면 같은 해시, 용도·키가 다르면 다른 해시, 키 없음·잘못된 키 */
class BlindIndexTest {

    private static final DataPurpose DI = DataPurpose.of("identity.di");
    private static final DataPurpose CI = DataPurpose.of("identity.ci");

    private static final String KEY = SecretCipher.generateKey();

    private final BlindIndex index = new BlindIndex(KEY);

    @Test
    void 같은_값_같은_용도는_늘_같은_16진_64자다() {
        String first = index.hash(DI, "di-value-1");

        assertThat(first).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(index.hash(DI, "di-value-1"));
        assertThat(new BlindIndex(KEY).hash(DI, "di-value-1")).isEqualTo(first); // 서버를 다시 띄워도(같은 키) 같다
        assertThat(index.isConfigured()).isTrue();
    }

    @Test
    void 값이나_용도가_다르면_해시가_다르다() {
        assertThat(index.hash(DI, "value")).isNotEqualTo(index.hash(DI, "value2"));
        assertThat(index.hash(DI, "value")).isNotEqualTo(index.hash(CI, "value"));
    }

    @Test
    void 키가_다르면_해시가_다르다() {
        assertThat(new BlindIndex(SecretCipher.generateKey()).hash(DI, "value")).isNotEqualTo(index.hash(DI, "value"));
    }

    @Test
    void 정규화는_부르는_쪽이_한다_대소문자가_다르면_다른_해시다() {
        assertThat(index.hash(DI, "Member@Example.com")).isNotEqualTo(index.hash(DI, "member@example.com"));
    }

    @Test
    void 용도와_값의_경계가_섞이지_않는다() {
        // 「용도 ‖ NUL ‖ 값」 이라 용도 이름 끝과 값 앞을 옮겨도 같은 입력이 되지 않는다
        assertThat(index.hash(DataPurpose.of("abc"), "d")).isNotEqualTo(index.hash(DataPurpose.of("ab"), "cd"));
    }

    @Test
    void HMAC_SHA256_용도_NUL_값_그대로다() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(KEY), "HmacSHA256"));
        byte[] expected = mac.doFinal("identity.di\0값 123".getBytes(StandardCharsets.UTF_8));

        assertThat(index.hash(DI, "값 123")).isEqualTo(HexFormat.of().formatHex(expected));
    }

    @Test
    void 키가_없으면_앱은_뜨고_해시만_503_INDEX_KEY_MISSING() {
        for (String key : new String[] {"", " ", null}) {
            BlindIndex none = new BlindIndex(key);

            assertThat(none.isConfigured()).isFalse();
            assertThatThrownBy(() -> none.hash(DI, "value"))
                    .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getErrorCode()).isEqualTo("INDEX_KEY_MISSING"));
            assertThatThrownBy(none::requireConfigured).isInstanceOf(CommonException.class);
        }
    }

    @Test
    void 잘못된_키는_기동을_멈추고_문구에_키를_싣지_않는다() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new BlindIndex("@@@@")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ROBOYAM_INDEX_KEY");
        assertThatThrownBy(() -> new BlindIndex(shortKey)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32바이트")
                .message().doesNotContain(shortKey);
        assertThatThrownBy(() -> new BlindIndex("1:" + KEY)).isInstanceOf(IllegalArgumentException.class); // 데이터 키 형식(버전 붙임)은 받지 않는다
    }
}
