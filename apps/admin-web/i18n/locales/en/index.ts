/**
 * Collects the messages from the per-feature files. The key is `fileName.what` — the file name is the first part of the key.
 * **When you add a feature file, add a line here** (the `import` and the `export default` list below). Do not auto-collect with `import.meta.glob` (`.claude/guides/frontend/i18n.md`).
 *
 * ⚠ **Must be the same list as `ko/index.ts`.** A feature present in only one language shows in Korean (the fallback) in the other.
 * Do not reuse top-level keys the layers already own (`common`, `display`) — one would overwrite the other when merged.
 */
import account from './account';
import audit from './audit';
import batch from './batch';
import board from './board';
import dashboard from './dashboard';
import error from './error';
import grade from './grade';
import login from './login';
import mail from './mail';
import mailTemplate from './mailTemplate';
import member from './member';
import menu from './menu';
import menuConfig from './menuConfig';
import notification from './notification';
import operator from './operator';
import permission from './permission';
import point from './point';
import profile from './profile';
import role from './role';
import section from './section';
import settingHistory from './settingHistory';
import shell from './shell';
import site from './site';
import social from './social';
import team from './team';

export default {
    account,
    audit,
    batch,
    board,
    dashboard,
    error,
    grade,
    login,
    mail,
    mailTemplate,
    member,
    menu,
    menuConfig,
    notification,
    operator,
    permission,
    point,
    profile,
    role,
    section,
    settingHistory,
    shell,
    site,
    social,
    team,
};
