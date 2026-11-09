package com.cristian.moneymanager.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Parses the optional "month" request parameter (format yyyy-MM, e.g. 2026-10). */
public final class MonthParam {

    /** Months are interpreted in the users' time zone, not the server's (UTC on Render). */
    public static final ZoneId APP_ZONE = ZoneId.of("Europe/Madrid");

    private MonthParam() {
    }

    /** Returns the requested month, or the current month when the parameter is missing. */
    public static YearMonth parseOrCurrent(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(APP_ZONE);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mes no válido, el formato es aaaa-mm (por ejemplo 2026-10)");
        }
    }
}
