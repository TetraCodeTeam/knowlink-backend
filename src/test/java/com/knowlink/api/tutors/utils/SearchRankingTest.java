package com.knowlink.api.tutors.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRankingTest {

    @Test
    @DisplayName("Rank 0 al inicio, 1 en otra palabra, 2 contenido y 3 ausente")
    void rank_goesFromPrefixToAbsent() {
        assertThat(SearchRanking.rank("Agustina Díaz", "a")).isZero();
        assertThat(SearchRanking.rank("Claudia Pereyra", "per")).isEqualTo(1);
        assertThat(SearchRanking.rank("Miguel Salgado", "alg")).isEqualTo(2);
        assertThat(SearchRanking.rank("Ana López", "alg")).isEqualTo(3);
    }

    @Test
    @DisplayName("El ranking ignora mayúsculas y acentos")
    void rank_ignoresCaseAndAccents() {
        assertThat(SearchRanking.rank("Álgebra", "algebra")).isZero();
        assertThat(SearchRanking.rank("Álgebra", "ALGEBRA")).isZero();
        assertThat(SearchRanking.rank("Análisis", "analisis")).isZero();
        assertThat(SearchRanking.rank("Niño", "nino")).isZero();
    }

    @Test
    @DisplayName("Materias: primero las que empiezan con el texto, luego las que lo contienen")
    void comparator_ordersSubjectsAsInTheAcceptanceScenario() {
        List<String> names = List.of("Base de Datos", "Álgebra", "Análisis", "Cálculo");

        List<String> ordered = names.stream()
                .sorted(SearchRanking.comparator("a", Function.identity()))
                .toList();

        assertThat(ordered).containsExactly("Álgebra", "Análisis", "Base de Datos", "Cálculo");
    }

    @Test
    @DisplayName("Tutores: primero los que empiezan con el texto, luego los que lo contienen")
    void comparator_ordersTutorsAsInTheAcceptanceScenario() {
        List<String> names = List.of("Claudia Pereyra", "Elena Ruiz", "Agustina Díaz", "Ana López");

        List<String> ordered = names.stream()
                .sorted(SearchRanking.comparator("a", Function.identity()))
                .toList();

        assertThat(ordered).containsExactly("Agustina Díaz", "Ana López", "Claudia Pereyra", "Elena Ruiz");
    }

    @Test
    @DisplayName("Un apellido que empieza con el texto va antes que uno que solo lo contiene")
    void comparator_putsSurnamePrefixBeforeContainedWords() {
        List<String> names = List.of("Esperanza Gil", "Mariana Soto", "Claudia Pereyra");

        List<String> ordered = names.stream()
                .sorted(SearchRanking.comparator("per", Function.identity()))
                .toList();

        assertThat(ordered).containsExactly("Claudia Pereyra", "Esperanza Gil", "Mariana Soto");
    }

    @Test
    @DisplayName("Los nombres sin el texto van al final, alfabéticos entre sí")
    void comparator_putsNamesWithoutTheQueryLast() {
        List<String> names = List.of("Claudia Pereyra", "Ana López", "Miguel Salgado");

        List<String> ordered = names.stream()
                .sorted(SearchRanking.comparator("alg", Function.identity()))
                .toList();

        assertThat(ordered).containsExactly("Miguel Salgado", "Ana López", "Claudia Pereyra");
    }

    @Test
    @DisplayName("Nombres empatados quedan equivalentes para que desempate el id")
    void comparator_tiesEqualNames() {
        assertThat(SearchRanking.comparator("a", Function.<String>identity())
                .compare("Ana López", "ANA LOPEZ")).isZero();
        assertThat(SearchRanking.comparator("a", Function.<String>identity())
                .compare("Álgebra", "algebra")).isZero();
    }
}