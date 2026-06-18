package vn.movehome.backend.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sinh order_code dạng MHyyyyMMddNNNN, ví dụ MH202606180001.
 * Sequence tăng trong bộ nhớ theo từng ngày và thread-safe trong một JVM.
 */
public final class OrderCodeGenerator {

    private static final String PREFIX = "MH";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Object LOCK = new Object();

    private static LocalDate currentDate = LocalDate.now(BUSINESS_ZONE);
    private static AtomicInteger dailySequence = new AtomicInteger(0);

    private OrderCodeGenerator() {
    }

    public static String generate() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        AtomicInteger sequenceForToday;

        synchronized (LOCK) {
            if (!today.equals(currentDate)) {
                currentDate = today;
                dailySequence = new AtomicInteger(0);
            }
            sequenceForToday = dailySequence;
        }

        int sequence = sequenceForToday.incrementAndGet();
        if (sequence > 9999) {
            throw new IllegalStateException("Đã vượt quá số lượng mã đơn hàng trong ngày.");
        }

        return PREFIX + today.format(DATE_FORMATTER) + String.format("%04d", sequence);
    }
}
