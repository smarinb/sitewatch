package dev.sergiomarin.sitewatch;

import org.springframework.boot.SpringApplication;

public class TestSitewatchApplication {

	public static void main(String[] args) {
		SpringApplication.from(SitewatchApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
