package edu.mayo.bior.pipeline.Treat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;

import com.tinkerpop.pipes.transform.TransformFunctionPipe;
import com.tinkerpop.pipes.util.Pipeline;

import edu.mayo.bior.cli.cmd.Cmds;
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
import edu.mayo.bior.util.DependancyUtil;
import edu.mayo.exec.AbnormalExitException;
import edu.mayo.pipes.history.CompressPipe;
import edu.mayo.pipes.history.History;
import edu.mayo.pipes.util.FieldSpecification;
import edu.mayo.pipes.util.FieldSpecification.FieldDirection;
import edu.mayo.pipes.util.metadata.Metadata;

/**
 * BioR implementation of TREAT annotation module.
 *  
 * @author Greg Dougherty, duffp, Mike Meiners
 *
 */
public class TreatPipeline extends Pipeline<History, History>
{
	private List<String> mConfigColumnsToOutput;
	private TreatUtils mUtils;
	
	private static Logger sLogger = Logger.getLogger(TreatPipeline.class);
	
	private String mBiorLiteHome = "";
	private String mBiorCmdDir = "";
	
	// Metadata lines that would be generated by the JSON columns.
	// These will NOT be in the output, but we need to generate the appropriate columns from these metadata lines
	private List<Metadata> mMetadataToAdd = new ArrayList<Metadata>();
	private List<String>   mCatalogForColumn = new ArrayList<String>();
	
	/**
	 * Constructor
	 * 
	 * @throws IOException
	 * @throws AbnormalExitException 
	 * @throws TimeoutException 
	 * @throws BrokenBarrierException 
	 * @throws InterruptedException 
	 * @throws URISyntaxException 
	 */
	public TreatPipeline() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException, URISyntaxException {
		this(null);
	}
	
	/**
	 * @param configFilePath
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws BrokenBarrierException
	 * @throws TimeoutException
	 * @throws AbnormalExitException
	 * @throws URISyntaxException
	 */
	public TreatPipeline(String configFilePath) throws IOException, InterruptedException, BrokenBarrierException, 
			TimeoutException, AbnormalExitException, URISyntaxException 
	{
		mUtils = new TreatUtils();
		mConfigColumnsToOutput = mUtils.loadConfig(configFilePath);
		mUtils.validateConfigFileColumns(mConfigColumnsToOutput);
		initPipes();
	}

    private String generatedCommand = "";
    /**
     * Get the full command that will be used for bior_annotate
     * @return	The generated command
     */
    public String getGeneratedCommand(){
        return generatedCommand;
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
	 * @throws URISyntaxException 
	 */
	@SuppressWarnings ({"rawtypes", "unchecked"})
	private void initPipes() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException, URISyntaxException
	{
		// tracks the order of the added JSON columns
		List<JsonColumn> order = new ArrayList<JsonColumn>();
		
		List<String> pipeList = new ArrayList<String>();
		
		setBiorLiteCmdDir();
		
		addTrim(pipeList);
		addVcfToTjson( order, pipeList);
		addVep(		   order, pipeList);
		addVepHgnc(	   order, pipeList);
		addSnpEff(	   order, pipeList);
		addSameVariant(order, pipeList, new DbsnpFormatter(), 			Key.dbsnpFile, 			JsonColumn.DBSNP_ALL);
		addSameVariant(order, pipeList, new DbsnpClinvarFormatter(),	Key.dbsnpClinvarFile, 	JsonColumn.DBSNP_CLINVAR);
		addSameVariant(order, pipeList, new CosmicFormatter(),			Key.cosmicFile, 		JsonColumn.COSMIC);
		addOverlap(    order, pipeList, new UcscBlacklistedFormatter(), Key.blacklistedFile, 	JsonColumn.UCSC_BLACKLISTED);
		addOverlap(    order, pipeList, new UcscConservationFormatter(),Key.conservationFile,	JsonColumn.UCSC_CONSERVATION);
		addOverlap(    order, pipeList, new UcscEnhancerFormatter(),	Key.enhancerFile,		JsonColumn.UCSC_ENHANCER);
		addOverlap(    order, pipeList, new UcscTfbsFormatter(),		Key.tfbsFile,			JsonColumn.UCSC_TFBS);
		addOverlap(    order, pipeList, new UcscTssFormatter(),			Key.tssFile,			JsonColumn.UCSC_TSS);
		addOverlap(    order, pipeList, new UcscUniqueFormatter(),		Key.uniqueFile,			JsonColumn.UCSC_UNIQUE);
		addOverlap(    order, pipeList, new UcscRepeatFormatter(),		Key.repeatFile, 		JsonColumn.UCSC_REPEAT);
		addOverlap(    order, pipeList, new UcscRegulationFormatter(),	Key.regulationFile,		JsonColumn.UCSC_REGULATION);
		addOverlap(    order, pipeList, new MirBaseFormatter(),			Key.mirBaseFile,		JsonColumn.MIRBASE);
		// allele frequency annotation
		addSameVariant(order, pipeList, new BgiFormatter(),				Key.bgiFile,			JsonColumn.BGI);
		addSameVariant(order, pipeList, new EspFormatter(),				Key.espFile,			JsonColumn.ESP); 
		addSameVariant(order, pipeList, new HapmapFormatter(), 			Key.hapMapFile,			JsonColumn.HAPMAP);
		addSameVariant(order, pipeList, new ThousandGenomesFormatter(), Key.kGenomeFile,		JsonColumn.THOUSAND_GENOMES); 
		// annotation requiring walking X-REFs
		addOverlap(    order, pipeList, new NcbiGeneFormatter(),		Key.genesFile,			JsonColumn.NCBI_GENE);
		// Drill to add Entrez GeneID X-REF
		addLookup(     order, pipeList, new HgncFormatter(),			Key.hgncFile,			JsonColumn.HGNC,	Key.hgncIndexFile,  "GeneID",  		  "Entrez_Gene_ID");
		// Drill to add OMIM ID X-REF
		addLookup(     order, pipeList, new OmimFormatter(),			Key.omimFile,			JsonColumn.OMIM,	Key.omimIndexFile, 	"mapped_OMIM_ID", "MIM_Number");

		
		// The many commands should be one string / one call
		String pipesAsStr = pipeAsString(pipeList);

		sLogger.info("bior_annotate pipeline long cmd: " + pipesAsStr);
        //System.err.println(pipesAsStr);
        this.generatedCommand = pipesAsStr;
        if(this.generatedCommand.contains("bior_snpeff")){
            System.err.println("SNPEFF is requested, bior is starting it up, this will take about 1 min.");
        }

		Map<String,String> envVars = new HashMap<String,String>();
		sLogger.info("BIOR_LITE_HOME: " + mBiorLiteHome);
		envVars.put("BIOR_LITE_HOME", mBiorLiteHome);
		
		// Transform JSON cols into final output
		FormatterPipeFunction formatterPipe = new FormatterPipeFunction(order, mConfigColumnsToOutput);
		// NOTE: Don't need a metadata object for the compress pipe since annotate always does a compress,
		//       and this is taken care of when constructing the annotate metadata line.
		mMetadataToAdd = formatterPipe.getMetadataForUserColumns(mCatalogForColumn);
		// specify final output cols to compress - compress to have 1-to-1 variants match
		FieldSpecification fSpec = new FieldSpecification(formatterPipe.getColumnsAdded().size() + "-", FieldDirection.RIGHT_TO_LEFT);

		this.setPipes( new Pipeline(
				new AnnotateEXE(new String[] { "/bin/sh", "-c", pipesAsStr }, envVars, mUtils.getMaxLinesInFlight(), mUtils.getTimeout(), order.size(), mUtils.getMaxAlts()),
				new TransformFunctionPipe(formatterPipe),
				//new PrintPipe(),
				new CompressPipe(Cmds.Names.bior_compress.toString(), fSpec, "|", "\\|", true)
			).getPipes() );
	}


	private void addTrim(List<String> pipeList) {
		// Don't use the STDBUF option on the first command as this will cause an exception
		String cmd = mBiorCmdDir + Cmds.Names.bior_trim_spaces + logFlag();
		pipeList.add(cmd);
	}
	
	private void addVcfToTjson(List<JsonColumn> order, List<String> pipeList) {
		// 1ST JSON column is the original variant
		order.add(JsonColumn.VARIANT);
		// Add "null" since this column is not used directly by formatters
		mCatalogForColumn.add(null);
		// Don't use the STDBUF option on the first command as this will cause an exception
		String cmd = mBiorCmdDir + Cmds.Names.bior_vcf_to_tjson + logFlag();
		pipeList.add(cmd);
	}

	private void addSameVariant(List<JsonColumn> order, List<String> pipeList,
	  Formatter formatter, BiorProperties.Key catalogKey, JsonColumn jsonColName) throws FileNotFoundException
	{
		if(! mUtils.isNeedPipe(formatter))
			return;
		
		mUtils.throwErrorIfMissing(catalogKey, TreatUtils.FileType.catalog);
		order.add(jsonColName);
		mCatalogForColumn.add(mUtils.getFile(catalogKey));
		// Using 1-order.size because we don't know how many columns the user passed in.
		// We want to reference the vcf2variant column, but it is easier to reference it from the end
		String cmd = mBiorCmdDir + Cmds.Names.bior_same_variant + " -d " + mUtils.getFile(catalogKey) + " -c " + (1-order.size()) + logFlag();
		pipeList.add(cmd);
	}

	private void addOverlap(List<JsonColumn> order, List<String> pipeList,
	  Formatter formatter, BiorProperties.Key catalogKey, JsonColumn jsonColName) throws FileNotFoundException
	{
		if( ! mUtils.isNeedPipe(formatter) )
			return;
		
		mUtils.throwErrorIfMissing(catalogKey, TreatUtils.FileType.catalog);
		order.add(jsonColName);
		mCatalogForColumn.add(mUtils.getFile(catalogKey));
		// Using 1-order.size because we don't know how many columns the user passed in.
		// We want to reference the vcf2variant column, but it is easier to reference it from the end
		String cmd = mBiorCmdDir + Cmds.Names.bior_overlap + " -d " + mUtils.getFile(catalogKey) + " -c " + (1-order.size()) + logFlag();
		pipeList.add(cmd);
	}
	
	private void addLookup(List<JsonColumn> order, List<String> pipeList, Formatter formatter,
	  BiorProperties.Key catalogKey, JsonColumn jsonColName, BiorProperties.Key indexKey,
	  String jsonPathToDrill, String jsonPathToLookup ) throws FileNotFoundException
	{
		if( ! mUtils.isNeedPipe(formatter) )
			return;
		
		mUtils.throwErrorIfMissing(catalogKey, TreatUtils.FileType.catalog);
		mUtils.throwErrorIfMissing(indexKey,   TreatUtils.FileType.index);

		// Drill column, and keep the JSON
		// (note: this requires that we insert at end minus one position since the JSON stays on the end)
		// Ignore drilled column, since it is the JSON we lookup that we care about
		order.add(order.size()-1,  JsonColumn.IGNORE);
		// Add "null" since this column is not used directly by formatters
		// NOTE: drill swaps last 2 columns, so we need to make the second to last column null
		mCatalogForColumn.add(mCatalogForColumn.size()-1, null);
		String drillCmd = mBiorCmdDir + Cmds.Names.bior_drill + " -p " + jsonPathToDrill + " -k" + logFlag();
		pipeList.add(drillCmd);
		
		// Now add the lookup column, which will be JSON
		// NOTE: Json path to lookup will likely be different from the path to drill
		order.add(jsonColName);
		mCatalogForColumn.add(mUtils.getFile(catalogKey));
		String lookupCmd = mBiorCmdDir + Cmds.Names.bior_lookup + " -d " + mUtils.getFile(catalogKey) 
				+ " -i " + mUtils.getFile(indexKey) + " -p " + jsonPathToLookup + " -c -2" + logFlag();
		pipeList.add(lookupCmd);
	}
	
	private void addSnpEff(List<JsonColumn> order, List<String> pipeList) throws IOException, URISyntaxException {
		// Since SNPEff takes a long time to load, AND that load is in the constructor, let's check if we need it first before calling the constructor
		if(! mUtils.isNeedPipe(new SNPEffFormatter()) )	
			return;
		
		//check to see if it is even installed, if not bail!
		if(DependancyUtil.isSNPEffInstalled()){
			order.add(JsonColumn.SNPEFF);
			mCatalogForColumn.add("/tools/snpeff");
			String cmd = mBiorCmdDir + Cmds.Names.bior_snpeff + logFlag();
			pipeList.add(cmd);
		} else {  // Show warning:
			System.err.println("Warning: SnpEffect is listed as a required field, but is not installed.  Running without it...");
		}
	}

	private void addVepHgnc(List<JsonColumn> order, List<String> pipeList) throws FileNotFoundException, IOException {
		// Since the drill and cut are for HGNC lookup, we must check if HGNC is needed before we perform the drill and cut
		VEPHgncFormatter vepHgncFormatter = new VEPHgncFormatter();
		if( ! mUtils.isNeedPipe(vepHgncFormatter) )
			return;
		
		if(DependancyUtil.isVEPInstalled()){
			addLookup(order, pipeList, vepHgncFormatter, Key.hgncFile, JsonColumn.VEP_HGNC, 
					Key.hgncEnsemblGeneIndexFile, "Gene", "Ensembl_Gene_ID");
			mUtils.throwErrorIfMissing(Key.hgncEnsemblGeneIndexFile, TreatUtils.FileType.index);
		} else {  // Show warning:
			System.err.println("Warning: VEP is listed as a required field (HGNC fields are dependent on it), but is not installed.  Running without it...");
		}
	}

	private void addVep(List<JsonColumn> order, List<String> pipeList) throws IOException, URISyntaxException {
		if( ! mUtils.isNeedPipe(new VEPFormatter()))
			return;
		
		//if vep is not installed and they try to use it, then we need to bail!
		if(DependancyUtil.isVEPInstalled()){
			order.add(JsonColumn.VEP);
			mCatalogForColumn.add("/tools/vep");
			String cmd = mBiorCmdDir + Cmds.Names.bior_vep + logFlag();
			pipeList.add(cmd);
		} else {  // Show warning:
			System.err.println("Warning: VEP is listed as a required field, but is not installed.  Running without it...");
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

	
	//==========================================================================
	
	private String logFlag() {
		boolean isLogOn = sLogger.isDebugEnabled() || sLogger.isInfoEnabled();
		sLogger.info("If logging is on, then set it for all bior_annotate sub-commands.  Is logging on?  " + isLogOn);
		return isLogOn ? " -l" : "";
	}

	private String setBiorLiteCmdDir() throws IOException {
		mBiorLiteHome = System.getenv().get("BIOR_LITE_HOME");
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

	
	/** Use this after calling the constructor to get the metadata 
	 *  for ONLY the columns the user wants in the end,
	 * that will be used for HistoryInPipe
	 * @return	List of {@link Metadata}
	 */
	public List<Metadata> getMetadata() {
		return mMetadataToAdd;
	}


}