package fleetBuilder.util.lib

// This might be a little over-engineered

object PrefixedCodec {
    init {
        registerDefaults()
    }

    interface PrefixAdapter<T : Any> {
        val prefix: Char
        fun serialize(value: T): String
        fun deserialize(value: String): T?
    }

    object PrefixRegistry {
        private val byType = mutableMapOf<Class<*>, PrefixAdapter<*>>()
        private val byPrefix = mutableMapOf<Char, PrefixAdapter<*>>()

        fun <T : Any> register(type: Class<T>, adapter: PrefixAdapter<T>) {
            byType[type] = adapter
            byPrefix[adapter.prefix] = adapter
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getByType(type: Class<T>): PrefixAdapter<T>? =
            byType[type] as? PrefixAdapter<T>

        fun getByPrefix(prefix: Char): PrefixAdapter<*>? =
            byPrefix[prefix]
    }

    inline fun <reified T : Any> register(
        prefix: Char,
        noinline serialize: (T) -> String,
        noinline deserialize: (String) -> T?
    ) {
        PrefixRegistry.register(T::class.java, object : PrefixAdapter<T> {
            override val prefix = prefix
            override fun serialize(value: T) = serialize(value)
            override fun deserialize(value: String) = deserialize(value)
        })
    }

    fun registerDefaults() {
        /*  PrefixRegistry.register(String::class, object : PrefixAdapter<String> {
                override val prefix = 'S'
                override fun serialize(value: String) = value
                override fun deserialize(value: String) = value
            })
        */
        register<Float>('F', Float::toString, String::toFloatOrNull)
        register<Double>('D', Double::toString, String::toDoubleOrNull)
        register<Boolean>('B', Boolean::toString) { it.lowercase().toBooleanStrictOrNull() }
        register<Int>('I', Int::toString, String::toIntOrNull)
        register<Long>('L', Long::toString, String::toLongOrNull)
        register<Short>('l', Short::toString, String::toShortOrNull)
        register<Byte>('b', Byte::toString, String::toByteOrNull)
        register<Char>('C', Char::toString) { it.firstOrNull() }
        register<String>('S', { it }, { it })
    }

    fun isPrefixable(value: Any?): Boolean {
        if (value == null) return true
        return PrefixRegistry.getByType(value::class.java) != null
    }

    @Suppress("UNCHECKED_CAST")
    fun encode(value: Any?): String? {
        if (value == null) return "N"

        val adapter = PrefixRegistry.getByType(value::class.java) as? PrefixAdapter<Any>
            ?: return null

        return "${adapter.prefix}${adapter.serialize(value)}"
    }

    data class DecodeOutcome<T>(
        val success: Boolean,
        val value: T?
    )

    inline fun <reified T : Any> decode(value: String?): DecodeOutcome<T> {
        if (value.isNullOrEmpty()) return DecodeOutcome(false, null)

        val prefix = value[0]

        if (prefix == 'N') return DecodeOutcome(true, null)

        val adapter = PrefixRegistry.getByPrefix(prefix)
            ?: return DecodeOutcome(false, null)

        val expected = PrefixRegistry.getByType(T::class.java)
            ?: return DecodeOutcome(false, null)

        if (adapter.prefix != expected.prefix)
            return DecodeOutcome(false, null)

        val result = expected.deserialize(value.substring(1))
            ?: return DecodeOutcome(false, null)

        return DecodeOutcome(true, result)
    }

    fun decodeAny(value: String?): DecodeOutcome<Any?> {
        if (value.isNullOrEmpty()) return DecodeOutcome(false, null)

        val prefix = value[0]

        if (prefix == 'N') return DecodeOutcome(true, null)

        val adapter = PrefixRegistry.getByPrefix(prefix)
            ?: return DecodeOutcome(false, null)

        val result = adapter.deserialize(value.substring(1))
            ?: return DecodeOutcome(false, null)

        return DecodeOutcome(true, result)
    }
}