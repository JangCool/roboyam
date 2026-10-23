package com.community.notificationtemplate.engine;

import com.community.media.service.ImageStorage;
import com.community.notificationtemplate.catalog.MailTemplateAudience;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageSeverity;
import com.community.notificationtemplate.catalog.MessageTargetType;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.service.SiteAssets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 렌더링 엔진이 읽는 사용자 문구 · 템플릿 · 대상별 선택 · 사이트 정보의 <b>짧은 메모리 사본</b> — 알림마다 표를 읽지 않게 한다(포인트 정책 사본 {@code PointPolicy} 와 같은 방식).
 *
 * <ul>
 *   <li>{@value #TTL_SECONDS}초마다 다시 읽는다 — 관리자 앱에서 저장하면 다른 서버(공개 서버 · 관리자 서버 여러 대)에는 <b>최대 그만큼 늦게</b> 닿는다.
 *       저장한 서버 안에서는 관리 API 가 {@link #invalidate()} 로 바로 비운다.</li>
 *   <li>다시 읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시 시도한다.</li>
 *   <li>JDBC 로 읽는다 — 발송 쪽이 업무 트랜잭션의 세이브포인트 안에서 부를 수 있는데, 저장소(JPA) 호출이 실패하면 업무 트랜잭션 전체를 「롤백만 가능」으로 표시해 업무까지 깨뜨린다
 *       ({@code PointPolicy} 와 같은 이유).</li>
 * </ul>
 */
@Component
public class MessageTemplateStore {

    private static final Logger log = LoggerFactory.getLogger(MessageTemplateStore.class);

    static final int TTL_SECONDS = 15;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    /** 대상 · 채널의 선택 — 행이 없으면 {@link #EMPTY} */
    public record Selection(String selectedKo, String selectedEn, List<MessageRule> rules) {

        public String selected(String locale) {
            String id = MessageLocales.EN.equals(locale) ? selectedEn : selectedKo;
            return id == null ? "" : id;
        }
    }

    public static final Selection EMPTY = new Selection("", "", List.of());

    /** 사이트 정보 — 사이트 이름(없으면 null) · 공개 주소 · 표시 기본 언어 · 표시 시간대 · 메일용 로고 주소(없으면 null) */
    public record Site(String siteName, String publicUrl, String language, ZoneId zone, String logoUrl) {
    }

    /** 한 번 읽은 사본 */
    public record Snapshot(Map<String, MessageValues> messages, Map<String, MailTemplateValues> mailTemplates, Map<String, ChatTemplateValues> chatTemplates,
                           Map<String, Selection> selections, Site site) {

        public Selection selection(MessageTargetType type, String code, MessageChannel channel) {
            return selections.getOrDefault(key(type.name(), code, channel.name()), EMPTY);
        }
    }

    private final JdbcTemplate jdbc;
    private final ImageStorage storage;
    private volatile Cached cached;

    public MessageTemplateStore(DataSource dataSource, ImageStorage storage) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.storage = storage;
    }

    public Snapshot current() {
        Cached current = cached;
        if (current != null && System.nanoTime() - current.loadedAt < TTL_NANOS) {
            return current.snapshot;
        }
        synchronized (this) {
            current = cached;
            long now = System.nanoTime();
            if (current != null && now - current.loadedAt < TTL_NANOS) {
                return current.snapshot;
            }
            try {
                Snapshot loaded = load();
                cached = new Cached(loaded, now);
                return loaded;
            } catch (RuntimeException e) {
                if (current == null) {
                    throw e;
                }
                log.warn("알림 문구·템플릿을 다시 읽지 못해 이전 값을 쓴다 원인={}", e.getClass().getSimpleName());
                cached = new Cached(current.snapshot, now - TTL_NANOS + RETRY_NANOS);
                return current.snapshot;
            }
        }
    }

    /** 사본을 버린다 — 다음 {@link #current()} 가 다시 읽는다(관리 API 가 저장한 뒤 부른다) */
    public void invalidate() {
        cached = null;
    }

    private Snapshot load() {
        Map<String, MessageValues> messages = new LinkedHashMap<>();
        jdbc.query("select id, target_type, target_code, channel, locale, name, template_id, subject, body, updated_at, updated_by from notification_messages order by id",
                (ResultSet rs) -> {
                    MessageChannel channel = MessageChannel.of(rs.getString("channel")).orElse(null);
                    MessageTargetType type = MessageTargetType.of(rs.getString("target_type")).orElse(null);
                    if (channel != null && type != null) {
                        String id = String.valueOf(rs.getLong("id"));
                        messages.put(id, new MessageValues(id, type, rs.getString("target_code"), channel, rs.getString("locale"), rs.getString("name"),
                                rs.getString("template_id"), rs.getString("subject"), rs.getString("body"), false, instant(rs, "updated_at"), longOrNull(rs, "updated_by")));
                    }
                });

        Map<String, MailTemplateValues> mail = new LinkedHashMap<>();
        jdbc.query("select id, name, audience, show_logo, show_site_name, header_color, footer_ko, footer_en, frame_ko, frame_en, updated_at, updated_by "
                + "from notification_mail_templates order by id", (ResultSet rs) -> {
                    String id = String.valueOf(rs.getLong("id"));
                    mail.put(id, new MailTemplateValues(id, rs.getString("name"), false, MailTemplateAudience.of(rs.getString("audience")).orElse(MailTemplateAudience.ANY),
                            rs.getBoolean("show_logo"), rs.getBoolean("show_site_name"), MailColor.of(rs.getString("header_color")).orElse(null),
                            Map.of(MessageLocales.KO, nz(rs.getString("footer_ko")), MessageLocales.EN, nz(rs.getString("footer_en"))),
                            Map.of(MessageLocales.KO, nz(rs.getString("frame_ko")), MessageLocales.EN, nz(rs.getString("frame_en"))), instant(rs, "updated_at"),
                            longOrNull(rs, "updated_by")));
                });

        Map<String, ChatTemplateValues> chat = new LinkedHashMap<>();
        jdbc.query("select * from notification_chat_templates order by id", (ResultSet rs) -> {
            MessageChannel tool = MessageChannel.of(rs.getString("tool")).orElse(null);
            if (tool == null || !tool.chat()) {
                return;
            }
            String id = String.valueOf(rs.getLong("id"));
            chat.put(id, new ChatTemplateValues(id, tool, rs.getString("name"), false, rs.getBoolean("show_severity_icon"), rs.getBoolean("bold_title"),
                    Map.of(MessageSeverity.INFO, ChatColor.of(rs.getString("color_info")).orElse(ChatColor.BLUE),
                            MessageSeverity.WARNING, ChatColor.of(rs.getString("color_warning")).orElse(ChatColor.AMBER),
                            MessageSeverity.CRITICAL, ChatColor.of(rs.getString("color_critical")).orElse(ChatColor.RED)),
                    rs.getBoolean("show_site_name"), rs.getBoolean("show_sent_at"), rs.getBoolean("show_open_button"),
                    Map.of(MessageLocales.KO, nz(rs.getString("open_label_ko")), MessageLocales.EN, nz(rs.getString("open_label_en"))),
                    Map.of(MessageLocales.KO, nz(rs.getString("frame_ko")), MessageLocales.EN, nz(rs.getString("frame_en"))), instant(rs, "updated_at"),
                    longOrNull(rs, "updated_by")));
        });

        Map<String, Selection> selections = new LinkedHashMap<>();
        jdbc.query("select target_type, target_code, channel, selected_ko, selected_en, rules from notification_message_selections", (ResultSet rs) -> {
            selections.put(key(rs.getString("target_type"), rs.getString("target_code"), rs.getString("channel")),
                    new Selection(nz(rs.getString("selected_ko")), nz(rs.getString("selected_en")), MessageRule.parse(rs.getString("rules"))));
        });

        return new Snapshot(Collections.unmodifiableMap(messages), Collections.unmodifiableMap(mail), Collections.unmodifiableMap(chat),
                Collections.unmodifiableMap(selections), site());
    }

    private Site site() {
        List<Site> rows = jdbc.query("select s.site_name, s.public_url, s.display_language, s.display_zone_id, s.logo_svg, f.storage_key "
                + "from site_settings s left join files f on f.id = s.logo_file_id where s.id = ?", (rs, i) -> {
                    String publicUrl = normalizeUrl(rs.getString("public_url"));
                    String logo = null;
                    if (rs.getString("logo_svg") != null) {
                        logo = SiteAssets.svgDataUri(rs.getString("logo_svg"));
                    } else if (rs.getString("storage_key") != null) {
                        logo = storage.publicUrl(rs.getString("storage_key"));
                        if (logo != null && logo.startsWith("/")) {
                            logo = publicUrl == null ? null : publicUrl + logo; // 메일 앱은 상대 주소를 못 읽는다
                        }
                    }
                    return new Site(rs.getString("site_name"), publicUrl, rs.getString("display_language"), zone(rs.getString("display_zone_id")), logo);
                }, SiteSettingEntity.ROW_ID);
        return rows.isEmpty() ? new Site(null, null, null, ZoneId.of(SiteSettingEntity.DEFAULT_ZONE_ID), null) : rows.get(0);
    }

    static String key(String type, String code, String channel) {
        return type + ":" + code + ":" + channel;
    }

    private static ZoneId zone(String id) {
        try {
            return id == null || id.isBlank() ? ZoneId.of(SiteSettingEntity.DEFAULT_ZONE_ID) : ZoneId.of(id);
        } catch (RuntimeException e) {
            return ZoneId.of(SiteSettingEntity.DEFAULT_ZONE_ID);
        }
    }

    /** {@code http(s)://} 로 시작하지 않으면 없는 것으로 본다. 끝의 {@code /} 는 뗀다 */
    static String normalizeUrl(String value) {
        String url = value == null ? "" : value.strip();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp at = rs.getTimestamp(column);
        return at == null ? null : at.toInstant();
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record Cached(Snapshot snapshot, long loadedAt) {
    }
}
