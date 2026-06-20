package vn.movehome.backend.email.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Cấu hình thread pool riêng cho email theo HR-11.
 * Hàng đợi đầy thì bỏ qua email và ghi cảnh báo, không ném lỗi về nghiệp vụ chính.
 */
@Configuration
@EnableAsync
public class EmailAsyncConfig {

    public static final String EMAIL_EXECUTOR_BEAN = "emailTaskExecutor";

    private static final Logger log = LoggerFactory.getLogger(EmailAsyncConfig.class);

    @Bean(name = EMAIL_EXECUTOR_BEAN)
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("movehome-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler((task, threadPool) ->
                log.warn("Hàng đợi email đã đầy; bỏ qua email để không ảnh hưởng nghiệp vụ chính"));
        executor.initialize();
        return executor;
    }
}
