/**
 * Organization (teams) — `/settings/teams`. A team has a name, description, on/off and a sort order; its members are admin operator accounts. Teams are used as an event's "Extra recipient teams" in notifications.
 * Server rejection texts (duplicate name, deleting a team that has members, …) come from the server, so they are not here. Keys are two parts: `team.what`. Same keys and order as `ko/team.ts`.
 */
export default {
    // ── Screen ──
    title: 'Organization',
    subtitle: 'Create teams and choose their members. Teams can be used as the "Extra recipient teams" of a notification event.',
    reload: 'Reload',
    loadFailed: 'Could not load the organization',
    emptyTitle: 'No teams yet',
    emptyBody: 'Create a team and add operators to it to receive notifications as a team.',
    readOnly: 'You do not have permission to change the organization, so this is view-only.',

    // ── Team list ──
    listTitle: '{n} teams',
    newTeam: 'New team',
    searchTeam: 'Find a team by name or description',
    noMatch: 'No teams match.',
    memberCount: '{n} members',
    stopped: 'Off',
    moveUp: 'Move {name} up',
    moveDown: 'Move {name} down',
    pickOneTitle: 'Pick a team',
    pickOneBody: 'Pick a team on the left to see its details and members.',

    // ── Team details ──
    infoTitle: 'Team details',
    fieldName: 'Team name',
    fieldDescription: 'Description',
    fieldStatus: 'On',
    nameHint: 'Up to {max} characters',
    descHint: 'Optional, up to {max} characters',
    statusOnHint: 'The team can be picked as an extra recipient team of a notification.',
    statusOffHint: 'A team that is off receives no notifications and cannot be newly picked.',
    deleteBlocked: 'A team with members cannot be deleted. Turn it off instead.',
    nameRequired: 'Enter a team name.',
    nameTooLong: 'The team name must be {max} characters or fewer.',
    descTooLong: 'The description must be {max} characters or fewer.',
    saved: 'Team details saved',
    deleteTitle: 'Delete this team?',
    deleteConfirm: 'This deletes the team "{name}". It cannot be undone.',
    deleteConfirmUsed: 'This deletes the team "{name}" and removes it from {n} notification events that use it as an extra recipient team. It cannot be undone.',
    deleted: 'Team deleted',

    // ── New team ──
    dialogCreate: 'Create a team',
    create: 'Create',
    created: 'Team created',

    // ── Members ──
    membersTitle: '{n} members',
    addMember: 'Add members',
    searchMember: 'Find by name or email',
    noMatchMember: 'No members match.',
    noMembersTitle: 'No members yet',
    noMembersBody: 'Use [Add members] to put operators in this team.',
    removeMember: 'Remove {name} from the team',
    removeTitle: 'Remove from the team?',
    removeConfirm: 'This removes {name} from the team "{team}". The operator account stays.',
    remove: 'Remove',
    memberRemoved: '{name} was removed from the team',
    memberDialogTitle: 'Add members to "{name}"',
    searchOperator: 'Find an operator by name or email',
    alreadyMember: 'Already a member',
    noMatchOperator: 'No operators match.',
    noCandidates: 'There are no operators to add.',
    chosenCount: '{n} selected',
    addChosen: 'Add selected operators',
    candidatesLoadFailed: 'Could not load the operators',
    membersAdded: '{n} operators added to the team',

    // ── Leaving without saving ──
    leaveTitle: 'You have unsaved changes',
    leaveMessage: 'If you continue without saving, your changes will be lost.',
    leaveConfirm: 'Continue',
    leaveCancel: 'Keep editing',
};
