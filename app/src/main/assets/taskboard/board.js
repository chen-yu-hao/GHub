/*
 * Local-only GHub task board. No remote content is interpreted as HTML.
 * Native contract: GHubBoard.postMessage(JSON.stringify({type, ...payload})).
 * Native updates: TaskBoard.render(snapshot).
 */
(function (global) {
  'use strict';

  var TABS = [
    { id: 'wait', label: '等待确认', icon: 'wait', size: 2 },
    { id: 'run', label: '运行中', icon: 'run', size: 3 },
    { id: 'done', label: '已完成', icon: 'done', size: 7 },
    { id: 'failed', label: '失败 / 中断', icon: 'failed', size: 2 }
  ];
  var STATES = TABS.map(function (tab) { return tab.id; });

  function string(value, fallback) {
    return typeof value === 'string' ? value : (typeof value === 'number' ? String(value) : (fallback || ''));
  }

  function number(value, fallback) {
    return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
  }

  function nullableNumber(value) {
    return typeof value === 'number' && Number.isFinite(value) ? value : null;
  }

  function array(value) { return Array.isArray(value) ? value : []; }

  function hostOnline(host) {
    return !!host && ['ONLINE', 'CONNECTED'].indexOf(string(host.connection).toUpperCase()) !== -1;
  }

  function hostSshConnected(host) { return !!host && (host.sshConnected === true || hostOnline(host)); }

  function normalizeSnapshot(input) {
    input = input && typeof input === 'object' ? input : {};
    var hosts = array(input.hosts).filter(function (host) {
      return host && typeof host === 'object' && string(host.id);
    }).map(function (host) {
      return {
        id: string(host.id), name: string(host.name, string(host.id)),
        connection: string(host.connection, 'DISCONNECTED'),
        sshConnected: host.sshConnected === true,
        error: string(host.error), hasMore: host.hasMore === true,
        lastSyncedAt: Math.max(0, number(host.lastSyncedAt, 0)), syncing: host.syncing === true,
        projects: array(host.projects).filter(function (project) { return project && string(project.path); }).map(function (project) {
          return { path: string(project.path), name: string(project.name, string(project.path).split('/').filter(Boolean).pop() || '/') };
        })
      };
    });
    var seen = Object.create(null);
    var tasks = array(input.tasks).filter(function (task) {
      return task && typeof task === 'object' && STATES.indexOf(task.state) !== -1 && string(task.hostId) && string(task.threadId);
    }).map(function (task) {
      var hostId = string(task.hostId);
      var threadId = string(task.threadId);
      var approvalKey = string(task.approvalKey);
      return {
        key: string(task.key, hostId + ':' + threadId + ':' + approvalKey),
        hostId: hostId, hostName: string(task.hostName, hostId), threadId: threadId,
        title: string(task.title, '未命名任务'), project: string(task.project, '未指定项目'), path: string(task.path),
        state: task.state, updatedAt: Math.max(0, number(task.updatedAt, 0)),
        approvalKey: approvalKey,
        approvalKind: ['command', 'file'].indexOf(task.approvalKind) !== -1 ? task.approvalKind : 'unsupported',
        command: string(task.command), reason: string(task.reason), output: string(task.output),
        workingDirectory: string(task.workingDirectory), writeRoot: string(task.writeRoot), networkHost: string(task.networkHost),
        exitCode: nullableNumber(task.exitCode), durationMs: nullableNumber(task.durationMs),
        files: array(task.files).filter(function (file) { return file && typeof file === 'object'; }).map(function (file) {
          return { path: string(file.path, '未提供路径'), added: nullableNumber(file.added), removed: nullableNumber(file.removed) };
        }),
        plan: array(task.plan).filter(function (step) { return step && typeof step === 'object'; }).map(function (step) {
          return { step: string(step.step), status: string(step.status) };
        }),
        current: string(task.current), unread: task.unread === true, submitting: task.submitting === true,
        connected: task.connected === true, interrupted: task.interrupted === true,
        detailLoaded: task.detailLoaded === true, imageCount: Math.max(0, number(task.imageCount, 0)),
        outputTruncated: task.outputTruncated === true, hasReply: task.hasReply === true
      };
    }).filter(function (task) {
      if (seen[task.key]) return false;
      seen[task.key] = true;
      return true;
    });
    return {
      hosts: hosts, tasks: tasks,
      lastSyncAt: Math.max(0, number(input.lastSyncAt, 0)), syncing: input.syncing === true,
      hasMore: input.hasMore === true || hosts.some(function (host) { return host.hasMore; })
    };
  }

  function selectedTasks(snapshot, filters, tab) {
    return snapshot.tasks.filter(function (task) {
      return (!filters.hostId || task.hostId === filters.hostId) &&
        (!filters.path || task.path === filters.path) && (!tab || task.state === tab);
    }).sort(function (a, b) { return b.updatedAt - a.updatedAt || a.key.localeCompare(b.key); });
  }

  function pageFor(tasks, tabId, requestedPage) {
    var tab = TABS.filter(function (item) { return item.id === tabId; })[0] || TABS[0];
    var pages = Math.max(1, Math.ceil(tasks.length / tab.size));
    var page = Math.min(pages - 1, Math.max(0, Math.floor(number(requestedPage, 0))));
    return { page: page, pages: pages, items: tasks.slice(page * tab.size, (page + 1) * tab.size), previous: page > 0, next: page + 1 < pages };
  }

  function canApprove(snapshot, task) {
    var host = task && snapshot.hosts.filter(function (item) { return item.id === task.hostId; })[0];
    return !!task && task.state === 'wait' && !!task.approvalKey &&
      ['command', 'file'].indexOf(task.approvalKind) !== -1 &&
      task.connected === true && task.submitting !== true && hostOnline(host);
  }

  function approvalAction(snapshot, key, approvalKey, decision) {
    var task = snapshot.tasks.filter(function (item) { return item.key === key; })[0];
    if (!canApprove(snapshot, task) || task.approvalKey !== approvalKey || ['accept', 'decline'].indexOf(decision) === -1) return null;
    return { type: 'approve', hostId: task.hostId, threadId: task.threadId, approvalKey: task.approvalKey, decision: decision };
  }

  function filteredHosts(snapshot, filters) {
    return snapshot.hosts.filter(function (host) { return !filters.hostId || host.id === filters.hostId; });
  }

  function hasMoreFor(snapshot, filters) {
    return filters.hostId ? filteredHosts(snapshot, filters).some(function (host) { return host.hasMore; }) : snapshot.hasMore;
  }

  function syncMeta(snapshot, filters) {
    var hosts = filteredHosts(snapshot, filters);
    var selected = filters.hostId ? hosts[0] : null;
    return {
      time: selected ? selected.lastSyncedAt : snapshot.lastSyncAt,
      syncing: selected ? selected.syncing : snapshot.syncing,
      online: hosts.some(hostOnline),
      allOnline: hosts.length > 0 && hosts.every(hostOnline),
      sshOnly: hosts.some(function (host) { return hostSshConnected(host) && !hostOnline(host); })
    };
  }

  function clock(timestamp) {
    var date = new Date(timestamp);
    return String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0');
  }

  function displayTime(seconds, now, relative) {
    if (!(seconds > 0)) return '时间未提供';
    var time = seconds * 1000;
    var date = new Date(time);
    if (Number.isNaN(date.getTime())) return '时间未提供';
    var current = new Date(now);
    var elapsed = Math.max(0, now - time);
    if (relative && elapsed < 60 * 1000) return '刚刚';
    if (relative && elapsed < 60 * 60 * 1000) return Math.floor(elapsed / 60000) + ' 分钟前';
    if (relative && elapsed < 24 * 60 * 60 * 1000) return Math.floor(elapsed / 3600000) + ' 小时前';
    if (date.toDateString() === current.toDateString()) return clock(time);
    var yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    if (date.toDateString() === yesterday.toDateString()) return '昨天 ' + clock(time);
    return (date.getMonth() + 1) + '/' + date.getDate() + ' ' + clock(time);
  }

  function diffText(files) {
    if (!files.length || !files.every(function (file) { return file.added !== null && file.removed !== null; })) return '';
    return '+' + files.reduce(function (total, file) { return total + file.added; }, 0) + ' −' + files.reduce(function (total, file) { return total + file.removed; }, 0);
  }

  function completedSummary(task, now) {
    var result = '打开会话查看详情';
    if (task.files.length) result = task.files.length + ' 个文件' + (diffText(task.files) ? ' ' + diffText(task.files) : '');
    else if (task.imageCount) result = task.imageCount + ' 张图片';
    else if (task.detailLoaded && task.hasReply && !task.command) result = '仅回复';
    else if (task.command) result = '命令已完成';
    else if (task.detailLoaded) result = '已完成';
    return task.project + ' · ' + result + ' · ' + displayTime(task.updatedAt, now, false);
  }

  function planProgress(plan) {
    var completed = 0;
    var active = -1;
    plan.forEach(function (step, index) {
      var status = step.status.toLowerCase().replace(/[- ]/g, '_');
      if (['completed', 'complete', 'done'].indexOf(status) !== -1) completed++;
      if (['in_progress', 'inprogress', 'running'].indexOf(status) !== -1 && active === -1) active = index;
    });
    return { completed: completed, active: active, current: active >= 0 ? active + 1 : completed, total: plan.length };
  }

  var Model = {
    tabs: TABS, normalizeSnapshot: normalizeSnapshot, selectedTasks: selectedTasks, pageFor: pageFor,
    canApprove: canApprove, approvalAction: approvalAction, hasMoreFor: hasMoreFor, syncMeta: syncMeta,
    hostOnline: hostOnline, hostSshConnected: hostSshConnected, displayTime: displayTime,
    completedSummary: completedSummary, diffText: diffText, planProgress: planProgress
  };
  if (typeof module === 'object' && module.exports) module.exports = Model;
  if (!global.document) return;

  var doc = global.document;
  var STORAGE_KEY = 'ghub.taskboard.preferences.v1';
  var snapshot = normalizeSnapshot({});
  var preferences = { tab: 'wait', hostId: '', path: '', colorMode: 'color' };
  var page = 0;
  var pageState = pageFor([], 'wait', 0);
  var modalKind = '';
  var pendingApproval = null;
  var returnFocus = null;
  var announcementTimer;
  var refreshing = false;
  var tabButtons = Object.create(null);

  function id(value) { return doc.getElementById(value); }
  function element(tag, className, text) {
    var node = doc.createElement(tag);
    if (className) node.className = className;
    if (typeof text !== 'undefined') node.textContent = string(text);
    return node;
  }

  function append(parent) {
    for (var index = 1; index < arguments.length; index++) if (arguments[index]) parent.appendChild(arguments[index]);
    return parent;
  }

  function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }

  function detectFlexGap() {
    // Grid gap support does not imply Flexbox gap support in older Android WebViews.
    // Two 1px children must occupy 3px when the 1px flex row-gap is implemented.
    var probe = element('div', 'flex-gap-probe');
    probe.setAttribute('aria-hidden', 'true');
    append(probe, element('div'), element('div'));
    var parent = doc.body || doc.documentElement;
    append(parent, probe);
    var supported = probe.scrollHeight === 3;
    parent.removeChild(probe);
    doc.documentElement.setAttribute('data-flex-gap', supported ? 'supported' : 'unsupported');
  }

  function icon(name) {
    var data = global.GHubBoardIcons && global.GHubBoardIcons[name];
    if (!data) return element('span');
    function create(spec) {
      var node = doc.createElementNS('http://www.w3.org/2000/svg', spec[0]);
      Object.keys(spec[1]).forEach(function (key) { node.setAttribute(key, spec[1][key]); });
      spec[2].forEach(function (child) { node.appendChild(create(child)); });
      return node;
    }
    var svg = create(data);
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    return svg;
  }

  function button(text, handler, className) {
    var node = element('button', className || 'button', text);
    node.type = 'button';
    if (handler) node.addEventListener('click', handler);
    return node;
  }

  function persist() {
    try { global.localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences)); } catch (_) { /* Preferences remain usable for this session. */ }
  }

  function announce(message) {
    global.clearTimeout(announcementTimer);
    id('announcement').textContent = message;
    id('announcement').hidden = false;
    announcementTimer = global.setTimeout(function () { id('announcement').hidden = true; }, 4500);
  }

  function post(action) {
    if (!global.GHubBoard || typeof global.GHubBoard.postMessage !== 'function') {
      if (action.type !== 'ready') announce('请在 GHub Android 应用中使用此操作。');
      return false;
    }
    try {
      global.GHubBoard.postMessage(JSON.stringify(action));
      return true;
    } catch (_) {
      announce('操作未发送，请重试。');
      return false;
    }
  }

  function applyColorMode(mode) {
    preferences.colorMode = mode === 'monochrome' || mode === '黑白' ? 'monochrome' : 'color';
    doc.documentElement.setAttribute('data-color-mode', preferences.colorMode);
    persist();
  }

  function openTask(task) { post({ type: 'openTask', hostId: task.hostId, threadId: task.threadId }); }

  function metaPart(iconName, text) {
    var part = element('span', 'meta-part');
    var label = element('span', 'meta-text', text);
    label.title = text;
    return append(part, icon(iconName), label);
  }

  function taskMeta(task, approval) {
    var meta = element('div', 'meta');
    if (approval) {
      var label = task.approvalKind === 'command' ? '命令' : task.approvalKind === 'file' ? '文件' : '确认';
      append(meta, append(element('span', 'approval-kind'), icon(task.approvalKind === 'file' ? 'file' : 'command'), element('span', '', label)));
    }
    append(meta, metaPart('folder', task.project), metaPart('server', task.hostName));
    var time = displayTime(task.updatedAt, Date.now(), true);
    if (!approval && task.updatedAt > 0) time += '更新';
    append(meta, element('span', 'meta-time', time));
    return meta;
  }

  function fileSummary(task) {
    var summary = element('div', 'code file-summary');
    var first = task.files[0];
    var path = first ? first.path : '打开详情查看文件修改';
    if (task.files.length > 1) path += ' 等 ' + task.files.length + ' 个文件';
    var pathNode = element('span', 'file-path', path);
    pathNode.title = path;
    append(summary, pathNode);
    var diff = diffText(task.files);
    if (diff) append(summary, element('span', 'file-diff', diff));
    return summary;
  }

  function approvalCard(task) {
    var card = element('article', 'task-card');
    append(card, taskMeta(task, true), element('h3', 'task-title', task.title));
    if (task.approvalKind === 'file') append(card, fileSummary(task));
    else append(card, element('div', 'code', task.command || task.reason || '请打开详情查看完整请求'));
    var actions = element('div', 'card-actions');
    if (task.approvalKind !== 'unsupported') {
      var accept = button(task.submitting ? '提交中…' : '允许一次', function () { showApproval(task, 'accept'); }, 'button primary');
      var decline = button('拒绝', function () { showApproval(task, 'decline'); });
      accept.disabled = !canApprove(snapshot, task);
      decline.disabled = !canApprove(snapshot, task);
      if (accept.disabled) {
        accept.title = decline.title = task.submitting ? '正在提交决定' : '连接任务服务后才能处理审批';
      }
      append(actions, accept, decline);
    } else {
      append(actions, button('打开审批', function () { openTask(task); }));
    }
    append(actions, button('详情', function () { openTask(task); }, 'details-link'));
    append(card, actions);
    if (!task.connected) append(card, element('p', 'connection-note', '连接已断开，重新连接后才能处理此请求。'));
    else if (task.approvalKind === 'unsupported') append(card, element('p', 'connection-note', '此类请求需要在会话详情中处理。'));
    return card;
  }

  function runningCard(task) {
    var card = element('article', 'task-card running');
    card.setAttribute('tabindex', '0');
    card.setAttribute('role', 'button');
    card.setAttribute('aria-label', '打开任务：' + task.title);
    card.addEventListener('click', function () { openTask(task); });
    card.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openTask(task); }
    });
    append(card, taskMeta(task, false), element('h3', 'task-title', task.title));
    var plan = element('div', 'plan');
    var progress = planProgress(task.plan);
    append(plan, element('span', 'plan-label', progress.total ? '计划 ' + progress.current + ' / ' + progress.total : '计划未提供'));
    if (progress.total) {
      var segments = element('div', 'plan-segments');
      segments.setAttribute('aria-hidden', 'true');
      task.plan.forEach(function (step, index) {
        var completed = ['completed', 'complete', 'done'].indexOf(step.status.toLowerCase()) !== -1;
        append(segments, element('span', 'plan-segment' + (index === progress.active ? ' in-progress' : completed ? ' completed' : '')));
      });
      append(plan, segments);
      plan.title = task.plan.map(function (step) { return step.step; }).join('\n');
    }
    var current = element('p', 'current');
    var currentValue = task.current || task.command || '打开会话查看进度';
    append(current, element('strong', '', '当前 '), element('span', 'current-value' + (task.command && currentValue === task.command ? ' command' : ''), currentValue));
    current.title = currentValue;
    append(card, plan, current);
    if (!task.connected) append(card, element('p', 'connection-note', '连接已断开，显示上次同步状态。'));
    return card;
  }

  function completedTask(task) {
    var row = button('', function () { openTask(task); }, 'completed-task');
    append(row, append(element('span', 'completed-text'),
      element('span', 'completed-title', task.title),
      element('span', 'completed-summary', completedSummary(task, Date.now()))));
    if (task.unread) append(row, element('span', 'unread', '未读'));
    row.setAttribute('aria-label', task.title + (task.unread ? '，未读' : '') + '，打开会话');
    return row;
  }

  function duration(milliseconds) {
    var seconds = Math.max(0, Math.floor(milliseconds / 1000));
    if (seconds >= 3600) return Math.floor(seconds / 3600) + ' 小时 ' + Math.floor(seconds % 3600 / 60) + ' 分';
    if (seconds >= 60) return Math.floor(seconds / 60) + ' 分 ' + seconds % 60 + ' 秒';
    return seconds + ' 秒';
  }

  function failureCard(task) {
    var card = element('article', 'task-card');
    var meta = element('div', 'meta failure-meta');
    append(meta, append(element('span', 'status-icon failure-icon state-' + (task.interrupted ? 'stopped' : 'failed')), icon(task.interrupted ? 'stopped' : 'failed')),
      element('span', 'failure-label', task.interrupted ? '已中断' : '执行失败'),
      element('span', 'meta-text', task.project + ' · ' + task.hostName),
      element('span', 'meta-time', displayTime(task.updatedAt, Date.now(), false)));
    append(card, meta, element('h3', 'task-title', task.title));
    var block = element('div', 'code failure-code');
    append(block, element('span', '', task.command || task.current || '打开会话查看执行记录'));
    var result = [];
    if (task.interrupted) {
      var progress = planProgress(task.plan);
      result.push(progress.total ? '中断于计划 ' + progress.current + ' / ' + progress.total : '任务已中断');
    } else if (task.exitCode !== null) result.push('退出码 ' + task.exitCode);
    else result.push(task.reason || '打开会话查看失败原因');
    if (task.durationMs !== null) result.push('用时 ' + duration(task.durationMs));
    append(block, element('span', 'failure-result', result.join(' · ')));
    append(card, block, append(element('div', 'card-actions'), button('打开会话', function () { openTask(task); })));
    return card;
  }

  function loadMore(hosts) {
    var candidates = hosts.filter(function (host) { return host.hasMore && hostOnline(host); });
    if (!candidates.length) { announce('连接任务服务后可以加载更多任务。'); return; }
    candidates.forEach(function (host) { post({ type: 'loadMore', hostId: host.id }); });
  }

  function emptyState() {
    var hosts = filteredHosts(snapshot, preferences);
    var online = hosts.some(hostOnline);
    var sshOnly = hosts.some(function (host) { return hostSshConnected(host) && !hostOnline(host); });
    var filtered = !!(preferences.hostId || preferences.path);
    var box = element('section', 'empty');
    var title;
    var explanation;
    if (!snapshot.hosts.length) {
      title = '连接你的第一台服务器';
      explanation = '添加 OpenSSH 连接后，在这里查看任务进度、处理审批和打开远端会话。';
    } else if (!hosts.length) {
      title = '所选服务器已不可用';
      explanation = '选择其他服务器，或清除筛选条件。';
    } else if (!online && sshOnly) {
      title = 'SSH 已连接，任务服务未就绪';
      explanation = '你可以打开终端。连接服务器上的任务服务后，看板会显示远端任务。';
    } else if (!online) {
      title = hosts.some(function (host) { return /CONNECTING|STARTING|SYNCING/.test(host.connection.toUpperCase()); }) ? '正在连接服务器' : '尚未连接服务器';
      explanation = '任务服务连接成功后将同步看板。可以在服务器管理中检查连接状态。';
    } else {
      title = { wait: '暂无等待确认的任务', run: '暂无运行中的任务', done: '暂无已完成的任务', failed: '暂无失败或中断的任务' }[preferences.tab];
      explanation = filtered ? '当前服务器和项目筛选下没有此类任务。' : '同步完成后，远端任务会按状态显示在这里。';
    }
    append(box, element('h2', '', title), element('p', '', explanation));
    var actions = element('div', 'empty-actions');
    if (!snapshot.hosts.length || !online) append(actions, button(snapshot.hosts.length ? '管理 SSH 服务器' : '添加 SSH 服务器', function () { post({ type: 'hosts' }); }, 'button primary'));
    if (online || sshOnly) append(actions, button(sshOnly && !online ? '打开终端' : '新建任务', function () { showFilters(); }));
    if (filtered) append(actions, button('清除筛选', function () { preferences.hostId = ''; preferences.path = ''; page = 0; persist(); renderBoard(); scrollToTaskStart(); }));
    if (actions.children.length) append(box, actions);
    return box;
  }

  function renderBoard() {
    var allFiltered = selectedTasks(snapshot, preferences);
    var partial = hasMoreFor(snapshot, preferences);
    TABS.forEach(function (tab) {
      var count = allFiltered.filter(function (task) { return task.state === tab.id; }).length;
      var control = tabButtons[tab.id];
      control.count.textContent = count + (partial ? '+' : '');
      control.button.setAttribute('aria-pressed', String(tab.id === preferences.tab));
      control.button.setAttribute('aria-label', tab.label + '，' + (partial ? '已载入 ' : '') + count + ' 项');
      control.button.title = partial ? '仅统计已载入任务，可在列表末尾加载更多' : tab.label;
    });
    var host = snapshot.hosts.filter(function (item) { return item.id === preferences.hostId; })[0];
    var project = preferences.path ? preferences.path.split('/').filter(Boolean).pop() || preferences.path : '全部项目';
    id('filter-label').textContent = (preferences.hostId ? (host ? host.name : '服务器已移除') : '全部服务器') + ' · ' + project;
    var meta = syncMeta(snapshot, preferences);
    if (meta.syncing) id('sync-time').textContent = '正在同步…';
    else if (meta.time > 0) id('sync-time').textContent = clock(meta.time) + (meta.allOnline ? ' 已同步' : ' 上次同步');
    else id('sync-time').textContent = meta.online ? '等待同步' : '尚未同步';
    id('sync-note').textContent = meta.sshOnly && !meta.online ? 'SSH 已连接 · 任务服务未就绪' : '每 5 分钟自动同步';
    id('sync').disabled = snapshot.syncing;
    id('sync').setAttribute('aria-busy', String(snapshot.syncing));

    var tasks = allFiltered.filter(function (task) { return task.state === preferences.tab; });
    pageState = pageFor(tasks, preferences.tab, page);
    page = pageState.page;
    var main = id('tasks');
    var scrollTop = main.scrollTop;
    clear(main);
    var activeTab = TABS.filter(function (tab) { return tab.id === preferences.tab; })[0];
    main.setAttribute('aria-label', activeTab.label);
    if (!tasks.length) append(main, emptyState());
    else {
      var list = element('div', preferences.tab === 'done' ? 'completed-list' : 'task-stack');
      var renderTask = { wait: approvalCard, run: runningCard, done: completedTask, failed: failureCard }[preferences.tab];
      pageState.items.forEach(function (task) { append(list, renderTask(task)); });
      append(main, list);
    }
    if (partial && !pageState.next) {
      var more = button(snapshot.syncing ? '正在加载…' : '加载更多任务', function () { loadMore(filteredHosts(snapshot, preferences)); }, 'button load-more');
      more.disabled = snapshot.syncing || !filteredHosts(snapshot, preferences).some(hostOnline);
      append(main, more);
    }
    main.scrollTop = scrollTop;
    id('page-label').textContent = '第 ' + (page + 1) + ' / ' + pageState.pages + ' 页';
    id('page-note').textContent = partial ? '已载入 ' + tasks.length + ' 项' : activeTab.label + ' · ' + tasks.length + ' 项';
    id('previous').disabled = !pageState.previous;
    id('next').disabled = !pageState.next;
    refreshApprovalStatus();
  }

  function scrollToTaskStart() {
    var main = id('tasks');
    main.scrollTop = 0;
    // Only explicit navigation resets reading position; native snapshots do not.
    // Read the current CSS mode so rotation does not leave a stale scroll target.
    if (global.getComputedStyle(main).overflowY === 'visible') {
      global.scrollTo(0, Math.max(0, global.pageYOffset + main.getBoundingClientRect().top));
    }
  }

  function closeModal() {
    id('modal-backdrop').hidden = true;
    id('board').removeAttribute('aria-hidden');
    modalKind = '';
    pendingApproval = null;
    post({ type: 'dialogClosed' });
    var target = returnFocus && doc.contains(returnFocus) ? returnFocus : id('filter');
    target.focus({ preventScroll: true });
  }

  function beginModal(title, kind) {
    returnFocus = doc.activeElement;
    modalKind = kind;
    var modal = id('modal');
    clear(modal);
    var heading = element('div', 'modal-heading');
    var titleNode = element('h2', '', title);
    titleNode.id = 'modal-title';
    var close = button('×', closeModal, 'button close-button');
    close.setAttribute('aria-label', '关闭');
    append(heading, titleNode, close);
    append(modal, heading);
    id('modal-backdrop').hidden = false;
    id('board').setAttribute('aria-hidden', 'true');
    modal.focus();
    post({ type: 'dialogOpened' });
    return modal;
  }

  function statusForHost(host) {
    if (hostOnline(host)) return 'SSH 已连接 · 任务服务已连接';
    if (hostSshConnected(host)) return 'SSH 已连接，任务服务未就绪';
    if (/CONNECTING|STARTING|SYNCING/.test(host.connection.toUpperCase())) return '正在连接';
    if (host.error) return '连接失败：' + host.error;
    return '尚未连接';
  }

  function showFilters() {
    var modal = beginModal('服务器与项目', 'filters');
    var draft = { hostId: preferences.hostId, path: preferences.path };
    var hostField = element('label', 'field', '服务器');
    var hostSelect = element('select');
    append(hostSelect, element('option', '', '全部服务器'));
    hostSelect.firstChild.value = '';
    snapshot.hosts.forEach(function (host) {
      var option = element('option', '', host.name);
      option.value = host.id;
      append(hostSelect, option);
    });
    hostSelect.value = draft.hostId;
    append(hostField, hostSelect);
    var projectField = element('label', 'field', '项目');
    var projectSelect = element('select');
    append(projectField, projectSelect);
    append(modal, hostField, projectField);
    var status = element('div', 'host-status');
    var hostName = element('strong');
    var hostState = element('p');
    append(status, hostName, hostState);
    append(modal, status);

    var apply = button('应用筛选', function () {
      preferences.hostId = draft.hostId;
      preferences.path = draft.path;
      page = 0;
      persist();
      closeModal();
      renderBoard();
      scrollToTaskStart();
    }, 'button primary');
    append(modal, append(element('div', 'modal-actions'), apply, button('清除筛选', function () {
      draft.hostId = ''; draft.path = ''; hostSelect.value = ''; updateProjects();
    })));
    append(modal, element('hr', 'modal-divider'));

    function selectedHost() {
      var candidates = filteredHosts(snapshot, draft);
      return candidates.length === 1 ? candidates[0] : null;
    }
    var newTask = button('新建任务', function () {
      var selected = selectedHost();
      if (!hostOnline(selected)) return;
      closeModal();
      post({ type: 'newTask', hostId: selected.id, path: draft.path });
    });
    var terminal = button('打开终端', function () {
      var selected = selectedHost();
      if (!hostSshConnected(selected)) return;
      closeModal();
      post({ type: 'terminal', hostId: selected.id, path: draft.path });
    });
    var projects = button('项目与会话', function () {
      var selected = selectedHost();
      if (!selected) return;
      closeModal();
      post({ type: 'projects', hostId: selected.id });
    });
    append(modal, append(element('div', 'modal-actions'), newTask, terminal, projects));
    var actionHint = element('p', 'hint');
    append(modal, actionHint, button('管理 SSH 服务器', function () { closeModal(); post({ type: 'hosts' }); }));
    append(modal, element('hr', 'modal-divider'));

    var modeLabel = element('label', 'mode-field');
    var mode = element('input');
    mode.type = 'checkbox';
    mode.checked = preferences.colorMode === 'monochrome';
    mode.addEventListener('change', function () { applyColorMode(mode.checked ? 'monochrome' : 'color'); });
    append(modeLabel, mode, element('span', '', '黑白显示'));
    append(modal, modeLabel, button('应用设置', function () { closeModal(); post({ type: 'settings' }); }));

    function updateStatus() {
      var selected = selectedHost();
      hostName.textContent = selected ? selected.name : snapshot.hosts.length + ' 台服务器';
      hostState.textContent = selected ? statusForHost(selected) : snapshot.hosts.length ? '选择一台服务器后可新建任务或打开终端。' : '添加 SSH 服务器以开始使用。';
      newTask.disabled = !hostOnline(selected);
      terminal.disabled = !hostSshConnected(selected);
      projects.disabled = !selected;
      actionHint.textContent = !selected ? '请先选择一台服务器。' : hostOnline(selected) ? '未选择项目时，可在下一步选择工作目录。' : hostSshConnected(selected) ? 'SSH 终端可用；任务服务连接成功后可新建任务。' : '请先在服务器管理中连接此主机。';
    }

    function updateProjects() {
      clear(projectSelect);
      var all = element('option', '', '全部项目');
      all.value = '';
      append(projectSelect, all);
      var projects = Object.create(null);
      filteredHosts(snapshot, draft).forEach(function (host) {
        host.projects.forEach(function (project) { projects[project.path] = project.name; });
      });
      snapshot.tasks.filter(function (task) { return !draft.hostId || task.hostId === draft.hostId; }).forEach(function (task) {
        if (task.path && !projects[task.path]) projects[task.path] = task.project;
      });
      Object.keys(projects).sort().forEach(function (path) {
        var option = element('option', '', projects[path] + ' · ' + path);
        option.value = path;
        append(projectSelect, option);
      });
      if (draft.path && !projects[draft.path]) draft.path = '';
      projectSelect.value = draft.path;
      updateStatus();
    }
    hostSelect.addEventListener('change', function () { draft.hostId = hostSelect.value; draft.path = ''; updateProjects(); });
    projectSelect.addEventListener('change', function () { draft.path = projectSelect.value; updateStatus(); });
    updateProjects();
  }

  function showApproval(task, decision) {
    if (!canApprove(snapshot, task)) { announce('请求已变化或连接不可用，请先同步。'); return; }
    var modal = beginModal(decision === 'accept' ? '确认允许一次' : '确认拒绝', 'approval');
    pendingApproval = { key: task.key, approvalKey: task.approvalKey, decision: decision };
    append(modal, element('h3', '', task.title), element('p', 'hint', task.project + ' · ' + task.hostName));
    var content = element('div', 'approval-content');
    if (task.workingDirectory || task.writeRoot || task.networkHost) {
      var locations = element('div', 'approval-details');
      if (task.workingDirectory) append(locations, element('p', '', '工作目录：' + task.workingDirectory));
      if (task.writeRoot) append(locations, element('p', '', '写入范围：' + task.writeRoot));
      if (task.networkHost) append(locations, element('p', '', '网络主机：' + task.networkHost));
      append(content, locations);
    }
    if (task.command) append(content, element('div', 'code', task.command));
    if (task.files.length) {
      var files = element('ul', 'approval-file-list');
      task.files.forEach(function (file) {
        append(files, element('li', '', file.path + (diffText([file]) ? '  ' + diffText([file]) : '')));
      });
      append(content, files);
    }
    if (task.reason) append(content, element('p', '', task.reason));
    if (!task.command && !task.files.length) append(content, element('p', '', '请求摘要未提供。建议先打开详情核对完整请求。'));
    append(modal, content, element('p', '', decision === 'accept' ? '仅批准当前这一次请求。请核对命令或文件修改。' : '拒绝当前请求后，远端任务可能停止或等待新的指令。'));
    var note = element('p', 'hint');
    note.id = 'approval-state';
    append(modal, note);
    var confirm = button(decision === 'accept' ? '确认允许一次' : '确认拒绝', function () {
      if (!pendingApproval) return;
      var action = approvalAction(snapshot, pendingApproval.key, pendingApproval.approvalKey, pendingApproval.decision);
      if (!action) { refreshApprovalStatus(); announce('请求已变化，请重新同步后处理。'); return; }
      if (post(action)) {
        snapshot.tasks.forEach(function (item) { if (item.key === pendingApproval.key) item.submitting = true; });
        closeModal();
        renderBoard();
      }
    }, 'button primary');
    confirm.id = 'approval-confirm';
    append(modal, append(element('div', 'modal-actions'), confirm, button('取消', closeModal), button('打开详情', function () { closeModal(); openTask(task); })));
    refreshApprovalStatus();
  }

  function refreshApprovalStatus() {
    if (modalKind !== 'approval' || !pendingApproval) return;
    var action = approvalAction(snapshot, pendingApproval.key, pendingApproval.approvalKey, pendingApproval.decision);
    var control = id('approval-confirm');
    var note = id('approval-state');
    if (control) control.disabled = !action;
    if (note) note.textContent = action ? '' : '请求已变化、已处理或服务器已断开。请取消并同步最新状态。';
  }

  function fullRedraw() {
    if (refreshing) return;
    refreshing = true;
    id('refresh').disabled = true;
    post({ type: 'refresh' });
    var flash = id('refresh-flash');
    flash.style.backgroundColor = '#000000';
    flash.hidden = false;
    // A short black/white repaint is the portable fallback, not a vendor waveform API.
    global.requestAnimationFrame(function () {
      global.setTimeout(function () {
        flash.style.backgroundColor = '#FFFFFF';
        global.requestAnimationFrame(function () {
          global.setTimeout(function () {
            flash.hidden = true;
            refreshing = false;
            id('refresh').disabled = false;
          }, 100);
        });
      }, 100);
    });
  }

  function receive(input) {
    try {
      if (typeof input === 'string') input = JSON.parse(input);
      if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('Invalid snapshot');
      snapshot = normalizeSnapshot(input);
      renderBoard();
    } catch (_) { announce('任务数据未能载入，请重新同步。'); }
  }

  function start() {
    detectFlexGap();
    try {
      var saved = JSON.parse(global.localStorage.getItem(STORAGE_KEY) || '{}');
      if (saved && typeof saved === 'object') {
        if (STATES.indexOf(saved.tab) !== -1) preferences.tab = saved.tab;
        preferences.hostId = string(saved.hostId);
        preferences.path = string(saved.path);
        preferences.colorMode = saved.colorMode === 'monochrome' ? 'monochrome' : 'color';
      }
    } catch (_) { /* A broken preference cannot prevent the board from opening. */ }
    applyColorMode(preferences.colorMode);
    ['brand', 'sync', 'refresh', 'filter', 'previous', 'next'].forEach(function (name) {
      append(id(name === 'brand' ? 'brand' : name + '-icon'), icon(name === 'filter' ? 'chevron' : name));
    });
    TABS.forEach(function (tab) {
      var node = button('', function () {
        if (preferences.tab === tab.id) return;
        preferences.tab = tab.id; page = 0; persist(); renderBoard(); scrollToTaskStart();
      }, 'tab');
      node.setAttribute('aria-controls', 'tasks');
      node.setAttribute('data-state', tab.id);
      var count = element('span', 'tab-count', '0');
      append(node, append(element('span', 'tab-top'), append(element('span', 'status-icon state-' + tab.id), icon(tab.icon)), count), element('span', 'tab-label', tab.label));
      append(id('tabs'), node);
      tabButtons[tab.id] = { button: node, count: count };
    });
    id('sync').addEventListener('click', function () { post({ type: 'sync' }); });
    id('refresh').addEventListener('click', fullRedraw);
    id('filter').addEventListener('click', showFilters);
    id('previous').addEventListener('click', function () { if (pageState.previous) { page--; renderBoard(); scrollToTaskStart(); } });
    id('next').addEventListener('click', function () { if (pageState.next) { page++; renderBoard(); scrollToTaskStart(); } });
    id('modal-backdrop').addEventListener('click', function (event) { if (event.target === id('modal-backdrop')) closeModal(); });
    doc.addEventListener('keydown', function (event) {
      if (!modalKind) return;
      if (event.key === 'Escape') { event.preventDefault(); closeModal(); return; }
      if (event.key !== 'Tab') return;
      var controls = Array.prototype.slice.call(id('modal').querySelectorAll('button:not(:disabled), select:not(:disabled), input:not(:disabled), [tabindex="0"]'));
      if (!controls.length) { event.preventDefault(); id('modal').focus(); return; }
      var first = controls[0]; var last = controls[controls.length - 1];
      if (event.shiftKey && (doc.activeElement === first || doc.activeElement === id('modal'))) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (doc.activeElement === last || doc.activeElement === id('modal'))) { event.preventDefault(); first.focus(); }
    });
    global.TaskBoard = {
      render: receive,
      setColorMode: applyColorMode,
      closeOverlay: function () { if (!modalKind) return false; closeModal(); return true; },
      closeDialog: function () { if (!modalKind) return false; closeModal(); return true; },
      showMessage: function (message) { announce(string(message)); }
    };
    renderBoard();
    post({ type: 'ready' });
  }

  if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', start, { once: true });
  else start();
})(typeof window !== 'undefined' ? window : globalThis);
