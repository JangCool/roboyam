package com.community.mfa.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** base32(RFC 4648) — 비밀키 표기(대문자·채움 없음), 읽을 때 대소문자·공백·채움 무시 */
class Base32Test {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "f, MY",
            "fo, MZXQ",
            "foo, MZXW6",
            "foob, MZXW6YQ",
            "fooba, MZXW6YTB",
            "foobar, MZXW6YTBOI"
    })
    void RFC_4648_시험_벡터를_채움_없이_쓴다(String plain, String encoded) {
        assertThat(Base32.encode(plain.getBytes(StandardCharsets.US_ASCII))).isEqualTo(encoded);
        assertThat(new String(Base32.decode(encoded), StandardCharsets.US_ASCII)).isEqualTo(plain);
    }

    @Test
    void 빈_값과_20바이트_비밀키() {
        assertThat(Base32.encode(new byte[0])).isEmpty();
        assertThat(Base32.encode(new byte[20])).hasSize(32).matches("[A-Z2-7]+");
    }

    @Test
    void 읽을_때는_소문자_공백_채움_문자를_무시한다() {
        assertThat(new String(Base32.decode("mzxw 6ytb oi======"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
    }

    @Test
    void base32_가_아닌_글자는_거절하고_값을_오류에_싣지_않는다() {
        assertThatThrownBy(() -> Base32.decode("MZXW1")).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("MZXW1");
    }
}
