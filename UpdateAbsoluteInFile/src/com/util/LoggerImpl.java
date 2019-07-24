package com.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.PropertyConfigurator;

import com.agile.api.APIException;
import com.agile.api.IAgileSession;

public class LoggerImpl {

	/**Method to initiate the logger
	 * @param objClassName
	 * @param strFileName
	 * Initialize Logger
	 * @throws APIException 
	 */
	@SuppressWarnings("unused")

	//Start JIRA#SCM-3093
	public static void initAppLogger(Class objClassName,IAgileSession session)
	{

		String path=null;

		org.slf4j.Logger  logger = null;
		// DO NOT GIVE SPACE BETWEEN PATH AND =/= AND VALUE 
		path="C:\\Agile\\Agile936\\agileDomain\\config\\log4j.properties";

		File log4jFile = new File(path);
		Properties objProperties = null;
		FileInputStream fileInputStream = null;
		try {
			objProperties = new Properties();
			fileInputStream = new FileInputStream(log4jFile);

			if (fileInputStream != null) {
				objProperties.load(fileInputStream);
				PropertyConfigurator.configure(objProperties);
			} else {
				logger.info("Could not locate the Config file for Log4j");
			}
		} catch (IOException ioEx) {
			logger.error("Exception in loggerutil" + ioEx);
		}
	}

	
	
	
}
