package com.agile.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import com.agile.api.APIException;
import com.agile.api.IAdmin;
import com.agile.api.IAdminList;
import com.agile.api.IAgileList;
import com.agile.api.IAgileSession;
import com.agile.api.ICell;
import com.agile.api.IDataObject;
import com.agile.api.IListLibrary;

/**
 * 
 * @author Supriya Varada
 * This file contains all reusable methods which are used across all PXs
 *
 */
public class CommonUtil {

	public static Logger logger = Logger.getLogger(CommonUtil.class.getName());
	public static String genericMessagesListName = "GenericMessagesList";
	static HashMap<Object, Object> genericMessagesList = new HashMap<Object, Object>();

	/**
	 * Method to initiate the logger
	 * 
	 * @param objClassName
	 * @param strFileName  Initialize Logger
	 * @throws APIException
	 */
	@SuppressWarnings("unused")
	public static void initAppLogger(Class objClassName, IAgileSession session) {

		String path = null;
		try {
			genericMessagesList = CommonUtil.loadListValues(session, genericMessagesListName);
			// DO NOT GIVE SPACE BETWEEN PATH AND =/= AND VALUE
			path = genericMessagesList.get("LOG4J_PROP_FILEPATH").toString();
		} catch (APIException e) {
			logger.error("Exception in loggerutil" + e);
		}

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
				logger.info("Could not find the Configuration file for Log4j");
			}
		} catch (IOException ioEx) {
			logger.info("Exception in loggerutil" + ioEx);
		}
	}

	/**
	 * This method returns the list Value of a single list
	 * 
	 * @param dataObject
	 * @param attrID
	 * @return
	 * @throws NumberFormatException
	 * @throws APIException
	 */
	public static String getSingleListValue(IDataObject dataObject, String attrID)
			throws NumberFormatException, APIException {
		ICell cell = dataObject.getCell(Integer.parseInt(attrID));
		IAgileList list = (IAgileList) cell.getValue();
		String cellValue = null;
		IAgileList[] arrayList = list.getSelection();
		if (arrayList != null && arrayList.length > 0) {
			cellValue = (arrayList[0].getValue()).toString();
			logger.debug("Cell Value is" + cellValue);
		}
		return cellValue;
	}

	/**
	 * Load the Agile List values in HashMap
	 * 
	 * @param session
	 * @param listName
	 * @return
	 * @throws APIException
	 */
	public static HashMap<Object, Object> loadListValues(IAgileSession session, String listName) throws APIException {
		HashMap<Object, Object> objHashMap = new HashMap<>();
		logger.debug("Session:" + session);
		logger.debug("Listname is" + listName);

		IAdmin admin = session.getAdminInstance();
		logger.debug("admin is" + admin);
		IListLibrary listLibrary = admin.getListLibrary();
		logger.debug("listLibrary is" + listLibrary);
		IAdminList myList = listLibrary.getAdminList(listName);
		logger.debug("myList is" + myList);
		IAgileList listValues = myList.getValues();
		logger.debug("listValues is" + listValues);
		Object[] obj = listValues.getChildren();
		logger.debug("obj is" + obj);
		for (int i = 0; i < obj.length; i++) {
			IAgileList list2 = (IAgileList) listValues.getChildNode(obj[i]);
			if (!list2.isObsolete())
				objHashMap.put(list2.getAPIName(), list2.getValue());
		}
		logger.debug("objHashMap:" + objHashMap);
		return objHashMap;
	}
	
	/**
	 * This method returns the list Values of a Multi list
	 * @param dataObject
	 * @param attrID
	 * @return
	 * @throws NumberFormatException
	 * @throws APIException
	 */
	public static ArrayList<String> getMultiListValues(IDataObject dataObject,String attrID) throws NumberFormatException, APIException
	{
		ArrayList<String> multiListValues = new ArrayList<String>();
		ICell cell = dataObject.getCell(Integer.parseInt(attrID));
		IAgileList list = (IAgileList) cell.getValue();
		String strlistValues = list.toString();
		if(!strlistValues.equals("") && !strlistValues.equals(null))
		{
			Object[] listArray = strlistValues.split(";");
			String listValue = null;
			for(int i=0;i<listArray.length;i++)
			{
				listValue = listArray[i].toString();
				multiListValues.add(listValue);						
			}
		}
		return multiListValues;
	}

}
