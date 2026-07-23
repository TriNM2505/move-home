package vn.movehome.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.movehome.backend.repository.LoginEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LoginEventRecorderTest {

    @Mock
    private LoginEventRepository loginEventRepository;

    private LoginEventRecorder recorder;

    @Test
    void nullUserIdIsIgnoredWithoutTouchingRepository() {
        recorder = new LoginEventRecorder(loginEventRepository);

        recorder.recordSuccessfulLogin(null);

        verifyNoInteractions(loginEventRepository);
    }

    @Test
    void successfulLoginIsRecordedViaRepository() {
        recorder = new LoginEventRecorder(loginEventRepository);
        UUID userId = UUID.randomUUID();

        recorder.recordSuccessfulLogin(userId);

        verify(loginEventRepository).insertSuccessfulLogin(userId);
    }

    @Test
    void repositoryFailureIsSwallowedWithoutPropagating() {
        recorder = new LoginEventRecorder(loginEventRepository);
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("DB tam thoi khong san sang"))
                .when(loginEventRepository).insertSuccessfulLogin(any());

        assertThatCode(() -> recorder.recordSuccessfulLogin(userId)).doesNotThrowAnyException();

        verify(loginEventRepository).insertSuccessfulLogin(userId);
        verify(loginEventRepository, never()).insertSuccessfulLogin(null);
    }
}
