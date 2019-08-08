package com.agile.px;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.INode;
import com.agile.util.CommonUtil;

/**
 * 
 * @author Supriya Varada
 * If any Impact assessment attribute is modified and if all the Impact assessment attributes are filled, 
 * this PX sets Change classification to 
 *  - Minor if all Impact assessment attributes are filled as No,
 *  - Major if any Impact assessment attribute is filled as Yes.
 *  
 */

public class UpdateChangeClassificationOnECR implements IEventAction {

	static Logger logger = Logger.getLogger(UpdateChangeClassificationOnECR.class);
	ActionResult actionResult = new ActionResult();

	public static String genericMessagesListName = "GenericMessagesList";
	public static String attributesMappingOnECRAndAWFListName = "ECRAWFAttributeIDsMappingList";
	public static String ecrMessagesListName = "ECRMessagesList";

	@Override
	public EventActionResult doAction(IAgileSession session, INode arg1, IEventInfo eventInfo) {
		try {
			//Initialize logger
			CommonUtil.initAppLogger(UpdateChangeClassificationOnECR.class, session);

			HashMap<Object, Object> genericMessagesList = new HashMap<Object, Object>();
			HashMap<Object, Object> attributesMappingOnECRAndAWFList = new HashMap<Object, Object>();
			HashMap<Object, Object> ecrMessagesList = new HashMap<Object, Object>();

			// Load List Values
			genericMessagesList = CommonUtil.loadListValues(session, genericMessagesListName);
			attributesMappingOnECRAndAWFList = CommonUtil.loadListValues(session, attributesMappingOnECRAndAWFListName);
			ecrMessagesList = CommonUtil.loadListValues(session, ecrMessagesListName);

			if (eventInfo.getEventType() == EventConstants.EVENT_UPDATE_TITLE_BLOCK) {

				IUpdateTitleBlockEventInfo info = (IUpdateTitleBlockEventInfo) eventInfo;

				// Get ECR Object Number
				IChange eCR = (IChange) info.getDataObject();
				logger.debug("ECR Object is" + eCR);

				if (eCR != null) {

					HashMap<Object, Object> assessmentAttrValues = new HashMap<Object, Object>();
					String attrValue = "";
					List<Integer> assessmentAttrIds = new ArrayList<Integer>();

					// Get Change Impact assessment attribute Ids and values
					for (Object key : attributesMappingOnECRAndAWFList.keySet()) {
						if (attributesMappingOnECRAndAWFList.get(key) != null) {
							assessmentAttrIds.add(Integer.parseInt(key.toString()));
							attrValue = CommonUtil.getSingleListValue(eCR, key.toString());
							assessmentAttrValues.put(key, attrValue);
						}
					}
					logger.debug("Assesment attribute ids are:" + assessmentAttrIds);
					logger.debug("assessmentAttrValues are:" + assessmentAttrValues);

					if (eventInfo.getEventTriggerType() == EventConstants.EVENT_TRIGGER_POST) {
						String result = "";

						// Get Dirty attribute IDs
						int i = 0;
						IEventDirtyCell[] cells = info.getCells();
						int dirtyAttrId = 0;
						boolean flag = false;
						while (i < cells.length) {
							dirtyAttrId = cells[i].getAttributeId();
							if (assessmentAttrIds.contains(dirtyAttrId)) {
								flag = true;
								break;
							}
							i++;
						}
						logger.debug("Flag value is:" + flag);
						
						// If any assessment attribute is modified,
						// set Change Classification based on Impact Assessment attribute
						// values
						if (flag == true) {
							// Calculate the number of attributes which are empty and has yes value.
							int countOfEmptyAttrs = 0;
							int countOfAttrsWithYes = 0;
							String value = "";
							for (Object key : assessmentAttrValues.keySet()) {
								value = (String) assessmentAttrValues.get(key);
								if (value == null || value.equalsIgnoreCase("")) {
									countOfEmptyAttrs++;
								}

								if (value != null
										&& value.equalsIgnoreCase(genericMessagesList.get("YES").toString())) {
									countOfAttrsWithYes++;
								}

							}
							logger.debug("count of empty Attributes is" + countOfEmptyAttrs);
							logger.debug("count of yes Attributes is" + countOfAttrsWithYes);
							if (countOfEmptyAttrs == 0) {
								if (countOfAttrsWithYes >= 1) {
									eCR.setValue(
											Integer.parseInt(
													ecrMessagesList.get("CHANGE_CLASSIFICATION_ATTRID").toString()),
											ecrMessagesList.get("MAJOR_LIST_VALUE").toString());
									result = result + ecrMessagesList.get("CLASSIFICATION_SET_TO_MAJOR").toString();
								} else {
									eCR.setValue(
											Integer.parseInt(
													ecrMessagesList.get("CHANGE_CLASSIFICATION_ATTRID").toString()),
											ecrMessagesList.get("MINOR_LIST_VALUE").toString());
									result = result + ecrMessagesList.get("CLASSIFICATION_SET_TO_MINOR").toString();
								}
							} else {
								result = result + ecrMessagesList.get("CHG_CLASS_NOT_UPDATED").toString();
							}

						} else {
							result = result + ecrMessagesList.get("ASSMT_ATTRS_NOT_EDITED").toString();
						}
						logger.debug("Result is:" + result);
						actionResult = new ActionResult(ActionResult.STRING, result);

					}

					else {
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
