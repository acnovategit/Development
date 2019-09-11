package com.cepheid.awf;

import java.util.HashMap;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.INode;
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
 * During Pre event,If AWF is at Submit/Regulatory Affairs state and if any impact assessment is filled as Yes,validate below and throw exception if 
 * - Regulatory Notification Required? attribute is not filled
 * - Regulatory Notification Required? attribute is filled as No and Justification attribute is not filled
 * During Post event,If AWF is in Review status and 
 * - if all approvals and acknowledgements are done,immediately 
 * 		- autopromote AWF to Submit/Regulatory Affairs state if any Impact assessment attributes is filled as Yes (or)
		- autopromote AWF to Approve state if all Impact assessment attributes are filled as No
 * - if all approvals are completed and acknowledgers are pending,after 5th day
 * 		- autopromote AWF to Submit/Regulatory Affairs state if any Impact assessment attributes is filled as Yes (or)
		- autopromote AWF to Approve state if all Impact assessment attributes are filled as No
 * 		
 *
 */

public class AutoPromoteAWF implements IEventAction {

	static Logger logger = Logger.getLogger(AutoPromoteAWF.class);
	public static String eCRToAWFAttributeIdsMappingListName = "ECRAWFAttributeIDsMappingList";
	public static String awfMessagesListName = "AWFMessagesList";

	ActionResult actionResult = new ActionResult();

	@Override
	public EventActionResult doAction(IAgileSession session, INode arg1, IEventInfo eventInfo) {

		try {
			// Initialize Logger
			GenericUtilities.initializeLogger(session);

			// get Agile List Values
			HashMap<Object, Object> eCRToAWFAttributeIdsMappingList = new HashMap<Object, Object>();
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();

			eCRToAWFAttributeIdsMappingList = GenericUtilities.getAgileListValues(session,
					eCRToAWFAttributeIdsMappingListName);
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);

			// Get Event Information and AWF object
			ISignOffEventInfo signOffEventInfo = (ISignOffEventInfo) eventInfo;
			IChange awf = (IChange) signOffEventInfo.getDataObject();
			logger.debug("awf is:" + awf);

			if (awf != null) {

				// Get Impact assessment attribute values of AWF
				HashMap<Object, Object> impactAssessmentattrValues = new HashMap<Object, Object>();
				impactAssessmentattrValues = GenericUtilities.getImpactAssessmentAttrValues(session,awf, eCRToAWFAttributeIdsMappingList);
				logger.debug("Impact assessment attribute values of AWF are:" + impactAssessmentattrValues);

				// Get count of Impact assessment attribute values whose value is yes
				int countOfYesAttrs = 0;
				countOfYesAttrs = GenericUtilities.getCountOfImpactAssessmentAttributes(awfMessagesList.get("YES").toString(),
						impactAssessmentattrValues, session);
				logger.debug("count of Yes Attributes is:" + countOfYesAttrs);

				// Get Status of AWF
				if (awf.getStatus() != null) {

					logger.debug("Status is:" + awf.getStatus());

					// During Pre-Event Trigger,validate Regulatory Notification Required and
					// Justification attributes of AWF
					if (eventInfo.getEventTriggerType() == EventConstants.EVENT_TRIGGER_PRE) {

						// If status is Submit/Regulatory affairs
						if (awf.getStatus().toString()
								.equalsIgnoreCase(awfMessagesList.get("AWF_SUBMIT_RA_STATUS").toString())) {

							// If any Impact assessment is Yes
							if (countOfYesAttrs != 0) {

								// Get Regulatory Notification Required attribute
								String regNotificationReqd = GenericUtilities.getSingleListAttributeValue(awf,
										awfMessagesList.get("REG_NOTIFICATION_REQD_ATTRID").toString());
								logger.debug("Regulatory Notification required value is:" + regNotificationReqd);

								if (regNotificationReqd != null && !regNotificationReqd.equals("")) {

									// If Regulatory Notification Required attribute is filled as No,Justification
									// is mandatory
									if (regNotificationReqd
											.equalsIgnoreCase(awfMessagesList.get("NO").toString())) {

										// Get Justification
										String justification = (String) awf.getValue(Integer
												.parseInt(awfMessagesList.get("JUSTIFICATION_ATTRID").toString()));
										logger.debug("Justification value is :" + justification);

										// If justification is not filled,throw exception
										if (justification.equals("") && justification.length() == 0) {
											actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(
													awfMessagesList.get("JUSTIFICATION_NOT_FILLED_MSG").toString()));
										} else {
											actionResult = new ActionResult(ActionResult.STRING,
													awfMessagesList.get("VALIDATION_SUCCESS").toString());
										}
									}
									// If Regulatory Notification Required attribute is filled as Yes,Justification
									// is not mandatory
									else {
										actionResult = new ActionResult(ActionResult.STRING,
												awfMessagesList.get("VALIDATION_SUCCESS").toString());
									}

								}
								// If Regulatory Notification Required attribute is not filled,throw exception
								else {
									actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(
											awfMessagesList.get("REG_NOTIFICATION_NOT_FILLED_MSG").toString()));
								}

							}
							// If all Impact assessment is No,no need to validate
							else {
								actionResult = new ActionResult(ActionResult.STRING,
										awfMessagesList.get("VALIDATION_SUCCESS").toString());
							}

						}
						// If status is not Submit/Regulatory affairs,no need to validate
						else {
							actionResult = new ActionResult(ActionResult.STRING,
									String.format(awfMessagesList.get("INVALID_STATUS").toString(),awfMessagesList.get("AWF_SUBMIT_RA_STATUS").toString()));
						}
						logger.debug("Result:" + actionResult);

						// During Post-Event Trigger,autopromote AWF from Review to Submit/Regulatory
						// Affairs or Approve status based on Impact Assessment and approvals
					} else {
						String result = "";

						// If status is review
						if (awf.getStatus().toString()
								.equalsIgnoreCase(awfMessagesList.get("AWF_REVIEW_STATUS").toString())) {

							// Get pending Signoff details
							HashMap<Object, Object> pendingSignOffDetails = GenericUtilities
									.getPendingSignOffDetails(awf, awfMessagesList);
							logger.debug("Pending SignOff Details are:" + pendingSignOffDetails);

							// Get difference between the date of Signoff and Moved to review date
							int difference = GenericUtilities.getDifferenceBetweenDates(awf,
									awfMessagesList.get("TIME_ZONE").toString(),
									awfMessagesList.get("DATE_FORMAT").toString(),
									awfMessagesList.get("DATE_FORMAT_WITHOUT_TIMEZONE").toString(),
									awfMessagesList.get("REVIEW_DATE_ATTRID").toString());
							logger.debug("Difference is:" + difference);

							// If all approvals and acknowledgements are done,
							// immediately autopromote AWF to
							// Submit/Regulatory Affairs state if any Impact assessment attributes is filled
							// as Yes (or)
							// autopromote AWF to Approve state if all Impact assessment attributes are
							// filled as No
							if ((boolean) pendingSignOffDetails.get("approvalPending") == false
									&& (boolean) pendingSignOffDetails.get("acknowledgementPending") == false
									&& (int) pendingSignOffDetails
											.get("totalNumOfApprovers") == (int) pendingSignOffDetails
													.get("numOfApprovalsDone")
									&& (int) pendingSignOffDetails
											.get("totalNumOfAcknowledgers") == (int) pendingSignOffDetails
													.get("numOfAcknowledgementsDone")) {

								GenericUtilities.autoPromoteAWF(countOfYesAttrs, awf, awfMessagesList);
								result = awfMessagesList.get("SUCCESS").toString();
							}
							// If all approvals are completed, after 5 days 
							// immediately autopromote AWF to
							// Submit/Regulatory Affairs state if any Impact assessment attributes is filled
							// as Yes (or)
							// autopromote AWF to Approve state if all Impact assessment attributes are
							// filled as No though acknowledgement is pending

							else if (difference > Integer.parseInt(awfMessagesList.get("DURATION").toString()) && (boolean) pendingSignOffDetails.get("approvalPending") == false
									&& (int) pendingSignOffDetails
											.get("totalNumOfApprovers") == (int) pendingSignOffDetails
													.get("numOfApprovalsDone")
									) {

								GenericUtilities.autoPromoteAWF(countOfYesAttrs, awf, awfMessagesList);
								result = awfMessagesList.get("SUCCESS").toString();
							}
							// One or more approvals/acknowledgements are pending,hence awf is not
							// autopromoted.
							else {
								result = awfMessagesList.get("REVIEW_PENDING").toString();
							}

						}
						// If status is not Review,dont autopromote
						else {
							result = String.format(awfMessagesList.get("INVALID_STATUS").toString(), awfMessagesList.get("AWF_REVIEW_STATUS").toString());
						}
						actionResult = new ActionResult(ActionResult.STRING, result);
						logger.debug("Result:" + actionResult);
					}

				}
			}

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
