package com.ningmengchang.codexcompanion.tunnel

import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException

/** Owns one attempt, including the socket while JSch is still connecting. */
internal class SshConnection(private val connectTimeoutMs: Int) : Closeable {
    private val lock = Any()
    private var closed = false
    private var session: Session? = null
    private var socket: Socket? = null

    private val socketFactory = object : SocketFactory {
        override fun createSocket(host: String, port: Int): Socket {
            val connectingSocket = synchronized(lock) {
                ensureOpen()
                Socket().also { socket = it }
            }
            try {
                connectingSocket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                synchronized(lock) { ensureOpen() }
                return connectingSocket
            } catch (error: Throwable) {
                runCatching { connectingSocket.close() }
                throw error
            }
        }

        override fun getInputStream(socket: Socket) = socket.getInputStream()

        override fun getOutputStream(socket: Socket) = socket.getOutputStream()
    }

    fun connect(session: Session) {
        synchronized(lock) {
            ensureOpen()
            check(this.session == null) { "An SSH attempt can only be used once" }
            this.session = session
            session.setSocketFactory(socketFactory)
        }
        session.connect(connectTimeoutMs)
        synchronized(lock) { ensureOpen() }
    }

    fun forwardLocalPort(localPort: Int, remotePort: Int) {
        // Binding and closing cannot cross, even if a cancelled connect returns late.
        synchronized(lock) {
            ensureOpen()
            checkNotNull(session).setPortForwardingL("127.0.0.1", localPort, "127.0.0.1", remotePort)
        }
    }

    override fun close() {
        val resources = synchronized(lock) {
            if (closed) return
            closed = true
            (socket to session).also {
                socket = null
                session = null
            }
        }
        // Closing the raw socket also interrupts connect/handshake before JSch is fully connected.
        runCatching { resources.first?.close() }
        runCatching { resources.second?.disconnect() }
    }

    private fun ensureOpen() {
        if (closed) throw CancellationException("SSH connection stopped")
    }
}
