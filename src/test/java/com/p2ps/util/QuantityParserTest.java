package com.p2ps.util;

import com.p2ps.lists.exception.ListValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityParserTest {

    @Test
    void parse_ValidQuantities_ReturnsCorrectParsedObject() {
        assertThat(QuantityParser.parse("500 g").value()).isEqualTo(500.0);
        assertThat(QuantityParser.parse("500 g").unit()).isEqualTo(QuantityParser.Unit.G);

        assertThat(QuantityParser.parse("1.5 kg").value()).isEqualTo(1.5);
        assertThat(QuantityParser.parse("1.5 kg").unit()).isEqualTo(QuantityParser.Unit.KG);

        assertThat(QuantityParser.parse("10 buc").value()).isEqualTo(10.0);
        assertThat(QuantityParser.parse("10 buc").unit()).isEqualTo(QuantityParser.Unit.PCS);
    }

    @Test
    void parse_EmptyOrNullString_DefaultsToOnePiece() {
        assertThat(QuantityParser.parse("").value()).isEqualTo(1.0);
        assertThat(QuantityParser.parse("").unit()).isEqualTo(QuantityParser.Unit.PCS);

        assertThat(QuantityParser.parse(null).value()).isEqualTo(1.0);
        assertThat(QuantityParser.parse(null).unit()).isEqualTo(QuantityParser.Unit.PCS);
    }

    @Test
    void parse_ThrowsException_WhenFormatIsInvalid() {
        assertThatThrownBy(() -> QuantityParser.parse("mult lapte"))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("Formatul cantității este invalid");
    }

    @Test
    void parse_ThrowsException_WhenQuantityIsNegative() {
        assertThatThrownBy(() -> QuantityParser.parse("-5 kg"))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("pozitiv");
    }

    @Test
    void parse_ThrowsException_WhenQuantityExceedsLimit() {
        assertThatThrownBy(() -> QuantityParser.parse("100000 kg"))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("depășit limita maximă");
    }

    @Test
    void addQuantities_SameFamily_SumsAndFormatsCorrectly() {
        assertThat(QuantityParser.addQuantities("500 g", "600 g")).isEqualTo("1.1 kg");

        assertThat(QuantityParser.addQuantities("1.2 kg", "300 g")).isEqualTo("1.5 kg");

        assertThat(QuantityParser.addQuantities("500 ml", "1.5 l")).isEqualTo("2 l");

        assertThat(QuantityParser.addQuantities("3 buc", "5 pcs")).isEqualTo("8 buc");
    }

    @Test
    void addQuantities_DifferentFamilies_ReturnsSecondQuantity() {
        assertThat(QuantityParser.addQuantities("3 buc", "2 l")).isEqualTo("2 l");
        assertThat(QuantityParser.addQuantities("500 g", "10 pcs")).isEqualTo("10 pcs");
    }

    @Test
    void addQuantities_ThrowsException_OnMassiveOverflow() {
        assertThatThrownBy(() -> QuantityParser.addQuantities("9000 kg", "2000 kg"))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("prea mare pentru a fi procesată");
    }
}