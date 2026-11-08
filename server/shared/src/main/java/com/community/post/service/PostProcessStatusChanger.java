package com.community.post.service;

import com.community.board.dto.EffectiveBoard;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.post.entity.PostEntity;
import com.community.post.entity.PostProcessStatus;
import com.community.post.exception.PostStatusDisabledException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 글의 처리 상태 바꾸기 한 곳(V166 운영 게시판) — 공개 사이트({@code PUT /api/posts/{id}/process-status}, 그 게시판 매니저·스텝·운영자 회원 계정)와
 * 관리자 앱({@code PUT /api/admin/posts/{id}/process-status}, {@code post.manage})이 같은 규칙을 쓴다. 권한·글 잠금·감사는 부르는 쪽이 한다.
 *
 * <ul>
 *   <li>값: {@link PostProcessStatus} 이름(대소문자 무시) — 비었거나 모르면 400 {@code fields.status}.</li>
 *   <li>게시판이 상태를 쓰지 않으면 409 {@code POST_STATUS_DISABLED}(소모임·핫딜 등 일반 게시판이 아니면 늘 꺼져 있다).</li>
 *   <li>지금 값(값이 없으면 RECEIVED)과 같으면 아무것도 바꾸지 않는다(시각·알림 없음).</li>
 *   <li>바뀌면 글쓴이(회원)에게 회원 알림 {@code POST_STATUS_CHANGED} — 바꾼 사람이 글쓴이 본인이면 보내지 않는다. 비회원 글은 알림이 없다.</li>
 * </ul>
 */
@Component
public class PostProcessStatusChanger {

    private final MemberNotificationPublisher memberNotices;

    public PostProcessStatusChanger(MemberNotificationPublisher memberNotices) {
        this.memberNotices = memberNotices;
    }

    /**
     * 바꾼 결과.
     *
     * @param before  바꾸기 전 유효 값
     * @param after   지금 유효 값
     * @param changed 실제로 바뀌었나
     */
    public record Result(PostProcessStatus before, PostProcessStatus after, boolean changed, Instant at) {
    }

    /** 요청 값 → 상태(비었거나 모르면 400 {@code fields.status}) */
    public static PostProcessStatus parse(String raw) {
        return PostProcessStatus.parse(raw).orElseThrow(() -> ValidationException.of("status", new MessageRef("post.processStatus.invalid")));
    }

    /**
     * 잠근 글의 상태를 바꾼다 — 부르는 쪽 트랜잭션 안(글 행 잠금 뒤). 알림은 같은 트랜잭션(세이브포인트 격리)으로 남는다.
     *
     * @param board    글이 놓인 게시판(없어진 게시판이면 null → 409)
     * @param byUserId 바꾼 회원(관리자 앱 운영자면 null)
     */
    public Result change(PostEntity locked, EffectiveBoard board, PostProcessStatus status, Long byUserId, Instant now) {
        if (board == null || !board.statusEnabled()) {
            throw new PostStatusDisabledException();
        }
        PostProcessStatus before = PostProcessStatus.parse(locked.getProcessStatus()).orElse(PostProcessStatus.RECEIVED);
        if (before == status && locked.getProcessStatus() != null) {
            return new Result(before, status, false, locked.getProcessStatusAt());
        }
        if (before == status) {
            // 값이 없던 글(상태를 켜기 전에 쓴 글)에 RECEIVED — 보이는 값은 같으니 저장만 맞추고 알리지 않는다
            locked.changeProcessStatus(status, byUserId, now);
            return new Result(before, status, false, now);
        }
        locked.changeProcessStatus(status, byUserId, now);
        if (locked.getAuthorId() != null) {
            memberNotices.postStatusChanged(locked.getAuthorId(), byUserId, locked.getId(), board.name(), status.name(), locked.getTitle());
        }
        return new Result(before, status, true, now);
    }
}
