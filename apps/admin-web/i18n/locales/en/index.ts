/**
 * Collects the messages from the per-feature files. The key is `fileName.what` — the file name is the first part of the key.
 * **When you add a feature file, add a line here** (the `import` and the `export default` list below). Do not auto-collect with `import.meta.glob` (`.claude/guides/frontend/i18n.md`).
 *
 * ⚠ **Must be the same list as `ko/index.ts`.** A feature present in only one language shows in Korean (the fallback) in the other.
 * Do not reuse top-level keys the layers already own (`common`, `display`) — one would overwrite the other when merged.
 */
import account from './account';
import address from './address';
import analytics from './analytics';
import audit from './audit';
import avatarUpload from './avatarUpload';
import batch from './batch';
import board from './board';
import commerce from './commerce';
import dashboard from './dashboard';
import error from './error';
import grade from './grade';
import homeLayout from './homeLayout';
import gadgetPage from './gadgetPage';
import groupBuy from './groupBuy';
import groupBuyQna from './groupBuyQna';
import groupBuyReview from './groupBuyReview';
import identity from './identity';
import group from './group';
import login from './login';
import mail from './mail';
import notificationMessage from './notificationMessage';
import notificationTemplate from './notificationTemplate';
import member from './member';
import memberWithdrawal from './memberWithdrawal';
import menu from './menu';
import menuConfig from './menuConfig';
import mfa from './mfa';
import notification from './notification';
import operator from './operator';
import operatorMember from './operatorMember';
import permission from './permission';
import point from './point';
import payment from './payment';
import pointShop from './pointShop';
import emoticon from './emoticon';
import portone from './portone';
import publicData from './publicData';
import profile from './profile';
import reason from './reason';
import restriction from './restriction';
import role from './role';
import section from './section';
import settingHistory from './settingHistory';
import shell from './shell';
import site from './site';
import social from './social';
import team from './team';
import withdrawnContact from './withdrawnContact';
import terms from './terms';
import nicknameImage from './nicknameImage';
import noticeBar from './noticeBar';
import content from './content';
import debate from './debate';
import highlight from './highlight';
import popular from './popular';
import post from './post';
import report from './report';
import sanction from './sanction';
import appeal from './appeal';
import boardSanction from './boardSanction';
import staffAction from './staffAction';
import business from './business';
import bizApplication from './bizApplication';
import ad from './ad';
import adAdvertiser from './adAdvertiser';
import drawerSummary from './drawerSummary';
import docIntake from './docIntake';
import hotdeal from './hotdeal';
import visitAnalytics from './visitAnalytics';
import holiday from './holiday';

export default {
    account,
    address,
    analytics,
    audit,
    avatarUpload,
    batch,
    board,
    commerce,
    dashboard,
    error,
    grade,
    homeLayout,
    gadgetPage,
    groupBuy,
    groupBuyQna,
    groupBuyReview,
    identity,
    group,
    login,
    mail,
    notificationMessage,
    notificationTemplate,
    member,
    memberWithdrawal,
    menu,
    menuConfig,
    mfa,
    notification,
    operator,
    operatorMember,
    permission,
    point,
    payment,
    pointShop,
    emoticon,
    portone,
    publicData,
    profile,
    reason,
    restriction,
    role,
    section,
    settingHistory,
    shell,
    site,
    social,
    team,
    withdrawnContact,
    terms,
    nicknameImage,
    noticeBar,
    content,
    debate,
    highlight,
    popular,
    post,
    report,
    sanction,
    appeal,
    boardSanction,
    staffAction,
    business,
    bizApplication,
    ad,
    adAdvertiser,
    drawerSummary,
    docIntake,
    hotdeal,
    visitAnalytics,
    holiday,
};
