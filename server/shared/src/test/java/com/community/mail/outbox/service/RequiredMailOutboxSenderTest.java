package com.community.mail.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.mail.outbox.dto.OutboxDelivery;
import com.community.mail.service.MailDispatcher;
import com.community.membernotification.repository.master.MemberNotificationMailQueries;
import com.community.media.service.ImageStorage;
import com.community.notificationtemplate.builtin.BuiltinMessages;
import com.community.notificationtemplate.builtin.BuiltinTemplates;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.notificationtemplate.engine.MessageRenderRequest;
import com.community.notificationtemplate.engine.MessageTemplateStore;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedEmail;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 대기열 메일의 기본 발송({@link RequiredMailOutboxSender}) — 받는 사람 규칙과 문구 해석(결정 11: 알림 문구 엔진의 필수 안내 메일과 같은 해석). 실제 PostgreSQL 의 회원 행 +
 * 실제 문구 엔진(기본 문구·템플릿), 메일 서버만 가짜(보낸 것을 모은다).
 */
class RequiredMailOutboxSenderTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(741);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static NotificationMessageRenderer renderer;

    private FakeDispatcher dispatcher;
    private RequiredMailOutboxSender sender;

    /** 보낸 메일을 모으는 가짜 메일 서버 — {@code configured=false} 면 설정이 없는 것처럼 false */
    static final class FakeDispatcher extends MailDispatcher {
        final List<String[]> sent = new ArrayList<>();
        boolean configured = true;

        FakeDispatcher() {
            super(null, null, null);
        }

        @Override
        public boolean sendTagged(String to, String taggedSubject, String untaggedSubject, String html) {
            if (!configured) {
                return false;
            }
            sent.add(new String[] {to, taggedSubject, untaggedSubject, html});
            return true;
        }
    }

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Locale.KOREAN);
        Messages messages = new Messages(source, new StandardEnvironment(), "messages/messages");
        ImageStorage storage = new ImageStorage() {
            @Override
            public void put(String key, byte[] data) {
            }

            @Override
            public void delete(String key) {
            }

            @Override
            public String publicUrl(String key) {
                return "/media/" + key;
            }
        };
        renderer = new NotificationMessageRenderer(new MessageTemplateStore(dataSource, storage), new BuiltinMessages(messages), new BuiltinTemplates(messages), messages, "");
    }

    @BeforeEach
    void setUp() {
        dispatcher = new FakeDispatcher();
        sender = new RequiredMailOutboxSender(new MemberNotificationMailQueries(dataSource), renderer, dispatcher);
    }

    /** 회원 한 명 — 상태·이메일·인증 여부·표시 언어 */
    private long member(String status, String email, boolean verified, String language) {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO users (id, email, status, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, ?, ?, 'v1', now(), now())",
                id, email, status, verified ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", id, "ob" + (id % 1_000_000_000L));
        if (language != null) {
            jdbc.update("INSERT INTO member_display_preferences (user_id, language, zone_id, time_format, date_format) VALUES (?, ?, 'Asia/Seoul', 'H24', 'YMD_DASH')",
                    id, language);
        }
        return id;
    }

    private static String email(long seed) {
        return "kim" + seed + "@example.com";
    }

    @Test
    void 인증한_회원에게_필수_안내_메일과_같은_문구로_보내고_가린_주소를_돌려준다() {
        long seed = System.nanoTime() % 1_000_000_000L;
        long user = member("ACTIVE", email(seed), true, null);
        OutboxDelivery result = sender.send(user, RequiredMail.MAIL_TEST.code(), Map.of("operatorName", "운영자"));

        assertThat(result.outcome()).isEqualTo(OutboxDelivery.Outcome.SENT);
        assertThat(result.toMasked()).isEqualTo("ki***@example.com").doesNotContain(String.valueOf(seed));
        assertThat(dispatcher.sent).hasSize(1);
        String[] mail = dispatcher.sent.get(0);
        assertThat(mail[0]).isEqualTo(email(seed));

        String nickname = jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, user);
        RenderedEmail expected = renderer.renderEmail(MessageRenderRequest.requiredMail(RequiredMail.MAIL_TEST).locale(Locale.KOREAN)
                .variables(Map.of("operatorName", "운영자")).variable("recipientName", nickname).build());
        assertThat(mail[1]).isEqualTo(expected.subject());
        assertThat(mail[2]).isEqualTo(expected.untaggedSubject());
        assertThat(mail[3]).isEqualTo(expected.html());
    }

    @Test
    void 회원_표시_언어로_그린다() {
        long user = member("ACTIVE", email(System.nanoTime() % 1_000_000_000L), true, "en");
        sender.send(user, RequiredMail.MAIL_TEST.code(), Map.of());
        String nickname = jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, user);
        RenderedEmail english = renderer.renderEmail(MessageRenderRequest.requiredMail(RequiredMail.MAIL_TEST).locale(Locale.ENGLISH).variable("recipientName", nickname).build());
        assertThat(dispatcher.sent.get(0)[1]).isEqualTo(english.subject());
    }

    @Test
    void 정지_회원도_돈_관련_안내는_받는다() {
        long user = member("SUSPENDED", email(System.nanoTime() % 1_000_000_000L), true, null);
        assertThat(sender.send(user, RequiredMail.MAIL_TEST.code(), Map.of()).outcome()).isEqualTo(OutboxDelivery.Outcome.SENT);
    }

    @Test
    void 받을_수_없는_회원이면_보내지_않고_CANCELED() {
        assertThat(sender.send(member("ACTIVE", email(System.nanoTime() % 1_000_000_000L), false, null), "MAIL_TEST", Map.of()))
                .isEqualTo(OutboxDelivery.canceled("EMAIL_NOT_VERIFIED"));
        assertThat(sender.send(member("WITHDRAWN", null, true, null), "MAIL_TEST", Map.of())).isEqualTo(OutboxDelivery.canceled("WITHDRAWN"));
        assertThat(sender.send(member("ACTIVE", null, true, null), "MAIL_TEST", Map.of())).isEqualTo(OutboxDelivery.canceled("NO_EMAIL"));
        assertThat(sender.send(IDS.nextId(), "MAIL_TEST", Map.of())).isEqualTo(OutboxDelivery.canceled("NO_RECIPIENT"));
        assertThat(sender.send(IDS.nextId(), "NO_SUCH_KIND", Map.of())).isEqualTo(OutboxDelivery.canceled("UNKNOWN_KIND"));
        assertThat(dispatcher.sent).isEmpty();
    }

    @Test
    void 메일_서버가_설정돼_있지_않으면_RETRY() {
        dispatcher.configured = false;
        long user = member("ACTIVE", email(System.nanoTime() % 1_000_000_000L), true, null);
        assertThat(sender.send(user, RequiredMail.MAIL_TEST.code(), Map.of())).isEqualTo(OutboxDelivery.retry("MAIL_NOT_CONFIGURED"));
    }

    @Test
    void 주소_가리기는_앞_두_글자() {
        assertThat(RequiredMailOutboxSender.mask("kim123@gmail.com")).isEqualTo("ki***@gmail.com");
        assertThat(RequiredMailOutboxSender.mask("ab@x.io")).isEqualTo("a***@x.io");
        assertThat(RequiredMailOutboxSender.mask("noat")).isEqualTo("***");
        assertThat(RequiredMailOutboxSender.mask(null)).isNull();
    }
}
