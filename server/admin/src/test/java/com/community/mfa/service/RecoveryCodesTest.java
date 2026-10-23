package com.community.mfa.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 복구 코드 — 10개, XXXXX-XXXXX(혼동 글자 뺀 대문자·숫자), 대소문자·하이픈 무시, SHA-256(운영자 ID ‖ 코드)만 저장 */
class RecoveryCodesTest {

    @Test
    void 열_개를_서로_다르게_만든다() {
        List<String> codes = RecoveryCodes.generate();

        assertThat(codes).hasSize(10);
        assertThat(new HashSet<>(codes)).hasSize(10);
    }

    @Test
    void 형식은_다섯_글자_하이픈_다섯_글자이고_헷갈리는_글자가_없다() {
        for (int round = 0; round < 50; round++) {
            for (String code : RecoveryCodes.generate()) {
                assertThat(code).matches("^[A-Z2-9]{5}-[A-Z2-9]{5}$").doesNotContain("0", "O", "1", "I");
            }
        }
        assertThat(RecoveryCodes.ALPHABET).hasSize(32).doesNotContain("0", "O", "1", "I");
    }

    @Test
    void 받을_때는_대소문자_하이픈_공백을_무시한다() {
        assertThat(RecoveryCodes.normalize("abcde-fghjk")).contains("ABCDEFGHJK");
        assertThat(RecoveryCodes.normalize(" ABCDE FGHJK ")).contains("ABCDEFGHJK");
        assertThat(RecoveryCodes.normalize("ABCDEFGHJK")).contains("ABCDEFGHJK");
        assertThat(RecoveryCodes.normalize("ab-cd-efghjk")).contains("ABCDEFGHJK");
    }

    @Test
    void 형식이_아니면_비어_있다() {
        assertThat(RecoveryCodes.normalize(null)).isEmpty();
        assertThat(RecoveryCodes.normalize("ABCDE-FGHJ")).isEmpty(); // 9자
        assertThat(RecoveryCodes.normalize("ABCDE-FGHJKL")).isEmpty(); // 11자
        assertThat(RecoveryCodes.normalize("ABCDE-FGH0K")).isEmpty(); // 0 은 쓰지 않는 글자
        assertThat(RecoveryCodes.normalize("ABCDE-FGH!K")).isEmpty();
    }

    @Test
    void 해시는_운영자마다_다르고_원문이_들어_있지_않다() {
        String a = RecoveryCodes.hash(1L, "ABCDEFGHJK");
        String b = RecoveryCodes.hash(2L, "ABCDEFGHJK");

        assertThat(a).hasSize(64).isNotEqualTo(b).doesNotContain("ABCDEFGHJK");
        assertThat(RecoveryCodes.hash(1L, RecoveryCodes.normalize("abcde-fghjk").orElseThrow())).isEqualTo(a);
    }
}
