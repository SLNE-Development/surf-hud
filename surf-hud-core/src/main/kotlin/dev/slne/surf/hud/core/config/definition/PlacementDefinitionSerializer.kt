package dev.slne.surf.hud.core.config

import dev.slne.surf.hud.core.config.definition.definitions.PlacementDefinition
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

/** Reads a placement from either a bare layout id (`- vote_bar`) or a map with `layout`, `anchor`, `x`, `y`, `conditions`. */
object PlacementDefinitionSerializer : TypeSerializer<PlacementDefinition> {
    override fun deserialize(type: Type, node: ConfigurationNode): PlacementDefinition {
        if (!node.isMap) {
            val layout = node.string ?: throw SerializationException(
                node,
                type,
                "expected a layout id or a placement map"
            )
            return PlacementDefinition(layout)
        }
        val layout = node.node("layout").string ?: throw SerializationException(
            node,
            type,
            "placement needs 'layout'"
        )
        return PlacementDefinition(
            layout = layout,
            anchor = node.node("anchor").string,
            x = number(type, node, "x"),
            y = number(type, node, "y"),
            conditions = conditions(type, node),
        )
    }

    /** Reads a present whole number strictly, so typos such as `x: abc` are reported instead of becoming 0. */
    private fun number(type: Type, node: ConfigurationNode, key: String): Int? {
        val child = node.node(key)
        if (child.virtual()) return null
        return try {
            child.get(Int::class.javaObjectType)
        } catch (e: SerializationException) {
            throw SerializationException(node, type, "$key must be a whole number")
        } ?: throw SerializationException(node, type, "$key must be a whole number")
    }

    private fun conditions(type: Type, node: ConfigurationNode): List<String>? {
        val child = node.node("conditions")
        if (child.virtual()) return null
        if (!child.isList) throw SerializationException(
            node,
            type,
            "conditions must be a list of strings"
        )
        return try {
            child.getList(String::class.java)
        } catch (e: SerializationException) {
            throw SerializationException(node, type, "conditions must be a list of strings")
        }
    }

    override fun serialize(type: Type, obj: PlacementDefinition?, node: ConfigurationNode) {
        if (obj == null) {
            node.raw(null)
            return
        }
        if (obj.anchor == null && obj.x == null && obj.y == null && obj.conditions == null) {
            node.set(obj.layout)
            return
        }
        node.node("layout").set(obj.layout)
        obj.anchor?.let { node.node("anchor").set(it) }
        obj.x?.let { node.node("x").set(it) }
        obj.y?.let { node.node("y").set(it) }
        obj.conditions?.let { node.node("conditions").setList(String::class.java, it) }
    }
}
