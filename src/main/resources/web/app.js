'use strict';

/**
 * AllMusic 服务端 Web 面板前端。
 * 所有外部文本（歌名、玩家名、日志）一律通过 textContent 写入 DOM，避免 XSS。
 */

const $ = (id) => document.getElementById(id);

const state = {
    token: sessionStorage.getItem('amToken') || '',
    admin: false,
    logSeq: 0,
    busy: { status: false, queue: false, players: false, stats: false, perf: false, logs: false }
};

/* ---------- 通用工具 ---------- */

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

function fmtDuration(ms) {
    const total = Math.max(0, Math.floor((Number(ms) || 0) / 1000));
    const m = Math.floor(total / 60);
    const s = total % 60;
    return m + ':' + String(s).padStart(2, '0');
}

function fmtConnect(ms) {
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

/** 封面地址只接受 http/https，避免把非预期协议塞进 img.src */
function safeImage(url) {
    if (typeof url !== 'string') {
        return '';
    }
    return (url.startsWith('https://') || url.startsWith('http://')) ? url : '';
}

function setMsg(node, text, kind) {
    if (!node) {
        return;
    }
    node.textContent = text || '';
    node.className = 'msg' + (kind ? ' ' + kind : '');
}

async function api(path, options) {
    const opts = options || {};
    const headers = Object.assign({}, opts.headers || {});
    if (state.token) {
        headers['Authorization'] = 'Bearer ' + state.token;
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
    if (res.status === 401) {
        setAdmin(false);
    }
    if (!res.ok) {
        throw new Error((data && data.error) ? data.error : ('请求失败（HTTP ' + res.status + '）'));
    }
    return data;
}

/* ---------- 渲染 ---------- */

function renderStatus(data) {
    $('version').textContent = data.version ? ('v' + data.version) : '';
    $('api').textContent = data.api ? ('API ' + data.api) : '';
    $('online').textContent = '在线 ' + data.playerCount;

    const song = data.song;
    const cover = $('cover');
    if (song) {
        $('songName').textContent = song.name || '未知歌曲';
        const parts = [];
        if (song.author) {
            parts.push(song.author);
        }
        if (song.player) {
            parts.push('点歌：' + song.player);
        }
        $('songMeta').textContent = parts.join('   ');
        const src = safeImage(song.picUrl);
        if (src) {
            cover.src = src;
            cover.hidden = false;
        } else {
            cover.removeAttribute('src');
            cover.hidden = true;
        }
        const all = Number(data.allTime) || 0;
        const now = Number(data.nowTime) || 0;
        const ratio = all > 0 ? Math.max(0, Math.min(100, (now / all) * 100)) : 0;
        $('progress').style.width = ratio + '%';
        $('times').textContent = fmtDuration(now) + ' / ' + fmtDuration(all);
    } else {
        $('songName').textContent = '没有正在播放的歌曲';
        $('songMeta').textContent = '';
        cover.removeAttribute('src');
        cover.hidden = true;
        $('progress').style.width = '0%';
        $('times').textContent = '0:00 / 0:00';
    }
}

function renderQueue(data) {
    $('queueCount').textContent = data.total > 0 ? ('共 ' + data.total + ' 首') : '';
    const body = $('queueBody');
    body.textContent = '';
    if (!data.items || data.items.length === 0) {
        const tr = el('tr');
        const td = el('td', 'muted', '队列为空');
        td.colSpan = state.admin ? 6 : 5;
        tr.appendChild(td);
        body.appendChild(tr);
        return;
    }
    data.items.forEach((item) => {
        const tr = el('tr');
        tr.appendChild(el('td', 'muted', item.index));
        tr.appendChild(el('td', null, item.name));
        tr.appendChild(el('td', 'muted', item.author));
        tr.appendChild(el('td', 'muted', item.player));
        tr.appendChild(el('td', 'muted', fmtDuration(item.length)));
        if (state.admin) {
            const td = el('td');
            const box = el('div', 'row');
            const del = el('button', 'btn danger', '移除');
            del.type = 'button';
            del.addEventListener('click', () => doAction('/api/admin/queue/delete', { index: item.index }, '移除'));
            box.appendChild(del);
            const ban = el('button', 'btn plain', item.banned ? 'UNBAN' : 'BAN');
            ban.type = 'button';
            ban.addEventListener('click', () => doAction(
                item.banned ? '/api/admin/music/unban' : '/api/admin/music/ban',
                { id: item.id }, item.banned ? '解封歌曲' : '封禁歌曲'));
            box.appendChild(ban);
            td.appendChild(box);
            tr.appendChild(td);
        }
        body.appendChild(tr);
    });
}

function renderPlayers(data) {
    $('playerCount').textContent = data.total > 0 ? ('共 ' + data.total + ' 人') : '';
    $('todayCount').textContent = '今日连接 ' + (Number(data.todayPlayers) || 0) + ' 人';
    const list = $('playerList');
    list.textContent = '';
    if (!data.items || data.items.length === 0) {
        list.appendChild(el('li', 'muted', '暂无玩家在线'));
        return;
    }
    data.items.forEach((item) => {
        const li = el('li');
        const left = el('div', 'name');
        left.appendChild(el('div', null, item.name));
        left.appendChild(el('div', 'muted', '连接时长 ' + fmtConnect(item.connectMs)));
        li.appendChild(left);
        if (state.admin) {
            const ban = el('button', 'btn plain', item.banned ? 'UNBAN' : 'BAN');
            ban.type = 'button';
            ban.addEventListener('click', () => doAction(
                item.banned ? '/api/admin/player/unban' : '/api/admin/player/ban',
                { name: item.name }, item.banned ? '解封玩家' : '封禁玩家'));
            li.appendChild(ban);
        }
        list.appendChild(li);
    });
}

function renderStats(data) {
    const top = $('topList');
    top.textContent = '';
    if (!data.top || data.top.length === 0) {
        top.appendChild(el('li', 'muted', '暂无点歌记录'));
    } else {
        data.top.slice(0, 10).forEach((item) => {
            const li = el('li');
            li.appendChild(el('span', 'name', item.name));
            li.appendChild(el('span', 'muted', item.count + ' 次'));
            top.appendChild(li);
        });
    }
    const body = $('statBody');
    body.textContent = '';
    if (!data.players || data.players.length === 0) {
        const tr = el('tr');
        const td = el('td', 'muted', '暂无玩家统计');
        td.colSpan = 3;
        tr.appendChild(td);
        body.appendChild(tr);
        return;
    }
    data.players.slice(0, 30).forEach((item) => {
        const tr = el('tr');
        tr.appendChild(el('td', null, item.name));
        tr.appendChild(el('td', 'muted', item.songCount + ' 次'));
        tr.appendChild(el('td', 'muted', fmtConnect(item.totalConnectMs)));
        body.appendChild(tr);
    });
}

/* ---------- 性能监视 ---------- */

function clampPercent(value) {
    const n = Number(value);
    if (!isFinite(n)) {
        return 0;
    }
    return Math.max(0, Math.min(100, n));
}

function renderPerf(data) {
    const cpu = clampPercent(data.cpuPercent);
    $('cpuVal').textContent = cpu.toFixed(1) + '%';
    $('cpuBar').style.width = cpu + '%';
    $('cpuModel').textContent = data.cpuModel || '';

    const ram = clampPercent(data.ramPercent);
    $('ramVal').textContent = ram.toFixed(1) + '%　' + data.ramUsedGb + ' / ' + data.ramTotalGb + ' GB';
    $('ramBar').style.width = ram + '%';
    $('ramModel').textContent = data.ramModel || '';

    const rx = Number(data.netRxKbps) || 0;
    const tx = Number(data.netTxKbps) || 0;
    $('netVal').textContent = (rx + tx).toFixed(1) + ' KB/s';
    $('netBar').style.width = Math.min(100, rx + tx) + '%';
    $('netHint').textContent = '↓ ' + rx.toFixed(1) + ' KB/s　↑ ' + tx.toFixed(1) + ' KB/s';

    renderCores(data.cores);
}

function renderCores(cores) {
    const grid = $('coreGrid');
    if (!cores || cores.length === 0) {
        if (grid.dataset.state !== 'none') {
            grid.textContent = '';
            grid.appendChild(el('span', 'muted', '首次采样中或当前平台不支持各核心查看'));
            grid.dataset.state = 'none';
        }
        return;
    }
    if (grid.childElementCount !== cores.length) {
        grid.textContent = '';
        cores.forEach((core, i) => {
            const cell = el('div', 'corecell');
            cell.appendChild(el('div', 'corelabel', '核心 ' + i));
            const bar = el('div', 'bar small');
            const fill = el('div', 'barfill');
            bar.appendChild(fill);
            cell.appendChild(bar);
            cell.appendChild(el('div', 'coreval', '--'));
            cell.dataset.fill = String(i);
            grid.appendChild(cell);
        });
        grid.dataset.state = 'cores';
    }
    cores.forEach((core, i) => {
        const cell = grid.children[i];
        if (!cell) {
            return;
        }
        const percent = clampPercent(core);
        const fill = cell.querySelector('.barfill');
        const value = cell.querySelector('.coreval');
        if (fill) {
            fill.style.width = percent + '%';
        }
        if (value) {
            value.textContent = percent.toFixed(0) + '%';
        }
    });
}

/* ---------- 轮询 ---------- */

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
    renderStatus(await api('/api/public/status'));
}

async function loadQueue() {
    renderQueue(await api('/api/public/queue'));
}

async function loadPlayers() {
    renderPlayers(await api('/api/public/players'));
}

async function loadStats() {
    renderStats(await api('/api/public/stats?limit=50'));
}

async function loadPerf() {
    renderPerf(await api('/api/public/perf'));
}

async function loadLogs() {
    const data = await api('/api/admin/logs?since=' + state.logSeq + '&limit=200');
    if (data && data.lines && data.lines.length > 0) {
        const box = $('logs');
        data.lines.forEach((line) => box.appendChild(document.createTextNode(line.text + '\n')));
        while (box.childNodes.length > 600) {
            box.removeChild(box.firstChild);
        }
        box.scrollTop = box.scrollHeight;
    }
    if (data) {
        state.logSeq = data.nextSeq;
    }
}

/* ---------- 管理员 ---------- */

function setAdmin(on) {
    state.admin = on;
    document.querySelectorAll('.adminOnly').forEach((node) => {
        node.hidden = !on;
    });
    const panel = $('adminPanel');
    if (panel) {
        panel.hidden = !on;
    }
    if (!on) {
        state.token = '';
        sessionStorage.removeItem('amToken');
        state.logSeq = 0;
        $('loginBox').hidden = false;
        $('adminBox').hidden = true;
        $('adminToggle').textContent = '管理员登录';
        $('logs').textContent = '';
    } else {
        $('loginBox').hidden = true;
        $('adminBox').hidden = false;
        $('adminToggle').textContent = '管理面板';
        setTimeout(() => loadLogs().catch(() => {}), 0);
    }
    loadQueue().catch(() => {});
    loadPlayers().catch(() => {});
}

async function login() {
    const input = $('pwd');
    const pwd = input.value;
    if (!pwd) {
        setMsg($('loginMsg'), '请输入管理员密码', 'bad');
        return;
    }
    try {
        const res = await fetch('/api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ password: pwd })
        });
        let data = null;
        try {
            data = await res.json();
        } catch (e) {
            data = null;
        }
        input.value = '';
        if (!res.ok) {
            setMsg($('loginMsg'), (data && data.error) || '登录失败', 'bad');
            return;
        }
        state.token = data.token;
        sessionStorage.setItem('amToken', state.token);
        setMsg($('loginMsg'), '', '');
        setAdmin(true);
    } catch (e) {
        setMsg($('loginMsg'), '网络错误，请稍后重试', 'bad');
    }
}

function logout() {
    api('/api/logout', { method: 'POST' }).catch(() => {});
    setAdmin(false);
    setMsg($('loginMsg'), '已退出登录', '');
}

async function doAction(path, body, label) {
    try {
        const res = await api(path, { method: 'POST', body: JSON.stringify(body || {}) });
        const output = res && res.output ? ('：' + res.output.replace(/\n/g, ' ')) : '';
        setMsg($('actionMsg'), label + ' 已提交' + output, 'good');
        setTimeout(refreshAll, 900);
    } catch (e) {
        setMsg($('actionMsg'), label + ' 失败：' + e.message, 'bad');
    }
}

async function execCommand() {
    const input = $('cmd');
    const command = input.value.trim();
    if (!command) {
        return;
    }
    try {
        const res = await api('/api/admin/command', { method: 'POST', body: JSON.stringify({ command }) });
        input.value = '';
        const output = res && res.output ? res.output.replace(/\n/g, ' ') : '已执行';
        setMsg($('actionMsg'), '指令' + output, 'good');
        setTimeout(refreshAll, 900);
    } catch (e) {
        setMsg($('actionMsg'), '执行失败：' + e.message, 'bad');
    }
}

function refreshAll() {
    loadStatus().catch(() => {});
    loadQueue().catch(() => {});
    loadPlayers().catch(() => {});
    loadStats().catch(() => {});
    loadPerf().catch(() => {});
    if (state.admin) {
        loadLogs().catch(() => {});
    }
}

/* ---------- 初始化 ---------- */

function bind() {
    $('loginBtn').addEventListener('click', login);
    $('pwd').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            login();
        }
    });
    $('logoutBtn').addEventListener('click', logout);
    $('nextBtn').addEventListener('click', () => doAction('/api/admin/next', {}, '切歌'));
    $('refreshBtn').addEventListener('click', () => {
        refreshAll();
        setMsg($('actionMsg'), '已刷新', '');
    });
    $('cmdBtn').addEventListener('click', execCommand);
    $('cmd').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            execCommand();
        }
    });
    $('adminToggle').addEventListener('click', () => {
        const panel = $('adminPanel');
        panel.hidden = false;
        panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
}

async function boot() {
    bind();
    poll(loadStatus, 'status', 2000);
    poll(loadQueue, 'queue', 5000);
    poll(loadPlayers, 'players', 2000);
    poll(loadStats, 'stats', 15000);
    poll(loadPerf, 'perf', 3000);
    poll(async () => {
        if (state.admin) {
            await loadLogs();
        }
    }, 'logs', 2000);

    if (state.token) {
        try {
            await api('/api/me');
            setAdmin(true);
        } catch (e) {
            setAdmin(false);
        }
    }
}

boot();