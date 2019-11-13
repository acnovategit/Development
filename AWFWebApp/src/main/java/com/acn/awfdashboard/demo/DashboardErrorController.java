package com.acn.awfdashboard.demo;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;

public class DashboardErrorController implements ErrorController{
	private static final String PATH = "/error";
    
    @RequestMapping(value=PATH)
    public String error() {
        return "Error";
    }
 
    @Override
    public String getErrorPath() {
        return PATH;
    }
}
