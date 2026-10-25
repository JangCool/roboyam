package com.community.debate.service;

import com.community.debate.entity.PostDebateEntity;
import com.community.debate.repository.master.PostDebateRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글의 찬반 토론 열기·설정 바꾸기·닫기 — 공개 사이트(글쓴이·게시판 매니저), 관리자 「토론 관리」, 하이라이트 만들기·고치기가 <b>같은 코드</b>를 쓴다(docs/features/post-debate-contract.md).
 * 누가 할 수 있는지는 부르는 쪽이 정한다(여기는 저장만). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <ul>
 *   <li>열기: 행이 없으면 만든다 — {@code INSERT … ON CONFLICT DO NOTHING} 이라 같은 글을 동시에 열어도 한 행만 남고 늦은 쪽은 설정 바꾸기로 이어 간다.
 *       값이 null 이면 기본(켬). 연 사람({@code opened_by_kind})은 처음 연 사람으로 남는다.</li>
 *   <li>바꾸기: 열려 있을 때 null 이 아닌 칸만 바꾼다(같으면 아무것도 바꾸지 않는다).</li>
 *   <li>닫기: 행을 지운다 — 입장·의견·고정·고정 요청은 그대로(다시 열면 돌아온다). 이미 닫혀 있으면 그대로.</li>
 * </ul>
 */
@Component
public class PostDebateWriter {

    private final PostDebateRepository debates;
    private final JdbcTemplate jdbc;

    public PostDebateWriter(PostDebateRepository debates, DataSource dataSource) {
        this.debates = debates;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 토론 설정 한 벌(감사·응답용).
     *
     * @param openedByKind 저장 값 MEMBER · MANAGER · OPERATOR
     */
    public record State(boolean neutralEnabled, boolean stanceChangeAllowed, String openedByKind) {

        static State of(PostDebateEntity row) {
            return new State(row.isNeutralEnabled(), row.isStanceChangeAllowed(), row.getOpenedByKind());
        }

        /** 감사용 칸 값 */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("neutralEnabled", neutralEnabled);
            map.put("stanceChangeAllowed", stanceChangeAllowed);
            map.put("openedBy", openedByKind);
            return map;
        }
    }

    /**
     * 바뀐 결과.
     *
     * @param before 바꾸기 전(닫혀 있었으면 null)
     * @param after  바꾼 뒤(닫았으면 null)
     */
    public record Change(State before, State after) {

        public boolean opened() {
            return before == null && after != null;
        }

        public boolean closed() {
            return before != null && after == null;
        }

        /** 열린 채로 설정이 바뀌었다 */
        public boolean updated() {
            return before != null && after != null && !before.equals(after);
        }
    }

    /**
     * @param enabled             열기(true)·닫기(false)
     * @param neutralEnabled      「중립도 받기」 — null 이면 열 때 켬, 열려 있으면 그대로
     * @param stanceChangeAllowed 「입장 바꾸기 허용」 — 같은 규칙
     * @param byKind              {@link PostDebateEntity#BY_MEMBER} · {@link PostDebateEntity#BY_MANAGER} · {@link PostDebateEntity#BY_OPERATOR}
     * @param by                  회원 ID(MEMBER·MANAGER) 또는 운영자 ID(OPERATOR)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Change apply(long postId, boolean enabled, Boolean neutralEnabled, Boolean stanceChangeAllowed, String byKind, Long by, Instant now) {
        if (!enabled) {
            PostDebateEntity row = debates.findWithLockByPostId(postId).orElse(null);
            if (row == null) {
                return new Change(null, null);
            }
            State before = State.of(row);
            debates.delete(row);
            debates.flush();
            return new Change(before, null);
        }
        boolean neutral = neutralEnabled == null || neutralEnabled;
        boolean change = stanceChangeAllowed == null || stanceChangeAllowed;
        Timestamp at = Timestamp.from(now);
        int inserted = jdbc.update("""
                INSERT INTO post_debates (post_id, neutral_enabled, stance_change_allowed, opened_by_kind, opened_by, created_at, updated_at, updated_by_kind, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, postId, neutral, change, byKind, by, at, at, byKind, by);
        PostDebateEntity row = debates.findWithLockByPostId(postId).orElseThrow();
        if (inserted > 0) {
            return new Change(null, State.of(row));
        }
        State before = State.of(row);
        boolean nextNeutral = neutralEnabled == null ? row.isNeutralEnabled() : neutralEnabled;
        boolean nextChange = stanceChangeAllowed == null ? row.isStanceChangeAllowed() : stanceChangeAllowed;
        if (nextNeutral != row.isNeutralEnabled() || nextChange != row.isStanceChangeAllowed()) {
            row.change(nextNeutral, nextChange, byKind, by, now);
            debates.flush();
        }
        return new Change(before, State.of(row));
    }
}
