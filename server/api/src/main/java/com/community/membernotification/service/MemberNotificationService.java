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
import com.community.report.entity.ReportReasons;
import com.community.post.repository.master.PostRepository;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.LocalDate;
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
        Set<Long> actorIds = rows.stream().map(MemberNotificationEntity::getActorUserId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.HashSet::new));
        // 매니저가 내보낸 멤버(GROUP_KICKED_BY_MANAGER 의 덧붙임 targetId) — 일으킨 사람과 같이 지금 닉네임을 읽는다
        for (MemberNotificationEntity row : rows) {
            if (MemberNotificationType.GROUP_KICKED_BY_MANAGER.name().equals(row.getType())) {
                Long targetId = idOf(payloadOf(row.getPayload()).get("targetId"));
                if (targetId != null) {
                    actorIds.add(targetId);
                }
            }
        }

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
        if (type == MemberNotificationType.COMMENT || type == MemberNotificationType.REPLY || type == MemberNotificationType.DM
                || type == MemberNotificationType.OPINION_PIN_REQUESTED || type == MemberNotificationType.GROUP_JOIN_REQUEST
                || type == MemberNotificationType.GROUP_POST_UNPUBLISHED || type == MemberNotificationType.GROUP_MANAGER_RESIGNED
                || type == MemberNotificationType.GROUP_KICKED_BY_MANAGER || type == MemberNotificationType.GROUP_EVENT
                || type == MemberNotificationType.GROUP_PUBLISH_REQUESTED || type == MemberNotificationType.GROUP_PUBLISH_APPROVED
                || type == MemberNotificationType.GROUP_PUBLISH_REJECTED || type == MemberNotificationType.GROUP_POST_PUBLISHED
                || type == MemberNotificationType.GROUP_VISIBILITY_CHANGED || type == MemberNotificationType.GROUP_POST_MEMBERS_ONLY
                || type == MemberNotificationType.GROUP_POST_RELEASED) {
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
            case OPINION_PIN_REQUESTED -> {
                // 요청한 회원 · 글 · 의견 앞부분 — 누르면 그 글(확인할 대기 요청은 글 상세의 debate.pendingPinRequests · GET …/pin-requests)
                facts = MemberNotificationMessages.Facts.pinRequested(actorName(actor), post == null ? null : post.title(), commentPreview);
                link = postAvailable ? "/posts/" + row.getPostId() : null;
            }
            case OPINION_PIN_REVIEWED -> {
                String result = payload.get("result") instanceof String r ? r : "APPROVED";
                String reason = payload.get("reason") instanceof String why && !why.isBlank() ? why : null;
                facts = MemberNotificationMessages.Facts.pinReviewed(result, reason, post == null ? null : post.title());
                link = postAvailable ? "/posts/" + row.getPostId() : null;
            }
            case NICKNAME_IMAGE_REVIEWED -> {
                String result = payload.get("result") instanceof String r ? r : "APPROVED";
                String reason = payload.get("reason") instanceof String why && !why.isBlank() ? why : null;
                facts = MemberNotificationMessages.Facts.nicknameReviewed(result, reason);
                link = "/settings/nickname-image";
            }
            case POST_REMOVED, COMMENT_REMOVED -> {
                // 운영자가 지운 글·댓글 — 지운 글은 열 수 없어 링크가 없고, 지운 댓글은 그 글로 간다(제목·앞부분은 지울 때 남긴 것, 본문 둘째 줄은 사유 — 신고 관리면 「사유 코드 이름: 글」)
                String reason = payload.get("reason") instanceof String why && !why.isBlank() ? why : null;
                facts = MemberNotificationMessages.Facts.removed(type, payload.get("snapshot") instanceof String s && !s.isBlank() ? s : null, reason, reasonCodeOf(payload));
                link = type == MemberNotificationType.COMMENT_REMOVED && postAvailable ? "/posts/" + row.getPostId() : null;
            }
            case FEATURE_RESTRICTED -> facts = MemberNotificationMessages.Facts.restricted(payload.get("kind") instanceof String k ? k : "",
                    instantOf(payload.get("until")), payload.get("reason") instanceof String r && !r.isBlank() ? r : null,
                    payload.get("autoRule") instanceof String a && !a.isBlank() ? a : null);
            case POINTS_EXPIRING -> {
                // 소멸 예정 포인트(알림 배치) — 기간 안 소멸 예정 합 · 가장 가까운 소멸일(한국 날짜) · 일수. 누르면 내 포인트
                facts = MemberNotificationMessages.Facts.expiring(number(payload.get("amount")), localDateOf(payload.get("date")), (int) number(payload.get("days")));
                link = "/me/points";
            }
            case POINT_SHOP_REFUND_APPROVED -> {
                // 정지 중 환불 신청 수락 — 품목 이름 · 돌려준 포인트. 누르면 내 아이템
                facts = MemberNotificationMessages.Facts.refundApproved(payload.get("itemName") instanceof String n ? n : null, number(payload.get("points")));
                link = "/me/items";
            }
            case POINT_SHOP_REFUND_REJECTED -> {
                // 정지 중 환불 신청 거절 — 품목 이름 · 사유. 누르면 내 아이템
                facts = MemberNotificationMessages.Facts.refundRejected(payload.get("itemName") instanceof String n ? n : null,
                        payload.get("reason") instanceof String why && !why.isBlank() ? why : null);
                link = "/me/items";
            }
            case GROUP_APPROVED, GROUP_REJECTED, GROUP_JOIN_REQUEST, GROUP_JOIN_APPROVED, GROUP_JOIN_REJECTED, GROUP_KICKED, GROUP_DISSOLVED, GROUP_LEADER_TRANSFERRED,
                 GROUP_DEACTIVATED, GROUP_REACTIVATED -> {
                // 소모임 — 소모임 이름(알릴 때 이름) · 사유. 가는 곳: 승인·가입 승인·모임장 넘김·비활성·다시 열림 → 그 소모임, 반려·거절 → 내 소모임,
                // 가입 신청 → 가입 신청 관리, 강퇴·해산 → 소모임 목록(community api-groups.md 7절)
                String groupId = payload.get("groupId") instanceof String g && !g.isBlank() ? g : null;
                String reason = payload.get("reason") instanceof String why && !why.isBlank() ? why : null;
                facts = MemberNotificationMessages.Facts.group(type, payload.get("groupName") instanceof String n ? n : null, reason,
                        type == MemberNotificationType.GROUP_JOIN_REQUEST ? actorName(actor) : null);
                link = switch (type) {
                    case GROUP_REJECTED, GROUP_JOIN_REJECTED -> "/groups/me";
                    case GROUP_KICKED, GROUP_DISSOLVED -> "/groups";
                    case GROUP_JOIN_REQUEST -> groupId == null ? "/groups/me" : "/groups/" + groupId + "/manage/applications";
                    default -> groupId == null ? "/groups/me" : "/groups/" + groupId;
                };
            }
            case GROUP_POST_UNPUBLISHED -> {
                // 소모임 글 비공개 되돌림(V91) — 소모임 이름 · 글 제목(알릴 때) · 처리자 역할. 누르면 그 글(작성자는 지금도 열 수 있다 — 지웠거나 가려졌으면 링크 없음)
                String title = payload.get("title") instanceof String t && !t.isBlank() ? t : post == null ? null : post.title();
                facts = MemberNotificationMessages.Facts.groupPostUnpublished(payload.get("groupName") instanceof String n ? n : null, title,
                        payload.get("actorRole") instanceof String r ? r : null);
                link = postAvailable ? "/posts/" + row.getPostId() : null;
            }
            case GROUP_MANAGER_APPOINTED, GROUP_MANAGER_PERMISSIONS_CHANGED, GROUP_MANAGER_REMOVED, GROUP_MANAGER_RESIGNED, GROUP_KICKED_BY_MANAGER -> {
                // 소모임 매니저(V92) — 소모임 이름(알릴 때) · 맡은 일(임명·바뀜 — 본문) · 일으킨 매니저(사퇴·강퇴) · 내보낸 멤버(지금 닉네임). 가는 곳: 임명·바뀜 → 관리 메인,
                // 해임 → 그 소모임, 사퇴·매니저 강퇴 → 멤버 관리(community api-groups.md 7절 「매니저 알림 5종」)
                String groupId = payload.get("groupId") instanceof String g && !g.isBlank() ? g : null;
                String keys = payload.get("permissions") instanceof List<?> list
                        ? list.stream().filter(String.class::isInstance).map(String.class::cast).collect(Collectors.joining(",")) : null;
                String targetName = null;
                if (type == MemberNotificationType.GROUP_KICKED_BY_MANAGER) {
                    Long targetId = idOf(payload.get("targetId"));
                    UserEntity targetUser = targetId == null ? null : userById.get(targetId);
                    ProfileEntity targetProfile = targetId == null ? null : profileById.get(targetId);
                    targetName = targetUser == null || targetUser.isGone() || targetProfile == null
                            ? messages.get(new MessageRef("member.notification.actor.withdrawn"), locale) : targetProfile.getNickname();
                }
                facts = MemberNotificationMessages.Facts.groupManager(type, payload.get("groupName") instanceof String n ? n : null, actorName(actor), keys, targetName);
                link = groupId == null ? "/groups/me" : switch (type) {
                    case GROUP_MANAGER_APPOINTED, GROUP_MANAGER_PERMISSIONS_CHANGED -> "/groups/" + groupId + "/manage";
                    case GROUP_MANAGER_REMOVED -> "/groups/" + groupId;
                    default -> "/groups/" + groupId + "/manage/members";
                };
            }
            case GROUP_EVENT -> {
                // 소모임 일정(V93) — 소모임 이름 · 일정 제목(알릴 때) · 한 일(CREATED · UPDATED · CANCELED) · 취소 사유. 누르면 그 일정(community api-groups.md 9절)
                String groupId = payload.get("groupId") instanceof String g && !g.isBlank() ? g : null;
                String eventId = payload.get("eventId") instanceof String e && !e.isBlank() ? e : null;
                facts = MemberNotificationMessages.Facts.groupEvent(payload.get("groupName") instanceof String n ? n : null,
                        payload.get("title") instanceof String t ? t : null, payload.get("eventAction") instanceof String a ? a : null,
                        payload.get("reason") instanceof String why && !why.isBlank() ? why : null);
                link = groupId == null ? "/groups/me" : eventId == null ? "/groups/" + groupId : "/groups/" + groupId + "/events/" + eventId;
            }
            case GROUP_PUBLISH_REQUESTED -> {
                // 공개 요청(V94) — 소모임 이름 · 요청한 작성자 · 글 제목(알릴 때). 누르면 공개 요청 처리(이미 처리됐으면 화면이 빈 목록으로 안내한다 — 알림은 회수하지 않는다)
                String groupId = payload.get("groupId") instanceof String g && !g.isBlank() ? g : null;
                String title = payload.get("title") instanceof String t && !t.isBlank() ? t : post == null ? null : post.title();
                facts = MemberNotificationMessages.Facts.groupPublish(type, payload.get("groupName") instanceof String n ? n : null, title, null, null, actorName(actor));
                link = groupId == null ? "/groups/me" : "/groups/" + groupId + "/manage/publish-requests";
            }
            case GROUP_PUBLISH_APPROVED, GROUP_PUBLISH_REJECTED, GROUP_POST_PUBLISHED, GROUP_POST_MEMBERS_ONLY, GROUP_POST_RELEASED -> {
                // 글 공개 처리 결과(V94) — 소모임 이름 · 글 제목(알릴 때) · 처리자 역할 · 거절 사유. 누르면 그 글(지웠거나 가려졌으면 링크 없음)
                String title = payload.get("title") instanceof String t && !t.isBlank() ? t : post == null ? null : post.title();
                facts = MemberNotificationMessages.Facts.groupPublish(type, payload.get("groupName") instanceof String n ? n : null, title,
                        payload.get("actorRole") instanceof String r ? r : null, payload.get("reason") instanceof String why && !why.isBlank() ? why : null, null);
                link = postAvailable ? "/posts/" + row.getPostId() : null;
            }
            case GROUP_VISIBILITY_CHANGED -> {
                // 공개 범위 변경(V94) — 소모임 이름 · 바뀐 값. 누르면 그 소모임
                String groupId = payload.get("groupId") instanceof String g && !g.isBlank() ? g : null;
                facts = MemberNotificationMessages.Facts.groupVisibilityChanged(payload.get("groupName") instanceof String n ? n : null,
                        payload.get("visibility") instanceof String v ? v : null);
                link = groupId == null ? "/groups/me" : "/groups/" + groupId;
            }
            case REPORT_RESULT -> {
                // 내 신고의 처리 결과 — 조치됨 · 위반 아님(신고 당시 제목·앞부분). 글을 볼 수 있으면 그 글로
                String result = payload.get("result") instanceof String r ? r : MemberNotificationMessages.REPORT_ACTIONED;
                String targetType = payload.get("targetType") instanceof String t ? t : "POST";
                facts = MemberNotificationMessages.Facts.reportResult(result, targetType, payload.get("snapshot") instanceof String s && !s.isBlank() ? s : null);
                link = postAvailable ? "/posts/" + row.getPostId() : null;
            }
            default -> { // POST_HIDDEN, COMMENT_HIDDEN
                hiddenUntil = instantOf(payload.get("until"));
                String hiddenSnapshot = payload.get("snapshot") instanceof String s && !s.isBlank() ? s : null;
                facts = MemberNotificationMessages.BY_STAFF.equals(payload.get("by"))
                        ? MemberNotificationMessages.Facts.hiddenByStaff(type, hiddenSnapshot, payload.get("reason") instanceof String why && !why.isBlank() ? why : null,
                        reasonCodeOf(payload))
                        : MemberNotificationMessages.Facts.hidden(type, hiddenSnapshot, hiddenUntil != null);
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

    /** 운영자 조치 사유 코드(관리자 신고 관리 — 신고 사유 체계) — 없거나 모르는 값이면 null */
    private static String reasonCodeOf(Map<String, Object> payload) {
        return payload.get("reasonCode") instanceof String code && ReportReasons.ALL.contains(code) ? code : null;
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

    /** 덧붙임의 회원 ID(문자열 숫자) — 없거나 숫자가 아니면 null */
    private static Long idOf(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
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

    /** 덧붙임의 날짜({@code YYYY-MM-DD}) — 없거나 모양이 다르면 null(문구는 빈 날짜로) */
    private static LocalDate localDateOf(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
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
