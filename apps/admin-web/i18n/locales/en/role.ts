/**
 * Roles (moved from hamonica3 "role management"). Keys are `role.what`. Same keys and order as `ko/role.ts`.
 * Area, detail type and permission names are in `permission.*` (`en/permission.ts`).
 * Terms: **group = operator roles / member roles**, **area = the top-level split of operator roles (a tab)**, **detail type = one of the 24 categories under an area (a role belongs to one and may only pick that type's permissions)**, **type (member) = grade / member type / board unit / custom**. Do not mix them.
 */
export default {
    // Screen kinds: one per submenu item of "Roles" (`/settings/roles/<kind>`)
    kindTitle: {
        operator: 'Operator roles',
        member: 'Member roles',
        grade: 'Grade roles',
    },
    kindSubtitle: {
        operator: 'Decide what operators can do in the admin app. An operator can hold several roles.',
        member: 'Decide what members can do on the public site. A member can hold several roles.',
        grade: 'Decide what each member grade can do. Grades and their promotion rules are set in grade settings.',
    },

    // Tabs
    tabsLabel: 'Role categories',
    typeBuiltin: {
        memberGrade: 'Grades',
        memberKind: 'Member types',
        memberBoard: 'Board unit',
        memberCustom: 'Custom',
    },
    operatorTabNote: 'A role belongs to one detail type. Picking a type limits the permissions to that type. An operator can hold several roles.',
    memberAllTabNote: 'Member types, board units and custom roles in one list. Pick a role to see its permissions as usual.',
    typeNoDescription: 'No description.',
    typeEmpty: 'No roles yet',
    typeEmptyHint: 'Press "Add role" to create the first one.',
    sectionEmpty: 'This type has no roles yet',
    sectionAdd: 'Add a role to {type}',
    derivedNote: 'Grade roles are created in grade settings. Here you only edit their permissions.',
    derivedLink: 'Go to grade settings',
    fixedNote: 'The roles of this type are set by the product. Here you only edit their permissions.',
    addRole: 'Add role',
    addMemberTip: 'Create a custom member role',

    // List
    listTitle: 'Roles',
    loadFailed: 'Could not load the role list',
    builtIn: 'Provided by the product',
    builtInTag: 'Built-in',
    permCountLabel: '{count} permissions',

    // Selected role
    detailTitle: 'Role details',
    pick: 'Select a role',
    pickHint: 'Select a role on the left to see the permissions it holds.',
    pickForMenus: 'Select a role to see the menus it can see.',
    noDescription: 'No description.',
    operatorCount: '{count} operators hold this role',
    generalNote: 'A general management role holds permissions from several areas without a type limit. Only the super administrator can create and edit it.',
    generalNoteSuper: 'A general management role holds permissions from several areas without a type limit. As the super administrator you can create and edit it.',
    granted: 'Permissions held',
    grantedCount: '{count}',
    grantedNone: 'No permissions granted yet',
    grantedNoneHint: 'Use "Edit" to decide what this role can do.',
    generalSuperOnly: 'Only the super administrator can edit or delete a general management role',
    deleteBuiltinTip: 'Built-in roles cannot be deleted',
    deleteTitle: 'Delete this role?',
    deleteMessage: 'This deletes "{name}" and cannot be undone. A role that operators or members hold cannot be deleted.',
    deleted: 'Role deleted',
    deleteFailed: 'Could not delete the role',

    // Menus this role sees
    menuTitle: 'Menus this role sees',
    menuBasis: 'Based on the permissions this role holds. It changes when you edit permissions under "Edit".',
    menuKeyNote: 'Menus with a key are visible because of this role. The rest are visible to everyone who signs in.',
    menuNeeds: 'Visible only with the {permission} permission',
    menuNone: 'No menus are gated by permission.',
    menuNoneHint: 'With no permissions at all, every menu gated by permission disappears.',
    menuCommon: 'Common',
    menuDraftTitle: 'Menus visible after saving',

    // Create and edit a role
    dialogCreate: 'Add role',
    dialogEdit: 'Edit role',
    fieldRoleType: 'Role type',
    fieldId: 'Role ID',
    idHelp: 'It cannot be changed after creation. ROLE_ is added automatically.',
    idPlaceholder: 'ANALYST',
    idRequired: 'Enter a role ID.',
    idTooLong: 'The role ID can be up to {max} characters (including ROLE_).',
    fieldName: 'Role name',
    namePlaceholder: 'e.g. Batch operations',
    nameRequired: 'Enter a role name.',
    nameTooLong: 'The role name can be up to {max} characters.',
    fieldDescription: 'Description',
    descTooLong: 'The description can be up to {max} characters.',
    fieldArea: 'Area',
    areaPlaceholder: 'Pick an area',
    areaRequired: 'Pick an area.',
    fieldType: 'Detail type',
    typePlaceholder: 'Pick a detail type',
    typeRequired: 'Pick a detail type.',
    superOnly: 'Super administrator only',
    typeHelpCreate: 'The detail type this role belongs to. Picking one limits the permissions below to that type. It cannot be changed after creation.',
    typeGeneralHint: 'General management picks from every permission without a type limit, and only the super administrator can create and edit it. The type cannot be changed after creation.',

    // Picking permissions
    permTitle: 'Permissions',
    permCount: '{picked}/{total} selected',
    permPickType: 'Pick a detail type to see the permissions you can choose.',
    permNoGrant: 'Granting permissions needs the "Change roles (privilege escalation)" permission.',
    permLater: 'You can skip this for now — edit the role later whenever you like.',
    pickAll: 'Select all',
    unpickAll: 'Clear all',
    pickAllOf: 'Select all {unit}',
    unpickAllOf: 'Clear all {unit}',
    outsideOfMine: 'Beyond your permissions',
    permPreparing: 'Coming soon',

    // Member roles
    memberBuiltinLocked: 'This member role is provided by the product, so its name and description cannot be edited. Only its permissions can.',
    memberNoMenu: 'Member roles do not see admin app menus. They decide what a member can do on the public site.',
    memberDoesTitle: 'What this role can do',
    memberCount: '{count} members hold this role',
    promoTitle: 'Promotion conditions',
    promoNote: 'The conditions to reach this grade. View only here.',
    promoLink: 'Change them in grade settings',
    promoNoPermission: 'You need permission to view grade settings to see the promotion conditions.',
    promoLoadFailed: 'Could not load the promotion conditions',
    metaGrantMode: 'How it is given',
    metaScope: 'Scope',
    grantMode: {
        AUTO: 'Automatic',
        APPOINTED: 'Appointed',
        MANUAL: 'Manual',
    },
    grantModeHint: {
        AUTO: 'Received automatically from activity or sign-up',
        APPOINTED: 'Appointed per board',
        MANUAL: 'An operator gives it to a member by hand',
    },
    scope: {
        SITE: 'Whole site',
        BOARD: 'One board',
    },
    boardLimitsTitle: 'Default limits (read-only)',
    boardLimitPostActions: 'Hide or delete posts: {count} per day',
    boardLimitCommentDeletes: 'Delete comments: {count} per day',
    boardLimitSanctions: 'Board sanctions: {count} per day',
    boardLimitHeadcount: 'Per board: {managers} managers, up to {staff} staff',
    boardLimitsNote: 'These are decided in the board feature. For now the community defaults are shown for information only.',

    // Role results
    added: 'Role added',
    updated: 'Role updated',
    createdButPermFailed: 'The role was created but its permissions could not be applied',
    createdButPermFailedTail: 'Pick them again under "Edit".',
};
