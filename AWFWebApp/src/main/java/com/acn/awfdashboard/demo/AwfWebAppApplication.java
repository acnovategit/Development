package com.acn.awfdashboard.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;



@SpringBootApplication
public class AwfWebAppApplication extends SpringBootServletInitializer{

	@Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(applicationClass);
    }
	public static void main(String[] args) {
		SpringApplication.run(applicationClass, args);
	}
	 private static Class<AwfWebAppApplication> applicationClass = AwfWebAppApplication.class;
}
