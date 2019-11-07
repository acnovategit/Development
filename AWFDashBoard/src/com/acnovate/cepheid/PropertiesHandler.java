/**
 * 
 */

package com.acnovate.cepheid;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Anup
 *
 */
public class PropertiesHandler {

    String result = "";

    Properties prop;
    FileInputStream file;

    public PropertiesHandler() {

	try {
	    prop = new Properties();
	    String propFileName = "./config.properties";
	    file = new FileInputStream(propFileName);

	    // load all the properties from this file

	    prop.load(file);

	}
	catch (Exception e) {
	    System.out.println("Exception: " + e);
	}
	finally {
	    try {
		file.close();
	    }
	    catch (IOException e) {
		e.printStackTrace();
	    }
	}
    }

    public String getPropertyValue(String sKey) {
	return prop.getProperty(sKey);

    }

    public void setPropertyValue(String sKey, String sValue) {
	prop.setProperty(sKey, sValue);

    }

}
