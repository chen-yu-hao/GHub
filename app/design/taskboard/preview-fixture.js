/* Design-only fixture. This file is outside Android assets and is never packaged. */
(function () {
  'use strict';
  var fixedNow = new Date(2026, 10, 2, 8, 26).getTime();
  Date.now = function () { return fixedNow; };
  function minutesAgo(minutes) { return (fixedNow - minutes * 60000) / 1000; }
  function at(day, hour, minute) { return new Date(2026, 10, day, hour, minute).getTime() / 1000; }
  function task(id, state, title, more) {
    return Object.assign({ key: id, hostId: 'workstation', hostName: 'workstation', threadId: id,
      state: state, title: title, project: 'GHub', path: '/work/GHub', connected: true,
      updatedAt: minutesAgo(3), detailLoaded: true, files: [], plan: [] }, more);
  }
  function plan(total, active) {
    return Array.from({ length: total }, function (_, index) {
      return { step: '计划步骤 ' + (index + 1), status: index < active - 1 ? 'completed' : index === active - 1 ? 'in_progress' : 'pending' };
    });
  }
  var fixture = {
    hosts: [
      { id: 'workstation', name: 'workstation', connection: 'ONLINE', sshConnected: true,
        lastSyncedAt: fixedNow - 180000, projects: [{ path: '/work/GHub', name: 'GHub' }, { path: '/work/dotfiles', name: 'dotfiles' }] },
      { id: 'gpu-node', name: 'gpu-node', connection: 'ONLINE', sshConnected: true,
        lastSyncedAt: fixedNow - 180000, projects: [{ path: '/work/md-analysis', name: 'md-analysis' }] }
    ],
    lastSyncAt: fixedNow - 180000, syncing: false, hasMore: false,
    tasks: [
      task('approve-command', 'wait', '终端：横屏输入法弹出时保留修饰键入口', {
        approvalKey: 'a1', approvalKind: 'command', command: './gradlew :app:connectedDebugAndroidTest', workingDirectory: '/work/GHub'
      }),
      task('approve-file', 'wait', '整理 v0.1.41 发布说明', {
        approvalKey: 'a2', approvalKind: 'file', updatedAt: minutesAgo(12),
        files: [{ path: 'docs/releases/v0.1.41.md', added: 42, removed: 0 }]
      }),
      task('run-tests', 'run', '为 SessionSync 补充断线重放测试', {
        updatedAt: minutesAgo(2), plan: plan(5, 3), current: './gradlew :codex-client:test', command: './gradlew :codex-client:test'
      }),
      task('run-rdf', 'run', 'RDF 批量计算改为多进程', {
        hostId: 'gpu-node', hostName: 'gpu-node', project: 'md-analysis', path: '/work/md-analysis',
        updatedAt: minutesAgo(0), plan: plan(4, 1), current: 'python rdf.py --jobs 16', command: 'python rdf.py --jobs 16'
      }),
      task('run-path', 'run', '清理 zsh 启动脚本中重复的 PATH', {
        project: 'dotfiles', path: '/work/dotfiles', updatedAt: minutesAgo(1), plan: plan(2, 2), current: '正在撰写回复'
      }),
      task('done-diff', 'done', '代码块 Diff 文件头配色调整', {
        updatedAt: at(2, 7, 52), unread: true,
        files: [{ path: 'file-1', added: 86, removed: 31 }, { path: 'file-2', added: 0, removed: 0 }, { path: 'file-3', added: 0, removed: 0 }, { path: 'file-4', added: 0, removed: 0 }]
      }),
      task('done-sftp', 'done', '修复 SFTP 中文文件名上传', {
        updatedAt: at(1, 23, 10), files: [{ path: 'file-1', added: 18, removed: 6 }, { path: 'file-2', added: 0, removed: 0 }]
      }),
      task('done-rmsd', 'done', '生成十月轨迹的 RMSD 图', {
        hostId: 'gpu-node', hostName: 'gpu-node', project: 'md-analysis', path: '/work/md-analysis', updatedAt: at(1, 21, 47), imageCount: 1
      }),
      task('done-font', 'done', '终端字号缩放后保存偏好', {
        updatedAt: at(1, 18, 5), files: [{ path: 'file-1', added: 54, removed: 12 }, { path: 'file-2', added: 0, removed: 0 }, { path: 'file-3', added: 0, removed: 0 }]
      }),
      task('done-tmux', 'done', '统计 tmux 会话里的残留进程', {
        project: 'dotfiles', path: '/work/dotfiles', updatedAt: at(1, 16, 30), hasReply: true
      }),
      task('done-protocol', 'done', '新增 ProtocolTest 审批同步用例', {
        updatedAt: at(1, 14, 12), files: [{ path: 'file-1', added: 73, removed: 0 }]
      }),
      task('done-jump', 'done', '为 gpu-node 配置 SSH 跳板', {
        project: 'dotfiles', path: '/work/dotfiles', updatedAt: at(1, 11, 40), files: [{ path: 'file-1', added: 9, removed: 1 }]
      }),
      // The supplied HTML has no second-page content; these two records only exercise pagination.
      task('done-page2-1', 'done', '分页测试示例 · 第 8 项', { updatedAt: at(1, 10, 0), hasReply: true }),
      task('done-page2-2', 'done', '分页测试示例 · 第 9 项', { updatedAt: at(1, 9, 0), hasReply: true }),
      task('failed-gradle', 'failed', '升级 Android Gradle Plugin', {
        updatedAt: at(2, 6, 40), command: './gradlew assembleRelease', exitCode: 1, durationMs: 134000
      }),
      task('stopped-conda', 'failed', '重建 conda 环境', {
        hostId: 'gpu-node', hostName: 'gpu-node', project: 'md-analysis', path: '/work/md-analysis',
        updatedAt: at(1, 22, 15), command: 'conda env create -f environment.yml', interrupted: true, plan: plan(3, 2)
      })
    ]
  };
  window.GHubBoard = {
    postMessage: function (json) {
      var action = JSON.parse(json);
      if (action.type === 'ready') {
        window.TaskBoard.setColorMode('color');
        window.TaskBoard.render(fixture);
        document.querySelector('[data-state="wait"]').click();
      } else if (action.type !== 'dialogOpened' && action.type !== 'dialogClosed' && action.type !== 'refresh') {
        window.TaskBoard.showMessage('UI 对照页：此操作不会连接服务器或执行命令。');
      }
    }
  };
})();
