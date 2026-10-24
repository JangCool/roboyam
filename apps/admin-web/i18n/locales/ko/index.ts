/**
 * 문구를 업무별 파일에서 모은다. 키는 `파일이름.무엇` — 파일 이름이 키의 첫 마디다.
 * **업무 파일을 늘리면 여기에 한 줄 더한다**(`import` 와 아래 `export default` 목록). `import.meta.glob` 으로 자동 수집하지 않는다(`.claude/guides/frontend/i18n.md`).
 *
 * ⚠ **`en/index.ts` 와 같은 목록이어야 한다.** 한쪽에만 있는 업무는 그 언어에서 한국어(폴백)로 보인다.
 * 레이어(`@roboyam/i18n`·`@roboyam/display-time`)가 이미 쓰는 최상위 키(`common`·`display`)는 여기서 다시 쓰지 않는다 — 합칠 때 한쪽이 덮인다.
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
import notificationMessage from './notificationMessage';
import notificationTemplate from './notificationTemplate';
import member from './member';
import menu from './menu';
import menuConfig from './menuConfig';
import mfa from './mfa';
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
import terms from './terms';

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
    notificationMessage,
    notificationTemplate,
    member,
    menu,
    menuConfig,
    mfa,
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
    terms,
};
