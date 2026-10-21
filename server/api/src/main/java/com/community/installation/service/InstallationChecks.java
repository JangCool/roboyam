package com.community.installation.service;

import com.community.installation.dto.response.InstallCheckItem;
import com.community.installation.dto.response.InstallChecksResponse;
import com.community.installation.repository.InstallationEnvironmentRepository;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.message.Messages;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 설치가 돌아갈 수 있는지 <b>판정</b>한다(입력이 아니다) — 설치 마법사 1단계의 점검 결과.
 *
 * <ul>
 *   <li>{@code DATABASE} 데이터베이스 연결 · {@code MIGRATIONS} 데이터 구조(마이그레이션, shared 스트림) · {@code EXTENSIONS} 검색 확장({@code pg_trgm}) — 실패하면 설치를 막는다.</li>
 *   <li>{@code CRYPTO_KEY} 암호화 키 — 없어도 설치는 할 수 있다({@code blocking: false}). 다만 메일 비밀번호·소셜 시크릿은 저장할 수 없어 마법사가 그 단계를 잠근다.</li>
 *   <li>{@code DB_LOCALE} DB 로캘이 한글 trigram 색인을 지원하는가 — 경고({@code blocking: false}).</li>
 * </ul>
 * 항목 순서가 계약이다. <b>접속 문자열·사용자 이름·경로 같은 내부 정보를 응답에 담지 않는다</b>({@code detail} 은 사람이 읽을 요약). 실패 원인은 로그에만 남긴다.
 * 트랜잭션을 열지 않는다 — 점검은 JDBC 로 직접 읽는다.
 *
 * <p>{@code label}·{@code detail} 은 메시지 번들({@code install.check.*})에서 요청 언어(Accept-Language)로 푼 문구다. {@code key}·{@code ok}·{@code blocking} 은 언어와 무관하다.
 * 데이터베이스 버전처럼 서버가 읽은 값은 번역하지 않고 그대로 싣는다.
 */
@Service
public class InstallationChecks {

    private static final Logger log = LoggerFactory.getLogger(InstallationChecks.class);

    private final InstallationEnvironmentRepository environment;
    private final SecretEncryptor encryptor;
    private final Messages messages;

    public InstallationChecks(InstallationEnvironmentRepository environment, SecretEncryptor encryptor, Messages messages) {
        this.environment = environment;
        this.encryptor = encryptor;
        this.messages = messages;
    }

    public InstallChecksResponse run() {
        List<InstallCheckItem> checks = new ArrayList<>();
        boolean databaseUp = checkDatabase(checks);
        checkMigrations(checks, databaseUp);
        checkExtensions(checks, databaseUp);
        checkCryptoKey(checks);
        checkDatabaseLocale(checks, databaseUp);
        boolean ok = checks.stream().filter(InstallCheckItem::blocking).allMatch(InstallCheckItem::ok);
        return new InstallChecksResponse(ok, checks);
    }

    private String text(String key, Object... args) {
        return messages.get(key, args);
    }

    private boolean checkDatabase(List<InstallCheckItem> checks) {
        try {
            checks.add(new InstallCheckItem("DATABASE", true, text("install.check.database.label"), environment.databaseVersion(), true));
            return true;
        } catch (Exception e) {
            log.warn("설치 점검: 데이터베이스에 연결하지 못했다 cause={}", e.getClass().getSimpleName());
            checks.add(new InstallCheckItem("DATABASE", false, text("install.check.database.label"), text("install.check.database.down"), true));
            return false;
        }
    }

    private void checkMigrations(List<InstallCheckItem> checks, boolean databaseUp) {
        String label = text("install.check.migrations.label");
        if (!databaseUp) {
            checks.add(new InstallCheckItem("MIGRATIONS", false, label, text("install.check.noDatabase"), true));
            return;
        }
        try {
            if (environment.hasPendingMigrations()) {
                checks.add(new InstallCheckItem("MIGRATIONS", false, label, text("install.check.migrations.pending"), true));
            } else {
                var version = environment.currentMigrationVersion();
                checks.add(new InstallCheckItem("MIGRATIONS", version.isPresent(), label,
                        version.map(v -> text("install.check.migrations.applied", v)).orElse(text("install.check.migrations.none")), true));
            }
        } catch (RuntimeException e) {
            log.warn("설치 점검: 데이터 구조 버전을 확인하지 못했다 cause={}", e.getClass().getSimpleName());
            checks.add(new InstallCheckItem("MIGRATIONS", false, label, text("install.check.migrations.unknown"), true));
        }
    }

    private void checkExtensions(List<InstallCheckItem> checks, boolean databaseUp) {
        String label = text("install.check.extensions.label");
        if (!databaseUp) {
            checks.add(new InstallCheckItem("EXTENSIONS", false, label, text("install.check.noDatabase"), true));
            return;
        }
        try {
            if (environment.trigramWorks()) {
                checks.add(new InstallCheckItem("EXTENSIONS", true, label, text("install.check.extensions.ok"), true));
            } else {
                checks.add(new InstallCheckItem("EXTENSIONS", false, label, text("install.check.extensions.broken"), true));
            }
        } catch (Exception e) {
            log.warn("설치 점검: 검색 확장(pg_trgm)을 쓸 수 없다 cause={}", e.getClass().getSimpleName());
            checks.add(new InstallCheckItem("EXTENSIONS", false, label, text("install.check.extensions.unusable"), true));
        }
    }

    private void checkCryptoKey(List<InstallCheckItem> checks) {
        if (encryptor.isConfigured()) {
            checks.add(new InstallCheckItem("CRYPTO_KEY", true, text("install.check.cryptoKey.label"), text("install.check.cryptoKey.ok"), false));
        } else {
            checks.add(new InstallCheckItem("CRYPTO_KEY", false, text("install.check.cryptoKey.label"), text("install.check.cryptoKey.missing"), false));
        }
    }

    /**
     * DB 로캘이 한글 검색 색인을 지원하는가 — <b>경고일 뿐 설치를 막지 않는다</b>({@code blocking: false}). {@code C} 로캘이어도 한글 검색은 동작하지만 색인을 못 타 느리다.
     * DB 의 로캘은 만든 뒤 바꿀 수 없어 설치를 막아도 마법사 안에서 고칠 수 없으므로, 알리고 운영자가 정하게 한다.
     */
    private void checkDatabaseLocale(List<InstallCheckItem> checks, boolean databaseUp) {
        String label = text("install.check.dbLocale.label");
        if (!databaseUp) {
            checks.add(new InstallCheckItem("DB_LOCALE", false, label, text("install.check.noDatabase"), false));
            return;
        }
        try {
            if (environment.koreanTrigramWorks()) {
                checks.add(new InstallCheckItem("DB_LOCALE", true, label, text("install.check.dbLocale.ok"), false));
            } else {
                checks.add(new InstallCheckItem("DB_LOCALE", false, label,
                        text("install.check.dbLocale.c"), false));
            }
        } catch (Exception e) {
            log.warn("설치 점검: DB 로캘을 확인하지 못했다 cause={}", e.getClass().getSimpleName());
            checks.add(new InstallCheckItem("DB_LOCALE", false, label, text("install.check.dbLocale.unknown"), false));
        }
    }
}
