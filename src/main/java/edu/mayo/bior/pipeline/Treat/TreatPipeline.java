package edu.mayo.bior.pipeline.Treat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.tinkerpop.pipes.transform.TransformFunctionPipe;
import com.tinkerpop.pipes.util.Pipeline;

import edu.mayo.bior.pipeline.Treat.format.BgiFormatter;
import edu.mayo.bior.pipeline.Treat.format.CosmicFormatter;
import edu.mayo.bior.pipeline.Treat.format.DbsnpClinvarFormatter;
import edu.mayo.bior.pipeline.Treat.format.DbsnpFormatter;
import edu.mayo.bior.pipeline.Treat.format.EspFormatter;
import edu.mayo.bior.pipeline.Treat.format.Formatter;
import edu.mayo.bior.pipeline.Treat.format.FormatterPipeFunction;
import edu.mayo.bior.pipeline.Treat.format.HapmapFormatter;
import edu.mayo.bior.pipeline.Treat.format.HgncFormatter;
import edu.mayo.bior.pipeline.Treat.format.MirBaseFormatter;
import edu.mayo.bior.pipeline.Treat.format.NcbiGeneFormatter;
import edu.mayo.bior.pipeline.Treat.format.OmimFormatter;
import edu.mayo.bior.pipeline.Treat.format.SNPEffFormatter;
import edu.mayo.bior.pipeline.Treat.format.ThousandGenomesFormatter;
import edu.mayo.bior.pipeline.Treat.format.UcscBlacklistedFormatter;
import edu.mayo.bior.pipeline.Treat.format.UcscConservationFormatter;
import edu.mayo.bior.pipeline.Treat.format.UcscEnhancerFormatter;
import edu.mayo.bior.pipeline.Treat.format.UcscRegulationFormatter;
import edu.mayo.bior.pipeline.Treat.format.UcscRepeatFormatter;
import edu.mayo.bior.pipeline.Treat.format.UcscTfbsFormatter;
import edu.mayo.bior.pipeline.Treat.format.UcscTssFormatter;
import edu.mayo.bior.pipeline.Treat.format.UcscUniqueFormatter;
import edu.mayo.bior.pipeline.Treat.format.VEPFormatter;
import edu.mayo.bior.pipeline.Treat.format.VEPHgncFormatter;
import edu.mayo.bior.util.BiorProperties;
import edu.mayo.bior.util.BiorProperties.Key;
import edu.mayo.exec.AbnormalExitException;
import edu.mayo.pipes.history.CompressPipe;
import edu.mayo.pipes.history.History;
import edu.mayo.pipes.util.FieldSpecification;
import edu.mayo.pipes.util.FieldSpecification.FieldDirection;

/**
 * BioR implementation of TREAT annotation module.
 *  
 * @author Greg Dougherty, duffp, Mike Meiners
 *
 */
public class TreatPipeline extends Pipeline<History, History>
{

	private BiorProperties	mProps;	
	
	private List<String> mConfigColumnsToOutput;
	
	private static Logger sLogger = Logger.getLogger(TreatPipeline.class);
	
	private String mBiorLiteHome = "";
	private String mBiorCmdDir = "";
	
	/**
	 * Constructor
	 * 
	 * @throws IOException
	 * @throws AbnormalExitException 
	 * @throws TimeoutException 
	 * @throws BrokenBarrierException 
	 * @throws InterruptedException 
	 */
	public TreatPipeline() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException {
		this(null);
	}
	
	public TreatPipeline(String configFilePath) throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException {
		mProps = new BiorProperties ();
		mConfigColumnsToOutput = loadConfig(configFilePath);
		validateConfigFileColumns(mConfigColumnsToOutput);
		initPipes();
	}

	
	/**
	 * Initializes what pipes will be used for this pipeline.
	 * NOTE: The reason we construct a string of unix-style pipes instead of using the Java pipes themselves is that 
	 *       in Java we do not have the pipes setup for multi-threading, which the unix-style command would do for us.
	 *       This can more than double the speed of the bior_annotate command!
	 * 
	 * @throws IOException
	 * @throws AbnormalExitException 
	 * @throws TimeoutException 
	 * @throws BrokenBarrierException 
	 * @throws InterruptedException 
	 */
	private void initPipes() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException
	{
		// tracks the order of the added JSON columns
		List<JsonColumn> order = new ArrayList<JsonColumn>();
		
		List<String> pipeList = new ArrayList<String>();
		
		setBiorLiteCmdDir();

		//	ColOrder								PipesToAdd
		//	--------------------------------------	-----------------------------------------------------------------
		// 1ST JSON column is the original variant
			order.add(JsonColumn.VARIANT);			pipeList.add(vcfToJson());
		if(isNeedPipe(new VEPFormatter())) {
			order.add(JsonColumn.VEP);				pipeList.add(vep());
		}
		// Since the drill and cut are for HGNC lookup, we must check if HGNC is needed before we perform the drill and cut
		if(isNeedPipe(new VEPHgncFormatter()) ) {
			// Drill to add Ensembl Gene X-REF col
			// The drill will keep the json, but will switch it and the drill column so the json is last. 
			// Therefore, add the drilled column to the order list as "IGNORE", but make it 2nd-last
			order.add(order.size()-1, JsonColumn.IGNORE); pipeList.add(drill("Gene")); 
			order.add(JsonColumn.VEP_HGNC);			pipeList.add(lookup(Key.hgncFile, Key.hgncEnsemblGeneIndexFile, "Ensembl_Gene_ID"));
		}
		// Since SNPEff takes a long time to load, AND that load is in the constructor, let's check if we need it first before calling the constructor
		if(isNeedPipe(new SNPEffFormatter()) )	{
			order.add(JsonColumn.SNPEFF);			pipeList.add(snpeff());
		}
		// Using 1-order.size because we don't know how many columns the user passed in.
		// We want to reference the vcf2variant column, but it is easier to reference it from the end
		if(isNeedPipe(new DbsnpFormatter())) { 
			order.add(JsonColumn.DBSNP_ALL);		pipeList.add(sameVariant(Key.dbsnpFile, order)); 	
		}
		if(isNeedPipe(new DbsnpClinvarFormatter())) {
			order.add(JsonColumn.DBSNP_CLINVAR);	pipeList.add(sameVariant(Key.dbsnpClinvarFile, order)); 
		}
		if(isNeedPipe(new CosmicFormatter())) {
			order.add(JsonColumn.COSMIC);			pipeList.add(sameVariant(Key.cosmicFile, order)); 
		}
		if(isNeedPipe(new UcscBlacklistedFormatter())) {
			order.add(JsonColumn.UCSC_BLACKLISTED);	pipeList.add(overlap(Key.blacklistedFile, order));
		}
		if(isNeedPipe(new UcscConservationFormatter())) {
			order.add(JsonColumn.UCSC_CONSERVATION);pipeList.add(overlap(Key.conservationFile, order));
		}
		if(isNeedPipe(new UcscEnhancerFormatter())) {
			order.add(JsonColumn.UCSC_ENHANCER);	pipeList.add(overlap(Key.enhancerFile, order));
		}
		if(isNeedPipe(new UcscTfbsFormatter())) {
			order.add(JsonColumn.UCSC_TFBS);		pipeList.add(overlap(Key.tfbsFile, order));
		}
		if(isNeedPipe(new UcscTssFormatter())) {
			order.add(JsonColumn.UCSC_TSS);			pipeList.add(overlap(Key.tssFile, order));
		}
		if(isNeedPipe(new UcscUniqueFormatter())) {
			order.add(JsonColumn.UCSC_UNIQUE);		pipeList.add(overlap(Key.uniqueFile, order));
		}
		if(isNeedPipe(new UcscRepeatFormatter())) {
			order.add(JsonColumn.UCSC_REPEAT);		pipeList.add(overlap(Key.repeatFile, order));
		}
		if(isNeedPipe(new UcscRegulationFormatter())) {
			order.add(JsonColumn.UCSC_REGULATION);	pipeList.add(overlap(Key.regulationFile, order));
		}
		if(isNeedPipe(new MirBaseFormatter())) {
			order.add(JsonColumn.MIRBASE);			pipeList.add(overlap(Key.mirBaseFile, order));
		}
		if(isNeedPipe(new BgiFormatter())) {
			// allele frequency annotation
			order.add(JsonColumn.BGI);				pipeList.add(sameVariant(Key.bgiFile, order)); 
		}
		if(isNeedPipe(new EspFormatter())) {
			order.add(JsonColumn.ESP);				pipeList.add(sameVariant(Key.espFile, order)); 
		}
		if(isNeedPipe(new HapmapFormatter())) {
			order.add(JsonColumn.HAPMAP);			pipeList.add(sameVariant(Key.hapMapFile, order)); 
		}
		if(isNeedPipe(new ThousandGenomesFormatter())) {
			order.add(JsonColumn.THOUSAND_GENOMES);	pipeList.add(sameVariant(Key.kGenomeFile, order)); 
		}
		if(isNeedPipe(new NcbiGeneFormatter())) {
			// annotation requiring walking X-REFs
			order.add(JsonColumn.NCBI_GENE);		pipeList.add(overlap(Key.genesFile, order));
		}
		if(isNeedPipe(new HgncFormatter())) {
			// Drill to add Entrez GeneID X-REF
			// Again, it will added to the 2nd-last position and ignored
			order.add(order.size()-1,JsonColumn.IGNORE); pipeList.add(drill("GeneID")); 
			order.add(JsonColumn.HGNC);				pipeList.add(lookup(Key.hgncFile, Key.hgncIndexFile, "Entrez_Gene_ID"));
		}
		if(isNeedPipe(new OmimFormatter()) ) {
			// Drill to add OMIM ID X-REF
			// Again, add to 2nd-last position and ignore
			order.add(order.size()-1,JsonColumn.IGNORE); pipeList.add(drill("mapped_OMIM_ID"));
			order.add(JsonColumn.OMIM);				pipeList.add(lookup(Key.omimFile, Key.omimIndexFile, "MIM_Number"));
		}
		
		
		// The many commands should be one string / one call
		String pipesAsStr = pipeAsString(pipeList);

		sLogger.info("bior_annotate pipeline long cmd: " + pipesAsStr);

		Map<String,String> envVars = new HashMap<String,String>();
		sLogger.info("BIOR_LITE_HOME: " + mBiorLiteHome);
		envVars.put("BIOR_LITE_HOME", mBiorLiteHome);
		
		// Transform JSON cols into final output
		FormatterPipeFunction formatterPipe = new FormatterPipeFunction(order, mConfigColumnsToOutput);
		// specify final output cols to compress - compress to have 1-to-1 variants match
		FieldSpecification fSpec = new FieldSpecification(formatterPipe.getColumnsAdded().size() + "-", FieldDirection.RIGHT_TO_LEFT);

		final String DUMMY_LINE = "1\t1\trsXXXXXXXX\tA\tC\t.\t.\t.";
		this.setPipes( new Pipeline(
				new EndLineGeneratorPipe(DUMMY_LINE, true),
				new TransformFunctionPipe(new AnnotateEXE(new String[] { "/bin/sh", "-c", pipesAsStr }, envVars, DUMMY_LINE, getMaxLinesInFlight())),
				new TransformFunctionPipe(formatterPipe),
				new CompressPipe(fSpec, "|", "\\|", true)
			).getPipes() );
	}

	private int getMaxLinesInFlight() {
		String maxLinesStr = mProps.get(Key.AnnotateMaxLinesInFlight);
		int maxLines = isInteger(maxLinesStr)  ?  Integer.parseInt(maxLinesStr)  :  10; // 10 = DEFAULT
		sLogger.info("AnnotateMaxLinesInFlight = " + maxLines);
		if( maxLines <= 1 )
			throw new IllegalArgumentException("AnnotateMaxLinesInFlight must be 2 or greater to prevent hangs!");
		if( maxLines > 50 )
			sLogger.warn("WARNING: AnnotateMaxLinesInFlight is set to > 50.  This may cause a hang state as the process buffers can overflow and cause data loss, especially in the case of a high number of fanouts!");
		return maxLines;
	}

	private boolean isInteger(String val) {
		try {
			Integer.parseInt(val);
			return true;
		} catch(Exception e) {
			return false;
		}
	}
	/** Given a list of separate pipe commands, build a string with all of them piped together using "|"
	 *  Ex: "bior_vcf_to_json | bior_vep | bior_drill ...."  */
	private String pipeAsString(List<String> pipeCmds) {
		StringBuilder bigPipe = new StringBuilder();
		for(int i=0; i < pipeCmds.size(); i++) {
			bigPipe.append(pipeCmds.get(i));
			if( i < pipeCmds.size()-1 )
				bigPipe.append(" | ");
		}
		return bigPipe.toString();
	}
	
	/** Build the vcf_to_json pipe string */
	private String vcfToJson() {
		return mBiorCmdDir + "bior_vcf_to_json" + logFlag();
	}

	/** Build the vep pipe string */
	private String vep() {
		return mBiorCmdDir + "bior_vep" + logFlag();
	}

	/** Build the snpeff pipe string */
	private String snpeff() {
		return mBiorCmdDir + "bior_snpeff" + logFlag();
	}

	/** Build the lookup pipe string 
	 * @throws IOException */
	private String lookup(BiorProperties.Key catalogFile, BiorProperties.Key indexFile, String jsonKey) throws IOException {
		return mBiorCmdDir + "bior_lookup -d " + getFile(catalogFile) + " -i " + getFile(indexFile) + " -p " + jsonKey + " -c -2" + logFlag();
	}

	/** Build the sameVariant pipe string 
	 * @throws IOException */
	private String sameVariant(BiorProperties.Key catalogFile, List<JsonColumn> order) throws IOException {
		return mBiorCmdDir + "bior_same_variant -d " + getFile(catalogFile) + " -c " + (1-order.size()) + logFlag();
	}

	/** Build the overlap pipe string 
	 * @throws IOException */
	private String overlap(BiorProperties.Key catalogFile, List<JsonColumn> order) throws IOException {
		return mBiorCmdDir + "bior_overlap -d " + getFile(catalogFile) + " -c " + (1-order.size()) + logFlag();
	}
	
	/** Build the drill pipe string - keep the json column */
	private String drill(String jsonPath) {
		return mBiorCmdDir + "bior_drill -p " + jsonPath + " -k" + logFlag();
	}
	
	private String logFlag() {
		boolean isLogOn = sLogger.isDebugEnabled() || sLogger.isInfoEnabled();
		sLogger.info("If logging is on, then set it for all bior_annotate sub-commands.  Is logging on?  " + isLogOn);
		return isLogOn ? " -l" : "";
	}



	private String setBiorLiteCmdDir() throws IOException {
		mBiorLiteHome = System.getProperty("BIOR_LITE_HOME");
		// If not given, then auto-detect inside maven target folder
		if( mBiorLiteHome == null ||  mBiorLiteHome.trim().length() == 0 ) {
			File targetFolder = new File("target");
			for (File f: targetFolder.listFiles()) {
				if (f.isDirectory() && (f.getName().startsWith("bior_pipeline"))) {
					mBiorLiteHome = f.getCanonicalPath();
					break;
				}
			}
		}
		mBiorCmdDir = mBiorLiteHome + "/bin/";
		
		if( ! new File(mBiorCmdDir).exists() )
			throw new IOException("Could not find the directory containing the BioR commands!");

		return mBiorCmdDir;
	}

	/** Do we need to add this pipe?  Yes, if any of its columns are in the config file
	   (or config properties are null, which signifies that the user wants ALL columns)
	   (or if the columnFormatter is null, which means it is a necessary pipe) */
	private boolean isNeedPipe(Formatter colFormatter) {
		if( colFormatter == null || mConfigColumnsToOutput == null )
			return true;
		// Else we need to loop thru the columns this pipe would add.
		// If any are in the config file, then we need it 
		for(String colFromPipe : colFormatter.getHeaders()) {
			if(mConfigColumnsToOutput.contains(colFromPipe))
				return true;
		}
		
		// There are a few dependencies:
		//  - Add Vep 		if VepHgnc is wanted    	(vepHgnc depends on vep)
		//  - Add NcbiGene 	if Hgnc is wanted			(Hgnc    depends on NcbiGene)
		//	- Add NcbiGene AND Hgnc  if Omim is wanted	(Omim	 depends on BOTH NcbiGene AND Hgnc)
		if(colFormatter instanceof VEPFormatter  &&  isNeedPipe(new VEPHgncFormatter()))
			return true;
		if(colFormatter instanceof NcbiGeneFormatter  &&  isNeedPipe(new HgncFormatter()))
			return true;
		if(colFormatter instanceof NcbiGeneFormatter  &&  isNeedPipe(new OmimFormatter())) 
			return true;
		if(colFormatter instanceof HgncFormatter &&  isNeedPipe(new OmimFormatter())) 
			return true;

		
		// None are in the config file, so safe to bypass pipe
		return false;
	}

	private String getFile(Key propKey) {
		String path =  mProps.get(Key.fileBase) + mProps.get(propKey);
		return path;
	}
	
	/** Load the config file that contains the columns that bior_annotate is to keep */
	private List<String> loadConfig(String configFilePath) throws IOException {
		if(configFilePath == null || configFilePath.length() == 0)
			return null;
		
		List<String> configCols = Files.readLines(new File(configFilePath), Charsets.UTF_8);
		// Remove those starting with "#"
		for(int i=configCols.size()-1; i>=0; i--) {
			if(configCols.get(i).startsWith("#") || configCols.get(i).trim().length() == 0)
				configCols.remove(i);
		}
		return configCols;
	}
	

	/** Throw an exception if the config file column is not one of the possible ones */
	private void validateConfigFileColumns(List<String> configFileCols) {
		if(configFileCols == null)
			return;  // No config file specified - OK
		
		if(configFileCols.size() == 0) {
			final String MSG = "Error: The config file does not contain any output columns.  Please add some columns to output.  Or, to add all columns, do not add the config file option.";
			throw new IllegalArgumentException(MSG);
		}

		List<String> allCols = FormatterPipeFunction.getAllPossibleColumns();
		StringBuffer errMsg = new StringBuffer();
		for(String configCol : configFileCols) {
			if( ! allCols.contains(configCol) )
				errMsg.append("    " + configCol + "\n");
		}
		if(errMsg.length() > 0) {
			errMsg.insert(0, "Error: these columns specified in the config file are not recognized:\n");
			throw new IllegalArgumentException(errMsg.toString());
		}
	}

}