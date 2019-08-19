package drawer

import kotlinx.serialization.*
import kotlinx.serialization.internal.makeNullable
import kotlinx.serialization.modules.EmptyModule
import kotlinx.serialization.modules.SerialModule
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.PacketByteBuf


/**
 * Puts [obj] into the [CompoundTag] instance of [inTag].
 * Later [getFrom] can be called to retrieve an identical instance of [obj] from the [CompoundTag].
 *
 * @param key If you are serializing two objects of the same type, you MUST  specify a key.
 * The same key must be used in [getFrom].
 * @param context Used for polymorphic serialization, see [Here](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md).
 */
fun <T> SerializationStrategy<T>.put(obj: T?, inTag: CompoundTag, key: String? = null, context: SerialModule = EmptyModule) {
    val usedKey = key ?: this.descriptor.name
    require(!inTag.containsKey(usedKey)) {
        """A '${this.descriptor.name}' appears twice in the CompoundTag.
            |If you are serializing two objects of the same type, you MUST specify a key, see kdoc.
        |Also make sure you didn't use the same key twice.
    """.trimMargin()
    }
    if (obj != null) inTag.put(usedKey, NbtFormat(context).serialize(this, obj))
}

/**
 * Retrieves the object the tag that was stored in [tag] with [put] and converts it into the original object.
 * That object can be null. If you know it's not nullable use [getFrom] instead.
 *
 * @param key If you are serializing two objects of the same type, you MUST specify a key.
 * The same key must be used in [put].
 * @param context Used for polymorphic serialization, see [Here](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md).
 */
fun <T> DeserializationStrategy<T>.getNullableFrom(tag: CompoundTag, key: String? = null, context: SerialModule = EmptyModule): T? {
    val deserializedTag = tag.getTag(key ?: this.descriptor.name) ?: return null
    return NbtFormat(context).deserialize(this, deserializedTag as CompoundTag)
}

/**
 * Retrieves the object the tag that was stored in [tag] with [put] and converts it into the original object.
 * That object cannot be null. If you need it to be nullable use [getNullableFrom] instead.
 *
 * @param key If you are serializing two objects of the same type, you MUST specify a key.
 * The same key must be used in [put].
 * @param context Used for polymorphic serialization, see [Here](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md).
 */
fun <T> DeserializationStrategy<T>.getFrom(tag: CompoundTag, key: String? = null, context: SerialModule = EmptyModule): T = getNullableFrom(tag, key,context)
    ?: throw SerializationException("getFrom cannot be used on a nullable value. Use getNullableFrom instead.")


/**
 * Writes [obj] into [toBuf], to later be retrieved with [readFrom].
 * @param context Used for polymorphic serialization, see [Here](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md).
 */
fun <T> SerializationStrategy<T>.write(obj: T?, toBuf: PacketByteBuf, context: SerialModule = EmptyModule) {
    ByteBufFormat(context).ByteBufEncoder(toBuf).apply {
        if (obj != null) {
            encodeNotNullMark()
            encode(this@write, obj)
        } else encodeNull()
    }
}

/**
 * Retrieves the object that was stored in the [buf] previously with [write].
 * Must be used on non-null values only. For nullable values use [readNullableFrom].
 *  @param context Used for polymorphic serialization, see [Here](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md).
 */
fun <T> DeserializationStrategy<T>.readFrom(buf: PacketByteBuf, context: SerialModule = EmptyModule): T = readNullableFrom(buf,context)
    ?: throw SerializationException("readFrom cannot be used on a nullable value. Use readNullableFrom instead.")

/**
 * Retrieves the object that was stored in the [buf] previously with [write].
 * For non-null values use [readFrom].
 *  @param context Used for polymorphic serialization, see [Here](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md).
 */
fun <T> DeserializationStrategy<T>.readNullableFrom(buf: PacketByteBuf, context: SerialModule = EmptyModule): T? {
    val decoder = ByteBufFormat(context).ByteBufDecoder(buf)
    return if (decoder.decodeNotNullMark()) {
        decoder.decode(this)
    } else null
}


//TODO: renamings
inline fun <reified T> DeserializationStrategy<T>.readFromSafe(
    buf: PacketByteBuf,
    context: SerialModule = EmptyModule
): T {
    val value = readFrom(buf, context)
    if (null is T) return value as T
    throw SerializationException(
        "Could not deserialize empty PacketByteBuf to null, as a non-nullable serializer was used. " +
                "If your value is nullable you need to use a nullable serialize using .nullable." +
                "Otherwise, you can ignore this as an invalid packet."
    )

}
val <T : Any> KSerializer<T>.nullable get() = makeNullable(this)