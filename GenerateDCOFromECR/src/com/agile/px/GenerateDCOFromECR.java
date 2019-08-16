package com.agile.px;

import java.util.HashMap;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.IAdmin;
import com.agile.api.IAgileClass;
import com.agile.api.IAgileSession;
import com.agile.api.IAutoNumber;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.INode;
import com.agile.api.ITable;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya
 * This PX creates a DCO Object and adds it to the relationship tab of ECR
 *
 */

public class GenerateDCOFromECR implements ICustomAction {

	static Logger logger = Logger.getLogger(GenerateDCOFromECR.class);
	public static String ecrMessagesListName = "ECRMessagesList";
	
	@Override
	public ActionResult doAction(IAgileSession session, INode arg1, IDataObject dataObject) {
		String result="";
		try {
			
			//Initialize logger
			GenericUtilities.initializeLogger(session);
			
			//Get Agile list values
			HashMap<Object, Object> ecrMessagesList = new HashMap<Object, Object>();
			ecrMessagesList = GenericUtilities.getAgileListValues(session, ecrMessagesListName);
		
			// Get eCR Object
			IChange eCR = (IChange) dataObject;
			logger.debug("ECR is:" + eCR);
			
			if (eCR != null) {
				//Get Next number from Auto Number
				IAdmin admin = session.getAdminInstance();
				IAgileClass subClass = admin.getAgileClass(ecrMessagesList.get("DCO_SUBCLASS_NAME").toString());
				IAutoNumber[] numSources = subClass.getAutoNumberSources();

				String nextNumber = "";
				int i = 0;
				IAutoNumber autoNumber = null;
				while (i < numSources.length) {
					autoNumber = numSources[i];

					if (autoNumber.toString().equals(ecrMessagesList.get("DCO_AUTO_NUMBER").toString())) {
						nextNumber = autoNumber.getNextNumber(subClass);
						break;
					}
					i++;
				}
				logger.debug("Next Autonmber is:" + nextNumber);
				
				//Create DCO Object
				IChange dco = (IChange) session.createObject(ecrMessagesList.get("DCO_SUBCLASS_NAME").toString(),nextNumber
						);
				logger.debug("DCO is:" + dco);
				
				if(dco!=null) {
					// Add dco to relationship tab of ECR
					ITable relationshipTab = eCR.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
					if(relationshipTab!=null) {
						relationshipTab.createRow(dco);
						result = dco.toString() + " " + ecrMessagesList.get("OBJ_CREATED_ADDED_RELTAB").toString() +" "+ eCR.toString();
					}
					
				}else {
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


		return new ActionResult(ActionResult.STRING,result);
	}

}
