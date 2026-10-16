package dev.slne.surf.hud.buildlogic.minecraft

import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** A minimal Source RCON client for sending console commands to the dev server. */
class RconClient(host: String, port: Int, password: String) : AutoCloseable {
    private val socket = Socket().apply {
        connect(InetSocketAddress(host, port), 5_000)
        soTimeout = 10_000
    }
    private val input = DataInputStream(socket.getInputStream())
    private var nextId = 1

    init {
        val id = send(TYPE_LOGIN, password)
        check(readPacket().id == id) { "RCON login failed: wrong password" }
    }

    /** Runs [command] (without a leading slash) and returns the server's reply. */
    fun command(command: String): String {
        send(TYPE_COMMAND, command)
        return readPacket().body
    }

    private fun send(type: Int, body: String): Int {
        val id = nextId++
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(14 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(10 + payload.size).putInt(id).putInt(type).put(payload).put(0).put(0)
        socket.getOutputStream().apply { write(buffer.array()); flush() }
        return id
    }

    private fun readPacket(): Packet {
        val header = ByteArray(4).also(input::readFully)
        val length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
        val data = ByteArray(length).also(input::readFully)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val id = buffer.int
        buffer.int // type
        return Packet(id, String(data, 8, length - 10, StandardCharsets.UTF_8))
    }

    override fun close() = socket.close()

    private data class Packet(val id: Int, val body: String)

    private companion object {
        const val TYPE_COMMAND = 2
        const val TYPE_LOGIN = 3
    }
}
