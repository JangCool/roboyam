/**
 * Operator wording — Settings > Operator & terms > Operator screen (`SiteOperatorSettings`) and the operator summary line on the terms screen. Same keys and order as `ko/operatorStage.ts`.
 */
export default {
    // ── Section ──
    title: 'Operator',
    pageSubtitle: 'Stage (individual, sole proprietor, corporation) and operator details — used when policy text is published',
    intro: "The person or business running this site. Placeholders in policy text (such as \"{'{{'}운영자{'}}'}\") are replaced with these values when a version is published.",
    loadFailed: "Couldn't load the operator",
    saved: 'Operator saved',

    // ── Stage ──
    stageLabel: 'Stage',
    stage: {
        INDIVIDUAL: 'Individual',
        SOLE_PROPRIETOR: 'Sole proprietor',
        CORPORATION: 'Corporation',
    },
    stageHelp: {
        INDIVIDUAL: 'Before business registration — called "운영자" in policies',
        SOLE_PROPRIETOR: 'After business registration — called "운영자"',
        CORPORATION: 'After incorporation — called "회사"',
    },
    individualNotice: 'AdSense ads are available. Group buy sales, paid Robotikon sales and paid ad/hot deal applications need a sole proprietor or corporation with a mail-order registration.',

    // ── Common fields ──
    commonTitle: 'Operator details',
    operatorName: 'Operator name',
    operatorNameHelp: 'Published as the privacy officer in the privacy policy.',
    contactEmail: 'Contact email',
    phone: 'Phone',
    phoneOptional: 'Optional',
    phoneRequired: 'Required',
    officerSame: 'Privacy officer — same as operator',
    officerSameHelp: 'Turn off to enter a separate name and email for the privacy officer.',
    officerName: 'Privacy officer name',
    officerEmail: 'Privacy officer email',
    hostingProvider: 'Hosting provider',
    hostingProviderPlaceholder: 'e.g. Amazon Web Services',
    optional: 'Optional',
    lengthHint: 'Up to {max} characters',

    // ── Business fields ──
    businessTitle: 'Business details',
    businessName: 'Business name',
    companyName: 'Company name',
    representative: 'Representative',
    registrationNumber: 'Business registration number',
    registrationNumberHelp: 'Type digits only — hyphens are added for you.',
    corporateNumber: 'Corporate registration number',
    address: 'Business address',
    addressPlaceholder: 'One line',
    mailOrderNumber: 'Mail-order business report number',
    mailOrderAuthority: 'Reporting authority',
    mailOrderAuthorityPlaceholder: 'e.g. Gangnam-gu, Seoul',
    mailOrderHelp: "Without it, group buy sales, paid Robotikon sales and paid ad/hot deal applications can't be taken.",

    // ── Footer notice ──
    footerNoticeTitle: 'Footer notice',
    footerNotice: 'Notice text',
    footerNoticeHint: 'One sentence per line · up to {lines} lines · {max} characters per line',
    footerNoticeHelp:
        "Each line is shown on its own line near the bottom of the public site footer. Leave it empty to hide the notice. {'{'}사이트{'}'} is replaced with the site name, and a Korean particle right after it (은/는·이/가·을/를) is adjusted to fit the name (e.g. {'{'}사이트{'}'}은 → 로보얌은, ROBOYAM은).",
    footerNoticeScopeLabel: 'Show',
    footerNoticeScope: {
        INDIVIDUAL_ONLY: 'Only while run by an individual',
        ALWAYS: 'Always',
    },
    footerNoticeProblemLines: 'Use up to {max} lines.',
    footerNoticeProblemLineLength: 'Keep each line to {max} characters or fewer.',

    // ── Field problems ──
    required: {
        operatorName: 'Enter the operator name.',
        contactEmail: 'Enter a contact email.',
        phone: 'A phone number is required for business stages.',
        privacyOfficerName: "Enter the privacy officer's name.",
        privacyOfficerEmail: "Enter the privacy officer's email.",
        hostingProvider: 'Enter the hosting provider.',
        businessName: 'Enter the business name.',
        companyName: 'Enter the company name.',
        representative: 'Enter the representative.',
        registrationNumber: 'Enter the business registration number.',
        address: 'Enter the business address.',
        mailOrderNumber: 'Enter the report number.',
        mailOrderAuthority: 'Enter the reporting authority.',
        corporateNumber: 'Enter the corporate registration number.',
    },
    problemLength: 'Keep it to {max} characters or fewer.',
    problemEmail: "That doesn't look like an email address.",
    problemPhone: 'Use digits, spaces and + - ( ) only.',
    problemBizNumber: 'Use the format 123-45-67890.',

    // ── Saving a stage change ──
    changeConfirmTitle: 'Change the stage from {from} to {to}?',
    /** Stage name used after "to" in the confirm title (Korean adds a particle here) */
    stageTo: {
        INDIVIDUAL: 'Individual',
        SOLE_PROPRIETOR: 'Sole proprietor',
        CORPORATION: 'Corporation',
    },
    changeConfirmMessage: "After changing the stage, publish new versions of the Terms, Privacy Policy and Operation Policy. Current published versions don't change.",
    changeConfirmCorporation: 'Becoming a corporation transfers member data to a different entity. Notify all members before the revised policies take effect (Personal Information Protection Act, Article 27).',
    changeConfirmLabel: 'Change and save',

    // ── Stage history ──
    historyTitle: 'Stage history',
    historyEmpty: 'The stage has not been changed yet',
    colChangedAt: 'Date',
    colChange: 'Stage',
    colChangedBy: 'Changed by',
    changeLine: '{from} → {to}',
    unknownOperator: 'Unknown',

    // ── Leaving ──
    leaveTitle: 'You have unsaved operator changes',
    leaveMessage: 'Unsaved changes will be lost. Continue?',
    leaveConfirm: 'Discard and leave',

    // ── Summary line on the terms screen ──
    summaryLine: 'Current operator: {stage} · called "{alias}" in policies',
    summaryLink: 'Change in Operator settings',
    summaryLoadFailed: "Couldn't load the operator",
    alias: {
        INDIVIDUAL: '운영자',
        SOLE_PROPRIETOR: '운영자',
        CORPORATION: '회사',
    },
};
