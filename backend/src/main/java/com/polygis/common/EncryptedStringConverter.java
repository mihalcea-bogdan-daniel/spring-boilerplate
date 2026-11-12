package com.polygis.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@code @Convert(converter = EncryptedStringConverter.class)}: transparent AES-GCM for a column. */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryption encryption;

    public EncryptedStringConverter(FieldEncryption encryption) { // created by Spring through Hibernate's bean container
        this.encryption = encryption;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryption.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryption.decrypt(dbData);
    }
}
