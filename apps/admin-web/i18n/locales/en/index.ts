/**
 * Collects the messages from the per-feature files. The key is `fileName.what` — the file name is the first part of the key.
 * **When you add a feature file, add a line here** (the `import` and the `export default` list below). Do not auto-collect with `import.meta.glob` (`.claude/guides/frontend/i18n.md`).
 *
 * ⚠ **Must be the same list as `ko/index.ts`.** A feature present in only one language shows in Korean (the fallback) in the other.
 * Do not reuse top-level keys the layers already own (`common`, `display`) — one would overwrite the other when merged.
 */
import audit from './audit';
import batch from './batch';
import dashboard from './dashboard';
import error from './error';
import grade from './grade';
import login from './login';
import member from './member';
import menu from './menu';
import notification from './notification';
import operator from './operator';
import permission from './permission';
import profile from './profile';
import role from './role';
import section from './section';
import shell from './shell';
import site from './site';

export default {
    audit,
    batch,
    dashboard,
    error,
    grade,
    login,
    member,
    menu,
    notification,
    operator,
    permission,
    profile,
    role,
    section,
    shell,
    site,
};
