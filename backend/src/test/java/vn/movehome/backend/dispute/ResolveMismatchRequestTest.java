package vn.movehome.backend.dispute;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveMismatchRequestTest {

    @Test
    void recordExposesAcceptAndNoteFields() {
        ResolveMismatchRequest request = new ResolveMismatchRequest(true, "Xac nhan sai tai xe nhan don");

        assertThat(request.accept()).isTrue();
        assertThat(request.note()).isEqualTo("Xac nhan sai tai xe nhan don");
    }

    @Test
    void recordSupportsRejectionWithNullNote() {
        ResolveMismatchRequest request = new ResolveMismatchRequest(false, null);

        assertThat(request.accept()).isFalse();
        assertThat(request.note()).isNull();
    }
}
