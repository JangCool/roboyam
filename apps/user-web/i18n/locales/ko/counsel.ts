/** AI 연애상담소(연애·결혼 게시판 「AI 상담소」 말머리) — 계약 `docs/features/ai-love-counsel-contract.md`. ⚠ `en/counsel.ts` 와 같은 키·같은 차례 */
export default {
    // 목록 — 이번 주 상담 순위(사연 쓴 사람이 고른 조언 기준, 사람 회원도 한 줄)
    rankingTitle: '이번 주 상담 순위',
    rankingBasis: '사연 쓴 사람이 고른 조언 기준 · {from}~{to}',
    rankingLoading: '상담 순위를 불러오는 중',
    rankingFailed: '상담 순위를 불러오지 못했어요',
    rankingEmpty: '이번 주에는 아직 고른 조언이 없어요',
    rankN: '{n}위',
    rankHuman: '사람 회원',
    rankRowAria: '{rank}위 {name}, {percent}%',
    // 글 줄 상태 칩
    chipLabel: '상담 상태',
    chipPicked: '채택',
    chipAnswered: 'AI 답변 {n}',
    chipWaiting: '답변 기다리는 중',
    // 글 보기 — AI 상담 칸
    guide: 'AI 상담사 셋이 10분 안에 답해요. 가장 도움 된 조언을 하나 골라 주세요(사람 답도 고를 수 있어요).',
    guideReader: 'AI 상담사 셋이 10분 안에 답해요. 사연을 쓴 분이 가장 도움 된 조언을 하나 골라요(사람 답도 고를 수 있어요).',
    aiTitle: 'AI 상담사 답변',
    memberTitle: '회원 답변',
    loading: 'AI 상담사 답을 불러오는 중',
    loadFailed: 'AI 상담사 답을 불러오지 못했어요',
    writing: '답을 쓰고 있어요',
    late: '답이 조금 늦어지고 있어요. 잠시 뒤 다시 확인해 주세요.',
    pick: '이 조언 고르기',
    picked: '가장 도움 된 조언',
    pickConfirmTitle: '이 조언을 고를까요?',
    pickConfirmMessage: '{name}님의 답을 「가장 도움 된 조언」으로 골라요. 이번 주 상담 순위에 반영돼요.',
    pickConfirm: '고르기',
    pickedToast: '가장 도움 된 조언으로 골랐어요',
    // 경고 — 글 보기 댓글 입력 위 · 글쓰기(「AI 상담소」 말머리를 골랐을 때)
    privacyWarning: 'AI 답변은 전문 상담이 아니에요. 사연은 해외 AI 제공사로 보내지니 이름·연락처는 적지 마세요.',
    // 위험 사연 — AI 가 답하지 않고 안내만
    riskTitle: '지금 많이 힘드신 것 같아요.',
    riskText: '이 사연에는 AI 상담사가 답하지 않아요. 혼자 견디지 말고 전문가와 이야기해 주세요.',
    riskSuicide: '자살예방 상담 109',
    riskWomen: '여성 폭력 상담 1366',
    risk24h: '둘 다 24시간',
    riskCall: '{name} 전화 걸기',
};
