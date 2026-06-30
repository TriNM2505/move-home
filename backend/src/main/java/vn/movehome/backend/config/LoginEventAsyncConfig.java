package vn.movehome.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class LoginEventAsyncConfig {

    public static final String LOGIN_EVENT_EXECUTOR_BEAN = "loginEventTaskExecutor";

    private static final Logger log = LoggerFactory.getLogger(LoginEventAsyncConfig.class);

    @Bean(name = LOGIN_EVENT_EXECUTOR_BEAN)
    public Executor loginEventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("movehome-login-event-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler((task, threadPool) ->
                log.warn("Login event queue is full; skip event so login is not blocked"));
        executor.initialize();
        return executor;
    }
}

