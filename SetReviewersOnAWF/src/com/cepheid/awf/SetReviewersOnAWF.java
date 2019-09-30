package com.cepheid.awf;

import java.util.HashMap;
import java.util.HashSet;
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
import com.agile.px.EventActionResult;
import com.agile.px.IEventAction;
import com.agile.px.IEventInfo;
import com.agile.px.IWFChangeStatusEventInfo;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya
 * If Current status is Review,this PX fetches the users under Reviewers attribute on AWF cover page.
 * If the user belongs to Quality function,he is added as Approver and remaining users are added as Acknowledgers at Review status
 * If Current Status is Submit/Regulatory affairs,this PX fetches the users under Approvers attribute on AWF cover page and adds them as 
 * approvers at Approve Status.Also if there are no approvers/acknowledgers at Submit/Regulatory affairs,AWF is autopromoted to Approve
 * If Current Status is Approve,this PX fetches the users under Implementation Reviewers attribute on AWF cover page 
 * and adds them as Approvers at Implement-Review state(Currently this part of code is commented) 
 * 
 * Before adding approvers/acknowledgers ,this PX checks for below
 * -  if any user is already added as Reviewer ,this px removes the user from approvers/acknowledgers list to avoid duplicate user exception
 * -  if any user doesnt contain 'A9 Approver - AWF' role,do not set him as approver/acknowledger
 * -  if same user is added as approver and acknowledger,the px doesnt adds the user as acknowledger
 * 
 * 
 */

public class SetReviewersOnAWF implements IEventAction {

	static Logger logger = Logger.getLogger(SetReviewersOnAWF.class);
	ActionResult actionResult = new ActionResult();
	public static String awfMessagesListName = "AWFMessagesList";

	@SuppressWarnings("unchecked")
	@Override
	public EventActionResult doAction(IAgileSession session, INode arg1, IEventInfo eventInfo) {

		try {
			String result = "";
			HashMap<Object, Object> reviewers = new HashMap<Object, Object>();
			HashSet<IUser> approvers = new HashSet<IUser>();
			HashSet<IUser> acknowledgers = new HashSet<IUser>();
			HashSet<IUser> reviewersWithoutRequiredRoles = new HashSet<IUser>();
			IStatus status = null;
			HashSet<IUser> existingReviewers = new HashSet<IUser>();

			// Initialize logger
			GenericUtilities.initializeLogger(session);

			// Get Agile List Values
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);

			//Get ChangeStatus Event Info
			IWFChangeStatusEventInfo changeStatusEventInfo = (IWFChangeStatusEventInfo) eventInfo;
			
			// Get AWF Object
			IChange AWF = (IChange) changeStatusEventInfo.getDataObject();
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
						reviewers = GenericUtilities.getReviewersOnAWF(
								awfMessagesList.get("REVIEWERS_ATTRID").toString(), session, AWF, awfMessagesList);
						approvers = (HashSet<IUser>) reviewers.get("Approvers");
						acknowledgers = (HashSet<IUser>) reviewers.get("Acknowledgers");
						logger.debug("Approvers are:" + approvers);
						logger.debug("Acknowledgers are:" + acknowledgers);
						
						//If users select any function which doesnt have any user under it (or)  
						//if the user selected is not available in Agile with FirstName,LastName and Email Id as specified in Cascade list,display a message in history tab
						if(((HashSet<String>)reviewers.get("InvalidSelection")).size()>0) {
							result = result+String.format(awfMessagesList.get("INVALID_SELECTION_MSG").toString(), (HashSet<String>)reviewers.get("InvalidSelection"));
						}

						if (!approvers.isEmpty()) {

							// Get existing reviewers
							existingReviewers = getExistingReviewers(approvers, AWF, currentStatus,
									WorkflowConstants.USER_APPROVER, session,
									awfMessagesList.get("USER_WITH_USERID_CRITERIA").toString(),awfMessagesList);
							logger.debug("Existing reviewers are:" + existingReviewers);
							
							// Remove existing reviewers to avoid duplicate user error
							if(existingReviewers.size()>0) {
								approvers.removeAll(existingReviewers);
								result = result +String.format(awfMessagesList.get("CHANGE_STATUS_APPROVERS_ADDED").toString(), existingReviewers, currentStatus.toString());
							}

							if (!approvers.isEmpty()) {
								
								// Get list of approvers who doesnt have 'A9 Approver - AWF' role
								reviewersWithoutRequiredRoles = GenericUtilities.getUsersWithoutRole(session, approvers,
										awfMessagesList.get("AWF_APPROVER_ROLE").toString());
								logger.debug("Approvers without A9 Approver - AWF  role:" + reviewersWithoutRequiredRoles);

								// Remove approvers who doesnt have 'A9 Approver - AWF' role from final set of
								// approvers
								if (reviewersWithoutRequiredRoles.size() > 0) {
									approvers.removeAll(reviewersWithoutRequiredRoles);
									logger.debug(
											"Approvers after removing users without A9 Approver - AWF are:" + approvers);
									result = result+String.format(awfMessagesList.get("REMOVED_USERS_WITHOUT_PRIVILEGES").toString(), reviewersWithoutRequiredRoles);

								}

								if (!approvers.isEmpty()) { 
									// Add approvers at review
									AWF.addReviewers(currentStatus, approvers, null, null, true, String.format(
											String.format(awfMessagesList.get("APPROVERS_ADDED").toString(),approvers,
													currentStatus.toString())));
									result = result + String.format(awfMessagesList.get("APPROVERS_ADDED").toString(),approvers,
											currentStatus.toString());
									
								}
							}
							
						}else {
							result = result + String.format(awfMessagesList.get("APPROVERS_NOT_SELECTED").toString(),currentStatus.toString());
						}

						if (!acknowledgers.isEmpty()) {

							// Get existing approvers 
							existingReviewers = getExistingReviewers(acknowledgers, AWF, currentStatus,
									WorkflowConstants.USER_APPROVER, session,
									awfMessagesList.get("USER_WITH_USERID_CRITERIA").toString(),awfMessagesList);
							logger.debug("Existing approvers are:" + existingReviewers);
							
							// Remove existing approvers to avoid duplicate user error
							if(existingReviewers.size()>0) {
								acknowledgers.removeAll(existingReviewers);
								result = result +String.format(awfMessagesList.get("DUPLICATE_USER").toString(), existingReviewers, currentStatus.toString());
							}
							
							if(!acknowledgers.isEmpty()) {
								// Get existing acknowledgers 
								existingReviewers = getExistingReviewers(acknowledgers, AWF, currentStatus,
										WorkflowConstants.USER_ACKNOWLEDGER, session,
										awfMessagesList.get("USER_WITH_USERID_CRITERIA").toString(),awfMessagesList);
								logger.debug("Existing acknowledgers are:" + existingReviewers);
								
								// Remove existing acknowledgers to avoid duplicate user error
								if(existingReviewers.size()>0) {
									acknowledgers.removeAll(existingReviewers);
									result = result +String.format(awfMessagesList.get("CHANGE_STATUS_ACKNOWLEDGERS_ADDED").toString(), existingReviewers, currentStatus.toString());
								}
							}

							if(!acknowledgers.isEmpty()) {
								// Get list of acknowledgers who doesnt have 'A9 Approver - AWF' role
								reviewersWithoutRequiredRoles = GenericUtilities.getUsersWithoutRole(session, acknowledgers,
										awfMessagesList.get("AWF_APPROVER_ROLE").toString());
								logger.debug(
										"Acknowledgers without A9 Approver - AWF  role:" + reviewersWithoutRequiredRoles);

								// Remove acknowledgers who doesnt have 'A9 Approver - AWF' role from final set
								// of
								// acknowledgers
								if (reviewersWithoutRequiredRoles.size() > 0) {
									acknowledgers.removeAll(reviewersWithoutRequiredRoles);
									logger.debug("Acknowledgers after removing users without A9 Approver - AWF are:"
											+ acknowledgers);
									result = result+String.format(awfMessagesList.get("REMOVED_USERS_WITHOUT_PRIVILEGES").toString(), reviewersWithoutRequiredRoles);

								}

								if (!acknowledgers.isEmpty()) {
									// Add acknowledgers at review
									AWF.addReviewers(currentStatus, null, null, acknowledgers, true,
											String.format(awfMessagesList.get("ACKNOWLEDGERS_ADDED").toString(),
													 acknowledgers, currentStatus.toString()));
									result = result + String.format(awfMessagesList.get("ACKNOWLEDGERS_ADDED").toString(),
											acknowledgers, currentStatus.toString());

								}
							}

						}
						else {
							result = result + String.format(awfMessagesList.get("ACKNOWLEDGERS_NOT_SELECTED").toString(),currentStatus.toString());
						}

						// If status is Submit/Regualtory Affairs
					} else if (currentStatus.toString()
							.equalsIgnoreCase(awfMessagesList.get("AWF_SUBMIT_RA_STATUS").toString())) {
						
						//Get status
						status = GenericUtilities.getStatus(awfMessagesList.get("AWF_APPROVE_STATUS").toString(), 
								AWF.getWorkflow());
						
						if(status!=null) {
							
							// Get Approvers
							reviewers = GenericUtilities.getReviewersOnAWF(
									awfMessagesList.get("APPROVERS_ATTRID").toString(), session, AWF, awfMessagesList);
							approvers = (HashSet<IUser>) reviewers.get("Approvers");
							acknowledgers = (HashSet<IUser>) reviewers.get("Acknowledgers");
							logger.debug("Approvers are:" + approvers);
							logger.debug("Acknowledgers are:" + acknowledgers);

							//If users select any function which doesnt have any user under it (or)  
							//if the user selected is not available in Agile with FirstName,LastName and Email Id as specified in Cascade list,display a message in history tab
							if(((HashSet<String>)reviewers.get("InvalidSelection")).size()>0) {
								result = result+String.format(awfMessagesList.get("INVALID_SELECTION_MSG").toString(), (HashSet<String>)reviewers.get("InvalidSelection"));
							}
							
							//Get existing reviewers
							existingReviewers = getExistingReviewers(approvers, AWF, status,
									WorkflowConstants.USER_APPROVER, session,
									awfMessagesList.get("USER_WITH_USERID_CRITERIA").toString(),awfMessagesList);
							logger.debug("Existing reviewers are:" + existingReviewers);
							
							// Remove existing reviewers to avoid duplicate user error
							if (!existingReviewers.isEmpty()) {
								AWF.removeReviewers(status, existingReviewers, null, null, "");
							}
							
							if (!approvers.isEmpty()) {

									// Get list of approvers who doesnt have 'A9 Approver - AWF' role
									reviewersWithoutRequiredRoles = GenericUtilities.getUsersWithoutRole(session, approvers,
											awfMessagesList.get("AWF_APPROVER_ROLE").toString());
									logger.debug("Approvers without A9 Approver - AWF  role:" + reviewersWithoutRequiredRoles);

									// Remove approvers who doesnt have 'A9 Approver - AWF' role from final set of
									// approvers
									if (reviewersWithoutRequiredRoles.size() > 0) {
										approvers.removeAll(reviewersWithoutRequiredRoles);
										logger.debug(
												"Approvers after removing users without A9 Approver - AWF are:" + approvers);
										result = result+String.format(awfMessagesList.get("REMOVED_USERS_WITHOUT_PRIVILEGES").toString(), reviewersWithoutRequiredRoles);

									}

									if (!approvers.isEmpty()) {
										
										// Add Approvers at Approve status
										AWF.addReviewers(status, approvers, null, null, true,
												String.format(awfMessagesList.get("APPROVERS_ADDED").toString(),approvers,
														status.toString()));
										result = result + String.format(awfMessagesList.get("APPROVERS_ADDED").toString(),approvers,
												status.toString());
									}

								
							}else {
								result = result + String.format(awfMessagesList.get("APPROVERS_NOT_SELECTED").toString(),status.toString());

							}
							
						}
						
						//Autopromote AWF from Submit/Regulatory Affairs to Approve if there are no approvers/acknowledgers at Submit/Regulatory Affairs
						result = result+GenericUtilities.autoPromoteAWFFromSubmitToApprove(AWF, awfMessagesList);
						
					} 
					/**Commented below part as the Implementation-Reviewers attribute has been disabled**/
					/**
					// If status is Approve
					else if (currentStatus.toString()
							.equalsIgnoreCase(awfMessagesList.get("AWF_APPROVE_STATUS").toString())) {
						
						//Get status
						status = GenericUtilities.getStatus(awfMessagesList.get("AWF_IMPLEMENT_REVIEW_STATUS").toString(), 
								AWF.getWorkflow());
						
						if(status!=null) {
							// Get Implementation Reviewers
							reviewers = GenericUtilities.getReviewersOnAWF(
									awfMessagesList.get("IMPLEMENT_REVIEWERS_ATTRID").toString(), session, AWF,
									awfMessagesList);
							approvers = (HashSet<IUser>) reviewers.get("Approvers");
							acknowledgers = (HashSet<IUser>) reviewers.get("Acknowledgers");
							logger.debug("Approvers are:" + approvers);
							logger.debug("Acknowledgers are:" + acknowledgers);
							
							//If users select any function which doesnt have any user under it (or)  
							//if the user selected is not available in Agile with FirstName,LastName and Email Id as specified in Cascade list,display a message in history tab
							if(((HashSet<String>)reviewers.get("InvalidSelection")).size()>0) {
								result = result+String.format(awfMessagesList.get("INVALID_SELECTION_MSG").toString(), (HashSet<String>)reviewers.get("InvalidSelection"));
							}

							//Get existing reviewers
							existingReviewers = getExistingReviewers(approvers, AWF, 
									status,
									WorkflowConstants.USER_APPROVER, session,
									awfMessagesList.get("USER_WITH_USERID_CRITERIA").toString(),awfMessagesList);
							logger.debug("Existing reviewers are:" + existingReviewers);

							// Remove existing reviewers to avoid duplicate user error
							if (!existingReviewers.isEmpty()) {
								AWF.removeReviewers(status, existingReviewers, null, null, "");
							}
							
							if (!approvers.isEmpty()) {
								
									// Get list of approvers who doesnt have 'A9 Approver - AWF' role
									reviewersWithoutRequiredRoles = GenericUtilities.getUsersWithoutRole(session, approvers,
											awfMessagesList.get("AWF_APPROVER_ROLE").toString());
									logger.debug("Approvers without A9 Approver - AWF  role:" + reviewersWithoutRequiredRoles);

									// Remove approvers who doesnt have 'A9 Approver - AWF' role from final set of
									// approvers
									if (reviewersWithoutRequiredRoles.size() > 0) {
										approvers.removeAll(reviewersWithoutRequiredRoles);
										logger.debug(
												"Approvers after removing users without A9 Approver - AWF are:" + approvers);
										result = result+String.format(awfMessagesList.get("REMOVED_USERS_WITHOUT_PRIVILEGES").toString(), reviewersWithoutRequiredRoles);
										

									}
									if (!approvers.isEmpty()) {
										
										// Add Approvers at Implement-Review state
										AWF.addReviewers(status, approvers, null, null, true,
												String.format(awfMessagesList.get("APPROVERS_ADDED").toString(),approvers,
														status.toString()));
										result = result + String.format(awfMessagesList.get("APPROVERS_ADDED").toString(),approvers,
												status.toString());
									}
								

							}
							// If no approvers are selected at Implement-Review,display below message in
							// history tab
							else {
								result = result + String.format(awfMessagesList.get("APPROVERS_NOT_SELECTED").toString(),status.toString());
							}
						}

					} **/
					else {
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

		return new EventActionResult(eventInfo, actionResult);
	}

	/**
	 * This method returns the list of existing reviewers at a status 
	 * @param AWF
	 * @param status
	 * @param reviewerType
	 * @param session
	 * @param criteria
	 * @return
	 * @throws APIException
	 */
	private HashSet<IUser> getExistingReviewers(HashSet<IUser> reviewers, IChange AWF, IStatus status,
			int reviewerType, IAgileSession session, String criteria,HashMap<Object, Object> awfMessagesList) throws APIException {

		// Get Existing reviewers based on state
		ISignoffReviewer[] existingReviewersArray = AWF.getReviewers(status, reviewerType);
		logger.debug("Existing Reviewers Array Size:" + existingReviewersArray.length);
		HashSet<IUser> existingReviewers = new HashSet<IUser>();

		if (existingReviewersArray.length > 0) {

			int i = 0;
			IDataObject existingReviewer = null;
			IUser existingReviewerUser = null;

			// Iterate through the existing reviewers array
			while (i < existingReviewersArray.length) {
				// Fetch the existing reviewer
				existingReviewer = existingReviewersArray[i].getReviewer();
				logger.debug("Existing Reviewer Data Object is:" + existingReviewer);

				if (existingReviewer != null) {
					// Fetch agile user
					existingReviewerUser = GenericUtilities.getAgileUser(session, existingReviewer.toString(),
							criteria);
					logger.debug("Existing reviewer Agile User is :" + existingReviewerUser);

					// remove the existing reviewer
					if (existingReviewerUser != null && status != null) {
						if (status.toString().equalsIgnoreCase(awfMessagesList.get("AWF_REVIEW_STATUS").toString())) {
							if (reviewers.contains(existingReviewerUser)) {
								existingReviewers.add(existingReviewerUser);
							}
						} else {
							existingReviewers.add(existingReviewerUser);
						}

					}
				}
				i++;
			}
		}

		return existingReviewers;
	}

}
