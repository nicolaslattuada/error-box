package com.nicolattu

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.json.PackageVersion
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.Deserializers
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.databind.util.StdConverter

typealias ErrorBox<T> = Either<Exception, T>

object EitherModule : SimpleModule(PackageVersion.VERSION) {

    init {
        addSerializer(Either::class.java, StdDelegatingSerializer(EitherSerializationConverter))
    }

    override fun setupModule(context: SetupContext) {
        super.setupModule(context)
        context.addDeserializers(EitherDeserializerResolver)
    }
}

private object EitherSerializationConverter : StdConverter<Either<*, *>, Any>() {
    override fun convert(value: Either<*, *>?): Any? = value.rightIfNotNull { null }
}

private const val NULL_VALUE_MESSAGE = "Could not deserialize null value"

@Suppress("ComplexCondition", "TooGenericExceptionCaught")
private class EitherDeserializer(
    private val fullType: JavaType,
    private val valueTypeDeserializer: TypeDeserializer?,
    private val valueDeserializer: JsonDeserializer<*>?,
    private val beanProperty: BeanProperty? = null
) : StdDeserializer<Either<*, *>>(fullType), ContextualDeserializer {

    override fun getValueType(): JavaType = fullType

    override fun getNullValue(): Either<*, *> {
        return NullPointerException(NULL_VALUE_MESSAGE).left()
    }

    private fun withResolved(
        fullType: JavaType,
        typeDeserializer: TypeDeserializer?,
        valueDeserializer: JsonDeserializer<*>?,
        beanProperty: BeanProperty?
    ): EitherDeserializer {
        return if (fullType == this.fullType &&
            typeDeserializer == this.valueTypeDeserializer &&
            valueDeserializer == this.valueDeserializer &&
            beanProperty == this.beanProperty) {
            this
        } else {
            EitherDeserializer(fullType, typeDeserializer, valueDeserializer, beanProperty)
        }
    }

    override fun createContextual(
        context: DeserializationContext,
        property: BeanProperty?
    ): JsonDeserializer<Either<*, *>> {
        val typeDeserializer = valueTypeDeserializer?.forProperty(property)
        var deserializer = valueDeserializer
        var type = fullType

        fun refdType(): JavaType = type.contentType ?: TypeFactory.unknownType()

        if (deserializer == null) {
            if (property != null) {
                val intr = context.annotationIntrospector
                val member = property.member
                if (intr != null && member != null) {
                    type = intr.refineDeserializationType(context.config, member, type)
                }
                deserializer = context.findContextualValueDeserializer(
                    type.contentType ?: TypeFactory.unknownType(), property)
            }
        } else { // otherwise directly assigned, probably not contextual yet:
            deserializer = context.handleSecondaryContextualization(
                deserializer, property, refdType()) as JsonDeserializer<*>
        }

        return withResolved(type, typeDeserializer, deserializer, property)
    }

    override fun deserialize(parser: JsonParser, context: DeserializationContext): Either<*, *> {
        val deserializer = valueDeserializer ?: context.findContextualValueDeserializer(
            fullType.contentType, beanProperty)
        return try {
            val result: Any
            if (valueTypeDeserializer == null) {
                result = deserializer.deserialize(parser, context)
            } else {
                result = deserializer.deserializeWithType(parser, context, valueTypeDeserializer)
            }
            result.right()
        } catch (e: Exception) {
            advanceToNextObject(parser)
            e.left()
        }
    }

    private fun advanceToNextObject(parser: JsonParser) {
        val startTokenId = parser.currentTokenId
        do {
            parser.nextToken()
        } while (parser.currentTokenId != startTokenId)
    }

    override fun deserializeWithType(
        jp: JsonParser,
        ctxt: DeserializationContext,
        typeDeserializer: TypeDeserializer
    ): Either<*, *> {
        val t = jp.currentToken
        return if (t == JsonToken.VALUE_NULL) {
            getNullValue(ctxt)
        } else {
            typeDeserializer.deserializeTypedFromAny(jp, ctxt) as Either<*, *>
        }
    }
}

private object EitherDeserializerResolver : Deserializers.Base() {

    private val either = Either::class.java

    override fun findBeanDeserializer(
        type: JavaType,
        config: DeserializationConfig,
        beanDesc: BeanDescription
    ): JsonDeserializer<Either<*, *>>? {
        val rawClass = type.rawClass
        return if (!either.isAssignableFrom(rawClass)) {
            null
        } else {
            val elementType: JavaType = type.bindings.getBoundType(1)
            val typeDeserializer: TypeDeserializer? = elementType.getTypeHandler<TypeDeserializer>()
            val valueDeserializer: JsonDeserializer<*>? = elementType.getValueHandler()
            EitherDeserializer(
                config.typeFactory.constructReferenceType(Either::class.java, elementType),
                typeDeserializer,
                valueDeserializer
            )
        }
    }
}
