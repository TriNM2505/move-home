package vn.movehome.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void mainDelegatesToSpringApplicationRun() {
		String[] args = {"--server.port=0"};
		try (var springApplication = mockStatic(SpringApplication.class)) {
			BackendApplication.main(args);

			springApplication.verify(
					() -> SpringApplication.run(BackendApplication.class, args), times(1));
		}
	}

}
