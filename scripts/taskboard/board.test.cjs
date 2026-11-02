/* Run with node scripts/taskboard/board.test.cjs. No Android or browser dependency. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ASSET_ROOT = path.join(__dirname, '../../app/src/main/assets/taskboard');
const Model = require(path.join(ASSET_ROOT, 'board.js'));

function host(extra = {}) {
  return { id: 'h1', name: 'workstation', connection: 'ONLINE', sshConnected: true, lastSyncedAt: 1700000000000,
    projects: [{ path: '/work/GHub', name: 'GHub' }], ...extra };
}

function task(extra = {}) {
  return { key: 't1', hostId: 'h1', hostName: 'workstation', threadId: 'thread1', title: '测试任务',
    project: 'GHub', path: '/work/GHub', state: 'wait', updatedAt: 1700000000,
    approvalKey: 'request1', approvalKind: 'command', command: 'printf hello', connected: true, ...extra };
}

function snapshot(tasks = [task()], hosts = [host()], extra = {}) {
  return Model.normalizeSnapshot({ tasks, hosts, ...extra });
}

test('only explicit supported states are counted; idle/draft/history-less sessions are excluded', () => {
  const state = snapshot([task(), task({ key: 'idle', state: 'idle' }), task({ key: 'draft', state: 'draft' }), null, {}]);
  assert.equal(state.tasks.length, 1);
  assert.equal(state.tasks[0].state, 'wait');
});

test('unknown times, diff counts, exit codes and duration remain unknown', () => {
  const state = snapshot([task({ updatedAt: undefined, files: [{ path: 'file.txt' }] })]);
  const item = state.tasks[0];
  assert.equal(item.updatedAt, 0);
  assert.equal(item.exitCode, null);
  assert.equal(item.durationMs, null);
  assert.equal(item.files[0].added, null);
  assert.equal(Model.diffText(item.files), '');
  assert.equal(Model.displayTime(0, Date.now(), true), '时间未提供');
});

test('task records are deduplicated by stable key without mutating native snapshots', () => {
  const original = task();
  const state = snapshot([original, original]);
  assert.equal(state.tasks.length, 1);
  state.tasks[0].submitting = true;
  assert.equal(original.submitting, undefined);
});

test('server and exact project path filters are applied before counts and paging', () => {
  const state = snapshot([task(), task({ key: 't2', hostId: 'h2' }), task({ key: 't3', path: '/other/GHub' })]);
  assert.equal(Model.selectedTasks(state, { hostId: 'h1', path: '/work/GHub' }).length, 1);
  assert.equal(Model.selectedTasks(state, { path: '/work/GHub' }).length, 2);
});

test('pagination follows source capacities 2/3/7/2 and clamps stale pages', () => {
  for (const [tab, size] of [['wait', 2], ['run', 3], ['done', 7], ['failed', 2]]) {
    const tasks = Array.from({ length: size + 1 }, (_, index) => ({ key: String(index) }));
    assert.equal(Model.pageFor(tasks, tab, 0).items.length, size);
    assert.equal(Model.pageFor(tasks, tab, 999).items.length, 1);
    assert.equal(Model.pageFor(tasks, tab, 999).page, 1);
    assert.equal(Model.pageFor([], tab, 999).pages, 1);
    assert.equal(Model.pageFor([], tab, 999).page, 0);
  }
});

test('approvals require an online matching host, live request, supported type and no pending submit', () => {
  const state = snapshot();
  assert.equal(Model.canApprove(state, state.tasks[0]), true);
  for (const patch of [{ connected: false }, { submitting: true }, { approvalKey: '' }, { approvalKind: 'unsupported' }, { state: 'done' }]) {
    const changed = snapshot([task(patch)]);
    assert.equal(Model.canApprove(changed, changed.tasks[0]), false);
  }
  assert.equal(Model.canApprove(snapshot([task()], []), state.tasks[0]), false);
  assert.equal(Model.canApprove(snapshot([task()], [host({ connection: 'ERROR' })]), state.tasks[0]), false);
});

test('approval confirmation binds to the exact current approval key and accepts no arbitrary decision', () => {
  const state = snapshot();
  assert.deepEqual(Model.approvalAction(state, 't1', 'request1', 'accept'), {
    type: 'approve', hostId: 'h1', threadId: 'thread1', approvalKey: 'request1', decision: 'accept'
  });
  assert.equal(Model.approvalAction(state, 't1', 'old-request', 'accept'), null);
  assert.equal(Model.approvalAction(state, 't1', 'request1', 'acceptForSession'), null);
  assert.equal(Model.approvalAction(state, 'deleted', 'request1', 'decline'), null);
});

test('SSH-only hosts allow terminals but do not masquerade as a connected task service', () => {
  const ssh = host({ connection: 'ERROR', sshConnected: true });
  assert.equal(Model.hostSshConnected(ssh), true);
  assert.equal(Model.hostOnline(ssh), false);
  const state = snapshot([task()], [ssh]);
  assert.equal(Model.canApprove(state, state.tasks[0]), false);
  assert.equal(Model.syncMeta(state, {}).sshOnly, true);
});

test('partial pagination and selected-host sync timestamps are truthful', () => {
  const state = snapshot([], [host({ hasMore: false }), host({ id: 'h2', hasMore: true, lastSyncedAt: 0 })], { lastSyncAt: 0 });
  assert.equal(Model.hasMoreFor(state, {}), true);
  assert.equal(Model.hasMoreFor(state, { hostId: 'h1' }), false);
  assert.equal(Model.syncMeta(state, {}).time, 0);
  assert.equal(Model.syncMeta(state, { hostId: 'h1' }).time, 1700000000000);
});

test('incomplete task history is not presented as a text-only reply', () => {
  assert.match(Model.completedSummary(snapshot([task({ state: 'done', command: '' })]).tasks[0], Date.now()), /打开会话查看详情/);
  assert.match(Model.completedSummary(snapshot([task({ state: 'done', detailLoaded: true, command: '', hasReply: true })]).tasks[0], Date.now()), /仅回复/);
  assert.match(Model.completedSummary(snapshot([task({ state: 'done', detailLoaded: true, output: 'Command output' })]).tasks[0], Date.now()), /命令已完成/);
  assert.doesNotMatch(Model.completedSummary(snapshot([task({ state: 'done', detailLoaded: true, command: '', output: 'Command output' })]).tasks[0], Date.now()), /仅回复/);
  assert.match(Model.completedSummary(snapshot([task({ state: 'done', files: [{ path: 'a', added: 42, removed: 0 }] })]).tasks[0], Date.now()), /1 个文件 \+42 −0/);
});

test('structured plans preserve actual active/completed steps and never invent a 0/0 plan', () => {
  assert.deepEqual(Model.planProgress([]), { completed: 0, active: -1, current: 0, total: 0 });
  assert.deepEqual(Model.planProgress([
    { step: 'a', status: 'completed' }, { step: 'b', status: 'completed' },
    { step: 'c', status: 'in_progress' }, { step: 'd', status: 'pending' }
  ]), { completed: 2, active: 2, current: 3, total: 4 });
});

/* Small DOM harness: verify actual production event/render paths and inert remote text. */
class Node {
  constructor(tag, doc) { this.tagName = tag.toUpperCase(); this.ownerDocument = doc; this.children = []; this.attributes = {}; this.listeners = {}; this._text = ''; this.hidden = false; this.disabled = false; this.value = ''; this.style = {}; }
  get firstChild() { return this.children[0] || null; }
  get scrollHeight() { return this.className === 'flex-gap-probe' ? (this.ownerDocument.flexGapSupported ? 3 : 2) : 0; }
  get textContent() { return this._text + this.children.map(child => child.textContent).join(''); }
  set textContent(text) { this.children.forEach(child => { child.parentNode = null; }); this.children = []; this._text = String(text); }
  appendChild(child) { if (child.parentNode) child.parentNode.removeChild(child); this.children.push(child); child.parentNode = this; return child; }
  removeChild(child) { const index = this.children.indexOf(child); if (index >= 0) this.children.splice(index, 1); child.parentNode = null; return child; }
  setAttribute(name, value) { this.attributes[name] = String(value); if (name === 'id') this.id = String(value); }
  removeAttribute(name) { delete this.attributes[name]; }
  addEventListener(name, fn) { (this.listeners[name] ||= []).push(fn); }
  dispatch(name, extra = {}) { const event = { target: this, preventDefault() {}, ...extra }; (this.listeners[name] || []).forEach(fn => fn(event)); }
  click() { if (!this.disabled) this.dispatch('click'); }
  focus() { this.ownerDocument.activeElement = this; }
  walk() { return [this, ...this.children.flatMap(child => child.walk())]; }
  querySelectorAll() { return this.walk().filter(node => !node.disabled && (['BUTTON', 'SELECT', 'INPUT'].includes(node.tagName) || node.attributes.tabindex === '0')); }
}

function browserHarness({ flexGapSupported = true } = {}) {
  const document = { readyState: 'complete', listeners: {}, created: [], activeElement: null, flexGapSupported };
  document.createElement = tag => { const node = new Node(tag, document); document.created.push(node); return node; };
  document.createElementNS = (_, tag) => document.createElement(tag);
  document.documentElement = document.createElement('html');
  document.getElementById = id => document.documentElement.walk().find(node => node.id === id) || null;
  document.contains = node => document.documentElement.walk().includes(node);
  document.addEventListener = (name, fn) => { (document.listeners[name] ||= []).push(fn); };
  for (const id of ['board', 'brand', 'sync', 'refresh', 'filter', 'sync-icon', 'refresh-icon', 'filter-icon', 'previous-icon', 'next-icon', 'filter-label', 'sync-time', 'sync-note', 'tabs', 'tasks', 'page-label', 'page-note', 'previous', 'next', 'modal-backdrop', 'modal', 'announcement', 'refresh-flash']) {
    const node = document.createElement('div'); node.id = id; document.documentElement.appendChild(node);
  }
  const actions = [];
  const storage = {};
  const timers = [];
  const context = { document, console, Date, JSON, Number, Math, String, Array, Object,
    localStorage: { getItem: key => storage[key] || null, setItem: (key, value) => { storage[key] = value; } },
    GHubBoard: { postMessage: text => actions.push(JSON.parse(text)) },
    setTimeout: fn => { timers.push(fn); return timers.length; }, clearTimeout() {}, requestAnimationFrame: fn => fn() };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(ASSET_ROOT, 'icons.js'), 'utf8'), context);
  vm.runInContext(fs.readFileSync(path.join(ASSET_ROOT, 'board.js'), 'utf8'), context);
  return { context, document, actions, storage, timers,
    find: text => document.documentElement.walk().find(node => node.tagName === 'BUTTON' && node.textContent === text),
    id: id => document.getElementById(id),
    tab: state => document.documentElement.walk().find(node => node.attributes['data-state'] === state) };
}

test('production renderer uses text nodes for hostile remote title, command, server and file data', () => {
  const harness = browserHarness();
  const injection = '<img src=x onerror="globalThis.pwned=true"><script>pwned=true</script>';
  harness.context.TaskBoard.render({ hosts: [host({ name: injection })], tasks: [task({ title: injection, command: injection, hostName: injection })] });
  assert.ok(harness.id('tasks').textContent.includes(injection));
  assert.equal(harness.context.pwned, undefined);
  assert.equal(harness.document.created.filter(node => ['IMG', 'SCRIPT', 'IFRAME'].includes(node.tagName)).length, 0);
  harness.find('允许一次').click();
  assert.ok(harness.id('modal').textContent.includes(injection));
  assert.equal(harness.actions.some(action => action.type === 'approve'), false);
});

test('real tab controls and pagination render 7 completed rows then 2, without fabricated totals', () => {
  const harness = browserHarness();
  harness.context.TaskBoard.render({ hosts: [host()], tasks: Array.from({ length: 9 }, (_, index) => task({ key: 'done' + index, state: 'done', title: '任务 ' + index })) });
  harness.tab('done').click();
  assert.equal(harness.id('tasks').children[0].children.length, 7);
  assert.equal(harness.id('page-label').textContent, '第 1 / 2 页');
  harness.id('next').dispatch('click');
  assert.equal(harness.id('tasks').children[0].children.length, 2);
  assert.equal(harness.id('page-label').textContent, '第 2 / 2 页');
  assert.equal(harness.id('next').disabled, true);
  assert.match(harness.id('page-note').textContent, /9 项/);
});

test('approval button opens a confirmation and submits precisely once, with native Back integration', () => {
  const harness = browserHarness();
  harness.context.TaskBoard.render({ hosts: [host()], tasks: [task()] });
  harness.find('允许一次').click();
  assert.equal(harness.actions.at(-1).type, 'dialogOpened');
  assert.equal(harness.actions.some(action => action.type === 'approve'), false);
  harness.id('approval-confirm').click();
  assert.equal(harness.actions.filter(action => action.type === 'approve').length, 1);
  assert.equal(harness.actions.at(-1).type, 'dialogClosed');
  assert.equal(harness.find('提交中…').disabled, true);
  assert.equal(harness.context.TaskBoard.closeDialog(), false);
});

test('disconnect or replaced approval while confirmation is open prevents stale execution', () => {
  for (const changed of [task({ approvalKey: 'request2' }), task({ connected: false })]) {
    const harness = browserHarness();
    harness.context.TaskBoard.render({ hosts: [host()], tasks: [task()] });
    harness.find('允许一次').click();
    harness.context.TaskBoard.render({ hosts: [host()], tasks: [changed] });
    assert.equal(harness.id('approval-confirm').disabled, true);
    harness.id('approval-confirm').click();
    assert.equal(harness.actions.some(action => action.type === 'approve'), false);
    assert.match(harness.id('approval-state').textContent, /已变化/);
  }
});

test('approval confirmation shows actual execution and write scope rather than inferred project path', () => {
  const harness = browserHarness();
  harness.context.TaskBoard.render({ hosts: [host()], tasks: [task({
    path: '/work/GHub', workingDirectory: '/other/work', writeRoot: '/explicit/output', networkHost: 'example.test'
  })] });
  harness.find('允许一次').click();
  assert.match(harness.id('modal').textContent, /工作目录：\/other\/work/);
  assert.match(harness.id('modal').textContent, /写入范围：\/explicit\/output/);
  assert.match(harness.id('modal').textContent, /网络主机：example\.test/);
});

test('unsupported requests only open native approval details', () => {
  const harness = browserHarness();
  harness.context.TaskBoard.render({ hosts: [host()], tasks: [task({ approvalKind: 'unsupported' })] });
  assert.equal(harness.find('允许一次'), undefined);
  harness.find('打开审批').click();
  assert.equal(harness.actions.at(-1).type, 'openTask');
});

test('SSH-only empty state is honest and terminal action remains available in filter dialog', () => {
  const harness = browserHarness();
  harness.context.TaskBoard.render({ hosts: [host({ connection: 'ERROR', sshConnected: true })], tasks: [] });
  assert.match(harness.id('tasks').textContent, /SSH 已连接，任务服务未就绪/);
  harness.id('filter').dispatch('click');
  assert.equal(harness.find('新建任务').disabled, true);
  const terminal = harness.id('modal').walk().find(node => node.tagName === 'BUTTON' && node.textContent === '打开终端');
  assert.equal(terminal.disabled, false);
  terminal.click();
  assert.deepEqual(harness.actions.at(-1), { type: 'terminal', hostId: 'h1', path: '' });
});

test('partial counts carry + and loading sends only eligible selected host ids', () => {
  const harness = browserHarness();
  harness.context.TaskBoard.render({ hosts: [host({ hasMore: true })], tasks: [task()], hasMore: true });
  assert.match(harness.tab('wait').textContent, /1\+/);
  assert.match(harness.id('page-note').textContent, /已载入 1 项/);
  harness.find('加载更多任务').click();
  assert.deepEqual(harness.actions.at(-1), { type: 'loadMore', hostId: 'h1' });
});

test('saved projects and sessions remain accessible while the host is offline', () => {
  const harness = browserHarness();
  harness.context.TaskBoard.render({ hosts: [host({ connection: 'OFFLINE', sshConnected: false })], tasks: [] });
  harness.id('filter').dispatch('click');
  const projects = harness.find('项目与会话');
  assert.equal(projects.disabled, false);
  projects.click();
  assert.deepEqual(harness.actions.at(-1), { type: 'projects', hostId: 'h1' });
});

test('color mode persists without remote scripts, and malformed updates retain current content', () => {
  const harness = browserHarness();
  harness.context.TaskBoard.setColorMode('monochrome');
  assert.equal(harness.document.documentElement.attributes['data-color-mode'], 'monochrome');
  assert.match(harness.storage['ghub.taskboard.preferences.v1'], /monochrome/);
  harness.context.TaskBoard.render({ hosts: [host()], tasks: [task()] });
  const previous = harness.id('tasks').textContent;
  harness.context.TaskBoard.render('{bad json');
  assert.equal(harness.id('tasks').textContent, previous);
  assert.match(harness.id('announcement').textContent, /未能载入/);
});

test('portable full redraw terminates after a black and white repaint', () => {
  const harness = browserHarness();
  harness.id('refresh').dispatch('click');
  assert.equal(harness.id('refresh-flash').hidden, false);
  assert.equal(harness.id('refresh-flash').style.backgroundColor, '#000000');
  harness.timers.shift()();
  assert.equal(harness.id('refresh-flash').style.backgroundColor, '#FFFFFF');
  harness.timers.shift()();
  assert.equal(harness.id('refresh-flash').hidden, true);
  assert.equal(harness.id('refresh').disabled, false);
});

// Compare against inline CSS extracted unchanged from the user's supplied HTML.
const referenceMarkup = fs.readFileSync(path.join(__dirname, '../../app/design/taskboard/supplied-board-markup.html'), 'utf8');
const baseCss = fs.readFileSync(path.join(ASSET_ROOT, 'board.css'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').split('@media')[0];
function declarations(text) {
  return Object.fromEntries(text.split(';').map(item => item.split(/:(.*)/s).slice(0, 2).map(value => value.trim())).filter(item => item.length === 2));
}
function sourceStyle(tag, needle) {
  const tags = [...referenceMarkup.matchAll(new RegExp('<' + tag + '\\b[^>]*style="([^"]*)"[^>]*>', 'g'))];
  const found = tags.find(match => match[1].includes(needle));
  assert.ok(found, 'source style: ' + tag + ' ' + needle);
  return declarations(found[1]);
}
function cssStyle(...selectors) {
  const result = {};
  for (const selector of selectors) {
    for (const rule of baseCss.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
      if (rule[1].trim().split(',').map(item => item.trim()).includes(selector)) Object.assign(result, declarations(rule[2]));
    }
  }
  return result;
}
function assertProperties(source, actual, properties) {
  for (const property of properties) {
    assert.equal(String(actual[property]).replace(/\s+/g, ''), String(source[property]).replace(/\s+/g, ''), property);
  }
}

test('source-derived geometry: 632×840 page, header/filter/tabs/content/footer positions match exactly', () => {
  const source = sourceStyle('div', 'width: 632px; height: 840px');
  const board = cssStyle('.board');
  assert.equal(board['max-width'], source.width);
  assertProperties(source, board, ['padding', 'display', 'flex-direction', 'gap', 'overflow', 'background']);
  const paddingY = parseInt(board.padding);
  const gap = parseInt(board.gap);
  const headerHeight = parseInt(cssStyle('.button').height) + parseInt(cssStyle('.header')['padding-bottom']) + parseInt(cssStyle('.header')['border-bottom']);
  const filterHeight = parseInt(cssStyle('.filter-button').height);
  const tabHeight = parseInt(cssStyle('.tab').height);
  const footerHeight = parseInt(cssStyle('.button').height) + parseInt(cssStyle('.footer')['padding-top']) + parseInt(cssStyle('.footer')['border-top']);
  assert.deepEqual({ header: paddingY, filter: paddingY + headerHeight + gap, tabs: paddingY + headerHeight + filterHeight + 2 * gap,
    content: paddingY + headerHeight + filterHeight + tabHeight + 3 * gap, footer: parseInt(source.height) - paddingY - footerHeight },
  { header: 16, filter: 91, tabs: 147, content: 231, footer: 761 });
});

test('source-derived styles: header, filter, statistic tiles and footer retain original visual measurements', () => {
  assertProperties(sourceStyle('header', 'border-bottom: 3px solid #000000'), cssStyle('.header'), ['display', 'align-items', 'gap', 'padding-bottom', 'border-bottom']);
  assertProperties(sourceStyle('h1', 'font-size: 24px'), cssStyle('.header h1'), ['margin', 'font-size', 'line-height', 'font-weight', 'white-space']);
  assertProperties(sourceStyle('button', 'margin-left: auto; height: 48px'), cssStyle('.button', '.header-button', '.sync-button'), ['height', 'padding', 'border', 'border-radius', 'font-size', 'font-weight', 'gap', 'margin-left']);
  assertProperties(sourceStyle('button', 'height: 44px'), cssStyle('.button', '.filter-button'), ['height', 'padding', 'border', 'border-radius', 'font-size', 'gap']);
  assertProperties(sourceStyle('button', 'height: 72px'), cssStyle('.tab'), ['height', 'padding', 'border', 'border-radius', 'display', 'flex-direction', 'justify-content', 'align-items', 'text-align']);
  assertProperties(sourceStyle('footer', 'margin-top: auto'), cssStyle('.footer'), ['display', 'align-items', 'gap', 'padding-top', 'border-top']);
});

test('source-derived styles: task cards, code panels and completed rows retain typography and borders', () => {
  assertProperties(sourceStyle('article', 'gap: 10px'), cssStyle('.task-card'), ['border', 'border-radius', 'padding', 'display', 'flex-direction', 'gap']);
  assertProperties(sourceStyle('h3', 'font-size: 20px'), cssStyle('.task-title'), ['font-size', 'line-height', 'font-weight', '-webkit-line-clamp', '-webkit-box-orient', 'overflow']);
  assertProperties(sourceStyle('div', "font-family: 'JetBrains Mono'"), cssStyle('.code'), ['background', 'border-radius', 'padding', 'font-family', 'font-size', 'line-height', 'font-weight', 'word-break']);
  assertProperties(sourceStyle('button', 'min-height: 64px'), cssStyle('.completed-task'), ['display', 'align-items', 'gap', 'width', 'min-height', 'padding', 'border', 'border-bottom', 'background', 'color', 'text-align']);
  const card = cssStyle('.task-card');
  const height = 2 * parseInt(card.border) + 2 * parseInt(card.padding) + 3 * parseInt(card.gap) +
    parseInt(cssStyle('.approval-kind').height) + parseInt(cssStyle('.task-title')['line-height']) +
    parseInt(cssStyle('.code')['line-height']) + 2 * parseInt(cssStyle('.code').padding) + parseInt(cssStyle('.button').height);
  assert.equal(height, 204);
});

test('source-derived SVG paths and view boxes remain unchanged; production cannot load remote resources', () => {
  const sandbox = { window: {} };
  vm.runInNewContext(fs.readFileSync(path.join(ASSET_ROOT, 'icons.js'), 'utf8'), sandbox);
  const flatten = node => [node, ...node[2].flatMap(flatten)];
  const nodes = Object.values(sandbox.window.GHubBoardIcons).flatMap(flatten);
  const paths = [...new Set(nodes.filter(node => node[0] === 'path').map(node => node[1].d))].sort();
  const suppliedPaths = [...new Set([...referenceMarkup.matchAll(/<path\b[^>]*\bd="([^"]*)"/g)].map(match => match[1]))].sort();
  assert.deepEqual(paths, suppliedPaths);
  assert.equal(sandbox.window.GHubBoardIcons.brand[1].viewBox, '20 20 68 68');
  assert.equal(sandbox.window.GHubBoardIcons.brand[1].width, '36');
  const html = fs.readFileSync(path.join(ASSET_ROOT, 'index.html'), 'utf8');
  assert.doesNotMatch(html, /(?:src|href)=["']https?:/);
  assert.match(html, /connect-src 'none'/);
  assert.match(html, /script-src 'self'/);
  assert.doesNotMatch(html, /\son(?:click|load|error)=/i);
});

test('legacy WebView branch uses one actual flex-height probe and scoped spacing; overlays have edge fallbacks', () => {
  for (const flexGapSupported of [true, false]) {
    const harness = browserHarness({ flexGapSupported });
    assert.equal(harness.document.documentElement.attributes['data-flex-gap'], flexGapSupported ? 'supported' : 'unsupported');
    harness.context.TaskBoard.render({ hosts: [host()], tasks: [task()] });
    harness.context.TaskBoard.render({ hosts: [host()], tasks: [] });
    const probes = harness.document.created.filter(node => node.className === 'flex-gap-probe');
    assert.equal(probes.length, 1, 'capability is checked once at startup, not on each snapshot');
    assert.equal(probes[0].parentNode, null, 'probe is removed after measurement');
    assert.equal(probes[0].children.length, 2);
  }
  for (const selector of ['.modal-backdrop', '.refresh-flash']) {
    const style = cssStyle(selector);
    for (const edge of ['top', 'right', 'bottom', 'left']) assert.equal(style[edge], '0');
  }
  assert.equal(cssStyle(":root[data-flex-gap='unsupported'] .board > * + *")['margin-top'], '12px');
  assert.equal(cssStyle(":root[data-flex-gap='unsupported'] .task-card > * + *")['margin-top'], '10px');
  assert.equal(cssStyle(":root[data-flex-gap='unsupported'] .header > :not(:last-child)")['margin-right'], '10px');
  assert.equal(cssStyle('.sync-button')['margin-left'], 'auto');
  assert.equal(cssStyle('.details-link')['margin-left'], 'auto');
  assert.equal(cssStyle('.page-buttons')['margin-left'], 'auto');
  assert.equal(cssStyle('.flex-gap-probe')['row-gap'], '1px');
  assert.doesNotMatch(baseCss, /@supports\s*\([^)]*gap\s*:/);
});
