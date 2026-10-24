package com.community.membernotification.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardNotificationSettingEntity;
import com.community.board.repository.master.BoardNotificationSettingRepository;
import com.community.board.service.BoardCatalog;
import com.community.board.service.RichText;
import com.community.comment.entity.CommentEntity;
import com.community.membernotification.dto.response.MemberNotificationItemView;
import com.community.membernotification.dto.response.MemberNotificationPageView;
import com.community.membernotification.dto.response.NotificationActorView;
import com.community.membernotification.dto.response.NotificationPointsView;
import com.community.membernotification.dto.response.NotificationPostRefView;
import com.community.membernotification.dto.response.ReadAllResultView;
import com.community.membernotification.entity.MemberNotificationEntity;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.exception.MemberNotificationNotFoundException;
import com.community.membernotification.repository.master.MemberNotificationRepositoryQDSL;
import com.community.notificationtemplate.engine.RenderedInApp;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 알림 — <b>로그인한 회원 본인 것만</b>(경로에 회원 ID 가 없고 모든 질의에 내 ID 가 들어간다). docs/features/member-notification-contract.md.
 *
 * <ul>
 *   <li><b>목록</b> {@code filter}({@code all} 기본 · {@code comment} · {@code system} · {@code dm}(쪽지)) · {@code size}(기본 {@value #DEFAULT_SIZE}, 1~{@value #MAX_SIZE}) · {@code cursor}(앞 응답의 {@code nextCursor}).
 *       최신이 위(ID 내림차순). 글·댓글·일으킨 사람은 <b>읽을 때의 현재 값</b>으로 채운다 — 지웠거나 숨긴 글은 제목을 내지 않고 {@code available=false}.</li>
 *   <li><b>문구</b>: 저장된 종류 + 칸(읽을 때의 현재 값)을 변수로 알림 문구 엔진이 읽는 사람의 요청 언어로 그린다({@code MemberNotificationMessages} — 그 회원 이벤트 앱 알림의
 *       쓸 문구·규칙, 없으면 기본 문구). 운영자가 문구를 바꾸면 이미 쌓인 알림도 새 문구로 보인다.</li>
 *   <li><b>안 읽은 수</b>는 거르기와 상관없이 전체, {@value #UNREAD_CAP} 까지만 센다.</li>
 *   <li><b>읽음</b>: 이미 읽은 알림도 성공(멱등). 남의 알림·없는 알림·형식이 틀린 ID 는 {@code 404 NOTIFICATION_NOT_FOUND}.</li>
 * </ul>
 */
@Service
public class MemberNotificationService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final int UNREAD_CAP = 100;
    /** 댓글 미리보기 글자 수 */
    static final int PREVIEW_CHARS = 60;
    /** 보관 일수 기본값(설정 행이 없을 때) */
    static final int DEFAULT_RETENTION_DAYS = 90;

    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,19}$");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Map<String, List<String>> FILTERS = Map.of(
            "comment", typesOf("comment"),
            "system", typesOf("system"),
            "dm", typesOf("dm"));

    private final MemberNotificationRepositoryQDSL notifications;
    private final PostRepository posts;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final BoardCatalog catalog;
    private final BoardNotificationSettingRepository boardNotifySettings;
    private final Messages messages;
    private final MemberNotificationMessages texts;

    public MemberNotificationService(MemberNotificationRepositoryQDSL notifications, PostRepository posts, ProfileRepository profiles, UserRepository users,
                                     BoardCatalog catalog, BoardNotificationSettingRepository boardNotifySettings, Messages messages,
                                     MemberNotificationMessages texts) {
        this.notifications = notifications;
        this.posts = posts;
        this.profiles = profiles;
        this.users = users;
        this.catalog = catalog;
        this.boardNotifySettings = boardNotifySettings;
        this.messages = messages;
        this.texts = texts;
    }

    /** 내 알림 한 쪽. 틀린 조건은 400 {@code fields.filter|size|cursor} */
    @Transactional(readOnly = true)
    public MemberNotificationPageView list(long userId, String filter, Integer size, String cursor) {
        List<String> types = typesFor(filter);
        int limit = size == null ? DEFAULT_SIZE : size;
        if (limit < 1 || limit > MAX_SIZE) {
            throw ValidationException.of("size", new MessageRef("notification.size.range", String.valueOf(MAX_SIZE)));
        }
        Long beforeId = cursorOf(cursor);

        List<MemberNotificationEntity> rows = notifications.page(userId, types, beforeId, limit + 1);
        boolean more = rows.size() > limit;
        List<MemberNotificationEntity> pageRows = more ? rows.subList(0, limit) : rows;
        int unread = notifications.unread(userId, UNREAD_CAP); // DB 읽기를 먼저 끝낸다 — 문구 그리기는 엔진의 메모리 사본만 쓴다
        int retention = retentionDays();
        List<MemberNotificationItemView> items = items(userId, pageRows);
        String nextCursor = more ? items.get(items.size() - 1).id() : null;
        return new MemberNotificationPageView(items, more, nextCursor, unread, retention);
    }

    @Transactional(readOnly = true)
    public int unreadCount(long userId) {
        return notifications.unread(userId, UNREAD_CAP);
    }

    @Transactional
    public void markRead(long userId, String idText) {
        if (idText == null || !DIGITS.matcher(idText).matches()) {
            throw new MemberNotificationNotFoundException();
        }
        long id;
        try {
            id = Long.parseLong(idText);
        } catch (NumberFormatException e) {
            throw new MemberNotificationNotFoundException();
        }
        if (!notifications.exists(userId, id)) {
            throw new MemberNotificationNotFoundException();
        }
        notifications.markRead(userId, id, Instant.now());
    }

    /** 모두 읽음 — {@code filter} 를 주면 그 거르기의 알림만(생략·{@code all} 이면 전부) */
    @Transactional
    public ReadAllResultView markAllRead(long userId, String filter) {
        return new ReadAllResultView(notifications.markAllRead(userId, typesFor(filter), Instant.now()));
    }

    // ── 한 건 만들기 ────────────────────────────────────────────────────────

    /** 알림 한 쪽 — 가리키는 글·댓글·일으킨 회원을 한 번에 읽어 채운다 */
    private List<MemberNotificationItemView> items(long userId, List<MemberNotificationEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        String recipientName = profiles.findById(userId).map(ProfileEntity::getNickname).orElse(null); // 문구 변수 recipientName
        Instant now = Instant.now();
        Set<Long> postIds = rows.stream().map(MemberNotificationEntity::getPostId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> commentIds = rows.stream().map(MemberNotificationEntity::getCommentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> actorIds = rows.stream().map(MemberNotificationEntity::getActorUserId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, PostEntity> postById = new HashMap<>();
        posts.findAllById(postIds).forEach(p -> postById.put(p.getId(), p));
        Map<Long, CommentEntity> commentById = new HashMap<>();
        notifications.comments(postIds, commentIds).forEach(c -> commentById.put(c.getId(), c));
        Map<Long, ProfileEntity> profileById = new HashMap<>();
        profiles.findAllById(actorIds).forEach(p -> profileById.put(p.getUserId(), p));
        Map<Long, UserEntity> userById = new HashMap<>();
        users.findAllById(actorIds).forEach(u -> userById.put(u.getId(), u));

        BoardCatalogSnapshot snapshot = catalog.snapshot();
        Locale locale = messages.locale();
        List<MemberNotificationItemView> out = new ArrayList<>();
        for (MemberNotificationEntity row : rows) {
            out.add(item(row, postById, commentById, profileById, userById, snapshot, now, locale, recipientName));
        }
        return out;
    }

    private MemberNotificationItemView item(MemberNotificationEntity row, Map<Long, PostEntity> postById, Map<Long, CommentEntity> commentById,
                                            Map<Long, ProfileEntity> profileById, Map<Long, UserEntity> userById, BoardCatalogSnapshot snapshot, Instant now,
                                            Locale locale, String recipientName) {
        MemberNotificationType type = MemberNotificationType.of(row.getType()).orElse(MemberNotificationType.COMMENT);
        Map<String, Object> payload = payloadOf(row.getPayload());

        NotificationPostRefView post = null;
        boolean postAvailable = false;
        if (row.getPostId() != null) {
            PostEntity p = postById.get(row.getPostId());
            postAvailable = p != null && !p.isGoneAt(now) && !p.isHiddenAt(now) && snapshot.board(p.getBoardId()).map(EffectiveBoard::active).orElse(false);
            post = new NotificationPostRefView(Long.toString(row.getPostId()), postAvailable ? p.getTitle() : null, postAvailable);
        }
        String commentPreview = null;
        if (row.getCommentId() != null && postAvailable) {
            CommentEntity c = commentById.get(row.getCommentId());
            if (c != null && !c.isDeleted() && !c.isHiddenAt(now)) {
                commentPreview = preview(RichText.plainOf(c.getContentFormat(), c.getContent()));
            }
        }

        NotificationActorView actor = null;
        if (type == MemberNotificationType.COMMENT || type == MemberNotificationType.REPLY || type == MemberNotificationType.DM) {
            actor = actorOf(row, profileById, userById, locale);
        }

        String link = null;
        NotificationPointsView points = null;
        Instant hiddenUntil = null;
        MemberNotificationMessages.Facts facts;
        switch (type) {
            case COMMENT, REPLY -> {
                facts = MemberNotificationMessages.Facts.comment(type, actorName(actor), post == null ? null : post.title(), commentPreview, row.getGroupCount());
                link = postAvailable ? "/posts/" + row.getPostId() : null;
            }
            case POINTS_REVOKED -> {
                long amount = number(payload.get("amount"));
                String kind = payload.get("kind") instanceof String k ? k : "ADMIN_ADJUST";
                String note = payload.get("note") instanceof String n && !n.isBlank() ? n : null;
                points = new NotificationPointsView(kind, amount, note);
                facts = MemberNotificationMessages.Facts.points(kind, amount, note);
            }
            case DM -> {
                // 쪽지 내용은 싣지 않는다(보낸 회원의 닉네임만) — 누르면 그 대화방
                facts = MemberNotificationMessages.Facts.dm(actorName(actor));
                link = payload.get("threadId") instanceof String thread && !thread.isBlank() ? "/messages/" + thread : "/messages";
            }
            case NICKNAME_IMAGE_REVIEWED -> {
                String result = payload.get("result") instanceof String r ? r : "APPROVED";
                String reason = payload.get("reason") instanceof String why && !why.isBlank() ? why : null;
                facts = MemberNotificationMessages.Facts.nicknameReviewed(result, reason);
                link = "/settings/nickname-image";
            }
            default -> { // POST_HIDDEN, COMMENT_HIDDEN
                hiddenUntil = instantOf(payload.get("until"));
                facts = MemberNotificationMessages.Facts.hidden(type, payload.get("snapshot") instanceof String s && !s.isBlank() ? s : null, hiddenUntil != null);
                // 숨긴 글은 열 수 없다(작성자 포함) — 댓글 숨김이면 그 글로 간다
                link = type == MemberNotificationType.COMMENT_HIDDEN && postAvailable ? "/posts/" + row.getPostId() : null;
            }
        }
        // 문구는 알림 문구 엔진이 읽는 사람 언어로 그린다(그 회원 이벤트 앱 알림의 쓸 문구 · 규칙 — 없으면 기본 문구)
        RenderedInApp text = texts.inApp(facts, locale, recipientName);
        String title = text.title();
        String body = text.body() == null || text.body().isBlank() ? null : text.body();
        return new MemberNotificationItemView(Long.toString(row.getId()), type.name(), type.category(), row.isRead(), row.getCreatedAt(), title, body, link, actor,
                post, row.getCommentId() == null ? null : Long.toString(row.getCommentId()), commentPreview, row.getGroupCount(), points, hiddenUntil);
    }

    /** 일으킨 사람 — 회원은 지금 닉네임(탈퇴·없음은 「탈퇴한 회원」), 비회원은 그때 이름 */
    private NotificationActorView actorOf(MemberNotificationEntity row, Map<Long, ProfileEntity> profileById, Map<Long, UserEntity> userById, Locale locale) {
        if (row.getActorUserId() == null) {
            return new NotificationActorView("GUEST", null, row.getActorName() == null ? "" : row.getActorName(), false);
        }
        UserEntity user = userById.get(row.getActorUserId());
        ProfileEntity profile = profileById.get(row.getActorUserId());
        boolean withdrawn = user == null || user.isGone() || profile == null;
        String name = withdrawn ? messages.get(new MessageRef("member.notification.actor.withdrawn"), locale) : profile.getNickname();
        return new NotificationActorView("MEMBER", Long.toString(row.getActorUserId()), name, withdrawn);
    }

    private static String actorName(NotificationActorView actor) {
        return actor == null ? "" : actor.name();
    }

    /** 앞 60자 — 공백은 한 칸, 넘으면 … */
    static String preview(String content) {
        if (content == null) {
            return null;
        }
        String flat = content.strip().replaceAll("\\s+", " ");
        return flat.codePointCount(0, flat.length()) <= PREVIEW_CHARS ? flat : flat.substring(0, flat.offsetByCodePoints(0, PREVIEW_CHARS)) + "…";
    }

    private int retentionDays() {
        return boardNotifySettings.findById(BoardNotificationSettingEntity.ROW_ID).map(row -> row.values().memberNotificationRetentionDays())
                .orElse(DEFAULT_RETENTION_DAYS);
    }

    private static Map<String, Object> payloadOf(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return map == null ? Map.of() : map;
        } catch (RuntimeException e) {
            return Map.of(); // 예전 기록의 모양이 달라도 목록이 멈추면 안 된다
        }
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }

    private static Instant instantOf(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 거르기 → 종류 목록. 없거나 {@code all} 이면 null(모두). 모르는 값은 400 {@code fields.filter} */
    private static List<String> typesFor(String filter) {
        if (filter == null || filter.isBlank() || "all".equals(filter)) {
            return null;
        }
        return Optional.ofNullable(FILTERS.get(filter)).orElseThrow(() -> ValidationException.of("filter", new MessageRef("member.notification.filter.invalid")));
    }

    private static List<String> typesOf(String category) {
        return Arrays.stream(MemberNotificationType.values()).filter(t -> t.category().equals(category)).map(Enum::name).toList();
    }

    private static Long cursorOf(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String text = cursor.strip();
        if (!DIGITS.matcher(text).matches()) {
            throw ValidationException.of("cursor", new MessageRef("notification.cursor.invalid"));
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw ValidationException.of("cursor", new MessageRef("notification.cursor.invalid"));
        }
    }
}
