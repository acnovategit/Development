package com.acn.awfdashboard.demo;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

@Component
public class WebConfig implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {
	 
	 @Override
	    public void customize(ConfigurableServletWebServerFactory container ) {
		 
		 String contextPath=DashboardUtility.getTheTitleAndVersion();
	        container.setContextPath(contextPath);
	    }
}
