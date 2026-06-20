package com.tainted.analytics.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class UuidIdGeneratorTest {

    private final IdGenerator generator = new UuidIdGenerator();

    @Test
    void newId_producesValidUniqueUuids() {
        String a = generator.newId();
        String b = generator.newId();

        assertThat(a).isNotEqualTo(b);
        // 정상적인 UUID 형식이어야 한다 (파싱 실패 시 예외)
        assertThatCode(() -> UUID.fromString(a)).doesNotThrowAnyException();
        assertThat(UUID.fromString(a)).hasToString(a);
    }
}
