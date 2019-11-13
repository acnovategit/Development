package com.acn.awfdashboard.demo;

import java.util.HashMap;
import java.util.Map;

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
		//SpringApplication.run(applicationClass, args);
		SpringApplication application =     new SpringApplication(AwfWebAppApplication.class);
		String contextPath=DashboardUtility.getTheTitleAndVersion();
		 Map<String, Object> map = new HashMap<>();
		    map.put("server.servlet.context-path",contextPath);
		    application.setDefaultProperties(map);
		    application.run(args);
	}
	 private static Class<AwfWebAppApplication> applicationClass = AwfWebAppApplication.class;
}
