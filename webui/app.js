'use strict';

/**
 * AllMusic 独立服务端 · Web 面板前端
 *
 * 设计约定：
 * - 所有外部文本（歌名、玩家名、日志等）只通过 textContent 写入 DOM，避免 XSS；
 * - 只使用服务端已有的接口（/api/public/* 与管理员接口），不依赖任何第三方库；
 * - 页面直接用浏览器打开（file://）或服务端未运行时自动进入「演示数据」模式，
 *   便于在没有服务端的情况下预览整套界面。
 */

const $ = (id) => document.getElementById(id);
const $$ = (selector) => Array.prototype.slice.call(document.querySelectorAll(selector));

const API = {
    status: '/api/public/status',
    queue: '/api/public/queue',
    players: '/api/public/players',
    stats: (limit) => '/api/public/stats?limit=' + (limit || 50),
    perf: '/api/public/perf',
    lyric: '/api/public/lyric',
    login: '/api/login',
    me: '/api/me',
    logout: '/api/logout',
    bans: '/api/admin/bans',
    config: '/api/admin/config',
    password: '/api/admin/password',
    logs: (since, limit) => '/api/admin/logs?since=' + since + '&limit=' + (limit || 200),
    command: '/api/admin/command',
    queueDelete: '/api/admin/queue/delete',
    next: '/api/admin/next',
    musicBan: '/api/admin/music/ban',
    musicUnban: '/api/admin/music/unban',
    playerBan: '/api/admin/player/ban',
    playerUnban: '/api/admin/player/unban'
};

const state = {
    token: sessionStorage.getItem('amToken') || '',
    admin: false,
    demo: false,
    view: 'dashboard',
    lyricAvailable: false,
    lyric: { prev: '', cur: '', next: '' },
    chart: { mode: 'total', tick: 0 },
    history: { cpu: [], ram: [], netRx: [], netTx: [], cores: [], coreCount: 0 },
    netPeak: 512,
    log: { seq: 0, paused: false, cleared: false, count: 0 },
    busy: {},
    lastStats: null
};

/* ============================ 通用工具 ============================ */

function setText(node, text) {
    if (node) {
        node.textContent = text === undefined || text === null ? '' : String(text);
    }
}

function show(node, visible) {
    if (node) {
        node.hidden = !visible;
    }
}

function fmtClock(seconds) {
    const total = Math.max(0, Math.floor(Number(seconds) || 0));
    const m = Math.floor(total / 60);
    const s = total % 60;
    return m + ':' + String(s).padStart(2, '0');
}

function fmtDuration(ms) {
    const total = Math.max(0, Math.floor((Number(ms) || 0) / 1000));
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    if (h > 0) {
        return h + ' 小时 ' + m + ' 分';
    }
    if (m > 0) {
        return m + ' 分 ' + s + ' 秒';
    }
    return s + ' 秒';
}

function fmtDateTime(ms) {
    const n = Number(ms);
    if (!n) {
        return '--';
    }
    const d = new Date(n);
    if (isNaN(d.getTime())) {
        return '--';
    }
    const pad = (v) => String(v).padStart(2, '0');
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
        + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
}

function clampPercent(value) {
    const n = Number(value);
    if (!isFinite(n)) {
        return 0;
    }
    return Math.max(0, Math.min(100, n));
}

/** 封面地址只接受 http/https（演示模式的 data:image 也允许，作为 <img> 源不会执行脚本） */
function safeImage(url) {
    if (typeof url !== 'string') {
        return '';
    }
    if (url.startsWith('data:image/')) {
        return url;
    }
    return (url.startsWith('https://') || url.startsWith('http://')) ? url : '';
}

function el(tag, cls, text) {
    const node = document.createElement(tag);
    if (cls) {
        node.className = cls;
    }
    if (text !== undefined && text !== null) {
        node.textContent = String(text);
    }
    return node;
}

/* ============================ 消息气泡 ============================ */

/**
 * 屏幕中央的消息气泡
 *
 * @param text     文本
 * @param kind     ok / bad
 * @param holdMs   停留时间，到点后渐淡消失（登录成功用 700ms）
 */
function toast(text, kind, holdMs) {
    const layer = $('toastLayer');
    const node = el('div', 'toast' + (kind ? ' toast-' + kind : ''), text);
    layer.appendChild(node);
    const hold = typeof holdMs === 'number' ? holdMs : 2200;
    setTimeout(() => {
        node.classList.add('is-out');
        setTimeout(() => node.remove(), 500);
    }, hold);
}

/* ============================ 主题 ============================ */

function applyTheme(theme) {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('amTheme', theme);
    const btn = $('themeBtn');
    if (btn) {
        btn.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
    }
}

function initTheme() {
    const saved = localStorage.getItem('amTheme');
    const preferDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    applyTheme(saved || (preferDark ? 'dark' : 'light'));
}

function toggleTheme() {
    applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');
}

/* ============================ 接口层 ============================ */

async function api(path, options) {
    const opts = options || {};
    const headers = Object.assign({}, opts.headers || {});
    if (state.token) {
        headers.Authorization = 'Bearer ' + state.token;
    }
    if (opts.body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    const res = await fetch(path, { method: opts.method || 'GET', headers, body: opts.body });
    let data = null;
    try {
        data = await res.json();
    } catch (e) {
        data = null;
    }
    if (res.status === 401 && state.admin) {
        setAdmin(false);
        toast('登录已过期，请重新登录', 'bad', 2400);
    }
    if (!res.ok) {
        throw new Error((data && data.error) ? data.error : ('请求失败（HTTP ' + res.status + '）'));
    }
    return data;
}

/* ============================ 视图切换 ============================ */

const VIEWS = ['dashboard', 'perf', 'stats', 'settings', 'console'];
const VIEW_IDS = {
    dashboard: 'viewDashboard',
    perf: 'viewPerf',
    stats: 'viewStats',
    settings: 'viewSettings',
    console: 'viewConsole'
};

function switchView(name) {
    if (!VIEWS.includes(name) || name === state.view) {
        return;
    }
    const from = $(VIEW_IDS[state.view]);
    const to = $(VIEW_IDS[name]);
    if (from && to) {
        from.classList.remove('is-active');
        setTimeout(() => {
            from.hidden = true;
        }, 300);
        to.hidden = false;
        requestAnimationFrame(() => to.classList.add('is-active'));
    }
    state.view = name;
    $$('.navbtn').forEach((btn) => {
        const on = btn.dataset.view === name;
        btn.classList.toggle('is-active', on);
        btn.setAttribute('aria-selected', on ? 'true' : 'false');
    });
    if (name === 'perf') {
        renderCharts();
    }
    if (name === 'settings' && state.admin) {
        loadConfig().catch(() => {});
    }
    if (name === 'console' && state.admin) {
        scrollLog(true);
    }
}

/* ============================ 管理员状态 ============================ */

function setAdmin(on) {
    state.admin = on;
    $$('.admin-only').forEach((node) => {
        node.hidden = !on;
    });
    const btn = $('adminBtn');
    if (btn) {
        setText(btn, on ? '退出登录' : '管理员登录');
    }
    if (!on) {
        state.token = '';
        sessionStorage.removeItem('amToken');
        state.log.seq = 0;
        state.log.cleared = false;
        setText($('consoleLog'), '');
        renderBansPlaceholder();
        if (state.view === 'settings' || state.view === 'console') {
            switchView('dashboard');
        }
    } else {
        loadBans().catch(() => {});
    }
    renderQueue(state.lastQueue);
    renderPlayers(state.lastPlayers);
}

function openLogin() {
    const modal = $('loginModal');
    modal.hidden = false;
    const input = $('loginPassword');
    input.value = '';
    input.classList.remove('is-error');
    setText($('loginError'), '');
    setTimeout(() => input.focus(), 30);
}

function closeLogin() {
    $('loginModal').hidden = true;
}

async function submitLogin() {
    const input = $('loginPassword');
    const errorNode = $('loginError');
    const password = input.value;
    if (!password) {
        input.classList.add('is-error');
        setText(errorNode, '请输入管理员密码');
        return;
    }
    if (state.demo) {
        // 演示模式下不校验真实密码，方便预览登录后的界面
        input.value = '';
        closeLogin();
        setAdmin(true);
        toast('已登录管理员', 'ok', 700);
        return;
    }
    const submit = $('loginSubmit');
    submit.disabled = true;
    try {
        const res = await fetch(API.login, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ password })
        });
        let data = null;
        try {
            data = await res.json();
        } catch (e) {
            data = null;
        }
        if (!res.ok) {
            // 密码错误 / 未设置密码 / 锁定：弹窗不关闭，输入框描红并给出原因
            input.classList.add('is-error');
            input.select();
            setText(errorNode, (data && data.error) || '密码错误');
            return;
        }
        state.token = data && data.token ? data.token : '';
        sessionStorage.setItem('amToken', state.token);
        input.value = '';
        input.classList.remove('is-error');
        setText(errorNode, '');
        closeLogin();
        setAdmin(true);
        toast('已登录管理员', 'ok', 700);
    } catch (e) {
        input.classList.add('is-error');
        setText(errorNode, '网络错误，请稍后重试');
    } finally {
        submit.disabled = false;
    }
}

async function logout() {
    if (!state.demo && state.token) {
        api(API.logout, { method: 'POST' }).catch(() => {});
    }
    setAdmin(false);
    toast('已退出登录', 'ok', 1600);
}

/* ============================ 仪表盘：正在播放 ============================ */

function renderStatus(data) {
    const playing = !!(data && data.playing && data.song);
    const song = playing ? data.song : null;

    setText($('navVersion'), data && data.version ? 'v' + data.version : '');
    setText($('footInfo'), data && data.api ? 'API ' + data.api : '');
    const online = data ? Number(data.playerCount) || 0 : 0;
    setText($('navOnline'), '');
    $('navOnline').appendChild(el('i', 'dot'), document.createTextNode('在线 ' + online + ' '));

    setText($('playState'), playing ? '播放中' : '未播放');
    $('playState').className = 'chip ' + (playing ? 'chip-ok' : 'chip-quiet');

    const vinyl = $('vinyl');
    if (vinyl) {
        vinyl.classList.toggle('is-playing', playing);
    }

    const cover = $('cover');
    const src = song ? safeImage(song.picUrl) : '';
    if (src) {
        if (cover.getAttribute('src') !== src) {
            cover.setAttribute('src', src);
        }
        cover.classList.remove('is-empty');
    } else {
        cover.removeAttribute('src');
        cover.classList.add('is-empty');
    }

    if (song) {
        setText($('songName'), song.name || '未知歌曲');
        setText($('songAuthor'), song.author || '未知歌手');
        const album = song.album || '';
        setText($('songAlbum'), album);
        show($('songAlbum'), !!album);
        show($('songAlbum').previousElementSibling, !!album);
        setText($('songPlayer'), '点歌：' + (song.player || '未知'));
        setText($('songLength'), '时长 ' + fmtClock(song.length));
        setText($('songId'), song.id ? 'ID ' + song.id : '');

        const all = Number(data.allTime) || 0;
        const now = Number(data.nowTime) || 0;
        const ratio = all > 0 ? clampPercent((now / all) * 100) : 0;
        $('progressFill').style.width = ratio + '%';
        $('progressDot').style.left = ratio + '%';
        setText($('timeNow'), fmtClock(now));
        setText($('timeAll'), fmtClock(all));
    } else {
        setText($('songName'), '没有正在播放的歌曲');
        setText($('songAuthor'), '空闲中');
        show($('songAlbum'), false);
        show($('songAlbum').previousElementSibling, false);
        setText($('songPlayer'), '点歌：--');
        setText($('songLength'), '时长 --');
        setText($('songId'), '');
        $('progressFill').style.width = '0%';
        $('progressDot').style.left = '0%';
        setText($('timeNow'), '0:00');
        setText($('timeAll'), '0:00');
        setLyrics('', '', '');
    }
}

/* ============================ 仪表盘：歌词 ============================ */

/**
 * 三行歌词（上一句 / 当前 / 下一句），变化时做平滑上移与渐隐。
 * 歌词接口当前由服务端按需提供：有 /api/public/lyric 或 status.lyric 时自动启用。
 */
function setLyrics(prev, cur, next) {
    const changed = cur !== state.lyric.cur;
    state.lyric = { prev: prev || '', cur: cur || '', next: next || '' };

    const box = $('lyrics');
    setText($('lyricPrev'), state.lyric.prev);
    setText($('lyricCur'), state.lyric.cur || '暂无歌词');
    setText($('lyricNext'), state.lyric.next);

    if (changed && box) {
        box.classList.remove('is-shifting');
        void box.offsetWidth;
        box.classList.add('is-shifting');
        setTimeout(() => box.classList.remove('is-shifting'), 460);
    }
}

function applyLyricPayload(payload) {
    if (!payload) {
        return;
    }
    if (typeof payload === 'string') {
        setLyrics('', payload, '');
        return;
    }
    setLyrics(payload.prev, payload.cur, payload.next);
}

async function probeLyrics() {
    if (state.demo) {
        state.lyricAvailable = true;
        return;
    }
    try {
        const data = await api(API.lyric);
        state.lyricAvailable = true;
        applyLyricPayload(data);
    } catch (e) {
        state.lyricAvailable = false;
    }
}

/* ============================ 仪表盘：队列 / 历史 ============================ */

function renderQueue(data) {
    state.lastQueue = data;
    const items = (data && data.items) || [];
    setText($('queueCount'), items.length + ' 首');
    const list = $('queueList');
    list.textContent = '';
    if (items.length === 0) {
        list.appendChild(el('li', 'qitem', ''));
        list.lastChild.appendChild(el('div', 'idx', '—'));
        const meta = el('div', 'meta');
        meta.appendChild(el('div', 'sub', '队列为空，等待点歌'));
        list.lastChild.appendChild(meta);
        return;
    }
    items.forEach((item) => {
        const li = el('li', 'qitem');
        li.appendChild(el('div', 'idx', item.index));
        const meta = el('div', 'meta');
        meta.appendChild(el('div', 'title', item.name || '未知歌曲'));
        const bits = [];
        if (item.author) {
            bits.push(item.author);
        }
        if (item.player) {
            bits.push('点歌 ' + item.player);
        }
        if (item.length) {
            bits.push(fmtClock(item.length));
        }
        meta.appendChild(el('div', 'sub', bits.join(' · ')));
        li.appendChild(meta);

        const tail = el('div', 'tail');
        if (item.banned) {
            tail.appendChild(el('span', 'chip chip-danger', '已封禁'));
        }
        if (state.admin) {
            const del = el('button', 'btn btn-ghost btn-sm', '移除');
            del.type = 'button';
            del.addEventListener('click', () => doAction(API.queueDelete, { index: item.index }, '移除'));
            tail.appendChild(del);
        }
        li.appendChild(tail);
        list.appendChild(li);
    });
}

function renderHistory(stats) {
    const songs = (stats && stats.songs) || [];
    const counter = new Map();
    ((stats && stats.top) || []).forEach((item) => counter.set(item.name, item.count));

    setText($('historyCount'), songs.length + ' 条');
    const list = $('historyList');
    list.textContent = '';
    if (songs.length === 0) {
        const li = el('li', 'qitem');
        li.appendChild(el('div', 'idx', '—'));
        const meta = el('div', 'meta');
        meta.appendChild(el('div', 'sub', '暂无历史点歌记录'));
        li.appendChild(meta);
        list.appendChild(li);
        return;
    }
    songs.slice(0, 12).forEach((item) => {
        const li = el('li', 'qitem');
        li.appendChild(el('div', 'idx', '♪'));
        const meta = el('div', 'meta');
        meta.appendChild(el('div', 'title', item.name || '未知歌曲'));
        const bits = [];
        if (item.player) {
            bits.push(item.player);
        }
        bits.push(fmtDateTime(item.time));
        meta.appendChild(el('div', 'sub', bits.join(' · ')));
        li.appendChild(meta);
        li.appendChild(el('div', 'tail', (counter.get(item.name) || 1) + ' 次'));
        list.appendChild(li);
    });
}

function renderPlayers(data) {
    state.lastPlayers = data;
    const items = (data && data.items) || [];
    setText($('playerCount'), items.length + ' 人');
    setText($('todayPlayers'), Number(data && data.todayPlayers) || 0);

    const counts = new Map();
    const statsPlayers = (state.lastStats && state.lastStats.players) || [];
    statsPlayers.forEach((p) => counts.set(p.name, p.songCount));
    setText($('historyPlayers'), statsPlayers.length);
    setText($('todaySongs'), statsPlayers.reduce((sum, p) => sum + (Number(p.songCount) || 0), 0));

    const list = $('playerList');
    list.textContent = '';
    if (items.length === 0) {
        const li = el('li', 'pitem');
        li.appendChild(el('div', 'psub', '暂无玩家在线'));
        list.appendChild(li);
        return;
    }
    items.forEach((item) => {
        const li = el('li', 'pitem');
        const left = el('div');
        left.appendChild(el('div', 'pname', item.name || '未知玩家'));
        left.appendChild(el('div', 'psub', '本次连接 ' + fmtDuration(item.connectMs)
            + ' · 点歌 ' + (counts.has(item.name) ? counts.get(item.name) : 0) + ' 次'));
        li.appendChild(left);

        const right = el('div', 'tail');
        if (item.banned) {
            right.appendChild(el('span', 'chip chip-danger', '已封禁'));
        }
        if (state.admin) {
            const btn = el('button', 'btn btn-ghost btn-sm', item.banned ? '解封' : '封禁');
            btn.type = 'button';
            btn.addEventListener('click', () => doAction(
                item.banned ? API.playerUnban : API.playerBan,
                { name: item.name }, item.banned ? '解封玩家' : '封禁玩家'));
            right.appendChild(btn);
        }
        li.appendChild(right);
        list.appendChild(li);
    });
}

/* ============================ 仪表盘：性能圆环 ============================ */

function setRing(name, percent) {
    const circle = document.querySelector('[data-ring="' + name + '"]');
    if (!circle) {
        return;
    }
    const circumference = 2 * Math.PI * 50;
    const clamped = clampPercent(percent);
    circle.style.strokeDasharray = circumference.toFixed(1);
    circle.style.strokeDashoffset = (circumference * (1 - clamped / 100)).toFixed(1);
}

function renderPerf(data) {
    state.lastPerf = data;
    const cpu = clampPercent(data && data.cpuPercent);
    const ram = clampPercent(data && data.ramPercent);
    const rx = Number(data && data.netRxKbps) || 0;
    const tx = Number(data && data.netTxKbps) || 0;
    const net = rx + tx;
    state.netPeak = Math.max(state.netPeak, net);

    setText($('ringCpu'), cpu.toFixed(1) + '%');
    setText($('ringRam'), ram.toFixed(1) + '%');
    setText($('ringNet'), net >= 1024 ? (net / 1024).toFixed(2) + 'M' : net.toFixed(0) + 'K');
    setRing('cpu', cpu);
    setRing('ram', ram);
    setRing('net', state.netPeak > 0 ? (net / state.netPeak) * 100 : 0);

    setText($('ringCpuNote'), 'CPU：' + (data && data.cpuModel ? data.cpuModel : '--'));
    setText($('ringRamNote'), '内存：' + (data ? data.ramUsedGb + ' / ' + data.ramTotalGb + ' GB' : '--'));
    setText($('ringNetNote'), '网络：↓ ' + rx.toFixed(1) + ' KB/s　↑ ' + tx.toFixed(1) + ' KB/s');
    setText($('perfModel'), data && data.coreCount ? data.coreCount + ' 核' : '');

    pushHistory(data);
    if (state.view === 'perf') {
        renderCharts();
    }
}

function pushHistory(data) {
    const maxPoints = 60;
    const push = (arr, value) => {
        arr.push(Number(value) || 0);
        while (arr.length > maxPoints) {
            arr.shift();
        }
    };
    push(state.history.cpu, data && data.cpuPercent);
    push(state.history.ram, data && data.ramPercent);
    push(state.history.netRx, data && data.netRxKbps);
    push(state.history.netTx, data && data.netTxKbps);

    const cores = data && data.cores;
    if (cores && cores.length > 0) {
        state.history.coreCount = cores.length;
        if (state.history.cores.length !== cores.length) {
            state.history.cores = cores.map(() => []);
        }
        cores.forEach((value, i) => push(state.history.cores[i], value));
    }
    state.chart.tick++;
}

/* ============================ 仪表盘：分列布局 ============================ */

/**
 * 把下排四张卡片分到若干列，并让每列最后一张撑满该列剩余高度。
 * 比纯 CSS 多列更可控，宽屏不会出现「某张卡片很高、旁边空着一大片」的观感。
 * <p>
 * 注意：必须先按 id 取出节点引用，再清空容器——节点一旦离开文档，
 * {@code document.getElementById} 就再也找不到它们了（否则会把卡片整批丢掉）。
 */
function layoutDashboard() {
    const box = $('dashboardMasonry');
    if (!box) {
        return;
    }
    // 桌面用两列：队列+历史 / 性能+玩家，两列高度接近，最后一张卡片只需少量拉伸
    const plan = window.innerWidth >= 760
        ? [['cardQueue', 'cardHistory'], ['cardPerf', 'cardPlayers']]
        : [['cardQueue', 'cardHistory', 'cardPerf', 'cardPlayers']];
    const signature = plan.map((ids) => ids.join(',')).join('|');
    if (box.dataset.signature === signature) {
        return;
    }
    const nodes = new Map();
    plan.forEach((ids) => ids.forEach((id) => {
        const node = $(id);
        if (node) {
            nodes.set(id, node);
        }
    }));
    if (nodes.size === 0) {
        return;                      // 卡片都还不在文档里时不要清空容器
    }

    box.dataset.signature = signature;
    box.textContent = '';
    plan.forEach((ids) => {
        const column = el('div', 'masonry-col');
        ids.forEach((id) => {
            const node = nodes.get(id);
            if (node) {
                node.classList.remove('col-fill');
                column.appendChild(node);
            }
        });
        if (column.lastElementChild) {
            column.lastElementChild.classList.add('col-fill');
        }
        box.appendChild(column);
    });
}

/* ============================ 性能页：折线图 ============================ */

const CHART_W = 640;
const CHART_H = 220;
const CORE_COLORS = ['#39C5BB', '#7ff0e6', '#ffc46b', '#ff8fab', '#8ecbff', '#b6a6ff',
    '#95e06c', '#ffa06b', '#6be3d0', '#c6e36b', '#e36be3', '#6b9be3'];

function svg(tag, attrs) {
    const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
    Object.keys(attrs || {}).forEach((key) => node.setAttribute(key, attrs[key]));
    return node;
}

/**
 * 读取 svg 的 viewBox 作为绘图坐标系。
 * 因为图表宽度是 100%、高度按 viewBox 比例自适应，所以整行宽的图必须用更宽的 viewBox，
 * 否则同一比例会被放大成两倍高（网络速率图就踩过这个坑）。
 */
function chartBounds(node) {
    const box = node.viewBox && node.viewBox.baseVal ? node.viewBox.baseVal : null;
    const width = box && box.width ? box.width : CHART_W;
    const height = box && box.height ? box.height : CHART_H;
    return {
        x0: Math.round(width * 0.055),
        x1: width - Math.round(width * 0.016),
        y0: 12,
        y1: height - 20
    };
}

/** 计算折线坐标：valuesList 为若干条数值序列，max 为纵轴上限 */
function chartGeometry(valuesList, max, bounds) {
    const scale = max > 0 ? max : 1;
    const span = bounds.x1 - bounds.x0;
    return valuesList.map((values) => {
        const list = values || [];
        const step = list.length > 1 ? span / (list.length - 1) : 0;
        return list.map((v, i) => {
            const ratio = Math.max(0, Math.min(1, (Number(v) || 0) / scale));
            return [bounds.x0 + step * i, bounds.y1 - ratio * (bounds.y1 - bounds.y0)];
        });
    });
}

/**
 * 画折线图
 *
 * @param svgId    svg 元素 id
 * @param series   [{ name, color, values }]
 * @param max      纵轴上限
 * @param labelFor 纵轴刻度文案，参数为 0~1 的比例
 */
function drawChart(svgId, series, max, labelFor) {
    const node = $(svgId);
    if (!node) {
        return;
    }
    node.textContent = '';

    const bounds = chartBounds(node);
    const points = chartGeometry(series.map((item) => item.values), max, bounds);

    [1, 0.75, 0.5, 0.25, 0].forEach((ratio) => {
        const y = bounds.y1 - ratio * (bounds.y1 - bounds.y0);
        node.appendChild(svg('line', {
            class: 'chart-grid', x1: bounds.x0, x2: bounds.x1, y1: y, y2: y
        }));
        const label = svg('text', { class: 'chart-axis', x: 2, y: y + 3 });
        label.textContent = labelFor(ratio);
        node.appendChild(label);
    });

    series.forEach((item, index) => {
        const pts = points[index] || [];
        if (pts.length === 0) {
            return;
        }
        const d = pts.map((p, i) => (i === 0 ? 'M' : 'L') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ');
        if (series.length === 1 && pts.length > 1) {
            const area = d + ' L' + pts[pts.length - 1][0].toFixed(1) + ' ' + bounds.y1
                + ' L' + pts[0][0].toFixed(1) + ' ' + bounds.y1 + ' Z';
            node.appendChild(svg('path', { class: 'chart-area', d: area, fill: item.color }));
        }
        node.appendChild(svg('path', { class: 'chart-line', d: d, stroke: item.color }));
    });
}

function renderLegend(nodeId, series) {
    const node = $(nodeId);
    if (!node) {
        return;
    }
    node.textContent = '';
    series.forEach((item) => {
        const span = el('span');
        const mark = el('i');
        mark.style.background = item.color;
        span.appendChild(mark);
        span.appendChild(document.createTextNode(item.name + ' ' + (item.hint || '')));
        node.appendChild(span);
    });
}

function renderCharts() {
    const hist = state.history;
    const cpuValues = hist.cpu;
    const ramValues = hist.ram;
    const rxValues = hist.netRx;
    const txValues = hist.netTx;

    if (state.chart.mode === 'cores' && hist.cores.length > 0) {
        const series = hist.cores.map((values, i) => ({
            name: '核心 ' + i,
            color: CORE_COLORS[i % CORE_COLORS.length],
            values
        }));
        drawChart('cpuChart', series, 100, (r) => Math.round(r * 100) + '%');
        renderLegend('cpuLegend', series);
    } else {
        const series = [{ name: '总使用率', color: '#39C5BB', values: cpuValues, hint: last(cpuValues) }];
        drawChart('cpuChart', series, 100, (r) => Math.round(r * 100) + '%');
        renderLegend('cpuLegend', series);
    }

    const ramSeries = [{ name: '内存占用', color: '#7ff0e6', values: ramValues, hint: last(ramValues) + '%' }];
    drawChart('ramChart', ramSeries, 100, (r) => Math.round(r * 100) + '%');
    setText($('ramChip'), last(ramValues) + '%');
    setText($('ramFoot'), state.lastPerf
        ? (state.lastPerf.ramUsedGb + ' / ' + state.lastPerf.ramTotalGb + ' GB　'
            + (state.lastPerf.ramModel || ''))
        : '--');

    const netMax = Math.max(64, state.netPeak);
    const netSeries = [
        { name: '下行', color: '#39C5BB', values: rxValues, hint: fmtRate(last(rxValues)) },
        { name: '上行', color: '#ffc46b', values: txValues, hint: fmtRate(last(txValues)) }
    ];
    drawChart('netChart', netSeries, netMax, (r) => Math.round(netMax * r) + 'K');
    renderLegend('netLegend', netSeries);
    setText($('netChip'), fmtRate(last(rxValues) + last(txValues)));
    setText($('netFoot'), '峰值 ' + fmtRate(state.netPeak) + '（按本次会话观测到的最高速率归一化）');

    setText($('cpuFoot'), state.lastPerf
        ? ('采样 ' + hist.cpu.length + ' 次 · ' + (state.lastPerf.cpuModel || '') + '　'
            + (state.chart.mode === 'cores' ? (hist.coreCount + ' 个核心') : ''))
        : '--');
}

function last(arr) {
    const values = arr || [];
    if (values.length === 0) {
        return 0;
    }
    return Math.round(values[values.length - 1] * 10) / 10;
}

function fmtRate(kbps) {
    const value = Number(kbps) || 0;
    if (value >= 1024) {
        return (value / 1024).toFixed(2) + ' MB/s';
    }
    return value.toFixed(1) + ' KB/s';
}

/* ============================ 统计页 ============================ */

function renderStats(data) {
    state.lastStats = data;
    const songs = (data && data.songs) || [];
    const top = (data && data.top) || [];
    const players = (data && data.players) || [];

    const byName = new Map();
    top.forEach((item) => byName.set(item.name, item.count));
    const totalSongs = top.reduce((sum, item) => sum + (Number(item.count) || 0), 0);

    setText($('statTotalSongs'), totalSongs);
    setText($('statUniqueSongs'), top.length);
    setText($('statTotalPlayers'), players.length);
    setText($('statOnlinePlayers'), Number(state.lastPlayers && state.lastPlayers.total) || 0);

    const topList = $('topList');
    topList.textContent = '';
    if (top.length === 0) {
        topList.appendChild(el('li', 'muted', '暂无点歌记录'));
    } else {
        top.slice(0, 10).forEach((item, i) => {
            const li = el('li');
            li.appendChild(el('span', 'no', String(i + 1).padStart(2, '0')));
            li.appendChild(el('span', 'nm', item.name || '未知歌曲'));
            li.appendChild(el('span', 'ct', item.count + ' 次'));
            topList.appendChild(li);
        });
    }

    const playersBody = $('statPlayers');
    playersBody.textContent = '';
    if (players.length === 0) {
        const tr = el('tr');
        const td = el('td', 'muted', '暂无玩家统计');
        td.colSpan = 4;
        tr.appendChild(td);
        playersBody.appendChild(tr);
    } else {
        players.slice().sort((a, b) => (Number(b.totalConnectMs) || 0) - (Number(a.totalConnectMs) || 0))
            .slice(0, 40)
            .forEach((item) => {
                const tr = el('tr');
                tr.appendChild(el('td', null, item.name || '--'));
                tr.appendChild(el('td', 'muted', (Number(item.songCount) || 0) + ' 次'));
                tr.appendChild(el('td', 'muted', fmtDuration(item.totalConnectMs)));
                tr.appendChild(el('td', 'muted', fmtDateTime(item.firstConnect)));
                playersBody.appendChild(tr);
            });
    }

    setText($('recentCount'), songs.length + ' 条');
    const recentBody = $('statRecent');
    recentBody.textContent = '';
    if (songs.length === 0) {
        const tr = el('tr');
        const td = el('td', 'muted', '暂无点歌记录');
        td.colSpan = 3;
        tr.appendChild(td);
        recentBody.appendChild(tr);
    } else {
        songs.forEach((item) => {
            const tr = el('tr');
            tr.appendChild(el('td', null, item.name || '--'));
            tr.appendChild(el('td', 'muted', item.player || '--'));
            tr.appendChild(el('td', 'muted', fmtDateTime(item.time)));
            recentBody.appendChild(tr);
        });
    }
}

function exportStatsCsv() {
    const data = state.lastStats;
    if (!data) {
        toast('暂无统计数据', 'bad', 1600);
        return;
    }
    const rows = [['类型', '名称', '玩家', '次数', '累计连接(秒)', '时间']];
    ((data.top) || []).forEach((item) => rows.push(['热门歌曲', item.name, '', item.count, '', '']));
    ((data.players) || []).forEach((item) => rows.push([
        '玩家', item.name, '', item.songCount,
        Math.round((Number(item.totalConnectMs) || 0) / 1000), fmtDateTime(item.firstConnect)
    ]));
    ((data.songs) || []).forEach((item) => rows.push(['点歌记录', item.name, item.player, '', '', fmtDateTime(item.time)]));

    const csv = rows.map((row) => row.map((cell) => '"' + String(cell === undefined ? '' : cell)
        .replace(/"/g, '""') + '"').join(',')).join('\r\n');
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = el('a');
    a.href = url;
    a.download = 'allmusic-stats-' + new Date().toISOString().slice(0, 10) + '.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
    toast('已导出 CSV', 'ok', 1600);
}

/* ============================ 设置页 ============================ */

function renderSettings() {
    const perf = state.lastPerf;
    const status = state.lastStatus;
    setText($('setVersion'), status && status.version ? 'v' + status.version : '--');
    setText($('setApi'), (status && status.api) || '--');
    setText($('setOnline'), (status ? Number(status.playerCount) || 0 : 0) + ' 人');
    setText($('setQueue'), (status ? Number(status.queueSize) || 0 : 0) + ' 首');
    if (perf) {
        setText($('setWeb'), '已启用（' + (perf.coreCount || '?') + ' 核 / '
            + (perf.ramTotalGb || '?') + ' GB）');
    }
}

async function loadBans() {
    const data = state.demo ? demo.bans() : await api(API.bans);
    const players = (data && data.players) || [];
    const musics = (data && data.musics) || [];

    const playerBox = $('banPlayers');
    playerBox.textContent = '';
    if (players.length === 0) {
        playerBox.appendChild(el('span', 'muted', '无'));
    } else {
        players.forEach((name) => playerBox.appendChild(banTag(name, () => doAction(
            API.playerUnban, { name }, '解封玩家'), false)));
    }

    const musicBox = $('banMusics');
    musicBox.textContent = '';
    if (musics.length === 0) {
        musicBox.appendChild(el('span', 'muted', '无'));
    } else {
        musics.forEach((id) => musicBox.appendChild(banTag(id, () => doAction(
            API.musicUnban, { id }, '解封歌曲'), true)));
    }
}

function banTag(text, onUnban, isMusic) {
    const tag = el('span', 'tag-item');
    tag.appendChild(document.createTextNode(isMusic ? 'ID ' + text : text));
    const btn = el('button', null, '解封');
    btn.type = 'button';
    btn.addEventListener('click', onUnban);
    tag.appendChild(btn);
    return tag;
}

/* ---------- 设置页：配置项读写 ---------- */

async function loadConfig() {
    if (state.demo) {
        renderConfig(demo.config());
        setText($('configState'), '演示数据');
        return;
    }
    const data = await api(API.config);
    renderConfig(data);
}

function renderConfig(data) {
    const box = $('configGroups');
    box.textContent = '';
    const groups = (data && data.groups) || [];
    let count = 0;
    if (groups.length === 0) {
        box.appendChild(el('p', 'note', '暂无可配置项'));
        setText($('configState'), '0 项');
        return;
    }
    groups.forEach((group) => {
        const wrap = el('div', 'config-group');
        wrap.appendChild(el('h3', null, group.title));
        const rows = el('div', 'config-rows');
        (group.items || []).forEach((item) => {
            rows.appendChild(configRow(item));
            count++;
        });
        wrap.appendChild(rows);
        box.appendChild(wrap);
    });
    setText($('configState'), groups.length + ' 组 · ' + count + ' 项');
}

function configRow(item) {
    const row = el('div', 'config-row');

    const label = el('div', 'config-label');
    const name = el('div', 'config-name');
    name.appendChild(el('span', null, item.cn));
    name.appendChild(el('span', 'config-path', item.path));
    if (item.restart) {
        name.appendChild(el('span', 'chip chip-quiet', '需重启'));
    }
    label.appendChild(name);
    if (item.desc) {
        label.appendChild(el('div', 'config-desc', item.desc));
    }
    row.appendChild(label);

    const control = el('div', 'config-control');
    let input;
    if (item.type === 'bool') {
        input = el('select');
        ['true', 'false'].forEach((value) => {
            const option = el('option', null, value);
            option.value = value;
            input.appendChild(option);
        });
    } else {
        input = el('input');
        input.type = item.type === 'int' ? 'number' : 'text';
        input.setAttribute('aria-label', item.cn);
    }
    input.value = item.value;
    control.appendChild(input);

    const save = el('button', 'btn btn-ghost btn-sm', '保存');
    save.type = 'button';
    save.disabled = true;
    const sync = () => {
        save.disabled = String(input.value) === String(item.value);
    };
    input.addEventListener('input', sync);
    input.addEventListener('change', sync);
    save.addEventListener('click', () => saveConfigItem(item, input.value, save, input));
    if (item.type === 'bool') {
        // 开关类改动直接落盘，不用再点保存（避免「拨了开关但没保存」的误解）
        input.addEventListener('change', () => saveConfigItem(item, input.value, save, input));
    } else {
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !save.disabled) {
                e.preventDefault();
                saveConfigItem(item, input.value, save, input);
            }
        });
    }
    control.appendChild(save);
    row.appendChild(control);
    return row;
}

async function saveConfigItem(item, value, button, input) {
    if (state.demo) {
        item.value = value;
        toast(item.cn + ' → ' + value + '（演示）', 'ok', 1800);
        return;
    }
    button.disabled = true;
    try {
        const res = await api(API.config, { method: 'POST', body: JSON.stringify({ path: item.path, value }) });
        item.value = String(value);
        toast((res && res.output) || (item.cn + ' 已保存'), 'ok', 2400);
    } catch (e) {
        toast('保存失败：' + e.message, 'bad', 2600);
        input.value = item.value;
    } finally {
        button.disabled = false;
    }
}

async function savePassword() {
    const input = $('newPassword');
    const value = input.value.trim();
    if (value.length < 4) {
        setText($('passwordMsg'), '密码至少 4 位');
        return;
    }
    if (state.demo) {
        input.value = '';
        setText($('passwordMsg'), '（演示）密码已更新，所有会话已失效');
        setAdmin(false);
        toast('密码已更新，请重新登录', 'ok', 2200);
        return;
    }
    try {
        const res = await api(API.password, { method: 'POST', body: JSON.stringify({ password: value }) });
        input.value = '';
        setText($('passwordMsg'), (res && res.output) || '已更新');
        setAdmin(false);
        toast('密码已更新，请重新登录', 'ok', 2200);
    } catch (e) {
        setText($('passwordMsg'), '设置失败：' + e.message);
    }
}

async function clearPassword() {
    if (state.demo) {
        setText($('passwordMsg'), '（演示）已清除管理员密码');
        setAdmin(false);
        toast('已清除管理员密码', 'ok', 2000);
        return;
    }
    try {
        const res = await api(API.password, { method: 'POST', body: JSON.stringify({ password: '' }) });
        setText($('passwordMsg'), (res && res.output) || '已清除');
        setAdmin(false);
        toast('已清除管理员密码', 'ok', 2200);
    } catch (e) {
        setText($('passwordMsg'), '清除失败：' + e.message);
    }
}

/* ============================ 控制台 ============================ */

const QUICK_COMMANDS = [
    { label: '切歌', command: 'next' },
    { label: '队列', command: 'list' },
    { label: '重载配置', command: 'reload' },
    { label: '封禁玩家', command: 'banplayer' },
    { label: '解封玩家', command: 'unbanplayer' }
];

function renderQuickCommands() {
    const box = $('quickCommands');
    box.textContent = '';
    QUICK_COMMANDS.forEach((item) => {
        const btn = el('button', 'btn btn-ghost btn-sm', item.label);
        btn.type = 'button';
        btn.addEventListener('click', () => {
            $('commandInput').value = item.command + ' ';
            $('commandInput').focus();
        });
        box.appendChild(btn);
    });
}

function logClass(text) {
    if (/异常|错误|Exception|ERROR|失败/.test(text)) {
        return 'line-error';
    }
    if (/警告|WARN/.test(text)) {
        return 'line-warn';
    }
    if (/\[AllMusic\]|正在播放|点歌/.test(text)) {
        return 'line-music';
    }
    return '';
}

function scrollLog(force) {
    const box = $('consoleLog');
    if (!box) {
        return;
    }
    const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 60;
    if (force || nearBottom) {
        box.scrollTop = box.scrollHeight;
    }
}

async function loadLogs() {
    if (!state.admin || state.log.paused) {
        return;
    }
    const data = await api(API.logs(state.log.seq, 200));
    if (data && data.lines && data.lines.length > 0) {
        const box = $('consoleLog');
        data.lines.forEach((line) => {
            const span = el('span', logClass(line.text), line.text + '\n');
            box.appendChild(span);
        });
        state.log.count += data.lines.length;
        while (box.childNodes.length > 800) {
            box.removeChild(box.firstChild);
        }
        scrollLog(false);
        setText($('logState'), '已连接 · ' + state.log.count + ' 行');
    }
    if (data) {
        state.log.seq = data.nextSeq;
    }
}

async function runCommand(command) {
    const cmd = String(command || '').trim();
    if (!cmd) {
        return;
    }
    const msg = $('commandMsg');
    setText(msg, '执行中…');
    try {
        const res = await api(API.command, { method: 'POST', body: JSON.stringify({ command: cmd }) });
        const output = res && res.output ? res.output.replace(/\s*\n\s*/g, '　') : '已执行';
        setText(msg, '/music ' + cmd + ' → ' + output);
        setTimeout(refreshAll, 700);
    } catch (e) {
        setText(msg, '/music ' + cmd + ' → ' + e.message);
    }
}

/* ============================ 管理员操作 ============================ */

async function doAction(path, body, label) {
    try {
        const res = await api(path, { method: 'POST', body: JSON.stringify(body || {}) });
        const output = res && res.output ? '：' + res.output.replace(/\s*\n\s*/g, '　') : '';
        toast(label + ' 已提交' + output, 'ok', 2000);
        setTimeout(refreshAll, 700);
    } catch (e) {
        toast(label + ' 失败：' + e.message, 'bad', 2600);
    }
}

/* ============================ 轮询 ============================ */

function poll(fn, flag, interval) {
    const run = async () => {
        if (document.hidden || state.busy[flag]) {
            setTimeout(run, interval);
            return;
        }
        state.busy[flag] = true;
        try {
            await fn();
        } catch (e) {
            /* 静默失败，下一轮重试 */
        } finally {
            state.busy[flag] = false;
            setTimeout(run, interval);
        }
    };
    run();
}

async function loadStatus() {
    if (state.demo) {
        const data = demo.status();
        renderStatus(data);
        applyLyricPayload(data.lyric);
        renderSettings();
        return;
    }
    const data = await api(API.status);
    state.lastStatus = data;
    renderStatus(data);
    renderSettings();
}

async function loadQueue() {
    if (state.demo) {
        return renderQueue(demo.queue());
    }
    renderQueue(await api(API.queue));
}

async function loadPlayers() {
    if (state.demo) {
        return renderPlayers(demo.players());
    }
    renderPlayers(await api(API.players));
}

async function loadStats() {
    if (state.demo) {
        const data = demo.stats();
        renderStats(data);
        renderHistory(data);
        renderPlayers(state.lastPlayers);
        return;
    }
    const data = await api(API.stats(50));
    renderStats(data);
    renderHistory(data);
    renderPlayers(state.lastPlayers);
}

async function loadPerf() {
    if (state.demo) {
        return renderPerf(demo.perf());
    }
    renderPerf(await api(API.perf));
}

function refreshAll() {
    loadStatus().catch(() => {});
    loadQueue().catch(() => {});
    loadPlayers().catch(() => {});
    loadStats().catch(() => {});
    loadPerf().catch(() => {});
    if (state.admin) {
        loadLogs().catch(() => {});
        loadBans().catch(() => {});
    }
}

/* ============================ 演示数据 ============================ */

/** 演示模式用的封面（内联 SVG，不依赖外部图片） */
const DEMO_COVER = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 300">'
    + '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">'
    + '<stop offset="0" stop-color="#39C5BB"/><stop offset="1" stop-color="#0c4f4b"/></linearGradient></defs>'
    + '<rect width="300" height="300" fill="url(#g)"/>'
    + '<circle cx="150" cy="150" r="98" fill="#0b1d20" opacity="0.35"/>'
    + '<path d="M122 198V110l78-16v88" fill="none" stroke="#eafffb" stroke-width="10" stroke-linecap="round" stroke-linejoin="round"/>'
    + '<circle cx="106" cy="198" r="19" fill="#eafffb"/><circle cx="184" cy="182" r="19" fill="#eafffb"/>'
    + '<text x="150" y="272" font-family="sans-serif" font-size="24" letter-spacing="4" fill="#eafffb" text-anchor="middle">DEMO</text>'
    + '</svg>');

/**
 * 演示数据：仅在服务端不可达（例如直接双击打开 index.html）时启用，
 * 让整套界面与动效都能预览（页面右下角会显示「演示数据」标记）。
 */
const demo = (() => {
    const started = Date.now();
    const lyrics = [
        '静かな夜に　響くメロディ',
        '君の声が　風に溶けていく',
        '手を伸ばして　光を掴む',
        'この歌よ　永遠に届け',
        'また会えると　信じている'
    ];
    const queue = [
        { index: 1, id: '1901371647', name: '千本桜', author: '初音ミク', player: 'emmWow9664', length: 244 },
        { index: 2, id: '186016', name: 'World is Mine', author: 'ryo / 初音ミク', player: 'MikuFan', length: 208 },
        { index: 3, id: '509729', name: 'ODDS & ENDS', author: 'ryo', player: 'Sakura', length: 231 }
    ];
    const historyNames = ['千本桜', 'Melt', 'ロミオとシンデレラ', 'Odds & Ends', 'Tell Your World'];
    const players = ['emmWow9664', 'MikuFan', 'Sakura', 'Hatsune'];

    const seconds = () => Math.floor((Date.now() - started) / 1000);
    let firstConnect = Date.now() - 1000 * 60 * 97;

    return {
        status() {
            const t = seconds() % 244;
            return {
                playing: true,
                version: 'demo',
                api: 'netease',
                playerCount: players.length,
                queueSize: queue.length,
                nowTime: t,
                allTime: 244,
                song: {
                    id: '1901371647',
                    name: '千本桜',
                    author: '初音ミク',
                    album: '千本桜 - Single',
                    player: 'emmWow9664',
                    picUrl: DEMO_COVER,
                    length: 244
                },
                lyric: {
                    prev: lyrics[Math.floor(t / 12) % lyrics.length],
                    cur: lyrics[(Math.floor(t / 12) + 1) % lyrics.length],
                    next: lyrics[(Math.floor(t / 12) + 2) % lyrics.length]
                }
            };
        },
        queue() {
            return { total: queue.length, items: queue };
        },
        players() {
            return {
                total: players.length,
                todayPlayers: players.length + 2,
                items: players.map((name, i) => ({
                    name,
                    connectMs: Date.now() - firstConnect - i * 1000 * 60 * 7,
                    banned: false
                }))
            };
        },
        stats() {
            return {
                songs: historyNames.map((name, i) => ({
                    name,
                    player: players[i % players.length],
                    time: Date.now() - i * 1000 * 60 * 9,
                    id: String(190000 + i)
                })),
                top: [
                    { name: '千本桜', count: 12 },
                    { name: 'Melt', count: 8 },
                    { name: 'World is Mine', count: 6 },
                    { name: 'ロミオとシンデレラ', count: 4 },
                    { name: 'Tell Your World', count: 3 }
                ],
                players: players.map((name, i) => ({
                    name,
                    songCount: 12 - i * 2,
                    totalConnectMs: (97 - i * 7) * 60 * 1000,
                    firstConnect: firstConnect + i * 60 * 1000
                }))
            };
        },
        perf() {
            const t = seconds();
            const wave = (base, amp, speed, phase) => base + amp * Math.sin(t / speed + (phase || 0))
                + (Math.random() - 0.5) * amp * 0.35;
            const cores = 8;
            return {
                cpuPercent: Math.max(2, Math.min(98, wave(38, 16, 9))),
                ramPercent: Math.max(5, Math.min(96, wave(62, 5, 17, 1))),
                ramUsedGb: Math.round(wave(19.8, 1.2, 17, 1) * 10) / 10,
                ramTotalGb: 32,
                netRxKbps: Math.max(0, wave(420, 260, 6)),
                netTxKbps: Math.max(0, wave(180, 120, 7, 2)),
                cpuModel: 'AMD Ryzen 7 5800X (demo)',
                ramModel: 'DDR4 32GB (demo)',
                coreCount: cores,
                cores: Array.from({ length: cores }, (v, i) => Math.max(1, Math.min(99, wave(30 + i * 3, 22, 5 + i, i))))
            };
        },
        config() {
            return {
                groups: [
                    {
                        title: '基础',
                        items: [
                            { path: 'lyricDelay', cn: '歌词延迟', en: 'lyricDelay', desc: '歌词显示延迟毫秒数', type: 'int', value: '500', restart: false },
                            { path: 'sendLyric', cn: '发送歌词', en: 'sendLyric', desc: '是否向客户端发送歌词', type: 'bool', value: 'true', restart: false },
                            { path: 'defaultApi', cn: '默认音乐 API', en: 'defaultApi', desc: '默认使用的音乐 API 名称', type: 'str', value: 'netease', restart: false }
                        ]
                    },
                    {
                        title: '独立服务端',
                        items: [
                            { path: 'standalone.port', cn: '监听端口', en: 'port', desc: '独立服务端 TCP 监听端口', type: 'int', value: '5223', restart: true },
                            { path: 'standalone.webPort', cn: 'Web 端口', en: 'webPort', desc: '内嵌 Web 面板监听端口', type: 'int', value: '8080', restart: true },
                            { path: 'standalone.webEnabled', cn: 'Web 面板', en: 'webEnabled', desc: '是否启用内嵌 Web 展示与管理面板', type: 'bool', value: 'true', restart: true }
                        ]
                    }
                ]
            };
        },
        bans() {
            return { players: ['Griefer01'], musics: ['123456', '998877'] };
        },
        logs() {
            const lines = [
                '<light_purple>[AllMusic]<yellow>演示模式：未连接到服务端，以下为示例日志',
                '[AllMusic]正在播放：千本桜 | 初音ミク by: emmWow9664',
                '[AllMusic]点歌成功：World is Mine | ryo / 初音ミク',
                '[WARN] 示例：netapi 响应较慢',
                '[AllMusic]已停止你的音乐播放'
            ];
            return lines[seconds() % lines.length];
        }
    };
})();

async function detectDemo() {
    if (location.protocol === 'file:') {
        return true;
    }
    try {
        const res = await fetch(API.status, { headers: { Accept: 'application/json' } });
        return !res.ok;
    } catch (e) {
        return true;
    }
}

/* ============================ 绑定与启动 ============================ */

function bind() {
    $('themeBtn').addEventListener('click', toggleTheme);

    $$('.navbtn').forEach((btn) => {
        btn.addEventListener('click', () => switchView(btn.dataset.view));
    });

    $('adminBtn').addEventListener('click', () => {
        if (state.admin) {
            logout();
        } else {
            openLogin();
        }
    });

    $('loginCancel').addEventListener('click', closeLogin);
    $('loginForm').addEventListener('submit', (e) => {
        e.preventDefault();
        submitLogin();
    });
    $('loginPassword').addEventListener('input', () => {
        $('loginPassword').classList.remove('is-error');
        setText($('loginError'), '');
    });
    $('loginModal').addEventListener('click', (e) => {
        if (e.target && e.target.dataset && e.target.dataset.close) {
            closeLogin();
        }
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !$('loginModal').hidden) {
            closeLogin();
        }
    });

    $('settingsLogout').addEventListener('click', logout);
    $('bansRefresh').addEventListener('click', () => loadBans().catch((err) => toast('刷新失败：' + err.message, 'bad')));
    $('configRefresh').addEventListener('click', () => loadConfig().catch((err) => toast('加载配置失败：' + err.message, 'bad')));
    $('savePassword').addEventListener('click', savePassword);
    $('clearPassword').addEventListener('click', clearPassword);

    $('statsRefresh').addEventListener('click', () => loadStats().catch(() => {}));
    $('statsExport').addEventListener('click', exportStatsCsv);

    $('cpuModeTotal').addEventListener('click', () => setCpuMode('total'));
    $('cpuModeCores').addEventListener('click', () => setCpuMode('cores'));

    $('commandForm').addEventListener('submit', (e) => {
        e.preventDefault();
        const input = $('commandInput');
        const value = input.value;
        input.value = '';
        runCommand(value);
    });
    $('logPause').addEventListener('click', () => {
        state.log.paused = !state.log.paused;
        setText($('logPause'), state.log.paused ? '继续' : '暂停');
        setText($('logState'), state.log.paused ? '已暂停' : '已连接');
    });
    $('logClear').addEventListener('click', () => {
        setText($('consoleLog'), '');
        state.log.count = 0;
    });

    window.addEventListener('resize', () => {
        layoutDashboard();
        if (state.view === 'perf') {
            renderCharts();
        }
    });
}

function setCpuMode(mode) {
    state.chart.mode = mode;
    $('cpuModeTotal').classList.toggle('is-active', mode === 'total');
    $('cpuModeCores').classList.toggle('is-active', mode === 'cores');
    $('cpuModeTotal').setAttribute('aria-pressed', mode === 'total' ? 'true' : 'false');
    $('cpuModeCores').setAttribute('aria-pressed', mode === 'cores' ? 'true' : 'false');
    renderCharts();
}

async function boot() {
    initTheme();
    bind();
    renderQuickCommands();
    layoutDashboard();

    state.demo = await detectDemo();
    show($('demoBadge'), state.demo);
    if (state.demo) {
        // 演示模式：定时推进数据，让歌词、圆环、折线图都在动
        setInterval(() => {
            if (document.hidden) {
                return;
            }
            loadStatus();
            loadPerf();
            if (state.chart.tick % 5 === 0) {
                loadQueue();
                loadPlayers();
                loadStats();
            }
            if (state.admin) {
                const text = demo.logs();
                const box = $('consoleLog');
                box.appendChild(el('span', logClass(text), text + '\n'));
                while (box.childNodes.length > 80) {
                    box.removeChild(box.firstChild);
                }
                scrollLog(false);
            }
        }, 2000);
        loadStatus();
        loadQueue();
        loadPlayers();
        loadStats();
        loadPerf();
        if (state.admin) {
            loadBans().catch(() => {});
        } else {
            renderBansPlaceholder();
        }
        return;
    }

    await probeLyrics();
    // 歌词随播放进度变化，单独高频轮询（服务端没有歌词接口时 probeLyrics 会关掉这一路）
    poll(async () => {
        if (state.lyricAvailable) {
            applyLyricPayload(await api(API.lyric));
        }
    }, 'lyric', 2000);
    poll(loadStatus, 'status', 2000);
    poll(loadQueue, 'queue', 4000);
    poll(loadPlayers, 'players', 2500);
    poll(loadStats, 'stats', 12000);
    poll(loadPerf, 'perf', 3000);
    poll(async () => {
        if (state.admin) {
            await loadLogs();
        }
    }, 'logs', 2000);

    refreshAll();

    if (state.token) {
        try {
            await api(API.me);
            setAdmin(true);
            toast('已恢复管理员会话', 'ok', 1600);
        } catch (e) {
            setAdmin(false);
        }
    }
}

function renderBansPlaceholder() {
    const box = $('banPlayers');
    const musicBox = $('banMusics');
    if (box) {
        box.textContent = '';
        box.appendChild(el('span', 'muted', '登录后可见'));
    }
    if (musicBox) {
        musicBox.textContent = '';
        musicBox.appendChild(el('span', 'muted', '登录后可见'));
    }
}

boot();