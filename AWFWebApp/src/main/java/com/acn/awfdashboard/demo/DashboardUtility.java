package com.acn.awfdashboard.demo;

import java.io.File;



public class DashboardUtility {
	public static String getTheNewestFile() {
	    File dir = new File("E:/demo/");
	   
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
}
