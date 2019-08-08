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
import com.agile.util.CommonUtil;

/**
 * 
 * @author Supriya Varada
 * This PX displays a exception message to user if any Impact assessment attributes are not filled on eCR during
 *  Change Status for workflow Pre event 
 * This PX adds the eCR to the relationship tab of AWF during  Change Status for workflow Post event 
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
			//Initialize logger
			CommonUtil.initAppLogger(ValIAAttrsOfECRAndAddECRToAWFRelationTab.class, session);

			HashMap<Object, Object> genericMessagesList = new HashMap<Object, Object>();
			HashMap<Object, Object> attributesMappingOnECRAndAWFList = new HashMap<Object, Object>();
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();

			if (eventInfo.getEventType() == EventConstants.EVENT_CHANGE_STATUS_FOR_WORKFLOW) {

				IWFChangeStatusEventInfo info = (IWFChangeStatusEventInfo) eventInfo;

				// Load List Values
				genericMessagesList = CommonUtil.loadListValues(session, genericMessagesListName);
				attributesMappingOnECRAndAWFList = CommonUtil.loadListValues(session,
						attributesMappingOnECRAndAWFListName);
				awfMessagesList = CommonUtil.loadListValues(session, awfMessagesListName);

				// Get AWF Object Number
				IChange AWF = (IChange) info.getDataObject();
				logger.debug("AWF Object is" + AWF);

				if (AWF != null) {

					// Get ECR Object on AWF
					String strECR = CommonUtil.getSingleListValue(AWF, awfMessagesList.get("ECR_ATTRID").toString());
					logger.debug("ECR is:" + strECR);
					IChange eCR = null;
					if (strECR != null) {
						eCR = (IChange) session.getObject(IChange.OBJECT_TYPE, strECR);
						logger.debug("ECR is:" + eCR);
					}

					if (eCR != null) {

						if (eventInfo.getEventTriggerType() == EventConstants.EVENT_TRIGGER_PRE) {

							HashMap<Object, Object> eCRAttrValues = new HashMap<Object, Object>();
							String attrValue = "";

							//Get eCR attribute Ids
							for (Object key : attributesMappingOnECRAndAWFList.keySet()) {
								if (attributesMappingOnECRAndAWFList.get(key) != null) {
									attrValue = CommonUtil.getSingleListValue(eCR, key.toString());
									eCRAttrValues.put(key, attrValue);
								}
							}
							logger.debug("eCRAttrValues are:" + eCRAttrValues);

							int countOfEmptyAttrs = 0;
							String value = null;

							for (Object key : eCRAttrValues.keySet()) {
								value = (String) eCRAttrValues.get(key);
								if (value == null || value.equals("")) {
										countOfEmptyAttrs++;
								}
							}

							logger.debug("count of empty Attributes is" + countOfEmptyAttrs);
							if (countOfEmptyAttrs > 0) {
								actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(
										awfMessagesList.get("ECRVALUES_NOT_FILLED1").toString() + " " + eCR.toString()+"."
										+awfMessagesList.get("ECRVALUES_NOT_FILLED2").toString()));
							}  else {
								actionResult = new ActionResult(ActionResult.STRING,
										genericMessagesList.get("VALIDATION_SUCCESS").toString());
							}
						}else if(eventInfo.getEventTriggerType() == EventConstants.EVENT_TRIGGER_POST) {
							// Add ECR to relationship tab of AWF
							ITable relationshipTab = AWF.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
							@SuppressWarnings("unchecked")
							Iterator<IChange> it = relationshipTab.getReferentIterator();
							IChange change = null;
							HashSet<IChange> relationObjects = new HashSet<IChange>();
							while(it.hasNext()) {
								change = (IChange) it.next();
								relationObjects.add(change);
								
							}
							if(relationObjects.contains(eCR)) {
								actionResult = new ActionResult(ActionResult.STRING,awfMessagesList.get("ECR_ALREADY_ADDED_TO_AWF").toString());
								
							}else {
								relationshipTab.createRow(eCR);
								actionResult = new ActionResult(ActionResult.STRING,awfMessagesList.get("ECR_ADDED_TO_AWF").toString());
							}
						}

					} else {
						actionResult = new ActionResult(ActionResult.STRING,
								genericMessagesList.get("INVALID_EVENT_TRIGGER_TYPE").toString());
					}

				} else {
					actionResult = new ActionResult(ActionResult.STRING,
							genericMessagesList.get("INVALID_EVENT_TYPE").toString());
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
