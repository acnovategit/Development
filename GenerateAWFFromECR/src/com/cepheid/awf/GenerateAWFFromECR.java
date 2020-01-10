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
 * This PX displays a message to user if any Impact assessment attribute is not filled on eCR while generating AWF.
 * If all Impact assessment attributes are filled,AWF is created and added to relationship tab of eCR. 
 * All Impact assessment attribute values,Description,Impact/Risk/Justification,change Analyst are copied from ECR to AWF.
 * AWFWorkflow is set on Workflow attribute of AWF
 * eCR Workflow is set on workflow attribute of eCR if workflow is not assigned
 * eCR is moved from Pending to Started status after AWF creation
 * AWF creation is possible only when eCR is in Unassigned/Pending/Started
 */

public class GenerateAWFFromECR implements ICustomAction {

	static Logger logger = Logger.getLogger(GenerateAWFFromECR.class);

	public static String eCRToAWFAttributeIdsMappingListName = "ECRAWFAttributeIDsMappingList";
	public static String awfMessagesListName = "AWFMessagesList";

	@Override
	public ActionResult doAction(IAgileSession session, INode arg1, IDataObject dataObject) {

		ActionResult actionResult = null;
		String result = "";

		HashMap<Object, Object> eCRToAWFAttributeIdsMappingList = new HashMap<Object, Object>();
		HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();

		try {
			// Initialize logger
			GenericUtilities.initializeLogger(session);

			// get Agile List Values
			eCRToAWFAttributeIdsMappingList = GenericUtilities.getAgileListValues(session,
					eCRToAWFAttributeIdsMappingListName);
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);

			// Get eCR Object
			IChange eCR = (IChange) dataObject;
			logger.debug("ECR is:" + eCR);

			if (eCR != null) {
				
				//Get eCR status
				IStatus status = eCR.getStatus();
				logger.debug("eCR Status is:"+status);
				
				String sStatus = "";
				if(status!=null) {
					sStatus = status.toString();
				}
				
				//Get eCR Workflow
				String eCRWorkflow = GenericUtilities.getSingleListAttributeValue(eCR, awfMessagesList.get("WORKFLOW_ATTRID").toString());
				logger.debug("Workflow assigned on eCR:"+eCRWorkflow);
				
				//If workflow is not Unassigned,Status is Unassigned
				if(eCRWorkflow==null || eCRWorkflow.equals("")) {
					sStatus = awfMessagesList.get("UNASSIGNED_STATUS").toString();
				}
				logger.debug("eCR sStatus is:"+sStatus);
				
				if(sStatus!=null && !sStatus.equals("")) {
					
					//Generate AWF only when eCR is in Unassigned/Pending/Started States
					if(sStatus.equalsIgnoreCase(awfMessagesList.get("UNASSIGNED_STATUS").toString()) || sStatus.equalsIgnoreCase(awfMessagesList.get("PENDING_STATUS").toString()) ||
							sStatus.equalsIgnoreCase(awfMessagesList.get("ECR_STARTED_STATUS").toString())) {
						
						//Get Impact assessment attribute values of eCR
						HashMap<Object, Object> impactAssessmentAttributeValues = new HashMap<Object, Object>();
						impactAssessmentAttributeValues = GenericUtilities.getImpactAssessmentAttrValues(session, eCR, eCRToAWFAttributeIdsMappingList);
						logger.debug("Impact assessment attribute values are:" + impactAssessmentAttributeValues);

						//Get count of empty Impact Assessment attribute values of eCR
						int countOfEmptyAttrs = 0;
						countOfEmptyAttrs = GenericUtilities.getCountOfImpactAssessmentAttributes(awfMessagesList.get("NULL").toString(),
								impactAssessmentAttributeValues, session);
						logger.debug("count of empty Attributes is:" + countOfEmptyAttrs);

						//If count>0, throw exception else create AWF
						if (countOfEmptyAttrs > 0) {
							actionResult = new ActionResult(ActionResult.EXCEPTION,
									new Exception(String.format(awfMessagesList.get("VALUES_NOT_FILLED_DURING_CREATION_OF_AWF").toString(), eCR.toString())));
						} else {
							// Get Next number from Auto Number
							String nextNumber = GenericUtilities.getNextAutoNumber(session,awfMessagesList.get("AWF_SUBCLASS_NAME").toString(),
									awfMessagesList.get("AWF_AUTONUM_NAME").toString());
							logger.debug("Next Number is:" + nextNumber);
							
							//copy common attribute values from eCR to AWF
							HashMap<Object, Object> map = new HashMap<Object, Object>();
							map = GenericUtilities.copyAttrValuesFromSourceObjToTargetObj(eCR, eCRToAWFAttributeIdsMappingList, session, awfMessagesList.get("ECR_SUBCLASS_NAME").toString(),
									awfMessagesList.get("AWF_SUBCLASS_NAME").toString());

							map.put(Integer.parseInt(awfMessagesList.get("NUM_ATTRID").toString()), nextNumber);
							logger.debug("Map contains:" + map);

							//create AWF
							IChange awf = (IChange)session.createObject(awfMessagesList.get("AWF_SUBCLASS_NAME").toString(),map);
							logger.debug("AWF is:" + awf);
							 
							if (awf != null) {
								
								// set eCR number on eCR attribute of AWF
								awf.setValue(Integer.parseInt(awfMessagesList.get("ECR_ATTRID").toString()), eCR);

								// set AWF Workflow on workflow attribute of AWF
								awf.setValue(Integer.parseInt(awfMessagesList.get("WORKFLOW_ATTRID").toString()),
										awfMessagesList.get("AWF_WORKFLOW_NAME").toString());

								// Add AWF to relationship tab of eCR
								ITable relationshipTab = eCR.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
								if (relationshipTab != null) {
									relationshipTab.createRow(awf);
									result = String.format(awfMessagesList.get("OBJ_CREATED_ADDED_RELTAB").toString(), awf.toString(), eCR.toString());
									logger.debug("Result:" + result);
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
								
							}else {
								result = awfMessagesList.get("OBJ_CREATION_FAILED").toString();
							}
							actionResult = new ActionResult(ActionResult.STRING, result);
						}
					}
					//Throw an exception when user tries to generate an AWF if eCR is not in Unassigned/Pending/Started states
					else {
						actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(String.format(awfMessagesList.get("OBJ_CREATION_NOT_POSSIBLE").toString(), 
								awfMessagesList.get("AWF_SUBCLASS_NAME").toString())));
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
		
		return actionResult;
	}

	
}
