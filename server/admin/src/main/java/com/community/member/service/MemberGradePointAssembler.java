package com.community.member.service;

import com.community.grade.entity.GradeChangeKind;
import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeManualPolicy;
import com.community.grade.entity.GradeSettingEntity;
import com.community.grade.entity.MemberGradeChangeEntity;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.grade.repository.master.GradeSettingRepository;
import com.community.grade.service.GradeProgress;
import com.community.member.dto.MemberGradeChangeRow;
import com.community.member.dto.response.MemberActivity;
import com.community.member.dto.response.MemberGradeChange;
import com.community.member.dto.response.MemberGradeNext;
import com.community.member.dto.response.MemberGradePoint;
import com.community.member.dto.response.MemberGradeRequirement;
import com.community.member.dto.response.MemberPointEntry;
import com.community.member.repository.master.MemberRepositoryQDSL;
import com.community.point.repository.master.PointLedgerRepositoryQDSL;
import com.community.point.service.PointLedgerQueryService;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 회원 상세의 등급·포인트 구획을 만든다 — 자동 승급 상태 · 다음 등급 조건 달성도 · 등급 변경 이력. 읽기 전용(부르는 서비스의 트랜잭션 안에서).
 *
 * <ul>
 *   <li><b>자동 승급 상태</b>: 정지(기간·영구) 회원은 {@code PAUSED_SANCTION}, 운영자가 올려 둔 회원({@code grade_manual})이고 등급 옵션이 「자동 승급 중지」면 {@code PAUSED_ADMIN}, 그 밖은 {@code ACTIVE}.
 *       (승급 판정 {@code GradePromotionService} 의 대상 규칙과 같다.)</li>
 *   <li><b>다음 등급</b>: 지금보다 서열이 높은 첫 등급. 조건은 가입 후 일수(실제 경과 일수, 상한 없음)와 글+댓글 수(활동 요약의 집계). 마지막 등급이면 null.
 *       활동 수는 즉시 승급과 같은 정의다. 활동 조건 승급은 글·댓글을 쓸 때 즉시 일어나고 일괄 경로(일수 배치·등급 저장의 소급)는 활동 수를 보지 않으므로,
 *       조건을 모두 채웠는데 아직 그 등급이 아닌 회원은 다음 글·댓글 때 올라간다(roles-contract.md 「세션 정리 — 서버 수정 결과」).</li>
 *   <li><b>이력</b>: 최근 것이 먼저, 최대 {@value #HISTORY_LIMIT}건. 사유는 운영자 입력문이 우선, 없으면 시스템 사유 코드를 요청 언어로 푼다. 처리한 운영자가 지워졌으면 「삭제된 운영자」.</li>
 *   <li><b>포인트 내역</b>: 원장의 최근 {@value #POINT_ENTRY_LIMIT} 줄(최신이 위, 사유는 요청 언어). 닉네임 변경권은 교환 기능이 없어 {@code nicknameTicketCount} 가 0.</li>
 * </ul>
 */
@Component
public class MemberGradePointAssembler {

    /** 상세에 싣는 등급 변경 이력의 최대 건수 */
    public static final int HISTORY_LIMIT = 50;
    /** 상세에 싣는 최근 포인트 내역의 최대 줄 수 */
    public static final int POINT_ENTRY_LIMIT = 10;

    private final GradeRepositoryQDSL gradeQueries;
    private final GradeSettingRepository settings;
    private final MemberRepositoryQDSL members;
    private final PointLedgerRepositoryQDSL ledger;
    private final Messages messages;

    public MemberGradePointAssembler(GradeRepositoryQDSL gradeQueries, GradeSettingRepository settings, MemberRepositoryQDSL members, PointLedgerRepositoryQDSL ledger,
                                     Messages messages) {
        this.ledger = ledger;
        this.gradeQueries = gradeQueries;
        this.settings = settings;
        this.members = members;
        this.messages = messages;
    }

    public MemberGradePoint assemble(UserEntity user, ProfileEntity profile, MemberActivity activity, Instant now) {
        long userId = user.getId();
        return new MemberGradePoint(autoPromotionOf(user, profile), nextOf(user, profile, activity, now), historyOf(userId), 0, pointEntriesOf(userId));
    }

    /** 최근 포인트 내역 — 원장의 최신 {@value #POINT_ENTRY_LIMIT} 줄, 사유는 요청 언어의 이름 */
    private List<MemberPointEntry> pointEntriesOf(long userId) {
        Locale locale = messages.locale();
        return ledger.recentOf(userId, POINT_ENTRY_LIMIT).stream()
                .map(row -> new MemberPointEntry(Long.toString(row.id()), row.kind(), row.delta(), PointLedgerQueryService.reasonLabel(messages, row.reason(), locale),
                        row.createdAt()))
                .toList();
    }

    private String autoPromotionOf(UserEntity user, ProfileEntity profile) {
        GradeManualPolicy policy = settings.findById(GradeSettingEntity.ROW_ID).map(GradeSettingEntity::getManualPolicy).orElse(GradeManualPolicy.CONTINUE_AUTO);
        return GradeProgress.autoPromotion(user.getStatus(), profile.isGradeManual(), policy); // 공개 사이트 내 등급(GET /api/me/grade)과 같은 셈
    }

    private MemberGradeNext nextOf(UserEntity user, ProfileEntity profile, MemberActivity activity, Instant now) {
        GradeEntity next = GradeProgress.next(gradeQueries.findAllOrdered(), profile.getGradeKey()).orElse(null);
        if (next == null) {
            return null;
        }
        long days = GradeProgress.elapsedDays(user.getCreatedAt(), now); // 승급 판정(가입 시각 <= 지금 - 일수)과 같은 날짜 계산
        return new MemberGradeNext(next.getGradeKey(), List.of(
                new MemberGradeRequirement(MemberGradeRequirement.DAYS, days, next.getMinDays()),
                new MemberGradeRequirement(MemberGradeRequirement.ACTIVITY, activity.postCount() + activity.commentCount(), next.getMinActivity())));
    }

    private List<MemberGradeChange> historyOf(long userId) {
        return members.gradeChangesOf(userId, HISTORY_LIMIT).stream().map(this::changeOf).toList();
    }

    private MemberGradeChange changeOf(MemberGradeChangeRow row) {
        return new MemberGradeChange(Long.toString(row.id()), row.fromGradeKey(), row.toGradeKey(), row.kind().name(), reasonOf(row), actorOf(row), row.createdAt());
    }

    /** 운영자가 쓴 사유가 먼저, 없으면 알려진 시스템 사유 코드를 요청 언어로 푼다(모르는 코드는 보이지 않는다) */
    private String reasonOf(MemberGradeChangeRow row) {
        if (row.reason() != null && !row.reason().isBlank()) {
            return row.reason();
        }
        if (MemberGradeChangeEntity.REASON_GRADE_REMOVED.equals(row.reasonCode())) {
            return messages.get("member.gradeChange.reason." + row.reasonCode());
        }
        return null;
    }

    /** 자동이면 null, 운영자면 그 이름, 운영자가 지워졌으면 「삭제된 운영자」(null 로 두면 자동처럼 보인다) */
    private String actorOf(MemberGradeChangeRow row) {
        if (row.kind() == GradeChangeKind.AUTO || row.actorOperatorId() == null) {
            return null;
        }
        return row.actorName() != null ? row.actorName() : messages.get("member.gradeChange.actorDeleted");
    }
}
