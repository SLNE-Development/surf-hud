package dev.slne.surf.hud.prototype.coreshader

import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Minimal Source RCON client (same protocol as build-logic's RconClient, which is not on this classpath).
 * Vanilla rejects requests over 1460 bytes, so callers keep each command small.
 */
class Rcon(host: String, port: Int, password: String) : AutoCloseable {
    private val socket = Socket().apply {
        connect(InetSocketAddress(host, port), 5_000)
        soTimeout = 10_000
    }
    private val input = DataInputStream(socket.getInputStream())
    private var nextId = 1

    init {
        val id = send(3, password)
        check(read().first == id) { "RCON login failed" }
    }

    fun command(command: String): String {
        val bytes = command.toByteArray(StandardCharsets.UTF_8).size
        require(bytes <= MAX_REQUEST) { "RCON command is $bytes bytes, vanilla accepts at most $MAX_REQUEST: ${command.take(120)}…" }
        send(2, command)
        return read().second
    }

    private fun send(type: Int, body: String): Int {
        val id = nextId++
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(14 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(10 + payload.size).putInt(id).putInt(type).put(payload).put(0).put(0)
        socket.getOutputStream().apply { write(buffer.array()); flush() }
        return id
    }

    private fun read(): Pair<Int, String> {
        val length = ByteBuffer.wrap(ByteArray(4).also(input::readFully)).order(ByteOrder.LITTLE_ENDIAN).int
        val data = ByteArray(length).also(input::readFully)
        val id = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
        return id to String(data, 8, length - 10, StandardCharsets.UTF_8)
    }

    override fun close() = socket.close()

    companion object {
        const val MAX_REQUEST = 1446
    }
}
