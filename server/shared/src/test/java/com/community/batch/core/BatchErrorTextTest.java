package com.community.batch.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/**
 * 실패 사유를 실행 로그·실패 건·워커 기록에 남길 때 가리는 규칙(docs/domain/commerce.md 15-3 「메시지(비밀값·개인정보 가림)」 — DB 쪽 batch_error_text 와 같은 규칙). DB 없이.
 */
class BatchErrorTextTest {

    @Test
    void 이메일_비밀번호_토큰_칸의_값을_가린다() {
        String text = BatchErrorText.of(new IllegalStateException("member a.b+c@mail.example.co.kr password=hunter2 Token: eyJabc api_key=xyz Key (email)=(x@y.com)"));

        assertThat(text).startsWith("IllegalStateException: ");
        assertThat(text).doesNotContain("a.b+c@mail").doesNotContain("hunter2").doesNotContain("eyJabc").doesNotContain("xyz").doesNotContain("x@y.com");
        assertThat(text).contains("(hidden)").contains("password=(hidden)").contains("api_key=(hidden)").contains("(email)=(hidden)");
    }

    @Test
    void 가장_깊은_원인의_첫_줄만_500자까지_남긴다() {
        RuntimeException wrapped = new RuntimeException("겉", new SQLException("첫 줄 원인\n  Detail: 행의 값 secret=s3cr3t"));
        assertThat(BatchErrorText.of(wrapped)).isEqualTo("SQLException: 첫 줄 원인");

        String longText = BatchErrorText.of(new IllegalArgumentException("가".repeat(800)));
        assertThat(longText).hasSize(BatchErrorText.MAX_LENGTH).endsWith("…");
    }

    @Test
    void 실패_건의_오류_코드와_문구() {
        BatchItemException item = new BatchItemException("LOT_BALANCE_MISMATCH", "회원 user@example.com 잔액 부족");
        assertThat(BatchErrorText.codeOf(item)).isEqualTo("LOT_BALANCE_MISMATCH");
        assertThat(BatchErrorText.messageOf(item)).isEqualTo("회원 (hidden) 잔액 부족");
        assertThat(BatchErrorText.codeOf(new RuntimeException(new IllegalStateException("x")))).isEqualTo("IllegalStateException");
        assertThat(BatchErrorText.codeOf(new BatchItemException("bad code!", "x"))).as("코드 규칙에 맞지 않으면 클래스 이름").isEqualTo("BatchItemException");
        assertThat(BatchErrorText.messageOf(new RuntimeException())).isEqualTo("RuntimeException");
    }
}
