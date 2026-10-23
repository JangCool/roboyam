package com.community.zconfiguration.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.zconfiguration.exception.CommonException;
import java.util.Base64;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 개인정보 칸 암호화({@link DataCipher}) — 키 읽기·암복호화·용도 묶기·키 교체·키 없음. 키는 시험마다 새로 만든다(DB·스프링 없이 본다) */
class DataCipherTest {

    private static final DataPurpose EMAIL = DataPurpose.of("withdrawn.email");
    private static final DataPurpose ADDRESS = DataPurpose.of("gb.address");

    private static final String KEY_A = SecretCipher.generateKey();
    private static final String KEY_B = SecretCipher.generateKey();

    private static DataCipher cipher(String active, String previous) {
        return new DataCipher(active, previous);
    }

    @Nested
    class 암복호화 {

        private final DataCipher cipher = cipher("1:" + KEY_A, "");

        @Test
        void 풀면_원래_값이고_암호문은_지금_버전_앞머리를_단다() {
            String encrypted = cipher.encrypt(EMAIL, "member@example.com");

            assertThat(encrypted).startsWith("d1:").doesNotContain("member@example.com");
            assertThat(cipher.decrypt(EMAIL, encrypted)).isEqualTo("member@example.com");
            assertThat(cipher.isConfigured()).isTrue();
            assertThat(cipher.activeVersion()).isEqualTo(1);
        }

        @Test
        void 같은_값도_매번_다른_암호문이다() {
            assertThat(cipher.encrypt(EMAIL, "같은 값")).isNotEqualTo(cipher.encrypt(EMAIL, "같은 값"));
        }

        @Test
        void 한글_이모지_빈_글자도_그대로_돌아온다() {
            for (String plain : new String[] {"서울특별시 중구 세종대로 110 🏢", "", " "}) {
                assertThat(cipher.decrypt(ADDRESS, cipher.encrypt(ADDRESS, plain))).isEqualTo(plain);
            }
        }

        @Test
        void 다른_용도로는_풀리지_않는다() {
            String encrypted = cipher.encrypt(EMAIL, "member@example.com");

            assertThatThrownBy(() -> cipher.decrypt(ADDRESS, encrypted)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 고친_암호문은_풀리지_않는다() {
            String encrypted = cipher.encrypt(EMAIL, "member@example.com");
            byte[] raw = Base64.getDecoder().decode(encrypted.substring(3));
            raw[raw.length - 1] ^= 1;
            String tampered = "d1:" + Base64.getEncoder().encodeToString(raw);

            assertThatThrownBy(() -> cipher.decrypt(EMAIL, tampered)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 같은_버전이라도_다른_키로는_풀리지_않는다() {
            String encrypted = cipher.encrypt(EMAIL, "member@example.com");

            assertThatThrownBy(() -> cipher("1:" + KEY_B, "").decrypt(EMAIL, encrypted)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 형식이_아닌_암호문은_거절한다() {
            String secretCipherText = new SecretCipher(KEY_A).encrypt("설정 비밀값"); // v1: — 설정 비밀값 형식은 섞이지 않는다

            for (String text : new String[] {secretCipherText, "d1:", "d:abc", "dx:abc", "d1:@@@", "d1:" + Base64.getEncoder().encodeToString(new byte[12]), "", "평문"}) {
                assertThatThrownBy(() -> cipher.decrypt(EMAIL, text)).as(text).isInstanceOf(IllegalArgumentException.class);
            }
            assertThatThrownBy(() -> cipher.decrypt(EMAIL, null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 실패_문구에_평문도_키도_싣지_않는다() {
            String encrypted = cipher.encrypt(EMAIL, "member@example.com");

            assertThatThrownBy(() -> cipher.decrypt(ADDRESS, encrypted))
                    .message().doesNotContain("member@example.com").doesNotContain(KEY_A).doesNotContain(encrypted);
        }
    }

    @Nested
    class 키_교체 {

        private final DataCipher old = cipher("1:" + KEY_A, null);
        private final DataCipher rotated = cipher("2:" + KEY_B, "1:" + KEY_A);

        @Test
        void 새_키로_바꾼_뒤에도_옛_암호문을_풀고_새_암호문은_새_버전이다() {
            String before = old.encrypt(EMAIL, "member@example.com");

            assertThat(rotated.decrypt(EMAIL, before)).isEqualTo("member@example.com");
            assertThat(rotated.encrypt(EMAIL, "x")).startsWith("d2:");
            assertThat(rotated.activeVersion()).isEqualTo(2);
        }

        @Test
        void 지금_키인지_가르고_다시_암호화하면_지금_키가_된다() {
            String before = old.encrypt(EMAIL, "member@example.com");

            assertThat(rotated.isCurrent(before)).isFalse();
            String after = rotated.reencrypt(EMAIL, before);
            assertThat(after).startsWith("d2:");
            assertThat(rotated.isCurrent(after)).isTrue();
            assertThat(rotated.decrypt(EMAIL, after)).isEqualTo("member@example.com");
        }

        @Test
        void 이미_지금_키면_다시_암호화하지_않고_그대로_돌려준다() {
            String current = rotated.encrypt(EMAIL, "member@example.com");

            assertThat(rotated.reencrypt(EMAIL, current)).isSameAs(current);
        }

        @Test
        void 예전_키를_여럿_줄_수_있고_공백은_무시한다() {
            String key3 = SecretCipher.generateKey();
            String v1 = old.encrypt(EMAIL, "하나");
            String v2 = rotated.encrypt(EMAIL, "둘");
            DataCipher third = cipher(" 3:" + key3 + " ", " 2:" + KEY_B + " , 1:" + KEY_A + ",");

            assertThat(third.decrypt(EMAIL, v1)).isEqualTo("하나");
            assertThat(third.decrypt(EMAIL, v2)).isEqualTo("둘");
            assertThat(third.activeVersion()).isEqualTo(3);
        }

        @Test
        void 예전_키를_설정에서_빼면_그_버전은_503_DATA_KEY_MISSING() {
            String before = old.encrypt(EMAIL, "member@example.com");
            DataCipher withoutOld = cipher("2:" + KEY_B, "");

            assertThatThrownBy(() -> withoutOld.decrypt(EMAIL, before))
                    .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getErrorCode()).isEqualTo("DATA_KEY_MISSING"));
            assertThatThrownBy(() -> withoutOld.reencrypt(EMAIL, before)).isInstanceOf(CommonException.class);
        }

        @Test
        void 지금_키와_같은_키를_예전_키에_같은_버전으로_다시_적어도_된다() {
            assertThatCode(() -> cipher("1:" + KEY_A, "1:" + KEY_A)).doesNotThrowAnyException();
        }

        @Test
        void 형식이_아닌_암호문은_지금_키인지_묻는_것도_거절한다() {
            assertThatThrownBy(() -> rotated.isCurrent("v1:abc")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 키가_없을_때 {

        private final DataCipher none = cipher("", "");

        @Test
        void 앱은_뜨고_쓰는_요청만_503_DATA_KEY_MISSING() {
            assertThat(none.isConfigured()).isFalse();
            assertThat(none.activeVersion()).isZero();
            assertThatThrownBy(() -> none.encrypt(EMAIL, "x"))
                    .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getErrorCode()).isEqualTo("DATA_KEY_MISSING"));
            assertThatThrownBy(() -> none.decrypt(EMAIL, "d1:abc")).isInstanceOf(CommonException.class);
            assertThatThrownBy(none::requireConfigured).isInstanceOf(CommonException.class);
        }

        @Test
        void 키가_null_이어도_같다() {
            assertThat(cipher(null, null).isConfigured()).isFalse();
        }

        @Test
        void 지금_키가_없으면_어떤_암호문도_지금_키가_아니다() {
            assertThat(none.isCurrent("d1:abc")).isFalse();
        }
    }

    @Nested
    class 잘못된_설정은_기동을_멈춘다 {

        @Test
        void 예전_키만_있고_지금_키가_없다() {
            assertThatThrownBy(() -> cipher("", "1:" + KEY_A)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ROBOYAM_DATA_KEY");
        }

        @Test
        void 버전이_없거나_범위_밖이다() {
            for (String entry : new String[] {KEY_A, "0:" + KEY_A, "1000:" + KEY_A, "-1:" + KEY_A, "v1:" + KEY_A, "1:"}) {
                assertThatThrownBy(() -> cipher(entry, "")).as(entry).isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        void base64_가_아니거나_32바이트가_아니다() {
            String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

            assertThatThrownBy(() -> cipher("1:@@@@", "")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> cipher("1:" + shortKey, "")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32바이트");
            assertThatThrownBy(() -> cipher("2:" + KEY_B, "1:@@@@")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ROBOYAM_DATA_KEY_PREVIOUS");
        }

        @Test
        void 같은_버전에_서로_다른_키가_둘이다() {
            assertThatThrownBy(() -> cipher("1:" + KEY_A, "1:" + KEY_B)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("버전 1");
            assertThatThrownBy(() -> cipher("3:" + KEY_A, "2:" + KEY_A + ",2:" + KEY_B)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 실패_문구에_키_값을_싣지_않는다() {
            String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

            assertThatThrownBy(() -> cipher("1:" + shortKey, "")).message().doesNotContain(shortKey);
            assertThatThrownBy(() -> cipher("1:" + KEY_A, "1:" + KEY_B)).message().doesNotContain(KEY_A).doesNotContain(KEY_B);
        }
    }

    @Nested
    class 용도_이름 {

        @Test
        void 규칙에_맞는_이름만_받는다() {
            assertThatCode(() -> DataPurpose.of("identity.di")).doesNotThrowAnyException();
            assertThatCode(() -> DataPurpose.of("gb.address-line1")).doesNotThrowAnyException();
            for (String name : new String[] {"", "a", "Identity.di", "1abc", "identity_di", "가나", "a".repeat(61)}) {
                assertThatThrownBy(() -> DataPurpose.of(name)).as(name).isInstanceOf(IllegalArgumentException.class);
            }
            assertThatThrownBy(() -> DataPurpose.of(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
