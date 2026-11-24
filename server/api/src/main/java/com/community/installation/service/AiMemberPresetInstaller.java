package com.community.installation.service;

import com.community.aimember.entity.AiAgeBand;
import com.community.aimember.entity.AiGender;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiModelEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiRelationKind;
import com.community.aimember.entity.AiVoice;
import com.community.aimember.repository.master.AiModelRepository;
import com.community.aimember.service.AiMemberAccountFactory;
import com.community.aimember.service.AiMemberRules;
import com.community.aimember.service.AiModelCatalog;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.installation.dto.AiMemberPreset;
import com.community.installation.entity.InstallationEntity;
import com.community.installation.repository.master.InstallationRepositoryQDSL;
import com.community.pointshop.service.AvatarCatalog;
import com.community.user.repository.master.ProfileRepositoryQDSL;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 설치 구성의 <b>AI 기본 계정</b>({@link BoardPresetCatalog#aiMembersOf} — IROBIT 은 {@code install-presets/irobit.ai-members.json}, 데모 원고와 같은 11계정)을
 * <b>꺼진 채</b> 한 번 만든다 — 정본은 docs/features/install-contract.md 「AI 기본 계정 (2026-11-24)」(사용자 확정 2026-11-24).
 *
 * <h2>언제</h2>
 * <ul>
 *   <li>「구조만」 설치: 설치가 커밋된 <b>뒤</b> 요청 안에서({@link InstallationService#complete}) — 설치 트랜잭션 밖이라 실패해도 설치는 그대로다.</li>
 *   <li>「데모 포함」 설치: 데모를 넣은 <b>뒤</b>(성공·실패 모두 — {@link DemoInstallLauncher}). 데모 팩에 같은 계정이 있으니 닉네임이 겹쳐 모두 건너뛰고 표시만 채운다.
 *       데모가 실패해 롤백됐으면 여기서 11계정을 만든다. 데모보다 <b>먼저</b> 만들면 데모 팩의 같은 닉네임이 유니크 인덱스에 걸리므로, 데모를 넣는 중(결과가 아직 없고
 *       {@link InstallationService#DEMO_STALE_AFTER} 안)에는 하지 않는다.</li>
 *   <li>이미 설치된 곳(이 기능 전에 설치된 운영 등): 공개 서버가 뜰 때 한 번({@link BoardPresetBackfillRunner}).</li>
 * </ul>
 *
 * <h2>한 번만 · 여러 서버</h2>
 * 설치 행을 쓰기 잠금으로 잡고 {@code installation.ai_members_seeded_at}(V266)이 비어 있을 때만 만들고, 같은 트랜잭션에서 그 칸을 채운다 — 공개 서버 여러 대가 동시에 떠도
 * 두 번째는 첫 번째가 끝나길 기다렸다가 채워진 칸을 보고 돌아간다. 구성에 AI 기본 계정이 없으면(NEUTRAL) 만들 것 없이 칸만 채운다. 운영자가 나중에 계정을 지워도 다시 만들지 않는다.
 *
 * <h2>계정마다</h2>
 * <ul>
 *   <li>같은 닉네임의 회원이 이미 있으면 건너뛴다(데모로 이미 있는 로컬 DB 등).</li>
 *   <li>모델은 이름(제공사 + 모델 아이디)으로 — 이 DB 에 켜져 있지 않으면 그 제공사 기본 모델, 그것도 없으면 경고하고 그 계정을 건너뛴다.</li>
 *   <li>게시판은 이 DB 에 있고 켜져 있고 AI 가 쓸 수 있는 것만({@link AiMemberRules#blockedReason}) — 하나도 남지 않으면 경고하고 건너뛴다.</li>
 *   <li>아바타가 카탈로그에 없으면 아바타 없이 만든다(데모와 같은 규칙).</li>
 *   <li>관계(부부·커플)는 양쪽 모두 이번에 만들어졌을 때만 잇는다. 소모임은 넣지 않는다(AI 전용 소모임은 데모 콘텐츠다).</li>
 *   <li>만든 이 = 루트 운영자, 감사 기록 {@code AI_MEMBER_CREATE}(detail {@code source = INSTALL_PRESET}). 전역 AI 설정({@code ai_member_settings})은 건드리지 않는다.</li>
 * </ul>
 * 샘플 글·댓글·모델 이력 연출은 넣지 않는다. 계정 하나라도 DB 에서 실패하면 이번 차례 전체가 롤백되고 칸은 비어 있어 다음 기동이 다시 시도한다.
 */
@Service
public class AiMemberPresetInstaller {

    private static final Logger log = LoggerFactory.getLogger(AiMemberPresetInstaller.class);
    static final String AUDIT_SOURCE = "INSTALL_PRESET";

    private final InstallationRepositoryQDSL installation;
    private final BoardPresetCatalog presets;
    private final AiMemberAccountFactory factory;
    private final AiModelCatalog modelCatalog;
    private final AiModelRepository models;
    private final BoardCatalog boards;
    private final AvatarCatalog avatars;
    private final ProfileRepositoryQDSL profiles;
    private final AuditLogService audit;
    private final TransactionTemplate tx;

    public AiMemberPresetInstaller(InstallationRepositoryQDSL installation, BoardPresetCatalog presets, AiMemberAccountFactory factory, AiModelCatalog modelCatalog,
                                   AiModelRepository models, BoardCatalog boards, AvatarCatalog avatars, ProfileRepositoryQDSL profiles, AuditLogService audit,
                                   PlatformTransactionManager transactions) {
        this.installation = installation;
        this.presets = presets;
        this.factory = factory;
        this.modelCatalog = modelCatalog;
        this.models = models;
        this.boards = boards;
        this.avatars = avatars;
        this.profiles = profiles;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactions);
    }

    /** 만든 결과 — 만든 계정 수 · 건너뛴 계정 수 */
    public record Outcome(int created, int skipped) {
    }

    /**
     * 할 차례면 한 번 만든다 — 자기 트랜잭션. <b>던지지 않는다</b>(설치·기동을 깨지 않는다 — 원인은 WARN, 칸이 비어 있으니 다음 기동이 다시 시도한다).
     *
     * @param when 로그에 남길 때(「설치 직후」 · 「데모 뒤」 · 「기동」)
     * @return 이번에 만들었으면(또는 넣을 것이 없어 표시만 했으면) 그 결과, 할 차례가 아니었거나 실패했으면 비어 있다
     */
    public Optional<Outcome> seedOnce(String when) {
        try {
            return Optional.ofNullable(tx.execute(status -> seedLocked(when)));
        } catch (RuntimeException e) {
            log.warn("AI 기본 계정을 만들지 못해 건너뛰었다({}) — 설치는 그대로, 다음 기동에서 다시 시도한다. 원인={}", when, e.toString(), e);
            return Optional.empty();
        }
    }

    private Outcome seedLocked(String when) {
        InstallationEntity row = installation.lockRow().orElse(null);
        if (row == null || !row.isCompleted() || row.getAiMembersSeededAt() != null) {
            return null;
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (demoInProgress(row, now)) {
            log.info("데모 데이터를 넣는 중이라 AI 기본 계정은 데모 뒤에 만든다({})", when);
            return null;
        }
        Optional<AiMemberPreset> file = presets.aiMembersOf(row.getBoardPreset());
        if (file.isEmpty()) {
            installation.markAiMembersSeeded(now); // 이 구성에는 AI 기본 계정이 없다 — 다시 보지 않게 표시만
            return new Outcome(0, 0);
        }
        Long rootId = row.getRootOperatorId();
        if (rootId == null) {
            log.warn("루트 운영자가 없어 AI 기본 계정을 만들지 않았다({})", when);
            return null;
        }
        Outcome outcome = createAll(file.get(), rootId, now);
        installation.markAiMembersSeeded(now);
        log.info("AI 기본 계정({}) — 만듦 {} · 건너뜀 {} · 모두 꺼진 채 preset={}", when, outcome.created(), outcome.skipped(), row.getBoardPreset());
        return outcome;
    }

    /** 데모를 고르고 결과가 아직 없는 채로 오래되지 않았다 — 다른 서버(또는 이 서버의 데모 스레드)가 데모를 넣는 중 */
    static boolean demoInProgress(InstallationEntity row, Instant now) {
        return row.isDemoData() && row.getDemoStatus() == null && row.getInstalledAt() != null
                && row.getInstalledAt().isAfter(now.minus(InstallationService.DEMO_STALE_AFTER));
    }

    /** 파일의 계정을 만든다(표시 칸은 건드리지 않는다) — 부르는 쪽의 트랜잭션 안에서. 시험이 직접 부른다 */
    Outcome createAll(AiMemberPreset file, long rootId, Instant now) {
        boards.invalidate(); // 설치 트랜잭션이 막 만든 게시판까지 보게 — 이 서버의 목록 캐시를 다시 읽는다
        BoardCatalogSnapshot snap = boards.snapshot();
        AiModelCatalog.Enabled enabled = modelCatalog.enabled();
        Map<String, AiMemberProfileEntity> created = new LinkedHashMap<>();
        int skipped = 0;
        for (AiMemberPreset.Account a : file.accounts()) {
            AiMemberProfileEntity made = createOne(a, snap, enabled, rootId, now);
            if (made == null) {
                skipped++;
            } else {
                created.put(a.nickname(), made);
            }
        }
        relate(file, created, rootId, now);
        return new Outcome(created.size(), skipped);
    }

    /** 계정 하나 — 건너뛰면 null */
    private AiMemberProfileEntity createOne(AiMemberPreset.Account a, BoardCatalogSnapshot snap, AiModelCatalog.Enabled enabled, long rootId, Instant now) {
        String nick = a.nickname();
        if (profiles.existsByNickname(nick)) {
            log.info("AI 기본 계정 {} — 같은 닉네임의 회원이 이미 있어 건너뛴다", nick);
            return null;
        }
        AiProvider provider = AiProvider.valueOf(a.provider());
        String model = resolveModel(provider, a.model(), enabled, nick);
        if (model == null) {
            return null;
        }
        List<Long> boardIds = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (String slug : new LinkedHashSet<>(a.boards())) {
            EffectiveBoard board = snap.board(slug).filter(b -> !BoardFields.KIND_GROUP.equals(b.kind()) && b.active() && AiMemberRules.blockedReason(b) == null)
                    .orElse(null);
            if (board == null) {
                dropped.add(slug);
            } else {
                boardIds.add(board.id());
            }
        }
        if (!dropped.isEmpty()) {
            log.info("AI 기본 계정 {} — 이 DB 에 없거나 AI 가 쓸 수 없는 게시판 {} 은 뺀다", nick, dropped);
        }
        if (boardIds.isEmpty()) {
            log.warn("AI 기본 계정 {} — 활동할 게시판이 하나도 없어 건너뛴다", nick);
            return null;
        }
        String avatarId = a.avatarId();
        boolean animated = false;
        if (avatarId != null) {
            AvatarCatalog.Avatar avatar = avatars.find(avatarId).orElse(null);
            if (avatar == null) {
                log.warn("AI 기본 계정 {} — 아바타 {} 가 카탈로그에 없어 아바타 없이 만든다", nick, avatarId);
                avatarId = null;
            } else {
                animated = avatar.animated();
            }
        }
        AiMemberPreset.Persona p = a.persona();
        LocalTime[] morning = AiMemberPresetValidator.window(a.windows().morning());
        LocalTime[] afternoon = AiMemberPresetValidator.window(a.windows().afternoon());
        String extra = a.extraPrompt() == null ? null : a.extraPrompt().replace("\r\n", "\n").strip();
        AiMemberProfileEntity.Values values = new AiMemberProfileEntity.Values(provider, model, AiGender.valueOf(p.gender()), AiAgeBand.valueOf(p.ageBand()),
                p.personality().strip(), p.tone().strip(), p.interests().strip(), morning[0], morning[1], afternoon[0], afternoon[1],
                a.voice() == null ? AiVoice.NORMAL : AiVoice.valueOf(a.voice()), boardIds, a.dailyPostMax(), a.newsRatio(), extra == null || extra.isEmpty() ? null : extra);
        String bio = a.bio() == null ? "" : a.bio().strip();
        AiMemberProfileEntity made = factory.create(nick, bio, values, avatarId, animated, rootId, now);
        audit.record(AuditEvent.byOperator(rootId, null, AuditActions.AI_MEMBER_CREATE, AuditResult.SUCCESS)
                .target("AI_MEMBER", Long.toString(made.getId())).with("userId", Long.toString(made.getUserId())).with("provider", provider.name())
                .with("model", model).with("voice", made.getVoice().name()).with("source", AUDIT_SOURCE));
        return made;
    }

    /** 원고의 모델이 켜져 있으면 그것, 아니면 그 제공사 기본 모델(켜져 있을 때), 그것도 없으면 null(건너뜀) */
    private String resolveModel(AiProvider provider, String wanted, AiModelCatalog.Enabled enabled, String nick) {
        if (enabled.allows(provider, wanted)) {
            return wanted;
        }
        String fallback = models.findFirstByProviderAndDefaultModelTrue(provider).map(AiModelEntity::getModelId).filter(m -> enabled.allows(provider, m)).orElse(null);
        if (fallback == null) {
            log.warn("AI 기본 계정 {} — 모델 {}/{} 도, 그 제공사의 켜진 기본 모델도 없어 건너뛴다", nick, provider, wanted);
            return null;
        }
        log.warn("AI 기본 계정 {} — 모델 {}/{} 가 이 DB 에 켜져 있지 않아 기본 모델 {} 로 만든다", nick, provider, wanted, fallback);
        return fallback;
    }

    /** 관계 — 양쪽 모두 이번에 만들었을 때만(이미 있던 계정의 관계는 건드리지 않는다) */
    private static void relate(AiMemberPreset file, Map<String, AiMemberProfileEntity> created, long rootId, Instant now) {
        for (AiMemberPreset.Account a : file.accounts()) {
            AiMemberProfileEntity self = created.get(a.nickname());
            if (a.relation() == null || self == null || self.getPartnerId() != null) {
                continue;
            }
            AiMemberProfileEntity partner = created.get(a.relation().partner());
            if (partner == null || partner.getPartnerId() != null) {
                continue;
            }
            AiRelationKind kind = AiRelationKind.valueOf(a.relation().kind());
            self.relate(kind, partner.getId(), rootId, now);
            partner.relate(kind, self.getId(), rootId, now);
        }
    }
}
