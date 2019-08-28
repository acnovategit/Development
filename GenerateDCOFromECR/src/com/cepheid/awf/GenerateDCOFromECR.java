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
 * -copies Description and Impact/Risk/Justification from eCR to DCO,
 * -adds DCO to the relationship tab of ECR
 *
 */

public class GenerateDCOFromECR implements ICustomAction {

	static Logger logger = Logger.getLogger(GenerateDCOFromECR.class);
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
				String nextNumber = GenericUtilities.getNextAutoNumber(session,ecrMessagesList.get("DCO_SUBCLASS_NAME").toString(),
						ecrMessagesList.get("DCO_AUTO_NUMBER").toString());
				logger.debug("Next Autonmber is:" + nextNumber);

				// Create DCO Object
				IChange dco = (IChange) session.createObject(ecrMessagesList.get("DCO_SUBCLASS_NAME").toString(),
						nextNumber);
				logger.debug("DCO is:" + dco);

				if (dco != null) {

					// set DCO Workflow on workflow attribute of DCO
					dco.setValue(Integer.parseInt(ecrMessagesList.get("DCO_VWF_WORKFLOW_ATTRID").toString()),
							ecrMessagesList.get("DCO_WORKFLOW_NAME").toString());

					// Get Description from eCR and set on DCO
					String descriptionOfECR = (String) eCR
							.getValue(Integer.parseInt(ecrMessagesList.get("DESCRIPTION_ATTRID").toString()));
					logger.debug("Descripton of ECR:" + descriptionOfECR);
					if (descriptionOfECR != null && !descriptionOfECR.equals("")) {
						dco.setValue(Integer.parseInt(ecrMessagesList.get("DESCRIPTION_ATTRID").toString()),
								descriptionOfECR);
					}

					// Get Impact/Risk/Justification from eCR and set on DCO
					String impact = (String) eCR
							.getValue(Integer.parseInt(ecrMessagesList.get("IMPACT_ATTRID").toString()));
					logger.debug("Impact/Risk/Justification of ECR:" + impact);
					if (impact != null && !impact.equals("")) {
						dco.setValue(Integer.parseInt(ecrMessagesList.get("IMPACT_ATTRID").toString()), impact);
					}
					// Add dco to relationship tab of ECR
					ITable relationshipTab = eCR.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
					if (relationshipTab != null) {
						relationshipTab.createRow(dco);
						result = dco.toString() + " " + ecrMessagesList.get("OBJ_CREATED_ADDED_RELTAB").toString() + " "
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
