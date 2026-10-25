package com.community.poll.service;

import com.community.board.dto.EffectiveBoard;
import com.community.poll.dto.PollData;
import com.community.poll.dto.PollOptionData;
import com.community.poll.dto.request.PollInput;
import com.community.poll.exception.PollLockedException;
import com.community.poll.repository.master.PollQueries;
import com.community.post.entity.PostEntity;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 글쓰기·고치기({@code PostWriteService})에 붙는 투표 — 검사는 글 칸 검사와 함께(트랜잭션 밖), 저장은 글 저장 트랜잭션 안에서. docs/features/post-poll-contract.md.
 *
 * <ul>
 *   <li><b>쓰기</b>: {@code poll} 이 있으면 회원 + 게시판 {@code pollEnabled} 일 때만(아니면 400 {@code fields.poll}), 칸 오류는 {@code fields."poll.*"}.</li>
 *   <li><b>고치기</b>: 칸을 <b>아예 빼면 그대로</b>, {@code null} 이면 빼기, 값이면 만들기·고치기. 첫 표가 들어온 투표를 고치거나 빼면 409 {@code POLL_LOCKED} —
 *       지금과 똑같은 내용(질문·선택지 차례·복수·다시 투표·마감)이면 글만 고친다. 비회원 글에는 붙일 수 없고, 게시판 {@code pollEnabled} 가 꺼졌으면 새로 붙이거나 바꿀 수 없다
 *       (같은 내용·빼기는 된다 — 나중에 꺼도 이미 붙은 투표는 그대로). 고치지 않은 지난 마감 시각은 다시 검사하지 않는다.</li>
 *   <li><b>잠금</b>: 고치는 쪽이 {@code polls} 행을 {@code FOR UPDATE} 로 잡고 표가 있는지 본다 — 투표하는 쪽({@code FOR SHARE})과 어긋나지 않는다.</li>
 * </ul>
 *
 * <p>고치기의 칸 구분(빠짐·null·값)은 record 로는 되지 않아 요청 칸을 {@link JsonNode} 로 받는다(Jackson 3 는 빠진 칸 = Java null, JSON null = {@code NullNode}).
 */
@Component
public class PollWriter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final PollQueries queries;
    private final SnowflakeIdGenerator ids;

    public PollWriter(PollQueries queries, SnowflakeIdGenerator ids) {
        this.queries = queries;
        this.ids = ids;
    }

    /** 고치기의 투표 바꾸기 — {@code valid} 는 {@link Kind#SET} 일 때만 */
    public record Change(Kind kind, PollRules.Valid valid) {

        public static final Change KEEP = new Change(Kind.KEEP, null);
        public static final Change REMOVE = new Change(Kind.REMOVE, null);
    }

    public enum Kind {
        /** 칸이 빠졌다 — 그대로 */
        KEEP,
        /** {@code null} — 빼기 */
        REMOVE,
        /** 값 — 만들기·고치기 */
        SET
    }

    /**
     * 쓰기의 투표 검사.
     *
     * @return 붙일 투표(없거나 칸 오류면 null — 칸 오류는 {@code fields} 에)
     */
    public PollRules.Valid checkForCreate(PollInput input, boolean guest, EffectiveBoard b, Instant now, Map<String, MessageRef> fields) {
        if (input == null) {
            return null;
        }
        if (guest || !b.pollEnabled()) {
            fields.put("poll", new MessageRef("poll.notAllowed")); // 회원 + 게시판 pollEnabled 일 때만
            return null;
        }
        return PollRules.check(input, now, null, fields);
    }

    /** 고치기의 투표 검사(잠그지 않고 지금 투표를 한 번 읽는다 — 저장할 때 {@link #apply} 가 잠그고 다시 본다) */
    public Change checkForUpdate(JsonNode raw, PostEntity post, EffectiveBoard b, Instant now, Map<String, MessageRef> fields) {
        if (raw == null) {
            return Change.KEEP;
        }
        if (raw.isNull()) {
            return Change.REMOVE;
        }
        PollInput input;
        try {
            input = raw.isObject() ? MAPPER.treeToValue(raw, PollInput.class) : null;
        } catch (JacksonException e) {
            input = null;
        }
        if (input == null) {
            fields.put("poll", new MessageRef("poll.invalid"));
            return Change.KEEP;
        }
        Optional<PollData> current = queries.find(post.getId());
        if (post.isGuest() || (!b.pollEnabled() && current.isEmpty())) {
            fields.put("poll", new MessageRef("poll.notAllowed"));
            return Change.KEEP;
        }
        PollRules.Valid valid = PollRules.check(input, now, current.map(PollData::closesAt).orElse(null), fields);
        if (!PollRules.hasErrors(fields) && !b.pollEnabled() && !same(current.get(), valid)) {
            fields.put("poll", new MessageRef("poll.notAllowed")); // 꺼진 게시판 — 이미 붙은 투표는 그대로 두거나 뺄 수만 있다
        }
        return new Change(Kind.SET, valid);
    }

    /** 새 글에 투표를 붙인다 — 글 저장 트랜잭션 안에서 */
    public void create(long postId, PollRules.Valid poll, Instant now) {
        long pollId = ids.nextId();
        queries.insert(pollId, postId, poll.question(), poll.multiple(), poll.changeAllowed(), poll.closesAt(), now);
        queries.insertOptions(pollId, newIds(poll.options().size()), poll.options());
    }

    /** 고친 글의 투표를 맞춘다 — 글 고치기 트랜잭션 안에서. 첫 표가 들어온 투표를 바꾸거나 빼면 409 POLL_LOCKED(글도 고쳐지지 않는다) */
    public void apply(long postId, Change change, Instant now) {
        if (change.kind() == Kind.KEEP) {
            return;
        }
        Optional<PollData> locked = queries.findForUpdate(postId);
        if (change.kind() == Kind.REMOVE) {
            if (locked.isEmpty()) {
                return;
            }
            if (queries.hasVotes(locked.get().id())) {
                throw new PollLockedException();
            }
            queries.delete(locked.get().id());
            return;
        }
        PollRules.Valid poll = change.valid();
        if (locked.isEmpty()) {
            create(postId, poll, now);
            return;
        }
        PollData current = locked.get();
        if (same(current, poll)) {
            return;
        }
        if (queries.hasVotes(current.id())) {
            throw new PollLockedException();
        }
        queries.update(current.id(), poll.question(), poll.multiple(), poll.changeAllowed(), poll.closesAt(), now);
        queries.deleteOptions(current.id());
        queries.insertOptions(current.id(), newIds(poll.options().size()), poll.options());
    }

    /** 지금 투표와 똑같은 내용인가(질문·선택지 차례·복수·다시 투표·마감) */
    private boolean same(PollData current, PollRules.Valid poll) {
        return current.question().equals(poll.question()) && current.multiple() == poll.multiple() && current.changeAllowed() == poll.changeAllowed()
                && Objects.equals(current.closesAt(), poll.closesAt())
                && queries.options(current.id()).stream().map(PollOptionData::label).toList().equals(poll.options());
    }

    private List<Long> newIds(int n) {
        return IntStream.range(0, n).mapToObj(i -> ids.nextId()).toList();
    }
}
