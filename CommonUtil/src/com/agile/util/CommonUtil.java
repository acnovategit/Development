package com.agile.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.log4j.PropertyConfigurator;

import com.agile.api.IAgileSession;

public class CommonUtil {
	
	public static Logger logger = Logger.getLogger(CommonUtil.class.getName());
	
	public static void initAppLogger(Class objClassName,IAgileSession session)
	{

		String path=null;

		// DO NOT GIVE SPACE BETWEEN PATH AND =/= AND VALUE 
		path="/ora01/APP/agile/agile936/agileDomain/config/log4j.properties";

		File log4jFile = new File(path);
		Properties objProperties = null;
		FileInputStream fileInputStream = null;
		try {
			objProperties = new Properties();
			fileInputStream = new FileInputStream(log4jFile);

			objProperties.load(fileInputStream);
			PropertyConfigurator.configure(objProperties);
		} catch (IOException ioEx) {
			logger.info("Exception in loggerutil" + ioEx);
		}
	}


}
