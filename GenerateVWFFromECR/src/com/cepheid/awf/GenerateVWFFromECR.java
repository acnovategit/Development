package com.cepheid.awf;

import java.util.HashMap;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.INode;
import com.agile.api.IStatus;
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
 * -copies Description,change analyst and Impact/Risk/Justification from eCR to VWF,
 * -adds VWF to the relationship tab of ECR
 * -VWF creation is possible only when eCR is in Unassigned/Pending/Started
 * -eCR Workflow is set on workflow attribute of eCR if workflow is not assigned
 * -eCR is moved from Pending to Started status after VWF creation
 */

public class GenerateVWFFromECR implements ICustomAction {

	static Logger logger = Logger.getLogger(GenerateVWFFromECR.class);
	public static String awfMessagesListName = "AWFMessagesList";
	public static String eCRToVWFAttributeIdsMappingListName = "ECRVWFAttributeIDsMappingList";

	@Override
	public ActionResult doAction(IAgileSession session, INode arg1, IDataObject dataObject) {

		ActionResult actionResult = null;
		String result = "";

		try {
			// Initialize logger
			GenericUtilities.initializeLogger(session);

			// Get Agile list values
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);
			HashMap<Object, Object> eCRToVWFAttributeIdsMappingList = new HashMap<Object, Object>();
			eCRToVWFAttributeIdsMappingList = GenericUtilities.getAgileListValues(session,
					eCRToVWFAttributeIdsMappingListName);

			// Get eCR Object
			IChange eCR = (IChange) dataObject;
			logger.debug("ECR is:" + eCR);

			if (eCR != null) {

				// Get eCR status
				IStatus status = eCR.getStatus();
				logger.debug("eCR Status is:" + status);

				String sStatus = "";
				if (status != null) {
					sStatus = status.toString();
				}

				// Get eCR Workflow
				String eCRWorkflow = GenericUtilities.getSingleListAttributeValue(eCR,
						awfMessagesList.get("WORKFLOW_ATTRID").toString());
				logger.debug("Workflow assigned on eCR:" + eCRWorkflow);

				// If workflow is not Unassigned,Status is Unassigned
				if (eCRWorkflow == null || eCRWorkflow.equals("")) {
					sStatus = awfMessagesList.get("UNASSIGNED_STATUS").toString();
				}
				logger.debug("eCR sStatus is:" + sStatus);

				if (sStatus != null && !sStatus.equals("")) {

					// Generate VWF only when eCR is in Unassigned/Pending/Started States
					if (sStatus.equalsIgnoreCase(awfMessagesList.get("UNASSIGNED_STATUS").toString())
							|| sStatus.equalsIgnoreCase(awfMessagesList.get("PENDING_STATUS").toString())
							|| sStatus.equalsIgnoreCase(awfMessagesList.get("ECR_STARTED_STATUS").toString())) {

						// Get Next number from Auto Number
						String nextNumber = GenericUtilities.getNextAutoNumber(session,
								awfMessagesList.get("VWF_SUBCLASS_NAME").toString(),
								awfMessagesList.get("VWF_AUTO_NUMBER").toString());
						logger.debug("Next Number is:" + nextNumber);

						// copy common attribute values from eCR to VWF
						HashMap<Object, Object> map = new HashMap<Object, Object>();
						map = GenericUtilities.copyAttrValuesFromSourceObjToTargetObj(eCR,
								eCRToVWFAttributeIdsMappingList, session,
								awfMessagesList.get("ECR_SUBCLASS_NAME").toString(),
								awfMessagesList.get("VWF_SUBCLASS_NAME").toString());

						map.put(Integer.parseInt(awfMessagesList.get("NUM_ATTRID").toString()), nextNumber);
						logger.debug("Map contains:" + map);

						// Create VWF Object
						IChange vwf = (IChange) session
								.createObject(awfMessagesList.get("VWF_SUBCLASS_NAME").toString(), map);
						logger.debug("VWF is:" + vwf);

						if (vwf != null) {

							// set VWF Workflow on workflow attribute of VWF
							vwf.setValue(Integer.parseInt(awfMessagesList.get("WORKFLOW_ATTRID").toString()),
									awfMessagesList.get("VWF_WORKFLOW_NAME").toString());

							// Add vwf to relationship tab of ECR
							ITable relationshipTab = eCR.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
							if (relationshipTab != null) {
								relationshipTab.createRow(vwf);
								result = String.format(awfMessagesList.get("OBJ_CREATED_ADDED_RELTAB").toString(),
										vwf.toString(), eCR.toString());
							}

							//If workflow is not assigned,assign eCR Workflow
							if(eCRWorkflow==null || eCRWorkflow.equals("")) {
								eCR.setValue(Integer.parseInt(awfMessagesList.get("WORKFLOW_ATTRID").toString()), awfMessagesList.get("ECR_WORKFLOW_NAME").toString());
							}
							
							//Get eCR status 
							status = eCR.getStatus();
							logger.debug("eCR Status is:"+status);
							
							if(status!=null) {
								//If eCR is in Pending status, autopromote eCR from Pending to Started status
								if (status.toString().equalsIgnoreCase(awfMessagesList.get("PENDING_STATUS").toString())) {
									logger.debug("Autopromoting " + eCR + " to Started Status");
									eCR.changeStatus(
											GenericUtilities.getStatus(awfMessagesList.get("ECR_STARTED_STATUS").toString(),
													eCR.getWorkflow()),
											false, "", false, false, null, null, null, null, false);
									logger.debug(eCR + " autopromoted to Started Status");
								}
							}
							
							
						
						} else {
							result = awfMessagesList.get("OBJ_CREATION_FAILED").toString();
						}
						actionResult = new ActionResult(ActionResult.STRING, result);
					}
					// Throw an exception when user tries to generate a VWF if eCR is not in
					// Unassigned/Pending/Started states
					else {
						actionResult = new ActionResult(ActionResult.EXCEPTION,
								new Exception(String.format(awfMessagesList.get("OBJ_CREATION_NOT_POSSIBLE").toString(),
										awfMessagesList.get("VWF_SUBCLASS_NAME").toString())));
					}

				}
			}
		} catch (APIException e) {
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		}

		return actionResult;
	}

}
