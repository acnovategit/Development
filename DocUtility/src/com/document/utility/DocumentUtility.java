package com.document.utility;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPicture;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import com.microsoft.schemas.office.office.CTLock;
import com.microsoft.schemas.office.office.STConnectType;
import com.microsoft.schemas.vml.CTFormulas;
import com.microsoft.schemas.vml.CTGroup;
import com.microsoft.schemas.vml.CTH;
import com.microsoft.schemas.vml.CTHandles;
import com.microsoft.schemas.vml.CTPath;
import com.microsoft.schemas.vml.CTShape;
import com.microsoft.schemas.vml.CTShapetype;
import com.microsoft.schemas.vml.CTTextPath;
import com.microsoft.schemas.vml.STExt;
import com.microsoft.schemas.vml.STTrueFalse;

public class DocumentUtility {

	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(DocumentUtility.class);
	static boolean bUpdated = false;

	public DocumentUtility() throws Exception {
		
	}

	/**
	 * Function which reads the Header of Docx files and returns the header text
	 * 
	 * @param file
	 * @return Header Data which is a string of data containing the entire
	 *         header text
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	public static String sGetHeaderDataForDocX(File file) throws InvalidFormatException, IOException {
		FileInputStream fis;
		String sHeaderData = null;

		fis = new FileInputStream(file);

		XWPFDocument xdoc;
		xdoc = new XWPFDocument(OPCPackage.open(fis));
		XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(xdoc);
		logger.info("Headerfooterpolicy created");
		// read header
		XWPFHeader header = policy.getDefaultHeader();
		sHeaderData = header.getText().toString();

		return sHeaderData;

	}

	public static XWPFDocument setHeader(XWPFDocument document, String token, String textToReplace) {
		XWPFHeaderFooterPolicy policy = document.getHeaderFooterPolicy();
		logger.info("Got policy");
		XWPFHeader header = policy.getDefaultHeader();
		logger.info("Got header");
		logger.info(textToReplace);
		bUpdated = replaceInParagraphs(header.getParagraphs(), token, textToReplace);

		if (bUpdated) {
			logger.info("Replaced");
		} else {
			bUpdated = replaceInParagraphsMod(header.getParagraphs(), token, textToReplace);
			if (bUpdated)
				logger.info("Now replaced");
		}

		logger.info("replaced");
		return document;
	}

	private static boolean replaceInParagraphs(List<XWPFParagraph> paragraphs, String placeHolder, String replaceText) {
		boolean bFlag = false;
		logger.info(placeHolder);
		for (XWPFParagraph xwpfParagraph : paragraphs) {
			List<XWPFRun> runs = xwpfParagraph.getRuns();

			for (XWPFRun run : runs) {

				String runText;
				try {
					runText = run.getText(run.getTextPosition());
				} catch (Exception e) {
					logger.error(e);
					continue;
				}
				logger.info("Runtext is:" + runText + " Placeholder is:" + placeHolder);
				if (placeHolder != "" && !placeHolder.isEmpty() && placeHolder != null) {

					if (runText != null
							&& Pattern.compile(placeHolder, Pattern.CASE_INSENSITIVE).matcher(runText).find()) {

						if (runText.contains("Effectiv"))
							runText = "Effective Date: " + replaceText;
						else
							runText = replaceText;

						bFlag = true;
					}

				}
				logger.info("setting run text:" + runText);
				run.setText(runText, 0);
			}
		}
		return bFlag;
	}

	private static boolean replaceInParagraphsMod(List<XWPFParagraph> paragraphs, String placeHolder,
			String replaceText) {
		boolean bFlag = false;
		logger.info(placeHolder);
		for (XWPFParagraph xwpfParagraph : paragraphs) {
			logger.info("Para text=" + xwpfParagraph.getText());

			if (xwpfParagraph.getText().contains(placeHolder)) {
				String paratext = xwpfParagraph.getText();
				String replacedPara = paratext.replace(placeHolder, replaceText);

				int size = xwpfParagraph.getRuns().size();
				for (int i = 0; i < size; i++) {
					xwpfParagraph.removeRun(0);
				}

				String[] replacementTextSplitOnCarriageReturn = replacedPara.split("\n");

				for (int j = 0; j < replacementTextSplitOnCarriageReturn.length; j++) {
					String part = replacementTextSplitOnCarriageReturn[j];
					logger.info("line:" + part);
					XWPFRun newRun = xwpfParagraph.insertNewRun(j);
					newRun.setText(part);

					if (j + 1 < replacementTextSplitOnCarriageReturn.length) {
						newRun.addCarriageReturn();
					}
				}
				bFlag = true;

			}

		}
		return bFlag;
	}

	public static String now() {
		String DATE_FORMAT_NOW = "MM-dd-yyyy";
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
		return sdf.format(cal.getTime());
	}

	public static XWPFParagraph getWatermarkParagraph(String text, int idx, XWPFDocument doc) {
		// String log="";
		CTP p = CTP.Factory.newInstance();
		// log+="Instance Created";
		byte[] rsidr = doc.getDocument().getBody().getPArray(0).getRsidR();
		byte[] rsidrdefault = doc.getDocument().getBody().getPArray(0).getRsidRDefault();
		p.setRsidP(rsidr);
		p.setRsidRDefault(rsidrdefault);
		CTPPr pPr = p.addNewPPr();
		pPr.addNewPStyle().setVal("Header");
		// start watermark paragraph
		CTR r = p.addNewR();
		CTRPr rPr = r.addNewRPr();
		rPr.addNewNoProof();
		CTPicture pict = r.addNewPict();
		CTGroup group = CTGroup.Factory.newInstance();
		CTShapetype shapetype = group.addNewShapetype();
		shapetype.setId("_x0000_t136");
		shapetype.setCoordsize("1600,21600");
		shapetype.setSpt(136);
		shapetype.setAdj("10800");
		shapetype.setPath2("m@7,0l@8,0m@5,21600l@6,21600e");
		CTFormulas formulas = shapetype.addNewFormulas();
		formulas.addNewF().setEqn("sum #0 0 10800");
		formulas.addNewF().setEqn("prod #0 2 1");
		formulas.addNewF().setEqn("sum 21600 0 @1");
		formulas.addNewF().setEqn("sum 0 0 @2");
		formulas.addNewF().setEqn("sum 21600 0 @3");
		formulas.addNewF().setEqn("if @0 @3 0");
		formulas.addNewF().setEqn("if @0 21600 @1");
		formulas.addNewF().setEqn("if @0 0 @2");
		formulas.addNewF().setEqn("if @0 @4 21600");
		formulas.addNewF().setEqn("mid @5 @6");
		formulas.addNewF().setEqn("mid @8 @5");
		formulas.addNewF().setEqn("mid @7 @8");
		formulas.addNewF().setEqn("mid @6 @7");
		formulas.addNewF().setEqn("sum @6 0 @5");
		CTPath path = shapetype.addNewPath();
		path.setTextpathok(STTrueFalse.T);
		path.setConnecttype(STConnectType.CUSTOM);
		path.setConnectlocs("@9,0;@10,10800;@11,21600;@12,10800");
		path.setConnectangles("270,180,90,0");
		CTTextPath shapeTypeTextPath = shapetype.addNewTextpath();
		shapeTypeTextPath.setOn(STTrueFalse.T);
		shapeTypeTextPath.setFitshape(STTrueFalse.T);
		CTHandles handles = shapetype.addNewHandles();
		CTH h = handles.addNewH();
		h.setPosition("#0,bottomRight");
		h.setXrange("6629,14971");
		CTLock lock = shapetype.addNewLock();
		lock.setExt(STExt.EDIT);
		CTShape shape = group.addNewShape();
		shape.setId("PowerPlusWaterMarkObject" + idx);
		shape.setSpid("_x0000_s102" + (4 + idx));
		shape.setType("#_x0000_t136");
		shape.setStyle(
				"position:absolute;margin-left:0;margin-top:0;width:415pt;height:207.5pt;z-index:-251654144;mso-wrap-edited:f;mso-position-horizontal:center;mso-position-horizontal-relative:margin;mso-position-vertical:center;mso-position-vertical-relative:margin");
		shape.setWrapcoords(
				"616 5068 390 16297 39 16921 -39 17155 7265 17545 7186 17467 -39 17467 18904 17467 10507 17467 8710 17545 18904 17077 18787 16843 18358 16297 18279 12554 19178 12476 20701 11774 20779 11228 21131 10059 21248 8811 21248 7563 20975 6316 20935 5380 19490 5146 14022 5068 2616 5068");
		shape.setFillcolor("black");
		shape.setStroked(STTrueFalse.FALSE);
		CTTextPath shapeTextPath = shape.addNewTextpath();
		shapeTextPath.setStyle("font-family:&quot;Cambria&quot;;font-size:1pt");
		shapeTextPath.setString(text);
		pict.set(group);
		// end watermark paragraph
		// log+="execution completed" +p.getRsidP().toString();
		return new XWPFParagraph(p, doc);
	}

}
