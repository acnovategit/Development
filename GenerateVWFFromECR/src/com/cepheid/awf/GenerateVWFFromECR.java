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
 * -creates a VWF Object,
 * -sets VWF-Validation Workflow on VWF object, 
 * -copies Description and Impact/Risk/Justification from eCR to VWF,
 * -adds VWF to the relationship tab of ECR
 *
 */

public class GenerateVWFFromECR implements ICustomAction {

	static Logger logger = Logger.getLogger(GenerateVWFFromECR.class);
	public static String ecrMessagesListName = "ECRMessagesList";

	@Override
	public ActionResult doAction(IAgileSession session, INode arg1, IDataObject dataObject) {
		String result = "";
		try {
			// Initialize logger
			GenericUtilities.initializeLogger(session);

			// Get Agile list values
			HashMap<Object, Object> ecrMessagesList = new HashMap<Object, Object>();
			ecrMessagesList = GenericUtilities.getAgileListValues(session, ecrMessagesListName);

			// Get eCR Object
			IChange eCR = (IChange) dataObject;
			logger.debug("ECR is:" + eCR);

			if (eCR != null) {
				
				// Get Next number from Auto Number
				String nextNumber = GenericUtilities.getNextAutoNumber(session,ecrMessagesList.get("VWF_SUBCLASS_NAME").toString(),
						ecrMessagesList.get("VWF_AUTO_NUMBER").toString());
				logger.debug("Next Autonmber is:" + nextNumber);
				
				IChange vwf = (IChange) session.createObject(ecrMessagesList.get("VWF_SUBCLASS_NAME").toString(),
						nextNumber);
				logger.debug("VWF is:" + vwf);

				if (vwf != null) {

					// set VWF Workflow on workflow attribute of VWF
					vwf.setValue(Integer.parseInt(ecrMessagesList.get("DCO_VWF_WORKFLOW_ATTRID").toString()),
							ecrMessagesList.get("VWF_WORKFLOW_NAME").toString());

					// Get Description from eCR and set on VWF
					String descriptionOfECR = (String) eCR
							.getValue(Integer.parseInt(ecrMessagesList.get("DESCRIPTION_ATTRID").toString()));
					logger.debug("Descripton of ECR:" + descriptionOfECR);
					if (descriptionOfECR != null && !descriptionOfECR.equals("")) {
						vwf.setValue(Integer.parseInt(ecrMessagesList.get("DESCRIPTION_ATTRID").toString()),
								descriptionOfECR);
					}

					// Get Impact/Risk/Justification from eCR and set on VWF
					String impact = (String) eCR
							.getValue(Integer.parseInt(ecrMessagesList.get("IMPACT_ATTRID").toString()));
					logger.debug("Impact/Risk/Justification of ECR:" + impact);
					if (impact != null && !impact.equals("")) {
						vwf.setValue(Integer.parseInt(ecrMessagesList.get("IMPACT_ATTRID").toString()), impact);
					}

					// Add vwf to relationship tab of ECR
					ITable relationshipTab = eCR.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
					if (relationshipTab != null) {
						relationshipTab.createRow(vwf);
						result = vwf.toString() + " " + ecrMessagesList.get("OBJ_CREATED_ADDED_RELTAB").toString() + " "
								+ eCR.toString();
					}

				} else {
					result = ecrMessagesList.get("OBJ_CREATION_FAILED").toString();
				}

			}

		} catch (APIException e) {
			e.printStackTrace();
			logger.error("Failed due to" + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Failed due to" + e.getMessage());
		}

		return new ActionResult(ActionResult.STRING, result);
	}

}
