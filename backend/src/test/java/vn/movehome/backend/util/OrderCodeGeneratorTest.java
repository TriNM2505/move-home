package vn.movehome.backend.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiem tra OrderCodeGenerator: dinh dang MHyyyyMMddNNNN, sequence tang trong ngay,
 * reset khi sang ngay moi, va chan sinh ma khi vuot qua 9999 don/ngay.
 * Field static (currentDate/dailySequence) duoc dieu khien qua reflection va reset
 * lai sau moi test de khong anh huong test khac trong cung JVM.
 */
class OrderCodeGeneratorTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    @AfterEach
    void resetStaticStateSoOtherTestsAreNotAffected() throws Exception {
        setCurrentDate(LocalDate.now(BUSINESS_ZONE));
        setDailySequence(new AtomicInteger(0));
    }

    @Test
    void generateReturnsCodeMatchingMhPrefixDatePlusFourDigitSequenceFormat() {
        String code = OrderCodeGenerator.generate();

        assertThat(code).matches("^MH\\d{8}\\d{4}$");
        assertThat(code).startsWith("MH" + LocalDate.now(BUSINESS_ZONE).format(DATE_FORMATTER));
    }

    @Test
    void generateProducesIncreasingUniqueCodesWithinSameDay() throws Exception {
        setCurrentDate(LocalDate.now(BUSINESS_ZONE));
        setDailySequence(new AtomicInteger(0));

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            codes.add(OrderCodeGenerator.generate());
        }

        assertThat(codes).hasSize(5);
        String today = LocalDate.now(BUSINESS_ZONE).format(DATE_FORMATTER);
        assertThat(codes).contains(
                "MH" + today + "0001",
                "MH" + today + "0002",
                "MH" + today + "0003",
                "MH" + today + "0004",
                "MH" + today + "0005");
    }

    @Test
    void generateResetsSequenceToOneWhenBusinessDateHasChanged() throws Exception {
        setCurrentDate(LocalDate.now(BUSINESS_ZONE).minusDays(1));
        setDailySequence(new AtomicInteger(9000));

        String code = OrderCodeGenerator.generate();

        String today = LocalDate.now(BUSINESS_ZONE).format(DATE_FORMATTER);
        assertThat(code).isEqualTo("MH" + today + "0001");
    }

    @Test
    void generateThrowsIllegalStateExceptionWhenDailySequenceExceedsNineNineNineNine() throws Exception {
        setCurrentDate(LocalDate.now(BUSINESS_ZONE));
        setDailySequence(new AtomicInteger(9999));

        assertThatThrownBy(OrderCodeGenerator::generate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Đã vượt quá số lượng mã đơn hàng trong ngày.");
    }

    private void setCurrentDate(LocalDate date) throws Exception {
        Field field = OrderCodeGenerator.class.getDeclaredField("currentDate");
        field.setAccessible(true);
        field.set(null, date);
    }

    private void setDailySequence(AtomicInteger value) throws Exception {
        Field field = OrderCodeGenerator.class.getDeclaredField("dailySequence");
        field.setAccessible(true);
        field.set(null, value);
    }
}
