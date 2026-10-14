package com.ningmengchang.codexcompanion

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.ningmengchang.codexcompanion.tunnel.SshConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConnectionTest {
    @Test(timeout = 10_000)
    fun closeInterruptsHandshakeWithoutWaitingForConnectTimeout() = withServer { server, executor ->
        val session = session(server)
        SshConnection(20_000).use { connection ->
            val result = executor.submit<Throwable?> {
                runCatching { connection.connect(session) }.exceptionOrNull()
            }
            server.accept().use { peer ->
                readClientGreeting(peer)
                // The server deliberately never sends its SSH greeting.
                assertFalse(result.isDone)
                connection.close()
                connection.close()
                assertTrue(result.get(2, TimeUnit.SECONDS) != null)
                assertFalse(session.isConnected)
                assertTrue(peer.getInputStream().read() == -1)
            }
        }
    }

    @Test(timeout = 10_000)
    fun stoppedAttemptRejectsLateConnectAndPortForwarding() = withServer { server, _ ->
        val session = session(server)
        val connection = SshConnection(20_000)
        connection.close()
        // A worker that was preparing credentials when stopped must not open a new socket.
        assertThrows(CancellationException::class.java) { connection.connect(session) }
        assertThrows(CancellationException::class.java) { connection.forwardLocalPort(3765, 3765) }
        assertFalse(session.isConnected)
    }

    @Test(timeout = 10_000)
    fun lateCleanupOfOldAttemptLeavesNewAttemptOpen() = withServer { server, executor ->
        SshConnection(20_000).use { oldConnection ->
            SshConnection(20_000).use { newConnection ->
                val oldResult = executor.submit<Throwable?> {
                    runCatching { oldConnection.connect(session(server)) }.exceptionOrNull()
                }
                server.accept().use { oldPeer ->
                    readClientGreeting(oldPeer)
                    val newSession = session(server)
                    val newResult = executor.submit<Throwable?> {
                        runCatching { newConnection.connect(newSession) }.exceptionOrNull()
                    }
                    server.accept().use { newPeer ->
                        readClientGreeting(newPeer)
                        oldConnection.close()
                        assertTrue(oldResult.get(2, TimeUnit.SECONDS) != null)
                        // Simulate the cancelled worker's finally block running after reconnect.
                        oldConnection.close()
                        assertFalse(newResult.isDone)
                        assertTrue(newSession.isConnected)
                        newConnection.close()
                        assertTrue(newResult.get(2, TimeUnit.SECONDS) != null)
                        assertFalse(newSession.isConnected)
                    }
                }
            }
        }
    }

    private fun session(server: ServerSocket): Session =
        JSch().getSession("test-user", "127.0.0.1", server.localPort).apply {
            setConfig("StrictHostKeyChecking", "yes")
        }

    private fun readClientGreeting(peer: Socket) {
        peer.soTimeout = 2_000
        assertTrue(peer.getInputStream().bufferedReader().readLine().startsWith("SSH-2.0-"))
    }

    private fun withServer(block: (ServerSocket, ExecutorService) -> Unit) {
        val executor = Executors.newFixedThreadPool(2)
        try {
            ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 2_000
                block(server, executor)
            }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }
}
