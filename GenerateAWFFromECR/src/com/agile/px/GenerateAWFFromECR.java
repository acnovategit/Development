package com.agile.px;

import java.util.HashMap;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.DataTypeConstants;
import com.agile.api.IAdmin;
import com.agile.api.IAgileClass;
import com.agile.api.IAgileList;
import com.agile.api.IAgileSession;
import com.agile.api.IAttribute;
import com.agile.api.IAutoNumber;
import com.agile.api.ICell;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.INode;
import com.agile.api.ITable;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya 
 * This PX displays a message to user if any Impact assessment attributes are not filled on eCR before generating AWF. 
 * If all Impact assessment attributes are filled,AWF is created and added to relationship tab of eCR. 
 * All Impact assessment attribute values,Description are copied from ECR to AWF.
 * AWFWorkflow is set on Workflow attribute of AWF
 *
 */

public class GenerateAWFFromECR implements ICustomAction {

	static Logger logger = Logger.getLogger(GenerateAWFFromECR.class);

	public static String attributesMappingOnECRAndAWFListName = "ECRAWFAttributeIDsMappingList";
	public static String awfMessagesListName = "AWFMessagesList";
	public static String ecrMessagesListName = "ECRMessagesList";
	public static String genericMessagesListName = "GenericMessagesList";

	@Override
	public ActionResult doAction(IAgileSession session, INode arg1, IDataObject dataObject) {

		ActionResult actionResult = null;
		String result = "";

		HashMap<Object, Object> attributesMappingOnECRAndAWFList = new HashMap<Object, Object>();
		HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();
		HashMap<Object, Object> ecrMessagesList = new HashMap<Object, Object>();
		HashMap<Object, Object> genericMessagesList = new HashMap<Object, Object>();

		try {
			// Initialize logger
			GenericUtilities.initializeLogger(session);

			// get Agile List Values
			attributesMappingOnECRAndAWFList = GenericUtilities.getAgileListValues(session,
					attributesMappingOnECRAndAWFListName);
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);
			ecrMessagesList = GenericUtilities.getAgileListValues(session, ecrMessagesListName);
			genericMessagesList = GenericUtilities.getAgileListValues(session, genericMessagesListName);

			// Get eCR Object
			IChange eCR = (IChange) dataObject;
			logger.debug("ECR is:" + eCR);

			if (eCR != null) {

				HashMap<Object, Object> eCRAttrValues = new HashMap<Object, Object>();
				eCRAttrValues = GenericUtilities.getIAAttributeValues(eCR, attributesMappingOnECRAndAWFList);
				logger.debug("eCRAttrValues are:" + eCRAttrValues);

				int countOfEmptyAttrs = 0;
				countOfEmptyAttrs = GenericUtilities.getCountOfIAAttributes(genericMessagesList.get("NULL").toString(),
						eCRAttrValues, session);
				logger.debug("count of empty Attributes is" + countOfEmptyAttrs);

				if (countOfEmptyAttrs > 0) {
					actionResult = new ActionResult(ActionResult.EXCEPTION,
							new Exception(ecrMessagesList.get("VALUES_NOT_FILLED1").toString() + " " + eCR.toString()
									+ "." + ecrMessagesList.get("VALUES_NOT_FILLED2").toString()));
				} else {
					IAdmin admin = session.getAdminInstance();
					IAgileClass subClass = admin.getAgileClass(awfMessagesList.get("AWF_SUBCLASS_NAME").toString());
					IAutoNumber[] numSources = subClass.getAutoNumberSources();

					String nextNumber = "";
					int i = 0;
					IAutoNumber autoNumber = null;
					while (i < numSources.length) {
						autoNumber = numSources[i];
						logger.debug("autoNumber is:" + autoNumber);
						if (autoNumber.toString().equals(awfMessagesList.get("AWF_AUTONUM_NAME").toString())) {
							nextNumber = autoNumber.getNextNumber(subClass);
							break;
						}
						i++;

					}
					logger.debug("Next Autonmber is:" + nextNumber);

					HashMap<Object, Object> map = new HashMap<Object, Object>();
					map = copyAttributeValuesFromECRToAWF(eCR, attributesMappingOnECRAndAWFList, session,
							ecrMessagesList.get("ECR_SUBCLASS_NAME").toString(),
							awfMessagesList.get("AWF_SUBCLASS_NAME").toString());

					map.put(Integer.parseInt(awfMessagesList.get("AWFNUM_ATTRID").toString()), nextNumber);
					logger.debug("Map is:" + map);

					IChange awf = (IChange) session.createObject(awfMessagesList.get("AWF_SUBCLASS_NAME").toString(),
							map);
					logger.debug("AWF is:" + awf);

					if (awf != null) {
						// set eCR number on eCR attribute of AWF
						awf.setValue(Integer.parseInt(awfMessagesList.get("ECR_ATTRID").toString()), eCR);
						
						// set AWF Workflow on workflow attribute of AWF
						awf.setValue(Integer.parseInt(awfMessagesList.get("WORKFLOW_ATTRID").toString()),
								awfMessagesList.get("AWF_WORKFLOW_NAME").toString());

						// Get Description from eCR and set description on AWF
						String descriptionOfECR = (String) eCR
								.getValue(Integer.parseInt(ecrMessagesList.get("DESCRIPTION_ATTRID").toString()));
						logger.debug("Descripton of ECR:" + descriptionOfECR);
						if(descriptionOfECR!=null) {
							awf.setValue(Integer.parseInt(awfMessagesList.get("DESCRIPTION_ATTRID").toString()),
									descriptionOfECR);

						}
						
						// Add AWF to relationship tab of eCR
						ITable relationshipTab = eCR.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
						if(relationshipTab!=null) {
							relationshipTab.createRow(awf);
							result = awf.toString() + " " + ecrMessagesList.get("OBJ_CREATED_ADDED_RELTAB").toString() + " "
									+ eCR.toString();
							logger.debug("Result:" + result);
						}
					
						actionResult = new ActionResult(ActionResult.STRING, result);
					}

				}
			}

		} catch (APIException e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to" + e.getMessage());
		} catch (Exception e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to" + e.getMessage());
		}

		return actionResult;
	}

	/**
	 * This method iterates through the list of attributes and copies the attribute
	 * values from ECR Object to AWF Object
	 * 
	 * @param eCR
	 * @param attributeIdsList
	 * @param session
	 * @param eCRClassName
	 * @param awfClassName
	 * @return
	 * @throws APIException
	 */
	public static HashMap<Object, Object> copyAttributeValuesFromECRToAWF(IDataObject eCR,
			HashMap<Object, Object> attributeIdsList, IAgileSession session, String eCRClassName, String awfClassName)
			throws APIException {

		HashMap<Object, Object> map = new HashMap<Object, Object>();
		int eCRAttrID = 0;
		int eCRAttrDataType = 0;
		int awfAttrID = 0;
		int awfAttrDataType = 0;
		IAttribute eCRattribute = null;
		IAttribute awfAttribute = null;

		for (Object key : attributeIdsList.keySet()) {

			if (attributeIdsList.get(key) != null) {

				/* Get eCR attribute IDs from list */
				eCRAttrID = Integer.parseInt(key.toString());
				eCRattribute = session.getAdminInstance().getAgileClass(eCRClassName).getAttribute(eCRAttrID);
				eCRAttrDataType = eCRattribute.getDataType();
				logger.debug("eCR Attribute Data Type is" + eCRAttrDataType);

				/* Get awf attribute IDs from list */
				awfAttrID = Integer.parseInt(attributeIdsList.get(key).toString());
				awfAttribute = session.getAdminInstance().getAgileClass(awfClassName).getAttribute(awfAttrID);
				awfAttrDataType = awfAttribute.getDataType();
				logger.debug("AWF attribute data type  is" + awfAttrDataType);

				/* Copy the values */
				if (eCRAttrDataType == DataTypeConstants.TYPE_SINGLELIST
						&& awfAttrDataType == DataTypeConstants.TYPE_SINGLELIST) {

					ICell cell = eCR.getCell(eCRAttrID);
					IAgileList agileList = (IAgileList) cell.getValue();
					IAgileList[] selection = agileList.getSelection();
					if (selection != null && selection.length > 0) {
						String selectedvalue = (selection[0].getValue()).toString();

						logger.debug("eCR Attribute cell value is" + selectedvalue);
						if (!selectedvalue.equals("") && !selectedvalue.isEmpty()) {
							IAgileList availableValues = awfAttribute.getAvailableValues();

							availableValues.setSelection(new Object[] { selectedvalue });
							map.put(awfAttribute, availableValues);

						}
					}
				}

			}

		}

		logger.debug("Map values are:" + map);
		return map;
	}

}
