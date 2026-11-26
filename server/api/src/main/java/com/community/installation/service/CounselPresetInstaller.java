package com.community.installation.service;

import com.community.aimember.entity.AiAgeBand;
import com.community.aimember.entity.AiGender;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiModelEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiVoice;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiModelRepository;
import com.community.aimember.service.AiMemberAccountFactory;
import com.community.aimember.service.AiMemberRules;
import com.community.aimember.service.AiModelCatalog;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardPrefixEntity;
import com.community.board.repository.master.BoardPrefixRepository;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.installation.entity.InstallationEntity;
import com.community.installation.repository.master.InstallationRepositoryQDSL;
import com.community.user.repository.master.ProfileRepositoryQDSL;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 설치 구성의 <b>AI 연애상담소 기본값</b>을 한 번 넣는다(ai-love-counsel-contract — 「결정」 상담 전용 AI 계정 3개). 연애·결혼 게시판({@value #BOARD_SLUG})이 있는 곳만:
 * <ul>
 *   <li><b>말머리</b> 「{@value #PREFIX_NAME}」(상담소 표시 {@code counsel}) — 그 게시판에 상담소 말머리가 없을 때만. 같은 이름 말머리가 있으면 그 말머리에 표시만 켜고, 없으면
 *       <b>꺼진 채</b>(고유 말머리 맨 앞) 만든다 — 운영자가 관리자 게시판 화면에서 켠다(키를 넣고 상담사를 켜기 전에 회원이 쓰면 답이 오지 않으므로).</li>
 *   <li><b>상담사 3계정</b>(팩폭언니 직설형 · 토닥이 공감형 · 연애분석관 현실 분석형) — 상담사가 하나도 없을 때만, 같은 닉네임 회원이 있으면 그 계정은 건너뛴다. 보통 AI 회원과 같이
 *       <b>꺼진 채</b>(로그인할 수 없는 회원 — {@link AiMemberAccountFactory}), 쓸 곳 = 연애·결혼 게시판, 새 글 상한 0. 모델은 Gemini 18세 미만 조항이 미확정이라 GPT·Claude 계열
 *       (원하는 모델이 꺼져 있으면 그 제공사 기본 모델). 아바타는 넣지 않는다(관리자가 고른다).</li>
 * </ul>
 * 언제: AI 기본 계정({@link AiMemberPresetInstaller#seedOnce})이 끝난 바로 뒤(설치 직후 · 데모 뒤 · 공개 서버 기동). 한 번만: 설치 행 잠금 + {@code installation.counsel_seeded_at}(V274).
 * 데모를 넣는 중이면 데모 뒤로 미룬다. 던지지 않는다(실패하면 다음 기동이 다시 시도). 만든 이 = 루트 운영자, 감사 {@code AI_MEMBER_CREATE}(detail {@code source = INSTALL_PRESET} · {@code counselor = true}).
 */
@Service
public class CounselPresetInstaller {

    private static final Logger log = LoggerFactory.getLogger(CounselPresetInstaller.class);

    static final String BOARD_SLUG = "love";
    static final String PREFIX_NAME = "AI 상담소";

    /** 상담사 원고 — 닉네임 · 성향 · 제공사 · 원하는 모델 · 페르소나 · 공개 소개 */
    record Counselor(String nickname, String style, AiProvider provider, String model, AiGender gender, AiAgeBand ageBand, String personality, String tone,
                     String interests, String bio) {
    }

    static final List<Counselor> COUNSELORS = List.of(
            new Counselor("팩폭언니", "직설형", AiProvider.OPENAI, "gpt-6-luna", AiGender.FEMALE, AiAgeBand.THIRTIES,
                    "돌려 말하지 않는 현실파 언니. 연애 고민을 많이 들어 봐서 핵심을 빨리 짚고, 듣기 불편한 말도 분명히 한다.",
                    "시원시원한 존댓말. 결론부터 말하고 이유를 짧게 붙인다.", "연애 상담, 관계 심리, 이별 극복, 자존감",
                    "AI 연애상담소의 직설형 상담사예요. AI 회원이고 성격은 설정이에요. 전문 상담이 아니니 참고만 해 주세요."),
            new Counselor("토닥이", "공감형", AiProvider.ANTHROPIC, "claude-haiku-5-5", AiGender.FEMALE, AiAgeBand.TWENTIES,
                    "다정하고 차분한 공감형. 마음을 먼저 알아주고, 다그치지 않고 작게 해 볼 수 있는 한 걸음을 권한다.",
                    "따뜻하고 부드러운 존댓말. 감정을 되짚어 주는 말로 시작한다.", "연애 고민, 마음 돌보기, 대화법, 자존감",
                    "AI 연애상담소의 공감형 상담사예요. AI 회원이고 성격은 설정이에요. 전문 상담이 아니니 참고만 해 주세요."),
            new Counselor("연애분석관", "현실 분석형", AiProvider.OPENAI, "gpt-6.1-terra", AiGender.MALE, AiAgeBand.FORTIES,
                    "상황을 차분히 나눠 보는 분석가. 감정보다 사실과 현실적인 조건을 보고, 선택지마다 좋은 점과 걸리는 점을 정리한다.",
                    "정돈된 존댓말. 상황 · 선택지 · 제안 차례로 말한다.", "관계 패턴, 결혼 준비, 현실 조건, 갈등 정리",
                    "AI 연애상담소의 현실 분석형 상담사예요. AI 회원이고 성격은 설정이에요. 전문 상담이 아니니 참고만 해 주세요."));

    private final InstallationRepositoryQDSL installation;
    private final BoardCatalog boards;
    private final BoardPrefixRepository prefixes;
    private final AiMemberProfileRepository accounts;
    private final AiMemberAccountFactory factory;
    private final AiModelCatalog modelCatalog;
    private final AiModelRepository models;
    private final ProfileRepositoryQDSL profiles;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public CounselPresetInstaller(InstallationRepositoryQDSL installation, BoardCatalog boards, BoardPrefixRepository prefixes, AiMemberProfileRepository accounts,
                                  AiMemberAccountFactory factory, AiModelCatalog modelCatalog, AiModelRepository models, ProfileRepositoryQDSL profiles,
                                  AuditLogService audit, SnowflakeIdGenerator ids, PlatformTransactionManager transactions) {
        this.installation = installation;
        this.boards = boards;
        this.prefixes = prefixes;
        this.accounts = accounts;
        this.factory = factory;
        this.modelCatalog = modelCatalog;
        this.models = models;
        this.profiles = profiles;
        this.audit = audit;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
    }

    /** 만든 결과 — 말머리를 만들었거나 표시를 켰는가 · 만든 상담사 수 */
    public record Outcome(boolean prefix, int counselors) {
    }

    /** 할 차례면 한 번 넣는다 — 자기 트랜잭션. <b>던지지 않는다</b> */
    public Optional<Outcome> seedOnce(String when) {
        try {
            Optional<Outcome> out = Optional.ofNullable(tx.execute(status -> seedLocked(when)));
            out.ifPresent(o -> boards.invalidate()); // 이 서버의 게시판 목록 사본 — 다른 서버는 TTL(몇 초)로 따라온다
            return out;
        } catch (RuntimeException e) {
            log.warn("AI 연애상담소 기본값을 넣지 못해 건너뛰었다({}) — 다음 기동에서 다시 시도한다. 원인={}", when, e.toString(), e);
            return Optional.empty();
        }
    }

    private Outcome seedLocked(String when) {
        InstallationEntity row = installation.lockRow().orElse(null);
        if (row == null || !row.isCompleted() || row.getCounselSeededAt() != null) {
            return null;
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (AiMemberPresetInstaller.demoInProgress(row, now)) {
            return null; // 데모를 넣는 중 — 데모 뒤에(데모 팩의 같은 닉네임과 겹치지 않게)
        }
        Long rootId = row.getRootOperatorId();
        if (rootId == null) {
            log.warn("루트 운영자가 없어 AI 연애상담소 기본값을 넣지 않았다({})", when);
            return null;
        }
        boards.invalidate(); // 막 만든 게시판까지 보게
        BoardCatalogSnapshot snap = boards.snapshot();
        EffectiveBoard love = snap.board(BOARD_SLUG).filter(b -> !BoardFields.KIND_GROUP.equals(b.kind()) && AiMemberRules.blockedReason(b) == null).orElse(null);
        if (love == null) {
            installation.markCounselSeeded(now); // 연애·결혼 게시판이 없는 구성 — 다시 보지 않게 표시만
            return new Outcome(false, 0);
        }
        boolean prefix = ensurePrefix(love, now);
        int made = 0;
        if (accounts.findByCounselorTrueOrderByIdAsc().isEmpty()) {
            AiModelCatalog.Enabled enabled = modelCatalog.enabled();
            for (Counselor c : COUNSELORS) {
                if (createOne(c, love, enabled, rootId, now)) {
                    made++;
                }
            }
        }
        installation.markCounselSeeded(now);
        log.info("AI 연애상담소 기본값({}) — 말머리 {} · 상담사 {}명(꺼진 채)", when, prefix ? "넣음(꺼진 채)" : "그대로", made);
        return new Outcome(prefix, made);
    }

    /** 상담소 말머리 — 이미 있으면 그대로, 같은 이름 말머리가 있으면 표시만, 없으면 꺼진 채 새로(고유 말머리 맨 앞) */
    private boolean ensurePrefix(EffectiveBoard love, Instant now) {
        if (love.prefixes().stream().anyMatch(p -> p.counsel() && !p.common())) {
            return false;
        }
        Optional<BoardPrefixInfo> same = love.prefixes().stream().filter(p -> !p.common() && PREFIX_NAME.equals(p.name().strip())).findFirst();
        if (same.isPresent()) {
            prefixes.findById(same.get().id()).ifPresent(p -> p.markCounsel(true, now));
            return true;
        }
        int first = love.prefixes().stream().filter(p -> !p.common()).mapToInt(BoardPrefixInfo::sortOrder).min().orElse(10);
        BoardPrefixEntity created = BoardPrefixEntity.own(ids.nextId(), love.id(), PREFIX_NAME, false, false, null, false, Math.max(5, first - 1), now);
        created.markCounsel(true, now);
        prefixes.persist(created);
        return true;
    }

    /** 상담사 하나 — 같은 닉네임이 있거나 쓸 모델이 없으면 건너뛴다(false) */
    private boolean createOne(Counselor c, EffectiveBoard love, AiModelCatalog.Enabled enabled, long rootId, Instant now) {
        if (profiles.existsByNickname(c.nickname())) {
            log.info("AI 상담사 {} — 같은 닉네임의 회원이 이미 있어 건너뛴다", c.nickname());
            return false;
        }
        String model = enabled.allows(c.provider(), c.model()) ? c.model()
                : models.findFirstByProviderAndDefaultModelTrue(c.provider()).map(AiModelEntity::getModelId).filter(m -> enabled.allows(c.provider(), m)).orElse(null);
        if (model == null) {
            log.warn("AI 상담사 {} — 모델 {}/{} 도, 그 제공사의 켜진 기본 모델도 없어 건너뛴다", c.nickname(), c.provider(), c.model());
            return false;
        }
        // 활동 창 · 새 글 상한은 상담사에게 쓰이지 않는다(상담 답은 시간대와 상관없이) — 칸 규칙(CHECK)만 맞춘다
        AiMemberProfileEntity.Values values = new AiMemberProfileEntity.Values(c.provider(), model, c.gender(), c.ageBand(), c.personality(), c.tone(), c.interests(),
                LocalTime.of(0, 0), LocalTime.of(12, 0), LocalTime.of(12, 0), LocalTime.of(23, 59), AiVoice.NORMAL, List.of(love.id()), 0, 0, null);
        AiMemberProfileEntity made = factory.create(c.nickname(), c.bio(), values, null, false, rootId, now);
        made.counsel(true, c.style(), rootId, now);
        audit.record(AuditEvent.byOperator(rootId, null, AuditActions.AI_MEMBER_CREATE, AuditResult.SUCCESS)
                .target("AI_MEMBER", Long.toString(made.getId())).with("userId", Long.toString(made.getUserId())).with("provider", c.provider().name())
                .with("model", model).with("voice", AiVoice.NORMAL.name()).with("source", AiMemberPresetInstaller.AUDIT_SOURCE).with("counselor", true)
                .with("style", c.style()));
        return true;
    }
}
