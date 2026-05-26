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

        assertThat(QuantityParser.parse("1,5 KG").value()).isEqualTo(1.5);
        assertThat(QuantityParser.parse("1,5 KG").unit()).isEqualTo(QuantityParser.Unit.KG);

        assertThat(QuantityParser.parse("750 grame").unit()).isEqualTo(QuantityParser.Unit.G);
        assertThat(QuantityParser.parse("2 litri").unit()).isEqualTo(QuantityParser.Unit.L);
        assertThat(QuantityParser.parse("3 bucăți").unit()).isEqualTo(QuantityParser.Unit.PCS);

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
                .hasMessageContaining("Quantity format is NOT valid");
    }

    @Test
    void parse_ThrowsException_WhenQuantityIsNegative() {
        assertThatThrownBy(() -> QuantityParser.parse("-5 kg"))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("positive number");
    }

    @Test
    void parse_ThrowsException_WhenQuantityExceedsLimit() {
        assertThatThrownBy(() -> QuantityParser.parse("100000 kg"))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("maximum accepted limit");
    }

    @Test
    void addQuantities_SameFamily_SumsAndFormatsCorrectly() {
        assertThat(QuantityParser.addQuantities("500 g", "600 g")).isEqualTo("1.1 kg");

        assertThat(QuantityParser.addQuantities("1.2 kg", "300 g")).isEqualTo("1.5 kg");

        assertThat(QuantityParser.addQuantities("200 g", "1 kg")).isEqualTo("1.2 kg");

        assertThat(QuantityParser.addQuantities("200 grame", "1 kilogram")).isEqualTo("1.2 kg");

        assertThat(QuantityParser.addQuantities("0,5 l", "250 ml")).isEqualTo("750 ml");

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
                .hasMessageContaining("too big to be processed");
    }

    @Test
    void convertToUnit_sameFamily_convertsToTargetUnitWithoutRoundingUp() {
        assertThat(QuantityParser.convertToUnit("500 ml", "1 l")).isEqualTo("0.5 l");
        assertThat(QuantityParser.convertToUnit("1500 g", "1 kg")).isEqualTo("1.5 kg");
        assertThat(QuantityParser.convertToUnit("2 buc", "10 buc")).isEqualTo("2 buc");
    }

    @Test
    void convertToUnit_differentFamilies_throwsException() {
        assertThatThrownBy(() -> QuantityParser.convertToUnit("500 ml", "1 kg"))
                .isInstanceOf(ListValidationException.class)
                .hasMessageContaining("different unit families");
    }
}
