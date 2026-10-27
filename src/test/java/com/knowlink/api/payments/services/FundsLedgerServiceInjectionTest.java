package com.knowlink.api.payments.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FundsLedgerServiceInjectionTest {

    @Autowired
    private FundsLedgerService fundsLedgerService;

    @Test
    @DisplayName("La retencion del sistema se inyecta desde la propiedad y no queda en null")
    void systemRetentionPercentage_isInjected() throws Exception {
        FundsLedgerService target = AopTestUtils.getUltimateTargetObject(fundsLedgerService);
        Field field = FundsLedgerService.class.getDeclaredField("systemRetentionPercentage");
        field.setAccessible(true);

        assertThat(field.get(target)).isNotNull();
    }
}