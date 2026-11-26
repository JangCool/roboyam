package com.community.counsel.service;

import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.board.dto.EffectiveBoard;
import com.community.counsel.entity.CounselPostEntity;
import com.community.counsel.repository.master.CounselPostRepository;
import com.community.counsel.repository.master.CounselQueries;
import com.community.post.dto.response.PostCounselRowView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 글 목록 줄의 AI 연애상담소 표시({@code PostRow.counsel} — ai-love-counsel-contract). 게시판 글 목록({@code PostQueryService#list})이 쪽마다 한 번 부른다 — 상담 글이 없으면 질의하지 않고,
 * 있으면 판정 줄 한 번 + 상담사 답 수 한 번(+ 상담사 계정 한 번). 상태 판정은 상담 칸과 같다({@link CounselService#status}).
 * (상담 칸 서비스는 글 읽기 서비스를 쓰므로 목록 쪽은 이 부품으로 따로 둔다 — 서로 부르지 않게.)
 */
@Component
public class CounselRowReader {

    private final CounselPostRepository rows;
    private final CounselQueries queries;
    private final AiMemberProfileRepository accounts;

    public CounselRowReader(CounselPostRepository rows, CounselQueries queries, AiMemberProfileRepository accounts) {
        this.rows = rows;
        this.queries = queries;
        this.accounts = accounts;
    }

    /**
     * @param prefixByPost 글 ID → 말머리 ID(말머리 없는 글은 빼도 된다)
     * @return 「AI 상담소」 말머리 글만 — 글 ID → 표시
     */
    public Map<Long, PostCounselRowView> rowsOf(EffectiveBoard b, Map<Long, Long> prefixByPost) {
        Map<Long, PostCounselRowView> out = new HashMap<>();
        List<Long> ids = prefixByPost.entrySet().stream().filter(e -> CounselRules.counselPrefix(b, e.getValue())).map(Map.Entry::getKey).toList();
        if (ids.isEmpty()) {
            return out;
        }
        Map<Long, CounselPostEntity> found = new HashMap<>();
        rows.findAllById(ids).forEach(r -> found.put(r.getPostId(), r));
        List<Long> counselorUsers = accounts.findByCounselorTrueOrderByIdAsc().stream().map(AiMemberProfileEntity::getUserId).toList();
        Map<Long, Long> counts = queries.answerCounts(ids, counselorUsers);
        for (Long id : ids) {
            int n = (int) Math.min(Integer.MAX_VALUE, counts.getOrDefault(id, 0L));
            out.put(id, new PostCounselRowView(CounselService.status(found.get(id), n), n));
        }
        return out;
    }
}
