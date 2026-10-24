/**
 * Terms management copy — `/settings/terms`. Same keys and order as `ko/terms.ts`.
 */
export default {
    // ── Screen ──
    title: 'Terms',
    subtitle: 'Write the Terms of Service and Privacy Policy and publish them as versions. Publishing shows a change notice on the public site.',
    readOnly: 'You can only view. Editing drafts or publishing needs the terms management permission.',
    loadFailed: "Couldn't load terms",
    detailLoadFailed: "Couldn't load the draft and history",

    // ── Link option ──
    linksTitle: 'Terms links',
    linksLabel: 'Show terms links on sign-up and login screens',
    linksHelp: 'When off, the "View" links in sign-up consent and the terms links under the login card are hidden. Consent is always required. A link to terms with no published version leads to an empty notice.',
    linksOn: 'Terms links turned on',
    linksOff: 'Terms links turned off',
    linksFailed: "Couldn't change the link setting. {reason}",

    // ── Kind and status ──
    kindTabs: 'Terms kind',
    statusTitle: 'Status',
    statusCurrent: 'In effect',
    statusUpcoming: 'Upcoming',
    statusNone: 'No published version yet',
    statusNoUpcoming: 'None',
    versionLine: 'Version {version} · effective {date}',
    versionNo: 'v{version}',

    // ── Draft ──
    draftTitle: 'Draft',
    bodyLabel: '{name} text',
    bodyPlaceholder: 'Write the terms text',
    bodyHelp: 'Publish the saved draft as a new version with [Publish…]. Published versions cannot be edited.',
    bodyRequired: 'Enter the text.',
    unsavedHint: 'You have unsaved changes. Save the draft before publishing.',
    draftNone: 'No saved draft',
    draftSavedAt: 'Draft saved {time} · {name}',
    draftSave: 'Save draft',
    draftSaved: 'Draft saved',
    draftDelete: 'Delete draft',
    draftDeleteTitle: 'Delete the draft?',
    draftDeleteMessage: 'This deletes the {name} draft. Published versions stay as they are.',
    draftDeleted: 'Draft deleted',
    unknownOperator: 'Unknown',

    // ── Publish ──
    publishOpen: 'Publish…',
    publishTitle: 'Publish {name}',
    publishImmutable: 'Published versions cannot be edited. To change them, publish a new version.',
    effectiveDate: 'Effective date',
    effectiveDateHelp: 'In the site time zone. Must be today or later, and on or after the last published effective date.',
    effectiveDateRequired: 'Pick an effective date.',
    effectiveDateTooEarly: "This date isn't allowed. Pick today or later, on or after the last published effective date.",
    changeNote: 'Summary of changes',
    changeNoteHint: 'Optional · {count}/{max}',
    changeNoteHelp: 'Shown in the change notice on the public site.',
    changeNoteTooLong: 'Keep it to {max} characters or fewer.',
    publishSubmit: 'Publish',
    published: '{name} version {version} published · effective {date}',

    // ── History ──
    historyTitle: 'Publish history',
    historyEmpty: 'No published versions',
    historyEmptyHint: 'Save and publish a draft to see it here.',
    colVersion: 'Version',
    colPublishedAt: 'Published',
    colPublishedBy: 'Published by',
    colStatus: 'Status',
    status: {
        CURRENT: 'Current',
        UPCOMING: 'Upcoming',
        PAST: 'Past',
    },
    view: 'View',

    // ── View text ──
    versionTitle: '{name} version {version}',
    versionLoadFailed: "Couldn't load the text",
    copyToDraft: 'Use as draft',
    copyTitle: 'Replace the draft?',
    copyMessage: 'This replaces the draft in the editor with the text of version {version}. Click [Save draft] to keep it.',
    copyConfirm: 'Replace',
    copied: 'Put version {version} text in the editor. Click [Save draft] to keep it.',

    // ── Leaving ──
    leaveTitle: 'You have an unsaved draft',
    leaveMessage: 'Unsaved changes will be lost. Continue?',
    leaveConfirm: 'Discard and leave',
};
