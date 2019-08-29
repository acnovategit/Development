package com.agile.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.TimeZone;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import com.agile.api.APIException;
import com.agile.api.IAdmin;
import com.agile.api.IAdminList;
import com.agile.api.IAgileClass;
import com.agile.api.IAgileList;
import com.agile.api.IAgileSession;
import com.agile.api.IAutoNumber;
import com.agile.api.ICell;
import com.agile.api.IDataObject;
import com.agile.api.IListLibrary;

/**
 * This file contains generic methods which are used across all PXs
 *
 */

public class GenericUtilities {

	public static Logger logger = Logger.getLogger(GenericUtilities.class.getName());
	public static String genericMessagesListName = "GenericMessagesList";
	static HashMap<Object, Object> genericMessagesList = new HashMap<Object, Object>();

	/**
	 * This method initializes the logger
	 * 
	 * @param session
	 */
	public static void initializeLogger(IAgileSession session) {

		String path = null;
		try {
			genericMessagesList = getAgileListValues(session, genericMessagesListName);
			path = genericMessagesList.get("LOG4J_PROP_FILEPATH").toString();

			File file = new File(path);
			Properties properties = null;
			FileInputStream fileInputStream = null;

			properties = new Properties();
			fileInputStream = new FileInputStream(file);

			if (fileInputStream != null) {
				properties.load(fileInputStream);
				PropertyConfigurator.configure(properties);
			}
		} catch (APIException e) {
			logger.error("Failed due to exception while initializing logger" + e);
		} catch (IOException ioEx) {
			logger.info("Failed due to exception while initializing logger" + ioEx);
		}
	}

	/**
	 * This method returns the Value of a single list attribute in Agile
	 * 
	 * @param dataObject
	 * @param attrID
	 * @return
	 * @throws NumberFormatException
	 * @throws APIException
	 */
	public static String getSingleListAttributeValue(IDataObject dataObject, String attrID)
			throws NumberFormatException, APIException {
		String cellValue = null;
		ICell cell = dataObject.getCell(Integer.parseInt(attrID));
		IAgileList agileList = (IAgileList) cell.getValue();
		IAgileList[] listValues = agileList.getSelection();
		if (listValues != null && listValues.length > 0) {
			cellValue = (listValues[0].getValue()).toString();
			logger.debug("Cell Value is" + cellValue);
		}
		return cellValue;
	}

	/**
	 * This method returns the values of a agile list in HashMap
	 * 
	 * @param session
	 * @param listName
	 * @return
	 * @throws APIException
	 */
	public static HashMap<Object, Object> getAgileListValues(IAgileSession session, String listName)
			throws APIException {
		HashMap<Object, Object> map = new HashMap<>();

		IAdmin admin = session.getAdminInstance();
		IListLibrary listLibrary = admin.getListLibrary();
		IAdminList adminList = listLibrary.getAdminList(listName);
		IAgileList agileList = adminList.getValues();

		Object[] children = agileList.getChildNodes().toArray();
		for (int i = 0; i < children.length; i++) {
			IAgileList listValue = (IAgileList) agileList.getChildNode(children[i]);
			if (!listValue.isObsolete())
				map.put(listValue.getAPIName(), listValue.getValue());
		}
		logger.debug("List Values in a Map are:" + map);
		return map;
	}

	/**
	 * This method returns values of a multilist attribute in Agile
	 * 
	 * @param dataObject
	 * @param attrID
	 * @return
	 * @throws NumberFormatException
	 * @throws APIException
	 */
	public static ArrayList<String> getMultiListAttributeValue(IDataObject dataObject, String attrID)
			throws NumberFormatException, APIException {

		ArrayList<String> multiListAttributeValues = new ArrayList<String>();
		ICell cell = dataObject.getCell(Integer.parseInt(attrID));
		IAgileList agileList = (IAgileList) cell.getValue();
		if (agileList != null) {
			String listValues = agileList.toString();
			if (!listValues.equals("") && !listValues.isEmpty()) {
				Object[] objectArray = listValues.split(";");
				String listValue = null;
				for (int i = 0; i < objectArray.length; i++) {
					listValue = objectArray[i].toString();
					multiListAttributeValues.add(listValue);
				}
			}
			logger.debug("MultiList Attribute Values are:" + multiListAttributeValues);
		}

		return multiListAttributeValues;
	}

	/**
	 * This method returns the Impact assessment attribute Values
	 * 
	 * @param dataObject
	 * @param attributeIdsList
	 * @return
	 * @throws NumberFormatException
	 * @throws APIException
	 */
	public static HashMap<Object, Object> getIAAttributeValues(IDataObject dataObject,
			HashMap<Object, Object> attributeIdsList) throws NumberFormatException, APIException {
		HashMap<Object, Object> attributeValues = new HashMap<Object, Object>();
		String attributeValue = "";

		for (Object key : attributeIdsList.keySet()) {
			if (attributeIdsList.get(key) != null && !attributeIdsList.get(key).equals("")) {

				attributeValue = getSingleListAttributeValue(dataObject, attributeIdsList.get(key).toString());
				attributeValues.put(attributeIdsList.get(key), attributeValue);
			}
		}
		logger.debug("Impact Assessment atrribute values are:" + attributeValues);

		return attributeValues;
	}

	/**
	 * This method returns the number of impact assessment attribute values which
	 * are marked as Yes or the number of assessment attributes which are null based
	 * on the parameter passed.
	 * 
	 * @param parameter
	 * @param attributeValues
	 * @return
	 * @throws APIException
	 */
	public static int getCountOfIAAttributes(String parameter, HashMap<Object, Object> attributeValues,
			IAgileSession session) throws APIException {

		genericMessagesList = getAgileListValues(session, genericMessagesListName);
		String value = null;
		int count = 0;

		for (Object key : attributeValues.keySet()) {
			value = (String) attributeValues.get(key);
			if (parameter.equalsIgnoreCase(genericMessagesList.get("NULL").toString())) {
				if (value == null || value.equals("")) {
					count++;
				}
			} else if (parameter.equalsIgnoreCase(genericMessagesList.get("YES").toString())) {
				if (value != null && value.equalsIgnoreCase(genericMessagesList.get("YES").toString())) {
					count++;
				}
			} else {
				logger.debug("Invalid Parameter");
			}

		}

		logger.debug("Count of " + parameter + " Values:" + count);
		return count;
	}
	
	/**
	 * This method returns the nextNumber from the autoNumber of subclass
	 * @param session
	 * @param className
	 * @param autoNumberName
	 * @return
	 * @throws APIException
	 */
	public static String getNextAutoNumber(IAgileSession session,String className,String autoNumberName) throws APIException {
		String nextNumber = "";
		
		//Get the autoNumber sources for the class
		IAdmin admin = session.getAdminInstance();
		IAgileClass subClass = admin.getAgileClass(className);
		IAutoNumber[] numberSources = subClass.getAutoNumberSources();
		IAutoNumber autoNumber = null;
		
		//Iterate through the autoNumber sources and get the required autoNumber
		for(int i=0;i<numberSources.length;i++) {
			if(numberSources[i].getName().equals(autoNumberName))
			{
				autoNumber = numberSources[i];
				break;
			}
		}
		
		//Get nextNumber from the autoNumber
		nextNumber = autoNumber.getNextNumber(subClass);
		logger.debug("Next Autonmber is:" + nextNumber);
		
		return nextNumber;
		
	}
	
	/**
	 * This method returns the date after converting it based on Timezone and format
	 * @param timeZoneName
	 * @param format
	 * @param dateToBeParsed
	 * @return
	 */
	public static String getDateBasedOnTimeZoneAndFormat(String timeZoneName, String format, Date dateToBeParsed) {

		TimeZone timeZone = TimeZone.getTimeZone(timeZoneName);
		DateFormat dateFormat = new SimpleDateFormat(format);
		dateFormat.setTimeZone(timeZone);
		String date = dateFormat.format(dateToBeParsed);
		logger.debug("Date based on Timezone is:"+date);
		return date;

	}

}
