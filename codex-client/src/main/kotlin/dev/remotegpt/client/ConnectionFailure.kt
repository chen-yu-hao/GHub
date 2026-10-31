package dev.remotegpt.client

/** Fixed diagnostic categories: never include server messages, addresses or credentials. */
class EventBacklog : Exception("客户端事件积压，正在重新同步会话。")
class ProxyClosed(val code: Int, val remote: Boolean, val heartbeat: Boolean) : Exception("代理连接已断开 ($code)")
class HostKeyRejected : Exception("主机指纹未获确认，请核对后手动连接。")
class DaemonNotRunning : Exception("官方 App Server 尚未运行，可以在主机设置中配置常驻服务。")
class RemoteCommandFailed : Exception("远端命令失败，请检查 Codex 可执行文件路径和服务状态。")
class RemoteCommandTimeout : Exception("远端命令超时，正在重新检查连接。")

fun connectionDiagnostic(error: Throwable?): String {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    val proxy = causes.filterIsInstance<ProxyClosed>().firstOrNull()
    return when {
        causes.any { it is EventBacklog } -> "event_backlog"
        causes.any { it is HostKeyRejected } -> "host_key_rejected"
        causes.any { it is UnsupportedServer } -> "incompatible_version"
        causes.any { it is DaemonNotRunning } -> "daemon_not_running"
        causes.any { it.javaClass.simpleName == "UserAuthException" } -> "ssh_authentication"
        proxy != null -> "websocket_close code=${proxy.code} remote=${proxy.remote} heartbeat=${proxy.heartbeat}"
        causes.any { it is RemoteCommandFailed } -> "remote_command_failed"
        causes.any { it is RemoteCommandTimeout || it is java.net.SocketTimeoutException || it is kotlinx.coroutines.TimeoutCancellationException } -> "timeout"
        causes.any { it.javaClass.simpleName == "TransportException" } -> "ssh_transport"
        causes.any { it is kotlinx.serialization.SerializationException } -> "protocol_decode"
        error == null -> "closed"
        else -> error.javaClass.simpleName
    }
}
