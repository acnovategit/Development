package com.cepheid.awf;

import java.util.HashMap;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.INode;
import com.agile.api.ITable;
import com.agile.px.ActionResult;
import com.agile.px.ICustomAction;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya 
 * This PX 
 * -creates a DCO Object,
 * -sets DCO Workflow on DCO object,
 * -copies Description,change Analyst and Impact/Risk/Justification from eCR to DCO,
 * -adds DCO to the relationship tab of ECR
 *
 */

public class GenerateDCOFromECR implements ICustomAction {

	static Logger logger = Logger.getLogger(GenerateDCOFromECR.class);
	public static String awfMessagesListName = "AWFMessagesList";
	public static String eCRToDCOAttributeIdsMappingListName = "ECRDCOAttributeIDsMappingList";

	@Override
	public ActionResult doAction(IAgileSession session, INode arg1, IDataObject dataObject) {
		String result = "";
		try {

			// Initialize logger
			GenericUtilities.initializeLogger(session);

			// Get Agile list values
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);
			HashMap<Object, Object> eCRToDCOAttributeIdsMappingList = new HashMap<Object, Object>();
			eCRToDCOAttributeIdsMappingList = GenericUtilities.getAgileListValues(session, eCRToDCOAttributeIdsMappingListName);

			// Get eCR Object
			IChange eCR = (IChange) dataObject;
			logger.debug("ECR is:" + eCR);

			if (eCR != null) {
				// Get Next number from Auto Number
				String nextNumber = GenericUtilities.getNextAutoNumber(session,awfMessagesList.get("DCO_SUBCLASS_NAME").toString(),
						awfMessagesList.get("DCO_AUTO_NUMBER").toString());
				logger.debug("Next Autonmber is:" + nextNumber);
				
				//copy common attribute values from eCR to DCO
				HashMap<Object, Object> map = new HashMap<Object, Object>();
				map = GenericUtilities.copyAttrValuesFromSourceObjToTargetObj(eCR, eCRToDCOAttributeIdsMappingList, session, awfMessagesList.get("ECR_SUBCLASS_NAME").toString(),
						awfMessagesList.get("DCO_SUBCLASS_NAME").toString());
				
				map.put(Integer.parseInt(awfMessagesList.get("NUM_ATTRID").toString()), nextNumber);
				logger.debug("Map contains:" + map);

				// Create DCO Object
				IChange dco = (IChange) session.createObject(awfMessagesList.get("DCO_SUBCLASS_NAME").toString(),
						map);
				logger.debug("DCO is:" + dco);

				if (dco != null) {

					// set DCO Workflow on workflow attribute of DCO
					dco.setValue(Integer.parseInt(awfMessagesList.get("WORKFLOW_ATTRID").toString()),
							awfMessagesList.get("DCO_WORKFLOW_NAME").toString());

					// Add dco to relationship tab of ECR
					ITable relationshipTab = eCR.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
					if (relationshipTab != null) {
						relationshipTab.createRow(dco);
						result = String.format(awfMessagesList.get("OBJ_CREATED_ADDED_RELTAB").toString(), dco.toString(), eCR.toString());
					}

				} else {
					result = awfMessagesList.get("OBJ_CREATION_FAILED").toString();
				}

			}

		} catch (APIException e) {
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		}

		return new ActionResult(ActionResult.STRING, result);
	}

}
