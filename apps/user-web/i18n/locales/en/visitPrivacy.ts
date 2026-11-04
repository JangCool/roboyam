/**
 * Visitor choices for visit stats — the "Visit stats" box below the privacy policy body (`/privacy`, phase 2 2026-11-04). Same keys and order as `ko/visitPrivacy.ts`.
 */
export default {
    title: 'Visit stats',
    intro: 'This site collects visit stats to improve the service. It keeps a random visitor ID in your browser and never stores your IP address or raw browser details. Visit records are not linked to member accounts.',
    optOutLabel: 'Opt out of visit stats in this browser',
    optOutHelp: 'When on, this browser sends no visit signals and its visitor ID is deleted. When you turn it on, you can also delete the visit records already kept.',
    optOutOn: 'This browser is not counted in visit stats. Turn this off to be counted again from the next page.',
    optOutSaved: 'Opted out of visit stats in this browser.',
    optInSaved: 'Visit stats are allowed again in this browser.',
    optOutAskTitle: 'Delete the existing visit records too?',
    optOutAskMessage: 'Opting out deletes this browser\'s visitor ID, so records already kept can no longer be found and deleted. We recommend deleting them now.',
    optOutAskErase: 'Delete records and opt out',
    optOutAskOnly: 'Opt out only',
    forgetButton: 'Delete this browser\'s visit records',
    forgetNone: 'This browser has no visit records to delete.',
    forgetHelp: 'Asks us to delete the visit records kept under this browser\'s visitor ID. They are deleted within minutes, and a new visitor ID is used from your next visit. Totals such as overall visitor counts don\'t point to a person and stay.',
    confirmTitle: 'Delete visit records?',
    confirmMessage: 'This asks us to delete the visit records kept under this browser\'s visitor ID. Deleted records can\'t be restored.',
    confirmLabel: 'Request deletion',
    received: 'Request received (no. {id} · {at}). Records will be deleted within minutes.',
    forgetFailed: 'Couldn\'t send the request. Please try again shortly.',
    forgetRetryAfter: 'You can ask again in about {minutes} min.',
};
