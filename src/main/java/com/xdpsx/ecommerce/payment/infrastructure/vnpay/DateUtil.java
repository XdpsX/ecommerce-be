package com.xdpsx.ecommerce.payment.infrastructure.vnpay;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;

public class DateUtil {
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static Date parseISO(String date) {
        try {
            return Date.from(LocalDate.parse(date, ISO_DATE_FORMAT)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant());
        } catch (Exception e) {
            return null;
        }
    }

    public static long getDiffInDays(LocalDate date1, LocalDate date2) {
        return ChronoUnit.DAYS.between(date1, date2);
    }

    public static LocalDate parse(String date) {
        return LocalDate.parse(date);
    }

    public static String getVnTime() {
        return formatVnTime(Instant.now());
    }

    public static String formatVnTime(Calendar calendar) {
        return formatVnTime(calendar.toInstant());
    }

    public static String formatVnTime(Instant instant) {
        return VNPAY_DATE_FORMAT.format(instant.atZone(VN_ZONE));
    }
}
