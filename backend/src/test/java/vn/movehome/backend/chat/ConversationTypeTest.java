package vn.movehome.backend.chat;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTypeTest {

    @Test
    void isValidReturnsTrueForAllKnownConversationTypes() {
        assertThat(ConversationType.isValid(ConversationType.CUSTOMER_MANAGER)).isTrue();
        assertThat(ConversationType.isValid(ConversationType.MANAGER_DRIVER)).isTrue();
        assertThat(ConversationType.isValid(ConversationType.CUSTOMER_DRIVER)).isTrue();
    }

    @Test
    void isValidReturnsFalseForUnknownOrNullType() {
        assertThat(ConversationType.isValid("UNKNOWN_TYPE")).isFalse();
        assertThat(ConversationType.isValid(null)).isFalse();
        assertThat(ConversationType.isValid("")).isFalse();
    }

    @Test
    void constantsHaveExpectedValues() {
        assertThat(ConversationType.CUSTOMER_MANAGER).isEqualTo("CUSTOMER_MANAGER");
        assertThat(ConversationType.MANAGER_DRIVER).isEqualTo("MANAGER_DRIVER");
        assertThat(ConversationType.CUSTOMER_DRIVER).isEqualTo("CUSTOMER_DRIVER");
    }

    @Test
    void privateConstructorIsInaccessibleButInstantiableViaReflection() throws Exception {
        Constructor<ConversationType> constructor = ConversationType.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        constructor.setAccessible(true);
        assertThat(constructor.newInstance()).isNotNull();
    }
}
