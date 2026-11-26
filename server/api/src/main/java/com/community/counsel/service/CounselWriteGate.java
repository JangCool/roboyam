package com.community.counsel.service;

import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.aimember.repository.master.AiMemberSettingsRepository;
import com.community.aimember.repository.master.MemberAiOptOutStore;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.counsel.repository.master.CounselQueries;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * AI 연애상담소 글쓰기 판정(ai-love-counsel-contract 「정책」 비용 — 회원당 하루 1건 · 사이트 하루 상한). 공개 글쓰기({@code PostWriteService#create})와 고치기(다른 말머리에서
 * 「AI 상담소」로 바꿀 때만)가 칸 검사 끝에 부른다. 걸리면 {@code fields.prefixId}(400 {@code VALIDATION} — 글쓰기 화면이 말머리 칸 아래에 문구를 보인다):
 * <ul>
 *   <li>비회원 — {@code counsel.prefix.memberOnly}(조언 고르기는 사연 쓴 회원만 한다)</li>
 *   <li>「내 글·댓글을 AI 회원에게 보내지 않기」(V264)를 켠 회원 — {@code counsel.prefix.optedOut}(사연이 해외 AI 제공사로 가므로)</li>
 *   <li>오늘(한국 날짜) 그 회원의 상담 글이 상한({@code counsel_member_daily}, 기본 1 — 지운 글 포함) 이상 — {@code counsel.prefix.dailyLimit}</li>
 *   <li>오늘 사이트 전체 상담 글이 상한({@code counsel_site_daily}, 기본 100) 이상 — {@code counsel.prefix.siteLimit}</li>
 * </ul>
 * 셈은 트랜잭션 밖의 질의 두 번이라 동시에 쓰면 한두 건 넘칠 수 있다 — 실행기가 사이트 상한을 다시 본다.
 */
@Component
public class CounselWriteGate {

    static final String FIELD = "prefixId";

    private final AiMemberSettingsRepository settings;
    private final MemberAiOptOutStore optOuts;
    private final CounselQueries queries;

    public CounselWriteGate(AiMemberSettingsRepository settings, MemberAiOptOutStore optOuts, CounselQueries queries) {
        this.settings = settings;
        this.optOuts = optOuts;
        this.queries = queries;
    }

    /**
     * @param prefixId       새 글(고친 글)의 말머리 — 「AI 상담소」가 아니면 아무것도 하지 않는다
     * @param previousPrefix 고치기 전 말머리(새 글이면 null) — 같으면(이미 상담 글) 보지 않는다
     * @param userId         쓴 회원(비회원 null)
     * @param exceptPostId   셈에서 뺄 글(고치기 — 그 글 자신), 새 글이면 null
     */
    public void check(BoardCatalogSnapshot s, EffectiveBoard b, Long prefixId, Long previousPrefix, Long userId, Long exceptPostId, Map<String, MessageRef> fields) {
        if (fields.containsKey(FIELD) || !CounselRules.counselPrefix(b, prefixId) || Objects.equals(prefixId, previousPrefix)) {
            return;
        }
        if (userId == null) {
            fields.put(FIELD, new MessageRef("counsel.prefix.memberOnly"));
            return;
        }
        if (optOuts.optedOut(userId)) {
            fields.put(FIELD, new MessageRef("counsel.prefix.optedOut"));
            return;
        }
        AiMemberSettingsEntity.CounselLimits limits = settings.findById(AiMemberSettingsEntity.ID).map(AiMemberSettingsEntity::getCounselLimits)
                .orElse(new AiMemberSettingsEntity.CounselLimits(1, 100));
        Set<Long> prefixIds = CounselRules.counselPrefixIds(s);
        Instant since = CounselRules.startOfDay(Instant.now());
        if (limits.memberDaily() <= 0 || queries.postsSince(prefixIds, userId, since, exceptPostId) >= limits.memberDaily()) {
            fields.put(FIELD, new MessageRef("counsel.prefix.dailyLimit", limits.memberDaily()));
            return;
        }
        if (limits.siteDaily() <= 0 || queries.postsSince(prefixIds, null, since, exceptPostId) >= limits.siteDaily()) {
            fields.put(FIELD, new MessageRef("counsel.prefix.siteLimit"));
        }
    }
}
