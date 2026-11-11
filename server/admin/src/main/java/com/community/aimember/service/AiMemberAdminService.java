package com.community.aimember.service;

import com.community.aimember.dto.AiMemberRequests;
import com.community.aimember.dto.AiMemberViews;
import com.community.aimember.entity.AiAgeBand;
import com.community.aimember.entity.AiGender;
import com.community.aimember.entity.AiMemberModelHistoryEntity;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiRelationKind;
import com.community.aimember.exception.AiMemberNotFoundException;
import com.community.aimember.repository.master.AiMemberModelHistoryRepository;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.aimember.repository.master.AiMemberSettingsRepository;
import com.community.aimember.repository.master.AiModelRepository;
import com.community.aimember.entity.AiModelEntity;
import com.community.aimember.entity.AiProviderKeyStatus;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupStatus;
import com.community.group.exception.GroupNotFoundException;
import com.community.group.exception.GroupNotPublicException;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupRepository;
import com.community.permission.dto.AdminActor;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.service.AvatarCatalog;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.exception.UserNicknameTakenException;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.ProfileRepositoryQDSL;
import com.community.user.repository.master.UserRepository;
import com.community.user.service.UserPolicy;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 회원 계정 관리(관리자 「AI 회원」 화면 — ai-members-contract 정책 1·2-1·3·7). 계정 = 회원 + 1:1 확장 줄.
 *
 * <ul>
 *   <li><b>만들기</b>({@link #create}): 서버가 로그인할 수 없는 회원 계정({@link UserEntity#createAiAccount} — 이메일·비밀번호·소셜 없음) + 프로필(닉네임 · 공개 소개 · 아바타)
 *       + 확장 줄을 한 트랜잭션에 만든다. 처음에는 꺼져 있다. 닉네임은 회원 닉네임 규칙({@link UserPolicy#memberNicknameViolation})과 중복 검사를 받는다.</li>
 *   <li><b>관계</b>: 양쪽 줄에 같이 적는다. 옛 상대 · 새 상대의 옛 상대는 푼다. 감사 기록 하나에 바뀐 상대를 남긴다.</li>
 *   <li><b>쓸 곳</b>: 게시판은 「항상 막힘」({@link AiMemberRules#blockedReason})을 고를 수 없다. 소모임은 AI 전용 소모임만 — 저장하면 그 소모임 멤버로 넣고, 뺀 소모임에서는 나간다
 *       (모임장이면 그대로).</li>
 * </ul>
 * 권한은 컨트롤러가 본다(읽기 {@code ai-member.read} · 쓰기 {@code ai-member.manage}).
 */
@Service
@Transactional(readOnly = true)
public class AiMemberAdminService {

    static final int PERSONALITY_MAX = 200;
    static final int TONE_MAX = 100;
    static final int INTERESTS_MAX = 300;
    static final int BIO_MAX = 150;
    static final String AUDIT_TARGET = "AI_MEMBER";
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime NOON = LocalTime.NOON;

    private final AiMemberProfileRepository accounts;
    private final AiMemberQueries queries;
    private final AiMemberSettingsRepository settings;
    private final AiProviderKeys keys;
    private final AiModelCatalog modelCatalog;
    private final AiModelRepository models;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileRepositoryQDSL profileQueries;
    private final PointShopMemberStore avatarStore;
    private final AvatarCatalog avatars;
    private final BoardCatalog catalog;
    private final GroupRepository groups;
    private final GroupMemberRepository groupMembers;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final AiMemberModelHistoryRepository history;

    public AiMemberAdminService(AiMemberProfileRepository accounts, AiMemberQueries queries, AiMemberSettingsRepository settings, AiProviderKeys keys,
                                AiModelCatalog modelCatalog, AiModelRepository models, UserRepository users, ProfileRepository profiles, ProfileRepositoryQDSL profileQueries,
                                PointShopMemberStore avatarStore, AvatarCatalog avatars, BoardCatalog catalog, GroupRepository groups,
                                GroupMemberRepository groupMembers, SnowflakeIdGenerator ids, AuditLogService audit, AiMemberModelHistoryRepository history) {
        this.history = history;
        this.accounts = accounts;
        this.queries = queries;
        this.settings = settings;
        this.keys = keys;
        this.modelCatalog = modelCatalog;
        this.models = models;
        this.users = users;
        this.profiles = profiles;
        this.profileQueries = profileQueries;
        this.avatarStore = avatarStore;
        this.avatars = avatars;
        this.catalog = catalog;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.ids = ids;
        this.audit = audit;
    }

    // ── 읽기 ──────────────────────────────────────────────────────────────

    public AiMemberViews.ListView list() {
        AiMemberSettingsEntity s = settingsRow();
        List<AiMemberProfileEntity> all = accounts.findAllByOrderByIdAsc();
        Context ctx = context(all);
        return new AiMemberViews.ListView(all.stream().map(a -> row(a, ctx, s)).toList(), s.isStopped(),
                new AiMemberViews.Calls(queries.callsSince(startOfToday()), s.getDailyCallLimit()));
    }

    public AiMemberViews.FormOptions options() {
        // 제공사 → 켜진 모델(모델 목록 차례) — 원천은 표 ai_models(V189). 켜진 모델이 없는 제공사도 빈 목록으로 낸다
        Map<String, List<AiMemberViews.ModelOption>> modelOptions = new LinkedHashMap<>();
        for (AiProvider p : AiProvider.values()) {
            modelOptions.put(p.name(), new ArrayList<>());
        }
        for (AiModelEntity m : models.findAllByOrderByProviderAscSortOrderAscIdAsc()) {
            if (m.isEnabled()) {
                modelOptions.get(m.getProvider().name()).add(new AiMemberViews.ModelOption(m.getModelId(), m.getLabel(), m.getPriceInput(), m.getPriceOutput(),
                        m.isFreeTier(), m.isDefault()));
            }
        }
        BoardCatalogSnapshot snap = catalog.snapshot();
        List<AiMemberViews.BoardOption> boards = snap.boards().stream().filter(b -> !BoardFields.KIND_GROUP.equals(b.kind()) && b.active())
                .map(b -> new AiMemberViews.BoardOption(b.slug(), b.name(), AiMemberRules.blockedReason(b))).toList();
        List<AiMemberViews.GroupOption> aiGroups = groups.findByAiOnlyTrueAndStatusOrderByIdAsc(GroupStatus.ACTIVE).stream()
                .map(g -> new AiMemberViews.GroupOption(Long.toString(g.getId()), g.getName())).toList();
        List<AiMemberProfileEntity> all = accounts.findAllByOrderByIdAsc();
        Map<Long, String> names = queries.nicknames(all.stream().map(AiMemberProfileEntity::getUserId).toList());
        List<AiMemberViews.AccountRef> refs = all.stream()
                .map(a -> new AiMemberViews.AccountRef(Long.toString(a.getUserId()), names.getOrDefault(a.getUserId(), ""), Long.toString(a.getId()))).toList();
        List<AiMemberViews.AvatarOption> avatarOptions = avatars.all().stream().filter(a -> !a.animated())
                .map(a -> new AiMemberViews.AvatarOption(a.id(), a.name(), a.category())).toList();
        return new AiMemberViews.FormOptions(modelOptions, boards, aiGroups, refs, avatarOptions);
    }

    public AiMemberViews.Detail detail(String idText) {
        return detailOf(require(idText));
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────

    /** 새 AI 회원 — 회원 계정(로그인 불가) + 프로필 + 확장 줄. 꺼진 채로 만든다 */
    @Transactional
    public AiMemberViews.Detail create(AdminActor actor, AiMemberRequests.Create body) {
        if (body == null) {
            throw ValidationException.of("nickname", new MessageRef("aiMember.nickname.required"));
        }
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String nickname = body.nickname() == null ? "" : body.nickname().strip();
        UserPolicy.memberNicknameViolation(nickname).ifPresent(m -> fields.put("nickname", m));
        Checked c = check(body.save(), null, fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        if (profileQueries.existsByNickname(nickname)) {
            throw new UserNicknameTakenException();
        }
        Instant now = now();
        long userId = ids.nextId();
        users.persist(UserEntity.createAiAccount(userId, now));
        ProfileEntity profile = ProfileEntity.create(userId, nickname, now);
        profile.changeBio(c.bio(), now);
        profiles.persist(profile);
        AiMemberProfileEntity a = AiMemberProfileEntity.create(ids.nextId(), userId, c.values(), actor.operatorId(), now);
        accounts.persist(a);
        history.persist(AiMemberModelHistoryEntity.of(ids.nextId(), a.getId(), a.getProvider(), a.getModel(), now, actor.operatorId())); // 모델 이력 첫 줄
        accounts.flush();
        if (c.avatarId() != null) {
            avatarStore.setAvatar(userId, AvatarCatalog.PROFILE_IMAGE_PREFIX + c.avatarId(), c.animated() ? null : c.avatarId(), now);
        }
        Map<String, Object> relation = applyRelation(a, c, actor.operatorId(), now);
        syncGroups(userId, c.groupIds(), now);
        AuditEvent created = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MEMBER_CREATE, AuditResult.SUCCESS).target(AUDIT_TARGET, Long.toString(a.getId()))
                .with("userId", Long.toString(userId)).with("provider", a.getProvider().name()).with("model", a.getModel());
        // 감사 detail 은 null 값을 받지 않는다(Map.copyOf) — 없는 사실은 넣지 않는다
        audit.record(relation == null ? created : created.with("relation", relation));
        return detailOf(a);
    }

    /** 고치기 — 관계는 상대 쪽까지, 소모임은 멤버 넣기·빼기까지 */
    @Transactional
    public AiMemberViews.Detail save(AdminActor actor, String idText, AiMemberRequests.Save body) {
        AiMemberProfileEntity a = accounts.findWithLockById(parseId(idText)).orElseThrow(AiMemberNotFoundException::new);
        if (body == null) {
            throw ValidationException.of("model", new MessageRef("aiMember.model.invalid"));
        }
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Checked c = check(body, a, fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Instant now = now();
        Map<String, Object> before = snapshot(a);
        AiProvider fromProvider = a.getProvider();
        String fromModel = a.getModel();
        a.apply(c.values(), actor.operatorId(), now);
        Map<String, Object> modelChange = null;
        if (fromProvider != a.getProvider() || !Objects.equals(fromModel, a.getModel())) {
            // 제공사·모델이 바뀔 때만 이력 새 줄(다른 칸 저장은 넣지 않는다)
            history.persist(AiMemberModelHistoryEntity.of(ids.nextId(), a.getId(), a.getProvider(), a.getModel(), now, actor.operatorId()));
            modelChange = new LinkedHashMap<>();
            modelChange.put("from", fromProvider.name() + "/" + fromModel);
            modelChange.put("to", a.getProvider().name() + "/" + a.getModel());
        }
        profiles.findById(a.getUserId()).ifPresent(p -> p.changeBio(c.bio(), now));
        profiles.flush();
        if (c.avatarId() != null) {
            avatarStore.setAvatar(a.getUserId(), AvatarCatalog.PROFILE_IMAGE_PREFIX + c.avatarId(), c.animated() ? null : c.avatarId(), now);
        }
        Map<String, Object> relation = applyRelation(a, c, actor.operatorId(), now);
        syncGroups(a.getUserId(), c.groupIds(), now);
        Map<String, Object> after = snapshot(a);
        AuditEvent updated = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MEMBER_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, Long.toString(a.getId()))
                .with("before", before).with("after", after).with("avatarChanged", c.avatarId() != null);
        // 감사 detail 은 null 값을 받지 않는다(Map.copyOf) — 바뀌지 않은 관계·모델은 넣지 않는다
        if (relation != null) {
            updated = updated.with("relation", relation);
        }
        if (modelChange != null) {
            updated = updated.with("model", modelChange);
        }
        audit.record(updated);
        return detailOf(a);
    }

    @Transactional
    public AiMemberViews.Row setEnabled(AdminActor actor, String idText, Boolean enabled) {
        if (enabled == null) {
            throw ValidationException.of("enabled", new MessageRef("aiMember.enabled.required"));
        }
        AiMemberProfileEntity a = accounts.findWithLockById(parseId(idText)).orElseThrow(AiMemberNotFoundException::new);
        boolean from = a.isEnabled();
        a.enable(enabled, actor.operatorId(), now());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MEMBER_ENABLE, AuditResult.SUCCESS).target(AUDIT_TARGET, Long.toString(a.getId()))
                .with("from", from).with("to", enabled));
        AiMemberSettingsEntity s = settingsRow();
        return row(a, context(List.of(a)), s);
    }

    /** AI 전용 소모임 지정·해제(정책 7 — 켜면 일반 회원 가입을 서버가 거절한다. 이미 있는 사람 멤버는 그대로). 켜기는 공개 소모임만(409 {@code GROUP_NOT_PUBLIC}) */
    @Transactional
    public AiMemberViews.GroupAiOnly setGroupAiOnly(AdminActor actor, String groupIdText, Boolean aiOnly) {
        if (aiOnly == null) {
            throw ValidationException.of("aiOnly", new MessageRef("aiMember.group.aiOnly.required"));
        }
        GroupEntity g = groups.findWithLockById(parseGroupId(groupIdText)).orElseThrow(GroupNotFoundException::new);
        if (aiOnly && !g.isPublicGroup()) {
            throw new GroupNotPublicException(); // AI 전용 = 공개 소모임만(구경하는 일반 회원이 글을 볼 수 있게 — 정책 7)
        }
        boolean from = g.isAiOnly();
        g.markAiOnly(aiOnly);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_GROUP_AI_ONLY, AuditResult.SUCCESS).target("GROUP", Long.toString(g.getId()))
                .with("from", from).with("to", aiOnly));
        return new AiMemberViews.GroupAiOnly(Long.toString(g.getId()), g.getName(), g.isAiOnly());
    }

    // ── 검사 ──────────────────────────────────────────────────────────────

    /** 검사를 지난 값 */
    private record Checked(AiMemberProfileEntity.Values values, String bio, AiRelationKind relationKind, AiMemberProfileEntity partner, List<Long> groupIds,
                           String avatarId, boolean animated) {
    }

    private Checked check(AiMemberRequests.Save b, AiMemberProfileEntity self, Map<String, MessageRef> f) {
        AiProvider provider = parseEnum(AiProvider.class, b.provider());
        // 모델을 비워 보내면 그 제공사의 기본 모델(모델 목록 「기본」). 꺼졌거나 목록에 없는 모델은 고를 수 없다
        String model = b.model() == null || b.model().isBlank()
                ? (provider == null ? null : models.findFirstByProviderAndDefaultModelTrue(provider).map(AiModelEntity::getModelId).orElse(null))
                : b.model().strip();
        if (provider == null) {
            f.put("provider", new MessageRef("aiMember.provider.unknown"));
        } else if (!modelCatalog.allows(provider, model)) {
            f.put("model", new MessageRef("aiMember.model.invalid"));
        }
        AiMemberRequests.Persona p = b.persona() == null ? new AiMemberRequests.Persona(null, null, null, null, null) : b.persona();
        AiGender gender = parseEnum(AiGender.class, p.gender());
        if (gender == null) {
            f.put("persona.gender", new MessageRef("aiMember.persona.gender"));
        }
        AiAgeBand age = parseEnum(AiAgeBand.class, p.ageBand());
        if (age == null) {
            f.put("persona.ageBand", new MessageRef("aiMember.persona.ageBand"));
        }
        String personality = text(p.personality(), "persona.personality", PERSONALITY_MAX, f);
        String tone = text(p.tone(), "persona.tone", TONE_MAX, f);
        String interests = text(p.interests(), "persona.interests", INTERESTS_MAX, f);
        String bio = b.bio() == null ? "" : b.bio().strip();
        if (bio.codePointCount(0, bio.length()) > BIO_MAX) {
            f.put("bio", new MessageRef("aiMember.bio.max", BIO_MAX));
        }
        LocalTime[] m = window(b.windows() == null ? null : b.windows().morning());
        if (m == null || !m[0].isBefore(m[1]) || m[1].isAfter(NOON)) {
            f.put("windows.morning", new MessageRef("aiMember.windows.morning"));
        }
        LocalTime[] n = window(b.windows() == null ? null : b.windows().afternoon());
        if (n == null || !n[0].isBefore(n[1]) || n[0].isBefore(NOON)) {
            f.put("windows.afternoon", new MessageRef("aiMember.windows.afternoon"));
        }
        int daily = b.dailyPosts() == null ? -1 : b.dailyPosts();
        if (daily < AiMemberRules.DAILY_POSTS_MIN || daily > AiMemberRules.DAILY_POSTS_MAX) {
            f.put("dailyPosts", new MessageRef("aiMember.dailyPosts", AiMemberRules.DAILY_POSTS_MIN, AiMemberRules.DAILY_POSTS_MAX));
        }
        BoardCatalogSnapshot snap = catalog.snapshot();
        List<Long> boardIds = new ArrayList<>();
        for (String slug : b.boards() == null ? List.<String>of() : new LinkedHashSet<>(b.boards())) {
            EffectiveBoard board = snap.board(slug).filter(x -> !BoardFields.KIND_GROUP.equals(x.kind()) && x.active()).orElse(null); // 꺼진 게시판은 고를 수 없다(고르기 목록과 같은 기준)
            if (board == null) {
                f.putIfAbsent("boards", new MessageRef("aiMember.boards.unknown"));
            } else if (AiMemberRules.blockedReason(board) != null) {
                f.put("boards", new MessageRef("aiMember.boards.blocked"));
            } else {
                boardIds.add(board.id());
            }
        }
        Set<Long> aiGroups = new HashSet<>();
        groups.findByAiOnlyTrueAndStatusOrderByIdAsc(GroupStatus.ACTIVE).forEach(g -> aiGroups.add(g.getId()));
        List<Long> groupIds = new ArrayList<>();
        for (String raw : b.groups() == null ? List.<String>of() : new LinkedHashSet<>(b.groups())) {
            Long gid = parseLong(raw);
            if (gid == null || !aiGroups.contains(gid)) {
                f.putIfAbsent("groups", new MessageRef("aiMember.groups.unknown"));
            } else {
                groupIds.add(gid);
            }
        }
        if (!f.containsKey("boards") && !f.containsKey("groups") && boardIds.isEmpty() && groupIds.isEmpty()) {
            f.put("boards", new MessageRef("aiMember.boards.required"));
        }
        AiRelationKind kind = null;
        AiMemberProfileEntity partner = null;
        if (b.relation() != null) {
            kind = parseEnum(AiRelationKind.class, b.relation().kind());
            Long pid = parseLong(b.relation().partnerAiMemberId());
            if (kind == null) {
                f.put("relation", new MessageRef("aiMember.relation.kind"));
            } else if (pid == null) {
                f.put("relation", new MessageRef("aiMember.relation.unknown"));
            } else if (self != null && pid.equals(self.getId())) {
                f.put("relation", new MessageRef("aiMember.relation.self"));
            } else {
                partner = accounts.findWithLockById(pid).orElse(null);
                if (partner == null) {
                    f.put("relation", new MessageRef("aiMember.relation.unknown"));
                }
            }
        }
        String avatarId = b.avatarId() == null || b.avatarId().isBlank() ? null : b.avatarId().strip();
        boolean animated = false;
        if (avatarId != null) {
            AvatarCatalog.Avatar avatar = avatars.find(avatarId).orElse(null);
            if (avatar == null) {
                f.put("avatarId", new MessageRef("aiMember.avatar.unknown"));
            } else {
                animated = avatar.animated();
            }
        }
        AiMemberProfileEntity.Values values = f.isEmpty() ? new AiMemberProfileEntity.Values(provider, model, gender, age, personality, tone, interests, m[0], m[1],
                n[0], n[1], daily, boardIds) : null;
        return new Checked(values, bio, kind, partner, groupIds, avatarId, animated);
    }

    private static String text(String raw, String field, int max, Map<String, MessageRef> f) {
        String v = raw == null ? "" : raw.strip();
        if (v.isEmpty()) {
            f.put(field, new MessageRef("aiMember." + field + ".required"));
        } else if (v.codePointCount(0, v.length()) > max) {
            f.put(field, new MessageRef("aiMember." + field + ".max", max));
        }
        return v;
    }

    private static LocalTime[] window(AiMemberRequests.Window w) {
        if (w == null || w.start() == null || w.end() == null || !TIME.matcher(w.start()).matches() || !TIME.matcher(w.end()).matches()) {
            return null;
        }
        try {
            return new LocalTime[]{LocalTime.parse(w.start(), HHMM), LocalTime.parse(w.end(), HHMM)};
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ── 관계 · 소모임 ─────────────────────────────────────────────────────────

    /** 관계를 양쪽에 적는다 — 바뀐 상대(감사용 {@code from}/{@code to}) */
    private Map<String, Object> applyRelation(AiMemberProfileEntity a, Checked c, long operatorId, Instant now) {
        Long oldPartner = a.getPartnerId();
        Long newPartner = c.partner() == null ? null : c.partner().getId();
        if (oldPartner != null && !oldPartner.equals(newPartner)) {
            accounts.findWithLockById(oldPartner).ifPresent(old -> {
                if (a.getId().equals(old.getPartnerId())) {
                    old.relate(null, null, operatorId, now);
                }
            });
        }
        if (c.partner() != null) {
            AiMemberProfileEntity partner = c.partner();
            Long partnersOld = partner.getPartnerId();
            if (partnersOld != null && !partnersOld.equals(a.getId())) {
                accounts.findWithLockById(partnersOld).ifPresent(third -> third.relate(null, null, operatorId, now));
            }
            a.relate(c.relationKind(), partner.getId(), operatorId, now);
            partner.relate(c.relationKind(), a.getId(), operatorId, now);
        } else {
            a.relate(null, null, operatorId, now);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", oldPartner == null ? null : Long.toString(oldPartner));
        out.put("to", newPartner == null ? null : Long.toString(newPartner));
        out.put("kind", c.relationKind() == null || newPartner == null ? null : c.relationKind().name());
        return out;
    }

    /** AI 전용 소모임 멤버 맞추기 — 고른 곳은 넣고, AI 전용인데 빠진 곳은 나간다(모임장 자리는 그대로) */
    private void syncGroups(long userId, List<Long> wanted, Instant now) {
        Set<Long> want = new HashSet<>(wanted);
        for (GroupEntity g : groups.findByAiOnlyTrueAndStatusOrderByIdAsc(GroupStatus.ACTIVE)) {
            boolean in = want.contains(g.getId());
            GroupMemberEntity row = groupMembers.findByGroupIdAndUserId(g.getId(), userId).orElse(null);
            boolean active = g.getLeaderId().equals(userId) || (row != null && row.active());
            if (in == active || g.getLeaderId().equals(userId)) {
                continue;
            }
            GroupEntity locked = groups.findWithLockById(g.getId()).orElse(null);
            if (locked == null) {
                continue;
            }
            if (in) {
                if (row == null) {
                    groupMembers.persist(GroupMemberEntity.joined(g.getId(), userId, null, now));
                } else {
                    row.join(null, now);
                }
                locked.addMembers(1, now);
            } else {
                row.leave(now);
                locked.addMembers(-1, now);
            }
        }
        groupMembers.flush();
    }

    // ── 응답 ──────────────────────────────────────────────────────────────

    /** 목록 한 벌을 만들 때 한 번에 읽어 둔 것 */
    private record Context(Map<Long, String> nicknames, Map<Long, String> images, Map<Long, AiMemberProfileEntity> byId, Map<Long, long[]> today,
                           Map<Long, Long> pending, Set<AiProvider> usable, Map<AiProvider, AiProviderKeyStatus> paused, AiModelCatalog.Enabled models,
                           Map<Long, Boolean> restricted) {
    }

    private Context context(List<AiMemberProfileEntity> list) {
        Map<Long, AiMemberProfileEntity> byId = new HashMap<>();
        accounts.findAllByOrderByIdAsc().forEach(a -> byId.put(a.getId(), a));
        Set<Long> userIds = new HashSet<>();
        byId.values().forEach(a -> userIds.add(a.getUserId()));
        list.forEach(a -> userIds.add(a.getUserId()));
        return new Context(queries.nicknames(userIds), avatarStore.profileImages(userIds), byId, queries.publishedSince(startOfToday()), queries.pendingByMember(),
                keys.usable(), keys.paused(), modelCatalog.enabled(), queries.restricted(userIds));
    }

    private AiMemberViews.Row row(AiMemberProfileEntity a, Context ctx, AiMemberSettingsEntity s) {
        long[] today = ctx.today().getOrDefault(a.getId(), new long[2]);
        String resting = AiMemberDirectory.restingReason(a, s, ctx.usable(), ctx.paused(), ctx.models().allows(a.getProvider(), a.getModel()),
                ctx.restricted().getOrDefault(a.getUserId(), false));
        return new AiMemberViews.Row(Long.toString(a.getId()), new AiMemberViews.PersonRef(Long.toString(a.getUserId()), ctx.nicknames().getOrDefault(a.getUserId(), "")),
                avatarIdOf(ctx.images().get(a.getUserId())), a.getProvider().name(), a.getModel(), relation(a, ctx), a.isEnabled(), new AiMemberViews.Today(today[0], today[1]),
                ctx.pending().getOrDefault(a.getId(), 0L), a.getDailyPosts(), resting);
    }

    private AiMemberViews.Relation relation(AiMemberProfileEntity a, Context ctx) {
        AiMemberProfileEntity partner = a.getPartnerId() == null ? null : ctx.byId().get(a.getPartnerId());
        if (partner == null || a.getRelationKind() == null) {
            return null;
        }
        return new AiMemberViews.Relation(a.getRelationKind().name(), new AiMemberViews.AccountRef(Long.toString(partner.getUserId()),
                ctx.nicknames().getOrDefault(partner.getUserId(), ""), Long.toString(partner.getId())));
    }

    private AiMemberViews.Detail detailOf(AiMemberProfileEntity a) {
        AiMemberSettingsEntity s = settingsRow();
        Context ctx = context(List.of(a));
        BoardCatalogSnapshot snap = catalog.snapshot();
        List<String> boards = a.getBoardIds().stream().map(id -> snap.board(id).map(EffectiveBoard::slug).orElse(null)).filter(Objects::nonNull).toList();
        Set<Long> aiGroups = new HashSet<>();
        groups.findByAiOnlyTrueAndStatusOrderByIdAsc(GroupStatus.ACTIVE).forEach(g -> aiGroups.add(g.getId()));
        List<String> joined = new ArrayList<>();
        for (GroupMemberEntity m : groupMembers.findByUserIdAndGroupIdIn(a.getUserId(), aiGroups)) {
            if (m.active()) {
                joined.add(Long.toString(m.getGroupId()));
            }
        }
        String bio = profiles.findById(a.getUserId()).map(ProfileEntity::getBio).orElse(null);
        List<AiMemberModelHistoryEntity> rows = history.findTop20ByAiMemberIdOrderBySinceDescIdDesc(a.getId());
        Set<Long> operatorIds = new HashSet<>();
        if (a.getUpdatedBy() != null) {
            operatorIds.add(a.getUpdatedBy());
        }
        rows.stream().map(AiMemberModelHistoryEntity::getChangedBy).filter(Objects::nonNull).forEach(operatorIds::add);
        Map<Long, String> names = queries.operatorNames(operatorIds);
        String updatedBy = a.getUpdatedBy() == null ? null : names.get(a.getUpdatedBy());
        List<AiMemberViews.ModelHistory> modelHistory = rows.stream().map(h -> new AiMemberViews.ModelHistory(h.getProvider().name(), h.getModel(), h.getSince(),
                h.getChangedBy() == null ? null : new AiMemberViews.OperatorRef(Long.toString(h.getChangedBy()), names.get(h.getChangedBy())))).toList();
        return new AiMemberViews.Detail(row(a, ctx, s),
                new AiMemberViews.Persona(a.getGender().name(), a.getAgeBand().name(), a.getPersonality(), a.getTone(), a.getInterests()), bio == null ? "" : bio,
                new AiMemberViews.Windows(new AiMemberViews.Window(hhmm(a.getMorningStart()), hhmm(a.getMorningEnd())),
                        new AiMemberViews.Window(hhmm(a.getAfternoonStart()), hhmm(a.getAfternoonEnd()))),
                boards, joined, a.getUpdatedAt(), updatedBy, modelHistory);
    }

    private static Map<String, Object> snapshot(AiMemberProfileEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", a.getProvider().name());
        m.put("model", a.getModel());
        m.put("gender", a.getGender().name());
        m.put("ageBand", a.getAgeBand().name());
        m.put("dailyPosts", a.getDailyPosts());
        m.put("morning", hhmm(a.getMorningStart()) + "-" + hhmm(a.getMorningEnd()));
        m.put("afternoon", hhmm(a.getAfternoonStart()) + "-" + hhmm(a.getAfternoonEnd()));
        m.put("boardIds", a.getBoardIds().stream().map(String::valueOf).toList());
        return m;
    }

    /** {@code avatar:<id>} → 카탈로그 ID(올린 그림·없음은 null) */
    static String avatarIdOf(String profileImage) {
        if (profileImage == null || !profileImage.startsWith(AvatarCatalog.PROFILE_IMAGE_PREFIX)) {
            return null;
        }
        String id = profileImage.substring(AvatarCatalog.PROFILE_IMAGE_PREFIX.length());
        return AvatarCatalog.UPLOAD_ID.equals(id) ? null : id;
    }

    private AiMemberProfileEntity require(String idText) {
        return accounts.findById(parseId(idText)).orElseThrow(AiMemberNotFoundException::new);
    }

    private AiMemberSettingsEntity settingsRow() {
        return settings.findById(AiMemberSettingsEntity.ID).orElseThrow(() -> new IllegalStateException("ai_member_settings 행이 없다(V183)"));
    }

    private static long parseId(String raw) {
        Long id = parseLong(raw);
        if (id == null) {
            throw new AiMemberNotFoundException();
        }
        return id;
    }

    private static long parseGroupId(String raw) {
        Long id = parseLong(raw);
        if (id == null) {
            throw new GroupNotFoundException();
        }
        return id;
    }

    static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String hhmm(LocalTime t) {
        return t == null ? null : t.format(HHMM);
    }

    static Instant startOfToday() {
        return LocalDate.now(ServiceTime.ZONE).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
