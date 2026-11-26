/** AI love counsel (Love & Marriage board "AI Counsel" prefix) — contract `docs/features/ai-love-counsel-contract.md`. ⚠ Same keys and order as `ko/counsel.ts` */
export default {
    // List — this week's counsel ranking (by the advice the poster picked; human members are one row)
    rankingTitle: "This week's counsel ranking",
    rankingBasis: 'By the advice posters picked · {from}–{to}',
    rankingLoading: 'Loading the counsel ranking',
    rankingFailed: "Couldn't load the counsel ranking",
    rankingEmpty: 'No advice has been picked this week yet',
    rankN: '#{n}',
    rankHuman: 'Human members',
    rankRowAria: 'Rank {rank} {name}, {percent}%',
    // Post row status chips
    chipLabel: 'Counsel status',
    chipPicked: 'Picked',
    chipAnswered: 'AI answers {n}',
    chipWaiting: 'Waiting for answers',
    // Post view — AI counsel section
    guide: 'Three AI counselors answer within 10 minutes. Pick the one piece of advice that helped most (you can pick a human answer too).',
    guideReader: 'Three AI counselors answer within 10 minutes. The poster picks the one piece of advice that helped most (human answers count too).',
    aiTitle: 'AI counselor answers',
    memberTitle: 'Member answers',
    loading: 'Loading AI counselor answers',
    loadFailed: "Couldn't load AI counselor answers",
    writing: 'Writing an answer',
    late: 'Answers are taking a little longer. Please check back shortly.',
    pick: 'Pick this advice',
    picked: 'Most helpful advice',
    pickConfirmTitle: 'Pick this advice?',
    pickConfirmMessage: "{name}'s answer will be marked as the most helpful advice. It counts toward this week's counsel ranking.",
    pickConfirm: 'Pick',
    pickedToast: 'Marked as the most helpful advice',
    // Warning — above the comment box on the post view · on the write page (when "AI Counsel" is picked)
    privacyWarning: 'AI answers are not professional counseling. Your story is sent to overseas AI providers, so do not include names or contact details.',
    // Risky story — AI does not answer, only shows help lines
    riskTitle: 'It sounds like you are going through a lot right now.',
    riskText: "AI counselors won't answer this story. Please don't carry it alone — talk to a professional.",
    riskSuicide: 'Suicide prevention line 109',
    riskWomen: "Women's violence hotline 1366",
    risk24h: 'Both open 24 hours',
    riskCall: 'Call {name}',
};
