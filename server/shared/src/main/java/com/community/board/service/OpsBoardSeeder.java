package com.community.board.service;

import com.community.board.dto.BoardPolicyValues;
import com.community.board.dto.NewBoard;
import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.grade.entity.GradeEntity;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.menu.dto.MenuNode;
import com.community.menu.entity.MenuNodeEntity;
import com.community.menu.entity.MenuSetEntity;
import com.community.menu.repository.master.MenuNodeRepository;
import com.community.menu.repository.master.MenuSetRepository;
import com.community.menu.target.BoardMenuTargetResolver;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 게시판 두 개(버그 신고 · 기능 요청)와 공개 메뉴 그룹 {@code footer} 를 만든다 — 새 설치의 프리셋 적용({@code BoardPresetApplier})이 부른다.
 * 값은 {@link OpsBoardPreset}(이미 설치된 DB 는 V168 마이그레이션이 같은 값으로 넣는다).
 *
 * <ul>
 *   <li>주소나 이름(대소문자 무시)이 이미 있는 게시판은 건너뛴다. 메뉴 그룹 {@code footer} 가 이미 있으면 그룹·항목을 만들지 않는다(V168 과 같은 규칙).</li>
 *   <li>메인 메뉴의 자동 추가 폴더에 넣지 않는다({@link BoardCreator#create(NewBoard, int, Long, Instant, boolean)} — 들어가는 길은 푸터뿐).</li>
 *   <li>게시판 순서는 지금 있는 마지막 순서 다음부터 1씩. 부르는 쪽의 트랜잭션 안에서만 돈다.</li>
 * </ul>
 */
@Component
public class OpsBoardSeeder {

    private final BoardCreator creator;
    private final BoardRepository boards;
    private final GradeRepositoryQDSL grades;
    private final MenuSetRepository sets;
    private final MenuNodeRepository nodes;
    private final SnowflakeIdGenerator ids;

    public OpsBoardSeeder(BoardCreator creator, BoardRepository boards, GradeRepositoryQDSL grades, MenuSetRepository sets, MenuNodeRepository nodes,
                          SnowflakeIdGenerator ids) {
        this.creator = creator;
        this.boards = boards;
        this.grades = grades;
        this.sets = sets;
        this.nodes = nodes;
        this.ids = ids;
    }

    /**
     * @param operatorId 만든 이(루트 운영자 — 없으면 null)
     * @return 만든 게시판 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int seed(Long operatorId, Instant now) {
        List<BoardEntity> existing = boards.findAllByOrderBySortOrderAscIdAsc();
        int order = existing.stream().mapToInt(BoardEntity::getSortOrder).max().orElse(0);
        String firstGrade = grades.findAllOrdered().stream().findFirst().map(GradeEntity::getGradeKey).orElse(null);
        int created = 0;
        for (OpsBoardPreset.Board spec : OpsBoardPreset.BOARDS) {
            String lower = spec.name().toLowerCase(Locale.ROOT);
            boolean taken = existing.stream().anyMatch(b -> b.getSlug().equals(spec.slug()) || b.getName().toLowerCase(Locale.ROOT).equals(lower));
            if (taken) {
                continue;
            }
            List<NewBoard.Prefix> own = spec.prefixes().stream().map(name -> new NewBoard.Prefix(name, false, null)).toList();
            NewBoard input = new NewBoard(spec.slug(), spec.name(), null, spec.description(), BoardFields.KIND_GENERAL, OpsBoardPreset.LAYOUT, true, null, false, null,
                    Set.of(OpsBoardPreset.COMMON_ACTIVE), own);
            BoardEntity board = creator.create(input, ++order, operatorId, now, false);
            board.changeGuides(null, spec.introText(), null);
            board.changeWriteTemplate(spec.writeTemplate());
            board.changeGadgetExclusions(OpsBoardPreset.GADGET_EXCLUSIONS);
            board.changeAdsHidden(true);
            board.changeOps(spec.secretMode(), true);
            board.changePolicy(policy(firstGrade));
            created++;
        }
        boards.flush(); // 메뉴 항목이 가리킬 게시판 행을 먼저
        if (sets.findById(OpsBoardPreset.FOOTER_SET).isEmpty()) {
            sets.persist(MenuSetEntity.create(OpsBoardPreset.FOOTER_SET, OpsBoardPreset.FOOTER_NAME, OpsBoardPreset.FOOTER_NAME_EN, OpsBoardPreset.FOOTER_PLACEMENT,
                    true, OpsBoardPreset.FOOTER_DESCRIPTION, operatorId, now));
            sets.flush();
            List<Long> targets = new ArrayList<>();
            for (OpsBoardPreset.Board spec : OpsBoardPreset.BOARDS) {
                boards.findBySlug(spec.slug()).ifPresent(b -> targets.add(b.getId()));
            }
            int sort = 0;
            for (long boardId : targets) {
                MenuNode item = new MenuNode(null, MenuNode.ITEM, null, null, true, "ALL", null, false, false, false, BoardMenuTargetResolver.TYPE,
                        Long.toString(boardId), null, null, false, List.of());
                nodes.persist(MenuNodeEntity.of(ids.nextId(), OpsBoardPreset.FOOTER_SET, null, sort++, item));
            }
        }
        return created;
    }

    /** 정책 — 글쓰기·댓글 첫 등급 · 이미지 켬 · 색인 끔 · 검색 켬 · 적립 끔 · 토론·투표 끔(나머지는 기본값 따름) */
    static BoardPolicyValues policy(String firstGrade) {
        return new BoardPolicyValues(firstGrade, firstGrade, null, true, null, null, null, false, true, null, "OFF", null, null, null, null, null, null, null, null,
                null, null, null, false, false, null, null, null, null, null);
    }
}
