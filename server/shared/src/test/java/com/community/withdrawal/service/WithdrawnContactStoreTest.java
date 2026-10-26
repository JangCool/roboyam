package com.community.withdrawal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditEvent;
import com.community.audit.service.AuditLogService;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.repository.master.WithdrawnContactRows;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 탈퇴 회원 연락처(commerce.md 17-2) — 값은 감사를 <b>먼저</b> 남긴 뒤에만 풀고, 감사를 못 남기면 풀지 않는다. 일반 회원 조회는 이 표를 읽지 않는다(논리적 분리).
 * 차례를 보려고 감사·복호화를 흉내 낸 객체로 바꾼다(실제 DB·같은 트랜잭션 되돌림은 api 의 {@code WithdrawalPreparationTest}).
 */
class WithdrawnContactStoreTest {

    /** SQL 에서 그 표를 읽거나 쓰는 자리(설명 주석은 빼고), 또는 SQL 을 이어 붙이려고 표 이름을 문자열로 든 자리(데이터 키 교체 배치의 칸 목록) */
    private static final java.util.regex.Pattern SQL_USE = java.util.regex.Pattern.compile("(?i)\\b(from|into|join|update|table)\\s+withdrawn_member_contacts\\b|\"withdrawn_member_contacts\"");

    private static final String DATA_KEY ="1:Nkfi1vUhkXnb6dM1b8wzSxEbPhDjKWCqih4IwYR9o34=";

    private final List<String> calls = new ArrayList<>();
    private final List<AuditEvent> audited = new ArrayList<>();
    private boolean auditFails;

    private final WithdrawnContactRows rows = new WithdrawnContactRows(new DriverManagerDataSource()) {
        @Override
        public Optional<Row> find(long userId) {
            return Optional.of(new Row(userId, "d1:cipher", Instant.now(), Instant.now()));
        }
    };

    private final DataCipher cipher = new DataCipher(DATA_KEY, "") {
        @Override
        public String decrypt(DataPurpose purpose, String cipherText) {
            calls.add("decrypt " + purpose.name() + " " + cipherText);
            return "who@test.local";
        }
    };

    private final AuditLogService audit = new AuditLogService(null, null) {
        @Override
        public void record(AuditEvent event) {
            calls.add("audit");
            if (auditFails) {
                throw new IllegalStateException("audit down");
            }
            audited.add(event);
        }
    };

    private final WithdrawnContactStore store = new WithdrawnContactStore(rows, cipher, audit, new WithdrawalProperties(30, 5),
            new StaticListableBeanFactory().getBeanProvider(Clock.class));

    @Test
    void 감사를_남긴_뒤에_복호화한다() {
        assertThat(store.reveal(7L, "분쟁 문의 연락", 1L, "127.0.0.1").email()).isEqualTo("who@test.local");

        assertThat(calls).containsExactly("audit", "decrypt member.withdrawn-contact.email d1:cipher");
        AuditEvent event = audited.get(0);
        assertThat(event.detail()).containsEntry("reason", "분쟁 문의 연락").doesNotContainValue("who@test.local");
        assertThat(event.targetType()).isEqualTo("MEMBER");
        assertThat(event.targetId()).isEqualTo("7");
        assertThat(event.actorId()).isEqualTo(1L);
    }

    @Test
    void 감사를_못_남기면_값을_풀지_않는다() {
        auditFails = true;
        assertThatThrownBy(() -> store.reveal(7L, "분쟁 문의 연락", 1L, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
        assertThat(calls).containsExactly("audit");
    }

    @Test
    void 일반_회원_조회는_분리_보관_표를_읽지_않는다() throws IOException {
        Path server = Paths.get("").toAbsolutePath().getParent(); // 시험 작업 폴더 = server/shared
        List<Path> users;
        try (Stream<Path> files = Files.walk(server)) {
            users = files.filter(path -> path.toString().endsWith(".java") && path.toString().contains("src" + java.io.File.separator + "main"))
                    .filter(path -> SQL_USE.matcher(read(path)).find()).toList();
        }
        assertThat(users).extracting(path -> path.getFileName().toString()).containsExactlyInAnyOrder("WithdrawnContactRows.java", "DataKeyColumns.java"); // 저장소 · 데이터 키 교체 배치(칸 목록 — DataKeyRotationJob 이 SQL 을 만든다)
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
