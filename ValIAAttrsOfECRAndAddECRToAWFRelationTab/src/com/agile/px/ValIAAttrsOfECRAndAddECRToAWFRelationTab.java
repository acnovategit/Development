package com.agile.px;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.INode;
import com.agile.api.ITable;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya
 * Pre Event: This PX displays a exception message to user if any Impact assessment attributes are not filled on eCR during
 *  Status change from Pending to review 
 * Post Event: This PX adds the eCR to the relationship tab of AWF during status change from Pending to Review
 * 
 *
 */
public class ValIAAttrsOfECRAndAddECRToAWFRelationTab implements IEventAction {

	static Logger logger = Logger.getLogger(ValIAAttrsOfECRAndAddECRToAWFRelationTab.class);
	ActionResult actionResult = new ActionResult();

	public static String genericMessagesListName = "GenericMessagesList";
	public static String attributesMappingOnECRAndAWFListName = "ECRAWFAttributeIDsMappingList";
	public static String awfMessagesListName = "AWFMessagesList";

	@Override
	public EventActionResult doAction(IAgileSession session, INode arg1, IEventInfo eventInfo) {

		try {
			// Initialize logger
			GenericUtilities.initializeLogger(session);

			HashMap<Object, Object> genericMessagesList = new HashMap<Object, Object>();
			HashMap<Object, Object> attributesMappingOnECRAndAWFList = new HashMap<Object, Object>();
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();

			IWFChangeStatusEventInfo info = (IWFChangeStatusEventInfo) eventInfo;

			// Get Agile List Values
			genericMessagesList = GenericUtilities.getAgileListValues(session, genericMessagesListName);
			attributesMappingOnECRAndAWFList = GenericUtilities.getAgileListValues(session,
					attributesMappingOnECRAndAWFListName);
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);

			// Get AWF Object Number
			IChange AWF = (IChange) info.getDataObject();
			logger.debug("AWF Object is" + AWF);

			if (AWF != null) {

				// Get ECR Object on AWF
				String strECR = GenericUtilities.getSingleListAttributeValue(AWF,
						awfMessagesList.get("ECR_ATTRID").toString());
				logger.debug("ECR is:" + strECR);
				IChange eCR = null;
				if (strECR != null) {
					eCR = (IChange) session.getObject(IChange.OBJECT_TYPE, strECR);
					logger.debug("ECR is:" + eCR);
				}

				if (eCR != null) {

					if (eventInfo.getEventTriggerType() == EventConstants.EVENT_TRIGGER_PRE) {

						// Get eCR Impact assessment attribute Values
						HashMap<Object, Object> eCRAttrValues = new HashMap<Object, Object>();
						eCRAttrValues = GenericUtilities.getIAAttributeValues(eCR, attributesMappingOnECRAndAWFList);
						logger.debug("eCRAttrValues are:" + eCRAttrValues);

						int countOfEmptyAttrs = 0;
						countOfEmptyAttrs = GenericUtilities.getCountOfIAAttributes(
								genericMessagesList.get("NULL").toString(), eCRAttrValues, session);
						logger.debug("count of empty Attributes is" + countOfEmptyAttrs);

						if (countOfEmptyAttrs > 0) {
							actionResult = new ActionResult(ActionResult.EXCEPTION,
									new Exception(awfMessagesList.get("ECRVALUES_NOT_FILLED1").toString() + " "
											+ eCR.toString() + "."
											+ awfMessagesList.get("ECRVALUES_NOT_FILLED2").toString()));
						} else {
							actionResult = new ActionResult(ActionResult.STRING,
									genericMessagesList.get("VALIDATION_SUCCESS").toString());
						}
					} else {
						// Add ECR to relationship tab of AWF
						ITable relationshipTab = AWF.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
						@SuppressWarnings("unchecked")
						Iterator<IChange> it = relationshipTab.getReferentIterator();
						IChange change = null;
						HashSet<IChange> relationObjects = new HashSet<IChange>();
						while (it.hasNext()) {
							change = (IChange) it.next();
							relationObjects.add(change);

						}
						if (relationObjects.contains(eCR)) {
							actionResult = new ActionResult(ActionResult.STRING,
									awfMessagesList.get("ECR_ALREADY_ADDED_TO_AWF").toString());

						} else {
							relationshipTab.createRow(eCR);
							actionResult = new ActionResult(ActionResult.STRING,
									awfMessagesList.get("ECR_ADDED_TO_AWF").toString());
						}
					}

				}
			}

		}

		catch (APIException e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to" + e.getMessage());
		} catch (Exception e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to" + e.getMessage());
		}
		return new EventActionResult(eventInfo, actionResult);
	}

}
