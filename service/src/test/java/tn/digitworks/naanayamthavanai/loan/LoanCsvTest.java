package tn.digitworks.naanayamthavanai.loan;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoanCsvTest {

    @Test
    void quotesAndEscapesQuotes() {
        assertEquals("\"Meena \"\"Raj\"\"\"", LoanCsv.cell("Meena \"Raj\""));
    }

    @Test
    void neutralisesFormulas() {
        assertEquals("\"'=HYPERLINK(1)\"", LoanCsv.cell("=HYPERLINK(1)"));
        assertEquals("\"'@SUM(A1)\"", LoanCsv.cell("@SUM(A1)"));
        assertEquals("\"'-2+3\"", LoanCsv.cell("-2+3"));
    }

    @Test
    void numbersStayPlain() {
        assertEquals("\"-1250.50\"", LoanCsv.cell(new BigDecimal("-1250.50")));
        assertEquals("\"\"", LoanCsv.cell(null));
    }
}
