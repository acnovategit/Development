package com.cepheid.awf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.INode;
import com.agile.px.ActionResult;
import com.agile.px.EventActionResult;
import com.agile.px.IEventAction;
import com.agile.px.IEventDirtyCell;
import com.agile.px.IEventInfo;
import com.agile.px.IUpdateTitleBlockEventInfo;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya
 *  If any Impact assessment attribute is modified and
 *   if all the impact assessment attributes are filled as No, this PX sets Change classification to - Minor
 *   if any Impact assessment attributes is filled as Yes, this PX sets Change classification to -Major 
 *   if any Impact assessment attribute is not filled, this PX sets Change classification to -NULL 
 * 
 */

public class UpdateChangeClassificationOnECR implements IEventAction {

	static Logger logger = Logger.getLogger(UpdateChangeClassificationOnECR.class);
	ActionResult actionResult = new ActionResult();

	public static String eCRToAWFAttributeIdsMappingListName = "ECRAWFAttributeIDsMappingList";
	public static String awfMessagesListName = "AWFMessagesList";

	@Override
	public EventActionResult doAction(IAgileSession session, INode arg1, IEventInfo eventInfo) {
		try {
			// Initialize logger
			GenericUtilities.initializeLogger(session);

			HashMap<Object, Object> eCRToAWFAttributeIdsMappingList = new HashMap<Object, Object>();
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();
			
			// Get Agile List Values
			eCRToAWFAttributeIdsMappingList = GenericUtilities.getAgileListValues(session,
					eCRToAWFAttributeIdsMappingListName);
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);

			IUpdateTitleBlockEventInfo info = (IUpdateTitleBlockEventInfo) eventInfo;

			// Get eCR Object Number
			IChange eCR = (IChange) info.getDataObject();
			logger.debug("ECR Object is:" + eCR);

			if (eCR != null) {
				String result = "";

				// Get Change Impact assessment attribute values
				HashMap<Object, Object> impactAssessmentAttributeValues = new HashMap<Object, Object>();
				impactAssessmentAttributeValues = GenericUtilities.getImpactAssessmentAttrValues(session,eCR, eCRToAWFAttributeIdsMappingList);
				logger.debug("Impact Assessment Attribute Values are:" + impactAssessmentAttributeValues);

				// Get Assessment attribute IDs into arraylist
				List<Integer> assessmentAttrIds = new ArrayList<Integer>();
				for (Object key : impactAssessmentAttributeValues.keySet()) {
					assessmentAttrIds.add(Integer.parseInt(key.toString()));
				}
				logger.debug("Impact Assesment attribute ids are:" + assessmentAttrIds);

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

					countOfAttrsWithYes = GenericUtilities.getCountOfImpactAssessmentAttributes(
							awfMessagesList.get("YES").toString(), impactAssessmentAttributeValues, session);
					countOfEmptyAttrs = GenericUtilities.getCountOfImpactAssessmentAttributes(
							awfMessagesList.get("NULL").toString(), impactAssessmentAttributeValues, session);

					logger.debug("count of empty Attributes is:" + countOfEmptyAttrs);
					logger.debug("count of yes Attributes is:" + countOfAttrsWithYes);

					if (countOfEmptyAttrs != 0) {
						eCR.setValue(Integer.parseInt(awfMessagesList.get("CHANGE_CLASSIFICATION_ATTRID").toString()),
								null);
						result = result + awfMessagesList.get("CLASSIFICATION_SET_TO_NULL").toString();
					} else {
						if (countOfAttrsWithYes >= 1) {
							eCR.setValue(
									Integer.parseInt(awfMessagesList.get("CHANGE_CLASSIFICATION_ATTRID").toString()),
									awfMessagesList.get("MAJOR_LIST_VALUE").toString());
							result = result + awfMessagesList.get("CLASSIFICATION_SET_TO_MAJOR").toString();
						} else {
							eCR.setValue(
									Integer.parseInt(awfMessagesList.get("CHANGE_CLASSIFICATION_ATTRID").toString()),
									awfMessagesList.get("MINOR_LIST_VALUE").toString());
							result = result + awfMessagesList.get("CLASSIFICATION_SET_TO_MINOR").toString();
						}
					}

				} else {
					result = result + awfMessagesList.get("ASSMT_ATTRS_NOT_EDITED").toString();
				}
				logger.debug("Result is:" + result);
				actionResult = new ActionResult(ActionResult.STRING, result);

			}
		}

		catch (APIException e) {
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
