package com.community.role.service;

import com.community.role.dto.response.RoleTypeView;
import com.community.role.entity.RoleAxis;
import com.community.role.entity.RoleTypes;
import com.community.role.repository.master.RoleRepositoryQDSL;
import com.community.zconfiguration.message.Messages;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할 유형 목록 — 고정 분류라 코드({@link RoleTypes})가 원천이고 이 서비스는 유형마다 든 역할 수를 붙인다. 운영자 유형은 영역 차례·영역 안 세부 유형 차례,
 * 회원 유형은 등급·회원 유형·게시판 단위·사용자 정의 차례다. 표시 이름은 화면이 번역 키로 보이므로 주지 않고, 회원 유형의 설명만 요청 언어로 푼다.
 */
@Service
@Transactional(readOnly = true)
public class RoleTypeService {

    private final RoleRepositoryQDSL roleQueries;
    private final Messages messages;

    public RoleTypeService(RoleRepositoryQDSL roleQueries, Messages messages) {
        this.roleQueries = roleQueries;
        this.messages = messages;
    }

    /** @param axis 주면 그 축의 유형만, 없으면 전부 */
    public List<RoleTypeView> list(RoleAxis axis) {
        Map<String, Long> counts = roleQueries.countByType();
        return RoleTypes.all().stream()
                .filter(type -> axis == null || type.axis() == axis)
                .map(type -> new RoleTypeView(type.id(), type.axis() == RoleAxis.MEMBER ? messages.get("roleType." + type.id() + ".description") : null,
                        type.axis().name(), type.areaId(), type.derived(), type.creatable(), counts.getOrDefault(type.id(), 0L)))
                .toList();
    }
}
