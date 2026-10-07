package pl.jollycart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class JollycartApplication {

	public static void main(String[] args) {
		SpringApplication.run(JollycartApplication.class, args);
	}

}
