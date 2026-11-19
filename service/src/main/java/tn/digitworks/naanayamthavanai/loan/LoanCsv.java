package tn.digitworks.naanayamthavanai.loan;

import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanSummary;

import java.math.BigDecimal;
import java.util.List;

/** Loan list as CSV for Excel: UTF-8 with BOM (Tamil names), CRLF, every cell quoted. */
final class LoanCsv {

    private static final String[] HEADER = {
            "Loan no", "Client", "Phone", "Area", "Status", "Frequency", "Principal", "Installment",
            "Total payable", "Paid installments", "Installments", "Outstanding", "Behind", "Start date", "End date",
    };

    private LoanCsv() {
    }

    static String write(List<LoanSummary> rows) {
        StringBuilder out = new StringBuilder("﻿");
        line(out, (Object[]) HEADER);
        for (LoanSummary r : rows) {
            line(out, r.loanNo(), r.clientName(), r.clientPhone(), r.areaName(), r.status(), r.frequency(),
                    r.principal(), r.installmentAmount(), r.totalPayable(), r.paidCount(), r.installmentCount(),
                    r.outstanding(), r.overdueAmount(), r.startDate(), r.endDate());
        }
        return out.toString();
    }

    private static void line(StringBuilder out, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells[i]));
        }
        out.append("\r\n");
    }

    /**
     * Quoted cell. Text starting with = + - @ (or tab / CR) is prefixed with ' so a spreadsheet does not run it
     * as a formula; numbers are written plain.
     */
    static String cell(Object v) {
        if (v == null) {
            return "\"\"";
        }
        String s = v instanceof BigDecimal b ? b.toPlainString() : v.toString();
        if (!(v instanceof Number) && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
