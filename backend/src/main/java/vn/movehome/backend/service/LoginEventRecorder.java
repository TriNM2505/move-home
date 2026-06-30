package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import vn.movehome.backend.config.LoginEventAsyncConfig;
import vn.movehome.backend.repository.LoginEventRepository;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoginEventRecorder {

    private final LoginEventRepository loginEventRepository;

    @Async(LoginEventAsyncConfig.LOGIN_EVENT_EXECUTOR_BEAN)
    public void recordSuccessfulLogin(UUID userId) {
        if (userId == null) {
            return;
        }

        try {
            loginEventRepository.insertSuccessfulLogin(userId);
        } catch (RuntimeException ex) {
            log.warn("Failed to record login event for userId={}", userId, ex);
        }
    }
}

