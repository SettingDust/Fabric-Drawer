package drawer

import kotlinx.serialization.*
import kotlinx.serialization.CompositeDecoder.Companion.READ_ALL
import kotlinx.serialization.internal.EnumDescriptor
import kotlinx.serialization.json.JsonUnknownKeyException
import kotlinx.serialization.modules.EmptyModule
import kotlinx.serialization.modules.SerialModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import net.minecraft.nbt.*
//TODO: document .nullable
//TODO: think if we want direct tags or nested ones (probably direct to avoid boilerplate)
//TODO: instruct to not use pos, and throw an error if they try to put when one already exists (somehow)
//TODO: tell not to use primitive serializers and point to issue

//TODO: handle invalid state (have a look at @Optional)
//TODO: test invalid state

//TODO: explain in readme how the packet handler already catches errors for you
//TODO: explain that you still need to check the validity of the packet
//TODO: explain in readme why we must have nulls everywhere and be careful
//TODO: test readme (example mod)
//TODO: revisit this claim "Or make myInfo nullable without lateinit if initializing it at first placement is not guaranteed"


//TODO: Later version:
//TODO: Text serializer
//TODO: getNullableFrom / readNullableFrom extension methods for all types  + optional key for all types
//TODO: "Identifiable" serializer
//TODO: SimpleFixedItemInv serializer

internal val TagModule = SerializersModule {
    polymorphic(Tag::class) {
        ByteTag::class with ForByteTag
        ShortTag::class with ForShortTag
        IntTag::class with ForIntTag
        LongTag::class with ForLongTag
        FloatTag::class with ForFloatTag
        DoubleTag::class with ForDoubleTag
        StringTag::class with ForStringTag
        EndTag::class with ForEndTag
        ByteArrayTag::class with ForByteArrayTag
        IntArrayTag::class with ForIntArrayTag
        LongArrayTag::class with ForLongArrayTag
        ListTag::class with ForListTag
        CompoundTag::class with ForCompoundTag
    }
}

/**
 * Keeping this class public for now in case you want to serializer an object directly to tag and vise versa.
 */
class NbtFormat(context: SerialModule = EmptyModule) : AbstractSerialFormat(context + TagModule) {

    /**
     * Converts [obj] into a [CompoundTag] that represents [obj].
     * Later [deserialize] can be called to retrieve an identical instance of [obj] from the [CompoundTag].
     *
     * These functions are not documented because I think they would be confusing.
     * Do you want these to be an official part of the API? Please make an issue.
     */
    fun <T> serialize(serializer: SerializationStrategy<T>, obj: T): CompoundTag {
        return TagEncoder().also { it.encode(serializer, obj) }.compoundTag
    }

    fun <T> deserialize(deserializer: DeserializationStrategy<T>, tag: CompoundTag): T {
        return TagDecoder(tag).decode(deserializer)
    }


    internal inner class TagEncoder(val compoundTag: CompoundTag = CompoundTag()) : NamedValueTagEncoder() {


        override val context: SerialModule = this@NbtFormat.context

        override fun encodeTaggedBoolean(tag: String, value: Boolean) = compoundTag.putBoolean(tag, value)
        override fun beginCollection(desc: SerialDescriptor, collectionSize: Int, vararg typeParams: KSerializer<*>):
                CompositeEncoder {
            encodeTaggedInt(nested("size"), collectionSize)
            return this
        }

        override fun encodeTaggedByte(tag: String, value: Byte) = compoundTag.putByte(tag, value)
        override fun encodeTaggedChar(tag: String, value: Char) = compoundTag.putString(tag, value.toString())
        override fun encodeTaggedDouble(tag: String, value: Double) = compoundTag.putDouble(tag, value)
        override fun encodeTaggedEnum(tag: String, enumDescription: EnumDescriptor, ordinal: Int) =
            compoundTag.putInt(tag, ordinal)

        override fun encodeTaggedFloat(tag: String, value: Float) = compoundTag.putFloat(tag, value)
        override fun encodeTaggedInt(tag: String, value: Int) = compoundTag.putInt(tag, value)
        override fun encodeTaggedLong(tag: String, value: Long) = compoundTag.putLong(tag, value)
        override fun encodeTaggedNotNullMark(tag: String) = compoundTag.putByte(tag + "mark", 1)
        override fun encodeTaggedNull(tag: String) {
            compoundTag.putByte(tag + "mark", 0)
        }

        override fun encodeTaggedShort(tag: String, value: Short) = compoundTag.putShort(tag, value)
        override fun encodeTaggedString(tag: String, value: String) = compoundTag.putString(tag, value)
        override fun encodeTaggedUnit(tag: String) = compoundTag.putByte(tag, 2)
        override fun encodeTaggedValue(tag: String, value: Any) {
            if (value is Tag) {
                compoundTag.put(tag, value)
            } else {
                throw SerializationException("Non-serializable ${value::class} is not supported by ${this::class} encoder")
            }

        }

        override fun encodeTaggedTag(key: String, tag: Tag) {
            compoundTag.put(key, tag)
        }


    }

    internal inner class TagDecoder(private val map: CompoundTag) : NamedValueTagDecoder() {
        private var currentIndex = -1
        override val context: SerialModule = this@NbtFormat.context
        override fun decodeCollectionSize(desc: SerialDescriptor): Int {
            return decodeTaggedInt(nested("size"))
        }

        override fun decodeTaggedBoolean(tag: String) = map.getBoolean(tag)
        override fun decodeTaggedByte(tag: String) = map.getByte(tag)
        override fun decodeTaggedChar(tag: String) = map.getString(tag).toCharArray()[0]
        override fun decodeTaggedDouble(tag: String) = map.getDouble(tag)
        override fun decodeTaggedEnum(tag: String, enumDescription: EnumDescriptor) =
            map.getInt(tag)

        override fun decodeTaggedFloat(tag: String) = map.getFloat(tag)
        override fun decodeTaggedInt(tag: String) = map.getInt(tag)

        override fun decodeTaggedLong(tag: String) = map.getLong(tag)
        override fun decodeTaggedNotNullMark(tag: String) = map.getByte(tag + "mark") != 0.toByte()
        override fun decodeTaggedShort(tag: String) = map.getShort(tag)
        override fun decodeTaggedString(tag: String): String = map.getString(tag)
        override fun decodeTaggedUnit(tag: String) = Unit
        override fun decodeTaggedTag(key: String): Tag = map.getTag(key)!!



        override fun decodeElementIndex(desc: SerialDescriptor): Int {
//            return READ_ALL
            while (true) {
                currentIndex++
                if(currentIndex == desc.elementsCount) return CompositeDecoder.READ_DONE
                if(map.containsKey(desc.getElementName(currentIndex))){
                    return currentIndex
                }
//
////                if (!reader.canBeginValue) return CompositeDecoder.READ_DONE
//                val key = reader.takeString()
//                reader.requireTokenClass(TC_COLON) { "Expected ':'" }
//                reader.nextToken()
//                val index = desc.getElementIndex(key)
//                if (index != CompositeDecoder.UNKNOWN_NAME) {
//                    return index
//                }
//                if (configuration.strictMode) throw JsonUnknownKeyException(key)
//                else reader.skipElement()

            }
        }
    }
    //TODO: support default values

}








