package com.acn.awfdashboard.demo;

import java.io.File;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import com.acnovate.cepheid.PropertiesHandler;

public class DashboardUtility {
	public static String getTheNewestFile() {
		PropertiesHandler prop = new PropertiesHandler();
	    File dir = new File(prop.getPropertyValue("report.agile.path"));
	   
	    File[] files = ( dir).listFiles();

	    if (files == null || files.length == 0) {
	        return null;
	    }

	    File lastModifiedFile = files[0];
	    for (int i = 1; i < files.length; i++) {
	       if (lastModifiedFile.lastModified() < files[i].lastModified()) {
	           lastModifiedFile = files[i];
	       }
	    }

	    return lastModifiedFile.toString();
	}
	public static String getTheTitleAndVersion()
	{
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		String version="",title="",contextPath="";
        try {
            DocumentBuilder builder = domFactory.newDocumentBuilder();
            org.w3c.dom.Document dDoc = builder.parse("pom.xml");

            XPath xPath = XPathFactory.newInstance().newXPath();
            XPathExpression expr = xPath.compile("/project/artifactId");
            title=(String) expr.evaluate(dDoc, XPathConstants.STRING);
            System.out.println("title = "+title);
//            Node node = (Node) xPath.evaluate("/project/parent/artifactId", dDoc, XPathConstants.NODE);
//            title=node.getNodeValue();
//            node = (Node) xPath.evaluate("/project/parent/version", dDoc, XPathConstants.NODE); 
            expr = xPath.compile("/project/version");
            System.out.println("version = "+version);
            version=(String) expr.evaluate(dDoc, XPathConstants.STRING);
            contextPath="/"+title+"-"+version;
            System.out.println(contextPath);
           
        } catch (Exception e) {
            e.printStackTrace();
        }
        return contextPath;
	}

}
