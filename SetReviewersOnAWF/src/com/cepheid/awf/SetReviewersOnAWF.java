package com.cepheid.awf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.INode;
import com.agile.api.ISignoffReviewer;
import com.agile.api.IStatus;
import com.agile.api.IUser;
import com.agile.api.WorkflowConstants;
import com.agile.px.ActionResult;
import com.agile.px.ICustomAction;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya
 * If Current status is Review,this PX fetches the users under Reviewers attribute on AWF cover page.
 * If the user belongs to Quality function,he is added as Approver and remaining users are added as Acknowledgers at Review status
 * If Current Status is Submit/Regulatory affairs,this PX fetches the users under Approvers attribute on AWF cover page and adds them as 
 * approvers at Approve Status.
 * If Current Status is Approve,this PX fetches the users under Implementation Reviewers attribute on AWF cover page and the approvers at 
 * Submit/Regulatory Affairs and adds them as Approvers at Implement-Review state 
 *
 */

public class SetReviewersOnAWF implements ICustomAction {

	static Logger logger = Logger.getLogger(SetReviewersOnAWF.class);
	ActionResult actionResult = new ActionResult();
	public static String awfMessagesListName = "AWFMessagesList";

	HashSet<IUser> approvers = new HashSet<IUser>();
	HashSet<IUser> acknowledgers = new HashSet<IUser>();
	HashSet<IUser> existingReviewers = new HashSet<IUser>();

	@Override
	public ActionResult doAction(IAgileSession session, INode node, IDataObject dataObject) {

		try {
			String result = "";

			// Initialize logger
			GenericUtilities.initializeLogger(session);

			// Get Agile List Values
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);

			// Get AWF Object
			IChange AWF = (IChange) dataObject;
			logger.debug("AWF is:" + AWF);

			if (AWF != null) {
				// Get Status
				IStatus currentStatus = AWF.getStatus();
				logger.debug("Current Status is:" + currentStatus);

				if (currentStatus != null) {

					// If status is Review
					if (currentStatus.toString()
							.equalsIgnoreCase(awfMessagesList.get("AWF_REVIEW_STATUS").toString())) {

						// Get approvers and acknowledgers
						getReviewers(awfMessagesList.get("REVIEWERS_ATTRID").toString(), session, AWF, currentStatus,
								awfMessagesList);
						logger.debug("Approvers are:" + approvers);
						logger.debug("Acknowledgers are:" + acknowledgers);

						if (!approvers.isEmpty()) {
							// Add approvers at review
							AWF.addReviewers(currentStatus, approvers, null, null, true,
									String.format(awfMessagesList.get("APPROVERS_ADDED").toString(), currentStatus.toString()));
							result = result + String.format(awfMessagesList.get("APPROVERS_ADDED").toString(), currentStatus.toString());
							logger.debug("Result:" + result);
						}

						if (!acknowledgers.isEmpty()) {
							// Add acknowledgers at review
							AWF.addReviewers(currentStatus, null, null, acknowledgers, true,
									String.format(awfMessagesList.get("ACKNOWLEDGERS_ADDED").toString(), currentStatus.toString()));
							result = result + String.format(awfMessagesList.get("ACKNOWLEDGERS_ADDED").toString(), currentStatus.toString());
						}

						// If status is Submit/Regualtory Affairs
					} else if (currentStatus.toString()
							.equalsIgnoreCase(awfMessagesList.get("AWF_SUBMIT_RA_STATUS").toString())) {

						// Get Approvers
						getReviewers(awfMessagesList.get("APPROVERS_ATTRID").toString(), session, AWF, currentStatus,
								awfMessagesList);
						logger.debug("Approvers are:" + approvers);
						logger.debug("Acknowledgers are:" + acknowledgers);

						if (!approvers.isEmpty()) {

							// Get existing reviewers at Approve status incase of backward workflow movement
							existingReviewers = getExistingReviewers(AWF, AWF.getDefaultNextStatus(),
									WorkflowConstants.USER_APPROVER, session,
									awfMessagesList.get("USER_WITH_USERID_CRITERIA").toString());
							logger.debug("Existing Reviewers are:" + existingReviewers);

							// Remove Existingreviewers if any at Approve status
							if (!existingReviewers.isEmpty()) {

								AWF.removeReviewers(AWF.getDefaultNextStatus(), existingReviewers, null, null, "");
							}
							// Add Approvers at Approve status
							AWF.addReviewers(AWF.getDefaultNextStatus(), approvers, null, null, true,
									String.format(awfMessagesList.get("APPROVERS_ADDED").toString(), awfMessagesList.get("AWF_APPROVE_STATUS").toString()));
							result = result+String.format(awfMessagesList.get("APPROVERS_ADDED").toString(), awfMessagesList.get("AWF_APPROVE_STATUS").toString());
						}
						// If status is Approve
					} else if (currentStatus.toString()
							.equalsIgnoreCase(awfMessagesList.get("AWF_APPROVE_STATUS").toString())) {

						//Get Implementation Reviewers 
						ArrayList<String> implementationReviewers = new ArrayList<String>();
						implementationReviewers = GenericUtilities.getMultiListAttributeValue(AWF,
								awfMessagesList.get("IMPLEMENT_REVIEWERS_ATTRID").toString());
						logger.debug("Implementation Reviewers are:" + implementationReviewers);

						//If there are any Implementation reviewers
						if (implementationReviewers.size() > 0) {

							Iterator<String> implementationReviewersIterator = implementationReviewers.iterator();
							String implementationReviewer = "";
							IUser implementationReviewerUser = null;

							//Iterate through the Implementation Reviewers
							while (implementationReviewersIterator.hasNext()) {
								//Fetch the user  
								implementationReviewer = implementationReviewersIterator.next();
								implementationReviewerUser = GenericUtilities.getAgileUser(session,
										implementationReviewer, awfMessagesList.get("USER_WITH_USERID_CRITERIA").toString());
								logger.debug("Implementation Reviewer is:" + implementationReviewerUser);

								//Add Implementation Reviewers to approvers list
								if (implementationReviewerUser != null) {
									approvers.add(implementationReviewerUser);
								}
							}
							logger.debug(
									"Approvers after adding users chosen at Implement-Review state are:" + approvers);
						}

						if (!approvers.isEmpty()) {

							// Get existing reviewers at Implement-Review Status in case of backward workflow movement
							existingReviewers = getExistingReviewers(AWF, AWF.getDefaultNextStatus(),
									WorkflowConstants.USER_APPROVER, session,
									awfMessagesList.get("USER_WITH_USERID_CRITERIA").toString());
							logger.debug("Existing Reviewers are:" + existingReviewers);

							// Remove Existing reviewers if any at Implement-Review status
							if (!existingReviewers.isEmpty()) {
								AWF.removeReviewers(AWF.getDefaultNextStatus(), existingReviewers, null, null, "");
							}

							// Add Approvers at Implement-Review state
							AWF.addReviewers(AWF.getDefaultNextStatus(), approvers, null, null, true,
									String.format(awfMessagesList.get("APPROVERS_ADDED").toString(), awfMessagesList.get("AWF_IMPLEMENT_REVIEW_STATUS").toString()));
							result = result + String.format(awfMessagesList.get("APPROVERS_ADDED").toString(), awfMessagesList.get("AWF_IMPLEMENT_REVIEW_STATUS").toString());
						}
						//If no approvers are selected at Submit/Regulatory Affairs and Implement-Review,display below message in history tab
						else {
							result = result + awfMessagesList.get("APPROVERS_NOT_SELECTED").toString();
						}


					} else {
						logger.info("Invalid Status");
					}

				}
			}
			logger.debug("Result:" + result);
			actionResult = new ActionResult(ActionResult.STRING, result);

		} catch (APIException e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());

		} catch (Exception e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());

		}

		return actionResult;
	}
	
	/**
	 * This method fetches the existing reviewers at a status,converts each reviewer to Agile User and returns the reviewers in the form of collection  
	 * @param AWF
	 * @param status
	 * @param reviewerType
	 * @param session
	 * @param criteria
	 * @return
	 * @throws APIException
	 */
	private HashSet<IUser> getExistingReviewers(IChange AWF, IStatus status, int reviewerType, IAgileSession session,
			String criteria) throws APIException {

		//Get Existing reviewers based on state
		HashSet<IUser> existingReviewersList = new HashSet<IUser>();
		ISignoffReviewer[] existingReviewersArray = AWF.getReviewers(status, reviewerType);
		logger.debug("Existing Reviewers Array Size:" + existingReviewersArray.length);

		if (existingReviewersArray.length > 0) {

			int i = 0;
			IDataObject existingReviewer = null;
			IUser existingReviewerUser = null;

			//Iterate through the existing reviewers array
			while (i < existingReviewersArray.length) {
				//Fetch the existing reviewer
				existingReviewer = existingReviewersArray[i].getReviewer();
				logger.debug("Existing Reviewer Data Object is:" + existingReviewer);

				if (existingReviewer != null) {
					//Fetch agile user
					existingReviewerUser = GenericUtilities.getAgileUser(session, existingReviewer.toString(),
							criteria);
					logger.debug("Existing reviewer Agile User is :" + existingReviewerUser);

					//Add user to collection
					if (existingReviewerUser != null) {
						existingReviewersList.add(existingReviewerUser);
					}
				}
				i++;
			}
			logger.debug("Existing Reviewers are :" + existingReviewersList);
		}

		return existingReviewersList;
	}

	/**
	 * This method
	 * - fetches users selected on Reviewers/Approvers attribute on AWF cover page
	 * - Iterates through the selected Cascade list values and fetches Users based on number of levels
	 * - If status is Review ,add users under Quality Function to approvers list and the users under other functions are added to acknowledgers list
	 * - If status is not Review ,add users under all Functions to approvers list
	 * @param attrId
	 * @param session
	 * @param AWF
	 * @param currentStatus
	 * @param awfMessagesList
	 * @throws NumberFormatException
	 * @throws APIException
	 */
	public void getReviewers(String attrId, IAgileSession session, IChange AWF, IStatus currentStatus,
			HashMap<Object, Object> awfMessagesList) throws NumberFormatException, APIException {

		//Get the users selected on Reviewers/Approvers attribute on AWF cover page
		ArrayList<String> reviewers = new ArrayList<String>();
		reviewers = GenericUtilities.getMultiListAttributeValue(AWF, attrId);
		logger.debug("Reviewers are:" + reviewers);

		if (!reviewers.isEmpty()) {
			Iterator<String> reviewersIterator = reviewers.iterator();
			String reviewer = null;
			String[] reviewerArray = null;
		
		
			//Iterate through the cascade list values
			while (reviewersIterator.hasNext()) {
				int length = 0;
				
				//Get each list value
				reviewer = (String) reviewersIterator.next();
				logger.debug("Reviewer : " + reviewer);
				if (reviewer != null && !reviewer.equals("")) {
					
					//If list value contains '|' it has sublevels
					if (reviewer.contains("|")) {
						logger.debug("Contains |");
						
						//Split based on '|' to get sublevels
						reviewerArray = reviewer.split("\\|");
						
						//Get number of levels
						length = reviewerArray.length;
						logger.debug("Reviewer Array Size is:" + length);
						
						String function = "";
						String reviewerUserName = "";
						IUser reviewerUser = null;
						
						//Get Function name which is always on Level1
						function = reviewerArray[0];
						
						//If number of levels is 3,the user is present on 3rd level
						if (length == 3) {
							reviewerUserName = reviewerArray[2];
						//If number of levels is 4,the user is present on 4th level
						} else if (length == 4) {
							reviewerUserName = reviewerArray[3];
						} else {
							logger.info("Invalid length");
						}
						logger.debug("User value is:" + reviewerUserName);
						logger.debug("Function is:" + function);
						
						if (reviewerUserName != null && !reviewerUserName.equals("")) {
							//Fetch Agile User
							reviewerUser = GenericUtilities.getAgileUser(session, reviewerUserName,
									awfMessagesList.get("USER_WITH_EMAILID_CRITERIA").toString());
							logger.debug("Agile user is:" + reviewerUser);
							
							if (reviewerUser != null && function != null) {
								
								//If status is Review and Function is Quality,add the user to approvers list
								if (currentStatus.toString()
										.equalsIgnoreCase(awfMessagesList.get("AWF_REVIEW_STATUS").toString())) {
									if (function.equalsIgnoreCase(awfMessagesList.get("QUALITY_FUNCTION").toString())) {
										approvers.add(reviewerUser);
									} 
									//If status is Review and Function is not Quality,add the user to acknowledgers list
									else {
										acknowledgers.add(reviewerUser);
									}
								} 
								//If status is not Review ,add the user to approvers list
								else {
									approvers.add(reviewerUser);
								}
							}

						}
					}
				}

			}
			logger.debug("Approvers are:" + approvers);
			logger.debug("Acknowledgers are:" + acknowledgers);
		}

	}

	
}
