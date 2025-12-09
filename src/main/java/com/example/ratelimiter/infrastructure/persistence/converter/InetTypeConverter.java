package com.example.ratelimiter.infrastructure.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Hibernate AttributeConverter for PostgreSQL INET type.
 * Converts between Java String and PostgreSQL INET type for IP addresses.
 *
 * The actual INET type conversion is handled by the @Column annotation with columnDefinition.
 * This converter ensures proper String mapping for the entity attribute.
 */
@Converter
public class InetTypeConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData;
    }
}
