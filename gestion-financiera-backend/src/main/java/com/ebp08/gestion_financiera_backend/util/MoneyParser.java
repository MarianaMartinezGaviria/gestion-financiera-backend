package com.ebp08.gestion_financiera_backend.util;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class MoneyParser {

    public static BigDecimal parse(String montoStr) {
        if (montoStr == null || montoStr.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.MONEY_REQUIRED_MSG);
        }

        try {
            BigDecimal monto = new BigDecimal(montoStr.trim());
            if (monto.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.MONEY_GREATER_ZERO_MSG);
            }
            return monto;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.MONEY_INVALID_FORMAT_MSG);
        }
    }

    private MoneyParser() {}
}