package com.agile.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.DataTypeConstants;
import com.agile.api.IAdmin;
import com.agile.api.IAdminList;
import com.agile.api.IAgileClass;
import com.agile.api.IAgileList;
import com.agile.api.IAgileSession;
import com.agile.api.IAttribute;
import com.agile.api.IAutoNumber;
import com.agile.api.ICell;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.IListLibrary;
import com.agile.api.IQuery;
import com.agile.api.IRow;
import com.agile.api.ISignoffReviewer;
import com.agile.api.IStatus;
import com.agile.api.ITable;
import com.agile.api.IUser;
import com.agile.api.IWorkflow;
import com.agile.api.UserConstants;
import com.agile.api.WorkflowConstants;

/**
 * This file contains generic methods which are used across all PXs
 *
 */

public class GenericUtilities {

	public static Logger logger = Logger.getLogger(GenericUtilities.class.getName());
	public static String awfMessagesListName = "AWFMessagesList";
	static HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();

	/**
	 * This method initializes the logger
	 * 
	 * @param session
	 */
	public static void initializeLogger(IAgileSession session) {

		String path = null;
		try {
			// Get log4j.properties file path
			awfMessagesList = getAgileListValues(session, awfMessagesListName);
			path = awfMessagesList.get("LOG4J_PROP_FILEPATH").toString();

			File file = new File(path);
			Properties properties = null;
			FileInputStream fileInputStream = null;

			properties = new Properties();
			fileInputStream = new FileInputStream(file);

			// Configure Log4j
			if (fileInputStream != null) {
				properties.load(fileInputStream);
				PropertyConfigurator.configure(properties);
			}
		} catch (APIException e) {
			logger.error("Failed due to exception while initializing logger:" + e);
		} catch (IOException ioEx) {
			logger.error("Failed due to exception while initializing logger:" + ioEx);
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
		// Get selection from attribute cell
		ICell cell = dataObject.getCell(Integer.parseInt(attrID));
		IAgileList agileList = (IAgileList) cell.getValue();
		IAgileList[] listValues = agileList.getSelection();

		// If selection is not empty,fetch cell value
		if (listValues != null && listValues.length > 0) {
			cellValue = (listValues[0].getValue()).toString();
			logger.debug("Single list attribute Value of attribute Id "+attrID+" on "+dataObject+" is: " + cellValue);
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

		// Retrieve agile list from Admin Instance
		IAdmin admin = session.getAdminInstance();
		IListLibrary listLibrary = admin.getListLibrary();
		IAdminList adminList = listLibrary.getAdminList(listName);
		IAgileList agileList = adminList.getValues();

		// Iterate through the list values and if list value is not obsolete,put APIName
		// and value into hashmap
		Object[] children = agileList.getChildNodes().toArray();
		for (int i = 0; i < children.length; i++) {
			IAgileList listValue = (IAgileList) agileList.getChildNode(children[i]);
			if (!listValue.isObsolete())
				map.put(listValue.getAPIName(), listValue.getValue());
		}
		logger.debug("List Values "+"of "+listName+" after converting into a Map are:" + map);
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

		// Get attribute value from cell
		ICell cell = dataObject.getCell(Integer.parseInt(attrID));
		IAgileList agileList = (IAgileList) cell.getValue();
		if (agileList != null) {
			String listValues = agileList.toString();

			// If attribute value is not empty,split values based on ';' and put them into
			// ArrayList
			if (!listValues.equals("") && !listValues.isEmpty()) {
				Object[] objectArray = listValues.split(";");
				String listValue = null;
				for (int i = 0; i < objectArray.length; i++) {
					listValue = objectArray[i].toString();
					multiListAttributeValues.add(listValue);
				}
			}
			logger.debug("Multi list attribute Values of attribute Id "+attrID+" on "+dataObject+" is: " + multiListAttributeValues);
		}

		return multiListAttributeValues;
	}

	/**
	 * This method returns the Impact assessment attribute Values
	 * @param session
	 * @param dataObject
	 * @param eCRToAWFAttributeIdsMappingList
	 * @return
	 * @throws NumberFormatException
	 * @throws APIException
	 */
	public static HashMap<Object, Object> getImpactAssessmentAttrValues(IAgileSession session, IDataObject dataObject,
			HashMap<Object, Object> eCRToAWFAttributeIdsMappingList) throws NumberFormatException, APIException {

		// Get agile list values
		awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);

		// Get impact assessment attribute Ids from list into Array
		String[] impactAssessmentAttributeIdsArray = awfMessagesList.get("IMPACT_ASSESSMENT_ATTRIDS").toString()
				.split(",");
		logger.debug(
				"Impact assessment attribute Ids array contains:" + Arrays.toString(impactAssessmentAttributeIdsArray));

		// Convert array to list
		List<String> impactAssessmentAttributeIdsList = Arrays.asList(impactAssessmentAttributeIdsArray);
		logger.debug("Impact assessment attribute Ids list contains:" + impactAssessmentAttributeIdsList);

		HashMap<Object, Object> impactAssessmentAttributeValues = new HashMap<Object, Object>();
		String impactAssessmentAttributeValue = "";

		// Iterate through the eCR To AWF Attribute Ids Mapping List
		for (Object key : eCRToAWFAttributeIdsMappingList.keySet()) {

			if (eCRToAWFAttributeIdsMappingList.get(key) != null
					&& !eCRToAWFAttributeIdsMappingList.get(key).equals("")) {

				// If APIName and Name of eCRToAWFAttributeIdsMappingList belongs to
				// impactAssessmentAttributeIdsList,get attribute value
				if (impactAssessmentAttributeIdsList.contains(key)
						&& impactAssessmentAttributeIdsList.contains(eCRToAWFAttributeIdsMappingList.get(key))) {

					// Get attribute value of each Impact assessment attribute and put into hashmap
					impactAssessmentAttributeValue = getSingleListAttributeValue(dataObject,
							eCRToAWFAttributeIdsMappingList.get(key).toString());
					impactAssessmentAttributeValues.put(eCRToAWFAttributeIdsMappingList.get(key),
							impactAssessmentAttributeValue);
				}

			}
		}
		logger.debug("Impact Assessment atrribute values for " + dataObject + " are:" + impactAssessmentAttributeValues);

		return impactAssessmentAttributeValues;
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
	public static int getCountOfImpactAssessmentAttributes(String parameter, HashMap<Object, Object> impactAssessmentAttributeValues,
			IAgileSession session) throws APIException {

		// Get Agile list values
		awfMessagesList = getAgileListValues(session, awfMessagesListName);
		String value = null;
		int count = 0;

		// Iterate through the map of values and get each value
		for (Object key : impactAssessmentAttributeValues.keySet()) {
			value = (String) impactAssessmentAttributeValues.get(key);

			// If parameter passed is NULL and attribute value is NULL,increase the count of
			// NULL values
			if (parameter.equalsIgnoreCase(awfMessagesList.get("NULL").toString())) {
				if (value == null || value.equals("")) {
					count++;
				}
			}
			// If parameter passed is yes and attribute value is yes,increase the count of
			// Yes values
			else if (parameter.equalsIgnoreCase(awfMessagesList.get("YES").toString())) {
				if (value != null && value.equalsIgnoreCase(awfMessagesList.get("YES").toString())) {
					count++;
				}
			} else {
				logger.info("Invalid Parameter");
			}

		}

		logger.debug("Count of " + parameter + " Values:" + count);
		return count;
	}

	/**
	 * This method returns the nextNumber from the autoNumber of subclass
	 * 
	 * @param session
	 * @param className
	 * @param autoNumberName
	 * @return
	 * @throws APIException
	 */
	public static String getNextAutoNumber(IAgileSession session, String className, String autoNumberName)
			throws APIException {
		String nextNumber = "";

		// Get the autoNumber sources for the class
		IAdmin admin = session.getAdminInstance();
		IAgileClass subClass = admin.getAgileClass(className);
		IAutoNumber[] numberSources = subClass.getAutoNumberSources();
		IAutoNumber autoNumber = null;

		// Iterate through the autoNumber sources and get the required autoNumber
		for (int i = 0; i < numberSources.length; i++) {
			if (numberSources[i].getName().equals(autoNumberName)) {
				autoNumber = numberSources[i];
				break;
			}
		}

		// Get nextNumber from the autoNumber
		if(autoNumber!=null) {
			nextNumber = autoNumber.getNextNumber(subClass);
			logger.debug("Next number of "+autoNumber+" is:" + nextNumber);
		}

		return nextNumber;

	}

	/**
	 * This method returns the date after converting it based on Timezone and format
	 * 
	 * @param timeZoneName
	 * @param format
	 * @param dateToBeParsed
	 * @return
	 */
	public static String getDateBasedOnTimeZoneAndFormat(String timeZoneName, String format, Date dateToBeParsed) {

		// Get Timezone
		TimeZone timeZone = TimeZone.getTimeZone(timeZoneName);

		// Get Date Format with Timezone
		DateFormat dateFormat = new SimpleDateFormat(format);
		dateFormat.setTimeZone(timeZone);

		// Format the date based on TimeZone and Format
		String date = dateFormat.format(dateToBeParsed);
		logger.debug("Date based on Timezone "+timeZoneName+" and format "+format+" for the date "+dateToBeParsed+" is: " + date);
		return date;

	}

	/**
	 * This method fetches the user by querying based on first name, last name and User ID/Email ID from the userName
	 * @param session
	 * @param userName
	 * @param criteria
	 * @return
	 * @throws APIException
	 */
	public static IUser getAgileUser(IAgileSession session, String userName, String criteria) throws APIException {

		IUser user = null;
		String lastName = "";
		String firstName = "";
		String userIdOrEmailId = "";

		if (userName != null && !userName.equals("") && userName.contains("(")) {

			// Split the username based on '('
			String[] name = userName.split("\\(");
			logger.debug("Text before ( in username:" + name[0]);
			logger.debug("Text after ) in username:" + name[1]);

			// Trim any spaces in second part and remove the end ')' to retrieve email
			// id/user id
			String userIdOrEmailIdTrimmed = name[1].trim();
			userIdOrEmailId = userIdOrEmailIdTrimmed.substring(0, userIdOrEmailIdTrimmed.length() - 1).trim();
			logger.debug("User Id/Email Id :" + userIdOrEmailId);

			// Split the first part based on ',' to retrieve first name and last name
			String[] fullName = name[0].trim().split(",");
			lastName = fullName[0].trim();
			firstName = fullName[1].trim();

			logger.debug("LastName:" + lastName);
			logger.debug("FirstName:" + firstName);

			if (firstName != null && !firstName.equals("") && lastName != null && !lastName.equals("")
					&& userIdOrEmailId != null && !userIdOrEmailId.equals("")) {
				
				// Query for the user based on First Name,Last Name and User ID/Email ID
				IQuery queryUser = (IQuery) session.createObject(IQuery.OBJECT_TYPE, UserConstants.CLASS_USER);
				queryUser.setCaseSensitive(false);
				queryUser.setCriteria(criteria);
				queryUser.setParams(new Object[] { firstName, lastName, userIdOrEmailId });

				ITable table = queryUser.execute();
				@SuppressWarnings("unchecked")
				Iterator<IRow> it = table.iterator();
				IRow row = null;

				// Iterate through the results and fetch user
				while (it.hasNext()) {
					row = it.next();
					user = (IUser) row.getReferent();
				}

			}
			logger.debug("User is:"+user);

		}

		return user;
	}

	/**
	 * This method returns the workflow status
	 * 
	 * @param statusName
	 * @param workflow
	 * @return
	 * @throws APIException
	 */
	public static IStatus getStatus(String statusName, IWorkflow workflow) throws APIException {
		int i = 0;
		IStatus state = null;

		// Get workflow states
		IStatus[] states = workflow.getStates();

		// Iterate through all states and fetch required state
		while (i < states.length) {
			state = states[i];
			if (state.toString().equals(statusName)) {
				break;
			}
			i++;
		}
		logger.debug("State in "+workflow+" with status name "+statusName+ " is: " + state);

		return state;
	}

	/**
	 * This method returns the difference between two dates excluding weekends
	 * 
	 * @param awf
	 * @return
	 * @throws ParseException
	 * @throws NumberFormatException
	 * @throws APIException
	 */
	public static int getDifferenceBetweenDates(IChange awf, String timeZone, String dateFormat1, String dateFormat2,
			String reviewDateAttributeID) throws ParseException, NumberFormatException, APIException {

		// Get Moved to Review date on AWF
		Date reviewDateOnAWF = (Date) awf.getValue(Integer.parseInt(reviewDateAttributeID));
		logger.debug("Moved to Review date of"+awf+":" + reviewDateOnAWF);

		// Get current date based on timezone and format
		String dateBasedOnTimeZone = getDateBasedOnTimeZoneAndFormat(timeZone, dateFormat1, new Date());
		logger.debug(
				"Today's Date in Timezone +" + timeZone + " and format " + dateFormat1 + " is:" + dateBasedOnTimeZone);

		// Retrieve Current date in required format
		DateFormat dateFormat = new SimpleDateFormat(dateFormat2);
		Date currentDate = dateFormat.parse(dateBasedOnTimeZone.substring(0, 10));
		logger.debug("Current Date in format " + dateFormat2 + " is:" + currentDate);

		Calendar cal1 = Calendar.getInstance();
		Calendar cal2 = Calendar.getInstance();
		cal1.setTime(reviewDateOnAWF);
		cal2.setTime(currentDate);
		logger.debug("Review date in Calendar instance for"+awf+":" + cal1);
		logger.debug("Current date in Calendar instance:" + cal2);

		int difference = 0;

		// Get the difference between two dates excluding weekends
		while (cal2.after(cal1)) {
			logger.debug("Day of week is:" + cal1.get(Calendar.DAY_OF_WEEK));
			if ((Calendar.SATURDAY != cal1.get(Calendar.DAY_OF_WEEK))
					&& (Calendar.SUNDAY != cal1.get(Calendar.DAY_OF_WEEK))) {
				difference++;
			}
			cal1.add(Calendar.DATE, 1);
		}
		logger.debug("Difference between dates for"+awf+":" + difference);
		return difference;
	}

	/**
	 * This method returns pending SignOff details of AWF by iterating through
	 * workflow tab
	 * 
	 * @param awf
	 * @param awfMessagesList
	 * @return
	 * @throws APIException
	 */
	@SuppressWarnings("unchecked")
	public static HashMap<Object, Object> getPendingSignOffDetails(IChange awf, HashMap<Object, Object> awfMessagesList, String sStatus)
			throws APIException {

		// Fetch approvers based on status
		ISignoffReviewer[] approvers = awf.getReviewers(awf.getStatus(), WorkflowConstants.USER_APPROVER);
		int totalNumOfApprovers = approvers.length;

		// Fetch acknowledgers based on status
		ISignoffReviewer[] acknowledgers = awf.getReviewers(awf.getStatus(), WorkflowConstants.USER_ACKNOWLEDGER);
		int totalNumOfAcknowledgers = acknowledgers.length;

		HashMap<Object, Object> pendingSignOffDetails = new HashMap<Object, Object>();
		HashSet<String> pendingApprovers = new HashSet<String>();

		ITable workflowTable = awf.getTable(ChangeConstants.TABLE_WORKFLOW);
		Iterator<IRow> workflowIterator = workflowTable.iterator();
		IRow row = null;
		ICell[] cells = null;
		boolean approvalPending = false;
		boolean acknowledgementPending = false;
		int numOfApprovalsDone = 0;
		int numOfAcknowledgementsDone = 0;

		// Iterate through the workflow table
		while (workflowIterator.hasNext()) {

			row = workflowIterator.next();
			cells = row.getCells();

			if (cells[1].getAPIName().equalsIgnoreCase(awfMessagesList.get("WORKFLOW_STATUS").toString())
					&& cells[2].getAPIName().equalsIgnoreCase(awfMessagesList.get("ACTION").toString())
					&& cells[4].getAPIName().equalsIgnoreCase(awfMessagesList.get("REVIEWER").toString())) {

				if (cells[0].getValue() != null && cells[1].getValue() != null && cells[2].getValue() != null) {

					String statusCode = cells[0].getValue().toString();
					String workflowState = cells[1].getValue().toString();
					String action = cells[2].getValue().toString();

					// If status is review
					if (statusCode.equalsIgnoreCase(awfMessagesList.get("CURRENT_PROCESS").toString())
							&& workflowState.equalsIgnoreCase(awfMessagesList.get(sStatus).toString())) {

						// If there is any pending approver,set approvalPending to True and add pending
						// approver to pendingApprovers list to send notification
						if (action.equalsIgnoreCase(awfMessagesList.get("AWAITING_APPROVAL_ACTION").toString())) {
							approvalPending = true;

							if (cells[4].getValue() != null) {
								pendingApprovers.add(cells[4].getValue().toString());
							}

						}
						// If there is any pending acknowledger,set acknowledgementPending to True
						else if (action.equals(awfMessagesList.get("AWAITING_ACKNOWLEDGEMENT_ACTION").toString())) {

							acknowledgementPending = true;

						}
						// Get count of completed approvals
						else if (action.equalsIgnoreCase(awfMessagesList.get("APPROVED_ACTION").toString())) {

							numOfApprovalsDone++;

						}
						// Get count of completed acknowledgements
						else if (action.equalsIgnoreCase(awfMessagesList.get("ACKNOWLEDGED_ACTION").toString())) {

							numOfAcknowledgementsDone++;
						} else {

							logger.info("Invalid action");
						}
					}

				}
			}

		}

		pendingSignOffDetails.put("approvalPending", approvalPending);
		pendingSignOffDetails.put("acknowledgementPending", acknowledgementPending);
		pendingSignOffDetails.put("numOfApprovalsDone", numOfApprovalsDone);
		pendingSignOffDetails.put("numOfAcknowledgementsDone", numOfAcknowledgementsDone);
		pendingSignOffDetails.put("pendingApprovers", pendingApprovers);
		pendingSignOffDetails.put("totalNumOfApprovers", totalNumOfApprovers);
		pendingSignOffDetails.put("totalNumOfAcknowledgers", totalNumOfAcknowledgers);

		logger.debug("Pending Signoff details for "+awf+" is: "+pendingSignOffDetails);
		return pendingSignOffDetails;
	}

	/**
	 * This method autopromotes AWF to Submit/Regulatory Affairs or Approve based on
	 * impact assessment
	 * 
	 * @param countOfYesAttrs
	 * @param awf
	 * @param awfMessagesList
	 * @throws APIException
	 */
	public static void autoPromoteAWF(int countOfYesAttrs, IChange awf, HashMap<Object, Object> awfMessagesList)
			throws APIException {
		// If all impact assessment attributes are filled as No,autopromote AWF to
		// Approve
		
		if (countOfYesAttrs == 0) {
			awf.changeStatus(GenericUtilities.getStatus(awfMessagesList.get("AWF_SUBMIT_RA_STATUS").toString(),
					awf.getWorkflow()), false, "", false, false, null, null, null, null, false);
			logger.info(awf+"Autopromoted to Submit/RA");
			
			if(awf.getStatus()!=null) {
				logger.debug("Status of "+awf+" is:"+awf.getStatus());
				if(awf.getStatus().toString().equalsIgnoreCase(awfMessagesList.get("AWF_SUBMIT_RA_STATUS").toString())) {
					awf.changeStatus(
							GenericUtilities.getStatus(awfMessagesList.get("AWF_APPROVE_STATUS").toString(), awf.getWorkflow()),
							false, "", false, false, null, null, null, null, false);
					logger.info(awf+"Autopromoted to Approve");
				}
				
			}
		
		}
		// If any impact assessment attribute is filled as Yes,autopromote AWF to
		// Submit/Regulatory affairs
		else {

			awf.changeStatus(GenericUtilities.getStatus(awfMessagesList.get("AWF_SUBMIT_RA_STATUS").toString(),
					awf.getWorkflow()), false, "", false, false, null, null, null, null, false);
			logger.info(awf+"Autopromoted to Submit/RA");
		}
	}
	
	/**
	 * This method is used to send an email
	 * @param from
	 * @param addressTo
	 * @param html
	 * @param subject
	 */
	public static void sendMail(IAgileSession agileSession,String addressFrom, String addressTo, String html, String subject) {
		
		try {
			logger.info("Inside send mail.");
			
			// Get Agile list values
			awfMessagesList = getAgileListValues(agileSession, awfMessagesListName);
			
			//Set properties
			Properties properties = System.getProperties();
			properties.setProperty(awfMessagesList.get("MAILSERVER_HOSTNAME").toString(), awfMessagesList.get("MAILSERVER_IP").toString());
			Session session = Session.getInstance(properties, (javax.mail.Authenticator)null);
			
			//Create message
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(addressFrom));
			String[] recipientList = addressTo.split(",");
			 InternetAddress[] recipientAddress = new InternetAddress[recipientList.length];
			 int counter = 0;
			 for (String recipientName : recipientList) {
			     recipientAddress[counter] = new InternetAddress(recipientName.trim());
				 logger.info("Recepient Name: "+recipientName.trim());
			     counter++;
			 }
			logger.info("Recepient List contains "+counter+"Addresses");
			message.setRecipients(RecipientType.TO, recipientAddress);
			message.setSubject(subject);
			message.setContent(html, "text/html");
			 
			//send message
			Transport.send(message);
			logger.info("Message Sent.");
			
		} catch (AddressException ae) {
			
			ae.printStackTrace();
			logger.error("Failed due to:" + ae.getMessage());
		} catch (MessagingException me) {
			
			me.printStackTrace();
			logger.error("Failed due to:" + me.getMessage());
		} catch (Exception e) {
			
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		}
		
	}
	
	/**
	 * This method iterates through the list of attributes and copies the attribute
	 * values from Source Object to target Object
	 * 
	 * @param sourceObject
	 * @param attributeIdsMappingList
	 * @param session
	 * @param sourceObjClassName
	 * @param targetObjClassName
	 * @return
	 * @throws APIException
	 */
	public static HashMap<Object, Object> copyAttrValuesFromSourceObjToTargetObj(IDataObject sourceObject,
			HashMap<Object, Object> attributeIdsMappingList, IAgileSession session, String sourceObjClassName,
			String targetObjClassName) throws APIException {

		// Get Agile List
		awfMessagesList = getAgileListValues(session, awfMessagesListName);

		HashMap<Object, Object> map = new HashMap<Object, Object>();
		int sourceObjAttrId = 0;
		int sourceObjDataType = 0;
		int targetObjAttrId = 0;
		int targetObjDataType = 0;
		IAttribute sourceAttrName = null;
		IAttribute targetAttrName = null;
		Object sourceObjAttrValue = null;

		// Iterate through attribute Ids mapping list
		for (Object key : attributeIdsMappingList.keySet()) {

			if (attributeIdsMappingList.get(key) != null && !attributeIdsMappingList.get(key).equals("")) {

				/* Get source object attribute IDs from mapping list */
				sourceObjAttrId = Integer.parseInt(key.toString());
				sourceAttrName = session.getAdminInstance().getAgileClass(sourceObjClassName)
						.getAttribute(sourceObjAttrId);
				sourceObjDataType = sourceAttrName.getDataType();
				logger.debug("Source Object Attribute Data Type is:" + sourceObjDataType);

				/* Get target object attribute IDs from mapping list */
				targetObjAttrId = Integer.parseInt(attributeIdsMappingList.get(key).toString());
				targetAttrName = session.getAdminInstance().getAgileClass(targetObjClassName)
						.getAttribute(targetObjAttrId);
				targetObjDataType = targetAttrName.getDataType();
				logger.debug("Target Object attribute data type  is:" + targetObjDataType);

				/* Copy the values based on data type */
				if (sourceObjDataType == DataTypeConstants.TYPE_SINGLELIST
						&& targetObjDataType == DataTypeConstants.TYPE_SINGLELIST) {

					ICell cell = sourceObject.getCell(sourceObjAttrId);
					IAgileList agileList = (IAgileList) cell.getValue();
					IAgileList[] selection = agileList.getSelection();
					if (selection != null && selection.length > 0) {
						String selectedvalue = (selection[0].getValue()).toString();
						logger.debug("Source Object attribute value is:" + selectedvalue);

						if (!selectedvalue.equals("") && !selectedvalue.isEmpty() && selectedvalue != null) {
							IAgileList availableValues = sourceAttrName.getAvailableValues();
							availableValues.setSelection(new Object[] { selectedvalue });
							map.put(targetAttrName, availableValues);

						}
					}
				}

				else if (sourceObjDataType == DataTypeConstants.TYPE_STRING
						&& targetObjDataType == DataTypeConstants.TYPE_STRING) {
					sourceObjAttrValue = sourceObject.getValue(sourceObjAttrId);
					logger.debug("Source Object Attribute value is" + sourceObjAttrValue);

					if (sourceObjAttrValue != null) {
						map.put(targetObjAttrId, sourceObjAttrValue);
					}
				} else {
					logger.info("Invalid Data Type");
				}

			}

		}

		logger.debug("Map contains:" + map);
		return map;
	}

	

	
	/**
	 * @param mapError
	 * @param sECONumber
	 * @return
	 */
	public static String sCreateHTMLtoSend(Map<String,List<String>> mapError, String sECONumber)
	{
		
		 String html="<html><head>"
                 + "<title>"+"AWF "+sECONumber+"</title>"
                 + "</head>"+"<LINK REL='stylesheet' HREF='stylesheet/fac_css.css' TYPE='text/css'>"
                 + "<body>"
                 +"<table width='900' cellpadding='0' cellspacing='0' border='0'>"
                 +"<tr><td class ='text12' width='100%'><br>Issues while update Effectivity Date</td></tr><tr>"
                 +"<td height='5'></td></tr>"
                 +"<tr><td></td></tr>"
                 +"<tr><td height='5'></td></tr>"
                 +"<tr><td><table border='1' width='800' cellpadding='2' cellspacing='1' bgColor='#808080' style='border-collapse: collapse' bordercolor='#EBDA2A' align='left'>"
                 +"<tr bgColor=#808080 class='centerheading' align='center'>"
                         +"<td width='30' style='color: #FFFFFF;'><b>S.No.</b></td>"
                         +"<td width='35' style='color: #FFFFFF;'><b>Part</b></td>"
                         +"<td width='35' style='color: #FFFFFF;'><b>Message</b></td>"
                    
                     
                + "</tr>";
		
 	 		
		  int i=1;
          for (Map.Entry<String,List<String>> entry : mapError.entrySet())
       	{
            List<String> check=entry.getValue();
	  			Iterator<String> it = check.iterator();
	  			while(it.hasNext()){
	  			
          	  html=html+"<tr align='center' bgColor=#FFFFFF>"+"<td width='30' style='color: #000000;'>"+i+"</b></td>"
          	  +"<td width='30' style='color: #000000;'>"+entry.getKey()+"</td>"
          	  +"<td width='60' style='color: #000000;'>"+it.next()+"</td>";
              
          	  i++;
	  				}
       		}
                 html=html  +"</table>"
              +"</td>"
      +"</tr>"
      +"<tr>"
           +"<td height='6'></td>"
      +"</tr>"
    
      +"<tr>"
           +"<td height='15'></td>"
      +"</tr>"
      
      +"</table>"
      +"</body></html>";
		  
		 
		  
		  return html;
		
		
	}
	
	
	
		
	/**
	 * @param inStream
	 * @param fileName
	 * @param filePath
	 * @return
	 * @throws APIException
	 * @throws IOException
	 */
	public static boolean bCheckFile(InputStream inStream, String fileName, String filePath) throws APIException, IOException
	{
		File file;
		OutputStream outStream = null;
	
	
			file=getAttachmentFile(inStream, outStream,fileName,filePath);
			logger.debug("getAttachmentFile method executed successfully");
		
		if(file.isFile())
			return true;
	
		return false;
	}
	/**
	 * Gets the file attachment from the File input stream, output stream
	 * @param inStream
	 * @param outStream
	 * @param fileName
	 * @param filePath
	 * @return
	 * @throws APIException
	 * @throws IOException
	 */
	public static File getAttachmentFile( InputStream inStream, OutputStream outStream,String fileName, String filePath)
			throws APIException, IOException{
		int read = 0;
		byte[] bytes = new byte[1024];


		filePath=filePath+fileName;
		File targetFile = new File(filePath);
		outStream = new FileOutputStream(targetFile);

		while ((read = inStream.read(bytes)) != -1) {
			outStream.write(bytes, 0, read);
			//logger.info(bytes);
		}

		outStream.close();
		return targetFile;
	}
	
	

	
	
}
