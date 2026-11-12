package com.polygis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class BreachedPasswordCheckerTest {

    @Test
    void hashesLikeTheRangeApiExpects() {
        // well-known SHA-1 of "password"
        assertThat(BreachedPasswordChecker.sha1Hex("password")).isEqualTo("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8");
    }

    @Test
    void findsSuffixInRangeResponseAndIgnoresPadding() {
        String body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:9545824\r\nFFFF:0";
        assertThat(BreachedPasswordChecker.occurrences(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8")).isEqualTo(9545824);
        assertThat(BreachedPasswordChecker.occurrences(body, "FFFF")).isZero();
        assertThat(BreachedPasswordChecker.occurrences(body, "ABCD")).isZero();
        assertThat(BreachedPasswordChecker.occurrences(null, "ABCD")).isZero();
    }

    @Test
    void disabledCheckerAcceptsEverything() {
        assertThatCode(() -> new BreachedPasswordChecker(false).assertNotBreached("password")).doesNotThrowAnyException();
    }
}
