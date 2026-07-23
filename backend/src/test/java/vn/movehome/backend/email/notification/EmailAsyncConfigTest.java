package vn.movehome.backend.email.notification;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Test cau hinh thread pool email (HR-11): dam bao bean duoc tao dung thong so
 * va handler tu choi task khong ném lỗi (chỉ ghi log cảnh báo).
 */
class EmailAsyncConfigTest {

    @Test
    void emailTaskExecutorIsConfiguredThreadPoolTaskExecutor() {
        EmailAsyncConfig config = new EmailAsyncConfig();

        Executor executor = config.emailTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("movehome-email-");
    }

    @Test
    void rejectedExecutionHandlerLogsWarningWithoutThrowing() {
        EmailAsyncConfig config = new EmailAsyncConfig();
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) config.emailTaskExecutor();
        ThreadPoolExecutor nativeExecutor = taskExecutor.getThreadPoolExecutor();
        RejectedExecutionHandler handler = nativeExecutor.getRejectedExecutionHandler();

        assertThatCode(() -> handler.rejectedExecution(() -> { }, nativeExecutor))
                .doesNotThrowAnyException();
    }
}
