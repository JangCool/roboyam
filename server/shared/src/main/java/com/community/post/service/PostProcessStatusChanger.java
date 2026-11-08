package com.community.post.service;

import com.community.board.dto.EffectiveBoard;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostProcessStatus;
import com.community.post.entity.PostProcessStatusHistoryEntity;
import com.community.post.exception.PostStatusDisabledException;
import com.community.post.repository.master.PostProcessStatusHistoryRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 글의 처리 상태 바꾸기 한 곳(V166 운영 게시판 · V168 메모·이력) — 공개 사이트({@code PUT /api/posts/{id}/process-status}, 그 게시판 매니저·스텝·운영자 회원 계정,
 * 소모임 글이면 그 소모임의 소모임장·매니저)와 관리자 앱({@code PUT /api/admin/posts/{id}/process-status}, {@code post.manage})이 같은 규칙을 쓴다.
 * 권한·글 잠금·감사는 부르는 쪽이 한다.
 *
 * <ul>
 *   <li>값: {@link PostProcessStatus} 이름(대소문자 무시) — 비었거나 모르면 400 {@code fields.status}. 메모({@link #note}): 앞뒤 공백을 떼고 비면 없음, 200자까지,
 *       줄 바꿈·제어 문자 불가(400 {@code fields.note}).</li>
 *   <li>게시판이 상태를 쓰지 않으면 409 {@code POST_STATUS_DISABLED}(핫딜·직접홍보 게시판은 늘 꺼져 있다).</li>
 *   <li>지금 값(값이 없으면 RECEIVED)과 같으면 아무것도 바꾸지 않는다(시각·이력·알림 없음 — 메모만 달라도 같다).</li>
 *   <li>바뀌면 이력 한 줄({@code post_process_status_history} — 그때 게시판·from·to·메모·바꾼 쪽)과 글쓴이(회원)에게 회원 알림 {@code POST_STATUS_CHANGED}(메모 포함)
 *       — 바꾼 사람이 글쓴이 본인이면 알리지 않는다. 비회원 글은 알림이 없다.</li>
 * </ul>
 */
@Component
public class PostProcessStatusChanger {

    private final MemberNotificationPublisher memberNotices;
    private final PostProcessStatusHistoryRepository history;
    private final SnowflakeIdGenerator ids;

    public PostProcessStatusChanger(MemberNotificationPublisher memberNotices, PostProcessStatusHistoryRepository history, SnowflakeIdGenerator ids) {
        this.memberNotices = memberNotices;
        this.history = history;
        this.ids = ids;
    }

    /**
     * 바꾼 결과.
     *
     * @param before  바꾸기 전 유효 값
     * @param after   지금 유효 값
     * @param changed 실제로 바뀌었나(이력 줄을 남겼나)
     */
    public record Result(PostProcessStatus before, PostProcessStatus after, boolean changed, Instant at) {
    }

    /**
     * 바꾼 사람.
     *
     * @param kind       {@link PostProcessStatusHistoryEntity#OPERATOR} · {@link PostProcessStatusHistoryEntity#BOARD_STAFF} · {@link PostProcessStatusHistoryEntity#GROUP_STAFF}
     * @param userId     공개 사이트의 회원(관리자 앱이면 null)
     * @param operatorId 관리자 앱 운영자(공개 사이트면 null)
     */
    public record Changer(String kind, Long userId, Long operatorId) {

        /** 관리자 앱 운영자 */
        public static Changer operator(long operatorId) {
            return new Changer(PostProcessStatusHistoryEntity.OPERATOR, null, operatorId);
        }

        /** 공개 사이트의 회원(운영자 회원 계정 · 게시판 운영진 · 소모임 운영진) */
        public static Changer member(String kind, long userId) {
            return new Changer(kind, userId, null);
        }
    }

    /** 요청 값 → 상태(비었거나 모르면 400 {@code fields.status}) */
    public static PostProcessStatus parse(String raw) {
        return PostProcessStatus.parse(raw).orElseThrow(() -> ValidationException.of("status", new MessageRef("post.processStatus.invalid")));
    }

    /** 요청 메모 → 저장할 값 — 앞뒤 공백을 떼고 비면 null, 200자 넘음·줄 바꿈·제어 문자는 400 {@code fields.note} */
    public static String note(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029)) {
            throw ValidationException.of("note", new MessageRef("post.processStatus.note.invalid"));
        }
        if (text.codePointCount(0, text.length()) > PostProcessStatusHistoryEntity.NOTE_MAX) {
            throw ValidationException.of("note", new MessageRef("post.processStatus.note.length", String.valueOf(PostProcessStatusHistoryEntity.NOTE_MAX)));
        }
        return text;
    }

    /**
     * 잠근 글의 상태를 바꾼다 — 부르는 쪽 트랜잭션 안(글 행 잠금 뒤). 이력은 같은 트랜잭션에, 알림은 같은 트랜잭션(세이브포인트 격리)으로 남는다.
     *
     * @param board   글이 놓인 게시판(없어진 게시판이면 null → 409)
     * @param note    {@link #note} 로 다듬은 메모(없으면 null)
     * @param changer 바꾼 사람
     */
    public Result change(PostEntity locked, EffectiveBoard board, PostProcessStatus status, String note, Changer changer, Instant now) {
        if (board == null || !board.statusEnabled()) {
            throw new PostStatusDisabledException();
        }
        PostProcessStatus before = PostProcessStatus.parse(locked.getProcessStatus()).orElse(PostProcessStatus.RECEIVED);
        if (before == status && locked.getProcessStatus() != null) {
            return new Result(before, status, false, locked.getProcessStatusAt());
        }
        Long byUserId = changer.userId();
        if (before == status) {
            // 값이 없던 글(상태를 켜기 전에 쓴 글)에 RECEIVED — 보이는 값은 같으니 저장만 맞추고 이력·알림은 남기지 않는다
            locked.changeProcessStatus(status, byUserId, now);
            return new Result(before, status, false, now);
        }
        locked.changeProcessStatus(status, byUserId, now);
        history.persist(PostProcessStatusHistoryEntity.create(ids.nextId(), locked.getId(), board.id(), before, status, note, changer.kind(), byUserId,
                changer.operatorId(), now));
        if (locked.getAuthorId() != null) {
            memberNotices.postStatusChanged(locked.getAuthorId(), byUserId, locked.getId(), board.name(), status.name(), locked.getTitle(), note);
        }
        return new Result(before, status, true, now);
    }

    /** 글 상세의 {@code processStatusNote} — 상태를 쓰는 게시판이면 마지막 변경의 메모(없으면 null), 쓰지 않으면 null(질의하지 않는다) */
    public String lastNote(EffectiveBoard board, long postId) {
        if (board == null || !board.statusEnabled()) {
            return null;
        }
        return history.findFirstByPostIdOrderByIdDesc(postId).map(PostProcessStatusHistoryEntity::getNote).orElse(null);
    }
}
