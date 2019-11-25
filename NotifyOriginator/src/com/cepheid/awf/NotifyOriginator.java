package com.cepheid.awf;

import java.util.HashMap;
import java.util.HashSet;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.INode;
import com.agile.api.IUser;
import com.agile.px.ActionResult;
import com.agile.px.EventActionResult;
import com.agile.px.EventConstants;
import com.agile.px.IEventAction;
import com.agile.px.IEventInfo;
import com.agile.px.ISignOffEventInfo;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya
 * Notify Originator of AWF once all approvals are done at
 *         Review
 *
 */
public class NotifyOriginator implements IEventAction {

	static Logger logger = Logger.getLogger(NotifyOriginator.class);
	ActionResult actionResult = new ActionResult();
	public static String awfMessagesListName = "AWFMessagesList";

	@SuppressWarnings("unchecked")
	@Override
	public EventActionResult doAction(IAgileSession session, INode arg1, IEventInfo eventInfo) {

		try {
			String result = "";
			
			// Initialize Logger
			GenericUtilities.initializeLogger(session);

			// get Agile List Values
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);

			// Get Event Information and AWF object
			ISignOffEventInfo signOffEventInfo = (ISignOffEventInfo) eventInfo;
			IChange awf = (IChange) signOffEventInfo.getDataObject();
			logger.debug("Awf is:" + awf);

			if (awf != null) {

				// Get Status of AWF
				if (awf.getStatus() != null) {

					logger.debug("Status is:" + awf.getStatus());

					// If Event Type is POST
					if (eventInfo.getEventTriggerType() == EventConstants.EVENT_TRIGGER_POST) {

						// If status is Review
						if (awf.getStatus().toString()
								.equalsIgnoreCase(awfMessagesList.get("AWF_REVIEW_STATUS").toString())) {

							// Get pending Signoff details
							HashMap<Object, Object> pendingSignOffDetails = GenericUtilities.getPendingSignOffDetails(
									awf, awfMessagesList, awfMessagesList.get("AWF_REVIEW_STATUS").toString());
							logger.debug("Pending SignOff Details are:" + pendingSignOffDetails);

							// If all approvals are done,notify Originator
							if ((boolean) pendingSignOffDetails.get("approvalPending") == false
									&& (int) pendingSignOffDetails
											.get("totalNumOfApprovers") == (int) pendingSignOffDetails
													.get("numOfApprovalsDone")) {

								// Get Originator attribute value on AWF
								String originator = GenericUtilities.getSingleListAttributeValue(awf,
										awfMessagesList.get("ORIGINATOR_ATTRID").toString());
								logger.debug("Originator is:" + originator);

								if (originator != null && !originator.equals("")) {

									// Get Originator User
									IUser originatorUser = GenericUtilities.getAgileUser(session, originator, awfMessagesList.get("USER_WITH_USERID_CRITERIA").toString());
									logger.debug("Originator User is:" + originatorUser);

									// Add Originator to list
									if (originatorUser != null) {

										HashSet<IUser> originatorList = new HashSet<IUser>();
										originatorList.add(originatorUser);
										logger.debug("Originator List Contains:" + originatorList);

										// Send Notification
										if (!originatorList.isEmpty()) {
											session.sendNotification(awf,
													awfMessagesList.get("NOTIFY_ORIGINATOR_TEMPLATE").toString(),
													originatorList, false, "");
											logger.debug("Notification Sent");
											result = result+awfMessagesList.get("SUCCESS").toString();

										}

									}

								}

							}
							//Else there are pending approvals
							else {
								HashSet<String> pendingApprovers = new HashSet<String>();
								pendingApprovers = (HashSet<String>) pendingSignOffDetails.get("pendingApprovers");
								logger.debug("Pending approvers are:" + pendingApprovers);
								
								if(pendingApprovers.size()>0) {
									result = result+String.format(awfMessagesList.get("PENDING_APPROVAL").toString(), pendingApprovers);
								}
							}

						}else {
							result = String.format(awfMessagesList.get("INVALID_STATUS").toString(), awfMessagesList.get("AWF_REVIEW_STATUS").toString());

						}
					}
				}

			}

			actionResult = new ActionResult(ActionResult.STRING,
					result);
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

}
