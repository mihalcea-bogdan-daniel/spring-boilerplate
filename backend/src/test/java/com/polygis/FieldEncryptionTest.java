package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.polygis.common.FieldEncryption;

class FieldEncryptionTest {

    private final FieldEncryption enc = new FieldEncryption("", "jwt-secret-jwt-secret-jwt-secret-jwt");

    @Test
    void roundTripsWithRandomIvAndPassesLegacyPlaintextThrough() {
        String a = enc.encrypt("JBSWY3DPEHPK3PXP");
        String b = enc.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(a).startsWith("v1:").isNotEqualTo(b);
        assertThat(enc.decrypt(a)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(enc.decrypt("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(enc.decrypt(null)).isNull();
    }

    @Test
    void anotherKeyCannotDecryptOrForge() {
        String a = enc.encrypt("secret");
        var other = new FieldEncryption(java.util.Base64.getEncoder().encodeToString(new byte[32]), "");
        assertThatThrownBy(() -> other.decrypt(a)).isInstanceOf(IllegalStateException.class);
        String tampered = a.substring(0, a.length() - 4) + (a.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThatThrownBy(() -> enc.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
