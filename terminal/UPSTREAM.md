# Termux terminal components

Source: https://github.com/termux/termux-app

Pinned commit: `8629e632fcb95da272221be327db653fb24befe9`.

Only terminal-emulator and terminal-view Java sources are included. TerminalSession is replaced with an SSH-backed adapter; JNI and local subprocess execution are omitted. Original per-file copyright and license headers are retained. Upstream is GPL-3.0-only with Apache-2.0 exceptions documented by Termux.

## Local modifications

- Removed upstream `TerminalSession.java` and the JNI/process bridge. `TerminalSession.kt` implements the same view-facing contract with remote SSH reads, writes and PTY resize callbacks.
- Adjusted the terminal view's generated resource import to `dev.remotegpt.terminal.R`.
- Added `TerminalCallbacks.kt` with default callbacks and explicit clipboard confirmation in the app.
- Kept the emulator, renderer, selection and input handling upstream sources. The adapter bounds pending UI reads and initial output buffering.
- Gradle configuration is maintained by this project; no upstream Termux app, packages or shell process launcher is included.

The upstream license notice is copied to `../licenses/Termux-LICENSE.md`.
