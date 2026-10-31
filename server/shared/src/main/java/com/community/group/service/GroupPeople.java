package com.community.group.service;

import com.community.business.service.BusinessEligibility;
import com.community.grade.entity.GradeEntity;
import com.community.grade.repository.master.GradeRepository;
import com.community.group.dto.response.GroupPersonView;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.pointshop.dto.AuthorLook;
import com.community.pointshop.service.ShopDecorationResolver;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 소모임 응답의 사람({@link GroupPersonView})을 한 번에 읽어 만든다 — 회원·프로필·등급(·공개 사이트는 장식·아바타)을 목록마다 한 번씩 읽는다(N+1 없음).
 * 부르는 서비스의 트랜잭션 안에서 돈다.
 */
@Component
public class GroupPeople {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final GradeRepository grades;
    private final OperatorRepository operators;
    private final ShopDecorationResolver decorations;
    private final BusinessEligibility businessEligibility;
    private final Messages messages;

    public GroupPeople(UserRepository users, ProfileRepository profiles, GradeRepository grades, OperatorRepository operators, ShopDecorationResolver decorations,
                       BusinessEligibility businessEligibility, Messages messages) {
        this.users = users;
        this.profiles = profiles;
        this.grades = grades;
        this.operators = operators;
        this.decorations = decorations;
        this.businessEligibility = businessEligibility;
        this.messages = messages;
    }

    /** 한 응답에서 쓸 회원들을 읽어 둔 것 */
    public final class Members {

        private final Map<Long, UserEntity> userById;
        private final Map<Long, ProfileEntity> profileById;
        private final Map<String, GradeEntity> gradeByKey;
        private final Map<Long, AuthorLook> looks;
        private final Set<Long> businesses;

        private Members(Map<Long, UserEntity> userById, Map<Long, ProfileEntity> profileById, Map<String, GradeEntity> gradeByKey, Map<Long, AuthorLook> looks,
                        Set<Long> businesses) {
            this.userById = userById;
            this.profileById = profileById;
            this.gradeByKey = gradeByKey;
            this.looks = looks;
            this.businesses = businesses;
        }

        /** 그 회원 — null 이면 null */
        public GroupPersonView of(Long userId) {
            if (userId == null) {
                return null;
            }
            UserEntity user = userById.get(userId);
            ProfileEntity profile = profileById.get(userId);
            if (user == null || user.isGone() || profile == null) {
                return new GroupPersonView(null, messages.get(new MessageRef("member.notification.actor.withdrawn")), true, null, null, null, false);
            }
            GradeEntity grade = profile.getGradeKey() == null ? null : gradeByKey.get(profile.getGradeKey());
            GroupPersonView.GroupGradeView badge = grade == null ? null
                    : new GroupPersonView.GroupGradeView(grade.getGradeKey(), grade.getLabel(), grade.getBadgeColor(), grade.getBadgeIcon());
            AuthorLook look = looks.get(userId);
            return new GroupPersonView(Long.toString(userId), profile.getNickname(), false, badge, look == null ? null : look.decoration(),
                    look == null ? null : look.avatarUrl(), businesses.contains(userId));
        }

        /** 그 회원의 지금 닉네임(탈퇴면 「탈퇴한 회원」) */
        public String nickname(Long userId) {
            GroupPersonView person = of(userId);
            return person == null ? null : person.nickname();
        }

        /** 지금 회원(탈퇴·없음 아님)의 행 */
        public UserEntity user(Long userId) {
            return userId == null ? null : userById.get(userId);
        }
    }

    /**
     * @param ids      회원 ID(null 은 건너뛴다)
     * @param withLook 장식·아바타·사업자 뱃지를 같이 읽는가(공개 사이트 true, 관리자 false)
     */
    public Members members(Collection<Long> ids, boolean withLook) {
        List<Long> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, UserEntity> userById = new HashMap<>();
        Map<Long, ProfileEntity> profileById = new HashMap<>();
        Map<String, GradeEntity> gradeByKey = new HashMap<>();
        Map<Long, AuthorLook> looks = Map.of();
        Set<Long> approved = Set.of();
        if (!wanted.isEmpty()) {
            users.findAllById(wanted).forEach(u -> userById.put(u.getId(), u));
            profiles.findAllById(wanted).forEach(p -> profileById.put(p.getUserId(), p));
            grades.findAll().forEach(g -> gradeByKey.put(g.getGradeKey(), g));
            if (withLook) {
                looks = decorations.resolve(wanted);
                approved = businessEligibility.approvedUserIds(wanted);
            }
        }
        return new Members(userById, profileById, gradeByKey, looks, approved);
    }

    /** 운영자들(처리자·승인자) — 지워졌으면 「삭제된 운영자」·withdrawn */
    public Map<Long, GroupPersonView> operators(Collection<Long> ids) {
        List<Long> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, GroupPersonView> out = new HashMap<>();
        if (wanted.isEmpty()) {
            return out;
        }
        Map<Long, OperatorEntity> byId = new HashMap<>();
        operators.findAllById(wanted).forEach(o -> byId.put(o.getId(), o));
        for (Long id : wanted) {
            OperatorEntity op = byId.get(id);
            out.put(id, op == null
                    ? new GroupPersonView(Long.toString(id), messages.get(new MessageRef("group.person.operatorDeleted")), true, null, null, null, false)
                    : new GroupPersonView(Long.toString(id), op.getOperatorName(), false, null, null, null, false));
        }
        return out;
    }

    /** 운영자 이름(설정의 updatedBy) — 지워졌으면 「삭제된 운영자」, id 가 null 이면 null */
    public String operatorName(Long id) {
        if (id == null) {
            return null;
        }
        return operators.findById(id).map(OperatorEntity::getOperatorName).orElseGet(() -> messages.get(new MessageRef("group.person.operatorDeleted")));
    }
}
