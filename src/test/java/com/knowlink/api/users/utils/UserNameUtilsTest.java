package com.knowlink.api.users.utils;

import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserNameUtilsTest {

    @Test
    @DisplayName("CP-003.21 - buildFullName recorta espacios extra")
    void buildFullName_trimsExtraSpaces() {
        assertThat(UserNameUtils.buildFullName("  María José ", " García  ")).isEqualTo("María José García");
        assertThat(UserNameUtils.buildFullName("Ana", "Pérez")).isEqualTo("Ana Pérez");
    }

    @Test
    @DisplayName("CP-003.22 - firstNameOf y lastNameOf usan las columnas completas")
    void firstNameOfAndLastNameOf_useStructuredColumns() {
        User user = User.builder()
                .firstName("María José")
                .lastName("García")
                .fullName("Nombre viejo")
                .build();

        assertThat(UserNameUtils.firstNameOf(user)).isEqualTo("María José");
        assertThat(UserNameUtils.lastNameOf(user)).isEqualTo("García");
    }

    @Test
    @DisplayName("CP-003.23 - fallback desde fullName de tres palabras")
    void firstNameOfAndLastNameOf_fallBackToFullName() {
        User user = User.builder().fullName("María José García").build();

        assertThat(UserNameUtils.firstNameOf(user)).isEqualTo("María");
        assertThat(UserNameUtils.lastNameOf(user)).isEqualTo("José García");
    }

    @Test
    @DisplayName("CP-003.24 - fallback con fullName de una sola palabra deja apellido vacío")
    void lastNameOf_singleWordFullName_returnsEmpty() {
        User user = User.builder().fullName("Madonna").build();

        assertThat(UserNameUtils.firstNameOf(user)).isEqualTo("Madonna");
        assertThat(UserNameUtils.lastNameOf(user)).isEmpty();
    }

    @Test
    @DisplayName("CP-003.25 - fallback con fullName nulo devuelve cadenas vacías")
    void firstNameOfAndLastNameOf_nullFullName_returnEmpty() {
        User user = User.builder().build();

        assertThat(UserNameUtils.firstNameOf(user)).isEmpty();
        assertThat(UserNameUtils.lastNameOf(user)).isEmpty();
    }
}
