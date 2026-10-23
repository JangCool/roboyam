/**
 * 성인 확인(`/settings/adult`) 문구 — 출생연도 자기 입력. 19금 표시의 이름은 서비스 이름 `ADULT_LABEL`(자리표시 `{label}`).
 * 키는 `adult.무엇` 두 마디. `en/adult.ts` 와 같은 키, 같은 차례.
 */
export default {
    seo: '{label} 글을 볼 수 있는 성인 확인을 해요.',
    loading: '성인 확인 상태를 불러오는 중',
    loadFailed: '성인 확인 상태를 불러오지 못했어요',
    stateLabel: '성인 확인 상태',
    stateNone: '아직 확인하지 않았어요',
    stateConfirmed: '성인 확인됨',
    stateConfirmedYear: '성인 확인됨 · {y}년생',
    stateMinor: '아직 성인이 아니에요 · {y}년생',
    stateMinorNote: '{y}년부터 {label} 글을 볼 수 있어요.',
    birthLabel: '출생연도',
    birthPlaceholder: '출생연도를 골라 주세요',
    yearOption: '{y}년',
    confirmLabel: '입력한 출생연도가 사실과 같음을 확인해요',
    birthRequired: '출생연도를 골라 주세요.',
    birthTooYoung: '만 {age}세 이상만 이용할 수 있어요.',
    birthOutOfRange: '출생연도를 다시 확인해 주세요.',
    confirmRequired: '입력한 출생연도가 사실과 같음을 확인해 주세요.',
    saved: '출생연도를 저장했어요',
    savedAdult: '성인 확인이 됐어요',
    saveFailed: '저장하지 못했어요. {reason}',
    note1: '{label} 글은 성인 확인을 한 회원에게만 보여요.',
    note2: '입력한 출생연도를 기준으로 해요. 만 {age}세가 되는 해의 1월 1일부터 성인으로 봐요.',
    note3: '사실과 다르게 입력하면 이용이 제한될 수 있어요.',
};
