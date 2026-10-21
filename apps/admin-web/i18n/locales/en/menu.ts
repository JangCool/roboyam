/**
 * Names of the admin menu items — the items are defined in one place, `utils/util-admin-menu.ts`; add a line there and a line here for each new feature screen.
 * Keys are `menu.<item key>`. Same keys and order as `ko/menu.ts`.
 * `batchAndLogs` is a section name (no screen); the other names follow the hamonica3 system settings menu.
 */
export default {
    dashboard: 'Dashboard',
    system: 'System',
    general: 'General',
    notifications: 'Notifications',
    member: 'Members',
    members: 'Members',
    grades: 'Grade settings',
    operatorsAndPermissions: 'Operators and permissions',
    operators: 'Operators',
    roles: 'Roles',
    rolesMember: 'Member roles',
    rolesOperator: 'Operator roles',
    rolesGrade: 'Grade roles',
    batchAndLogs: 'Batch and logs',
    batches: 'Batch jobs',
    batchLogs: 'Batch logs',
    audit: 'Audit log',
};
