package vn.movehome.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Test cau hinh thread pool ghi nhan login event: dam bao bean duoc tao dung thong so
 * va handler tu choi task khong ném lỗi (chỉ ghi log cảnh báo, không chặn login).
 */
class LoginEventAsyncConfigTest {

    @Test
    void loginEventTaskExecutorIsConfiguredThreadPoolTaskExecutor() {
        LoginEventAsyncConfig config = new LoginEventAsyncConfig();

        Executor executor = config.loginEventTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("movehome-login-event-");
    }

    @Test
    void rejectedExecutionHandlerLogsWarningWithoutThrowing() {
        LoginEventAsyncConfig config = new LoginEventAsyncConfig();
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) config.loginEventTaskExecutor();
        ThreadPoolExecutor nativeExecutor = taskExecutor.getThreadPoolExecutor();
        RejectedExecutionHandler handler = nativeExecutor.getRejectedExecutionHandler();

        assertThatCode(() -> handler.rejectedExecution(() -> { }, nativeExecutor))
                .doesNotThrowAnyException();
    }
}
