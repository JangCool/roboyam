package com.community.terms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.mail.outbox.repository.master.MailOutboxStore;
import com.community.terms.entity.TermsKind;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.Messages;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 불리한 약관 변경 메일을 커머스 메일 대기열에 나눠 넣기(terms-operator-contract) — 받는 회원(탈퇴·삭제·미인증 제외)·같은 판 중복 없음·1분에 {@value TermsChangeMails#PER_MINUTE}통.
 * 실제 PostgreSQL(메일 대기열은 커머스 기반 — commerce.md 18절). DB 를 다른 시험과 같이 쓰므로 판 번호를 시험마다 새로 잡고 끝나면 그 줄만 지운다.
 */
class TermsChangeMailsTest {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TermsChangeMails mails;
    private static TransactionTemplate tx;
    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(747);

    private int version;
    private final List<Long> users = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Locale.KOREAN);
        mails = new TermsChangeMails(new MailOutboxStore(dataSource), IDS, new Messages(source, new StandardEnvironment(), "messages/messages"), dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mail_outbox WHERE dedupe_key LIKE ?", "TERMS_CHANGE:OPERATION_POLICY:" + version + ":%");
        for (long id : users) {
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private long member(String status, boolean verified, boolean deleted) {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO users (id, email, status, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at, deleted_at) "
                + "VALUES (?, ?, ?, " + (verified ? "now()" : "NULL") + ", 'v1', now(), now(), " + (deleted ? "now()" : "NULL") + ")", id, "t" + id + "@test.local", status);
        users.add(id);
        return id;
    }

    private int enqueue() {
        return tx.execute(s -> mails.enqueue(TermsKind.OPERATION_POLICY, version, LocalDate.of(2026, 12, 7), null));
    }

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList("SELECT user_id, mail_kind, status, variables::text AS variables, next_attempt_at FROM mail_outbox WHERE dedupe_key LIKE ? ORDER BY next_attempt_at",
                "TERMS_CHANGE:OPERATION_POLICY:" + version + ":%");
    }

    @Test
    void 받는_회원은_탈퇴_삭제_미인증을_뺀_회원_같은_판은_두_번_넣지_않는다() {
        version = 900_000 + ThreadLocalRandom.current().nextInt(99_999);
        long ok = member("ACTIVE", true, false);
        long suspended = member("SUSPENDED", true, false);
        long unverified = member("ACTIVE", false, false);
        long withdrawn = member("WITHDRAWN", true, false);
        long deleted = member("ACTIVE", true, true);

        enqueue();
        List<Long> got = rows().stream().map(r -> ((Number) r.get("user_id")).longValue()).toList();
        assertThat(got).contains(ok, suspended).doesNotContain(unverified, withdrawn, deleted);
        Map<String, Object> mine = rows().stream().filter(r -> ((Number) r.get("user_id")).longValue() == ok).findFirst().orElseThrow();
        assertThat(mine.get("mail_kind")).isEqualTo("MEMBER_TERMS_CHANGE");
        assertThat(mine.get("status")).isEqualTo("PENDING");
        assertThat((String) mine.get("variables")).contains("운영정책").contains("2026-12-07").contains("/policy?version=" + version).contains("링크에서 확인");

        int before = rows().size();
        enqueue();
        assertThat(rows()).as("같은 종류·판·회원은 한 통").hasSize(before);
    }

    @Test
    void 보낼_때를_1분에_정한_통_수씩_미뤄_다른_대기열_메일이_밀리지_않게_한다() {
        version = 900_000 + ThreadLocalRandom.current().nextInt(99_999);
        for (int i = 0; i < TermsChangeMails.PER_MINUTE * 2 + 5; i++) {
            member("ACTIVE", true, false);
        }
        enqueue();
        Map<Object, Long> perSlot = rows().stream().collect(java.util.stream.Collectors.groupingBy(r -> r.get("next_attempt_at"), java.util.stream.Collectors.counting()));
        assertThat(perSlot.values()).allSatisfy(n -> assertThat(n).isLessThanOrEqualTo(TermsChangeMails.PER_MINUTE));
        assertThat(perSlot).as("45통 이상이면 적어도 세 번에 나뉜다").hasSizeGreaterThanOrEqualTo(3);
    }
}
