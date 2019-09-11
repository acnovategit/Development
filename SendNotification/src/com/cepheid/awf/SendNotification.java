package com.cepheid.awf;

import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import javax.mail.internet.AddressException;

import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.IAdmin;
import com.agile.api.IAgileClass;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.INode;
import com.agile.api.IQuery;
import com.agile.api.IRow;
import com.agile.api.ITable;
import com.agile.api.IUser;
import com.agile.px.ActionResult;
import com.agile.px.EventActionResult;
import com.agile.px.IEventAction;
import com.agile.px.IEventInfo;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya
 * This PX Queries for all the AWF objects in review status.
 * If any AWF object is in review for 5 days and all approvals are done,autopromote AWF after 5 days though acknowledgement is pending
 * If any AWF object is in review for >=5 days and approval is pending,send notification after 5 days to approvers who are yet to provide approval till they provide approval
 *
 */

public class SendNotification implements IEventAction {

	static Logger logger = Logger.getLogger(SendNotification.class);
	ActionResult actionResult = new ActionResult();

	public static String eCRToAWFAttributeIdsMappingListName = "ECRAWFAttributeIDsMappingList";
	public static String awfMessagesListName = "AWFMessagesList";

	HashMap<Object, Object> eCRToAWFAttributeIdsMappingList = new HashMap<Object, Object>();
	HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();

	@Override
	public EventActionResult doAction(IAgileSession session, INode arg1, IEventInfo eventInfo) {

		try {

			String result = "";

			// Initialize logger
			GenericUtilities.initializeLogger(session);

			// get Agile List Values
			eCRToAWFAttributeIdsMappingList = GenericUtilities.getAgileListValues(session,
					eCRToAWFAttributeIdsMappingListName);
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);

			// Query for all the AWFs in review
			ITable results = executeQuery(session, awfMessagesList.get("AWF_SUBCLASS_NAME").toString(),
					awfMessagesList.get("AWF_IN_REVIEW").toString());
			logger.debug("Number of objects found: " + results.size());

			// Autopromote AWF or send Notification to approvers based on the pending
			// approvals
			if (results.size() > 0) {

				autoPromoteOrSendNotification(results, session);
				result = awfMessagesList.get("SUCCESS").toString();
			} else {

				result = awfMessagesList.get("NO_OBJECTS_FOUND").toString();
			}

			actionResult = new ActionResult(ActionResult.STRING, result);
			logger.debug("Result:" + actionResult);

		} catch (APIException e) {

			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		} catch (Exception e) {

			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		}
		return new EventActionResult(eventInfo, actionResult);
	}

	/**
	 * This method autopromotes AWF or sends notification to approvers based on
	 * approval status
	 * 
	 * @param results
	 * @param session
	 * @throws APIException
	 * @throws NumberFormatException
	 * @throws ParseException
	 * @throws AddressException 
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void autoPromoteOrSendNotification(ITable results, IAgileSession session)
			throws APIException, NumberFormatException, ParseException, AddressException {

		Iterator resultsIterator = results.iterator();
		IRow row = null;
		IChange awf = null;

		// Iterate through the results
		while (resultsIterator.hasNext()) {

			row = (IRow) resultsIterator.next();

			// Get AWF
			awf = (IChange) row.getReferent();
			logger.debug("AWF is:" + awf);

			if (awf != null) {

				// Get difference between today and Moved to review date
				int difference = GenericUtilities.getDifferenceBetweenDates(awf,
						awfMessagesList.get("TIME_ZONE").toString(), awfMessagesList.get("DATE_FORMAT").toString(),
						awfMessagesList.get("DATE_FORMAT_WITHOUT_TIMEZONE").toString(),
						awfMessagesList.get("REVIEW_DATE_ATTRID").toString());
				logger.debug("Difference is:" + difference);
				
				if(difference > Integer.parseInt(awfMessagesList.get("DURATION").toString())) {
					// Get pending Signoff details
					HashMap<Object, Object> pendingSignOffDetails = new HashMap<Object, Object>();
					pendingSignOffDetails = GenericUtilities.getPendingSignOffDetails(awf, awfMessagesList);
					logger.debug("Pending SignOff Details are:" + pendingSignOffDetails);

					// Get Impact assessment attribute values of AWF
					HashMap<Object, Object> impactAssessmentAttrValues = new HashMap<Object, Object>();
					impactAssessmentAttrValues = GenericUtilities.getImpactAssessmentAttrValues(session,awf, eCRToAWFAttributeIdsMappingList);
					logger.debug("Impact assessment attribute values of AWF are:" + impactAssessmentAttrValues);

					// Get count of Impact assessment attribute values whose value is yes
					int countOfYesAttrs = GenericUtilities.getCountOfImpactAssessmentAttributes(awfMessagesList.get("YES").toString(),
							impactAssessmentAttrValues, session);
					logger.debug("count of Yes Attributes is:" + countOfYesAttrs);

					// If AWF is in review for 5 days and all approvals are done,autopromote AWF after 5 days though acknowledgement is pending
					if (difference > Integer.parseInt(awfMessagesList.get("DURATION").toString())
							&& (boolean) pendingSignOffDetails.get("approvalPending") == false
							&& (int) pendingSignOffDetails.get("totalNumOfApprovers") == (int) pendingSignOffDetails
									.get("numOfApprovalsDone")) {

						GenericUtilities.autoPromoteAWF(countOfYesAttrs, awf, awfMessagesList);
						logger.info("Autopromotion Successful");

					}
					// If AWF is in review for >=5 days and approval is pending,send notification to
					// pending approvers after 5 days
					else if (difference > Integer.parseInt(awfMessagesList.get("DURATION").toString())
							&& (boolean) pendingSignOffDetails.get("approvalPending") == true) {

						// Fetch approvers who are yet to provide signoff
						HashSet<String> pendingApprovers = new HashSet<String>();
						pendingApprovers = (HashSet<String>) pendingSignOffDetails.get("pendingApprovers");
						logger.debug("Pending approvers are:" + pendingApprovers);

						Iterator<String> pendingApproversIterator = pendingApprovers.iterator();
						String pendingApprover = null;
						IUser pendingApproverUser = null;
						String emailID = null;
						String url = null;

						String subject = String.format(awfMessagesList.get("MAIL_SUBJECT_AWF_IN_REVIEW").toString(),
								awf.toString());

						if (awf.getAgileClass().getSuperClass().getId() != null && awf.getObjectId() != null) {
							url = String.format(awfMessagesList.get("AWF_URL").toString(),
									awf.getAgileClass().getSuperClass().getId().toString(),
									awf.getObjectId().toString());
							logger.debug("URL:" + url);
						}

						String html = String.format(awfMessagesList.get("MAILBODY_AWF_IN_REVIEW").toString(),
								awf.toString(), url);

						// Iterate through pending approvers
						while (pendingApproversIterator.hasNext()) {

							pendingApprover = pendingApproversIterator.next();
							logger.debug("Pending Approver is:" + pendingApprover);

							if (pendingApprover != null && !pendingApprover.equals("")) {

								// Fetch agile user
								pendingApproverUser = GenericUtilities.getAgileUser(session, pendingApprover,
										awfMessagesList.get("USER_WITH_USERID_CRITERIA").toString());
								logger.debug("Pending Approvers User is:" + pendingApproverUser);

								if (pendingApproverUser != null) {
									// Get email ID
									emailID = (String) pendingApproverUser.getValue(
											Integer.parseInt(awfMessagesList.get("USER_EMAIL_ATTRID").toString()));
									logger.debug("Email ID is" + emailID);
									if (emailID != null && !emailID.equals("")) {

										// Send notification to pending approvers
										GenericUtilities.sendMail(session,
												awfMessagesList.get("MAIL_FROM_ADDRESS").toString(), emailID, html,
												subject);
										logger.info("Mail Sent");

									}

								}

							}

						}

					} else {
						logger.info("Invalid scenario");
					}
				}else {
					logger.info("Difference is not >="+Integer.parseInt(awfMessagesList.get("DURATION").toString()));
				}

			}

		}
	}

	/**
	 * This method returns the results after executing the query
	 * 
	 * @param session
	 * @param className
	 * @param queryString
	 * @return
	 * @throws APIException
	 */
	public static ITable executeQuery(IAgileSession session, String className, String queryString) throws APIException {

		ITable results = null;

		// Fetch class
		IAdmin admin = session.getAdminInstance();
		IAgileClass agileClass = admin.getAgileClass(className);

		// Create query object
		IQuery query = (IQuery) session.createObject(IQuery.OBJECT_TYPE, agileClass);
		query.setCaseSensitive(false);
		query.setCriteria(queryString);

		// execute query
		results = query.execute();
		logger.debug("Results size is:" + results.size());

		return results;
	}
	
}
