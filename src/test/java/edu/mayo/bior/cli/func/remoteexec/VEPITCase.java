package edu.mayo.bior.cli.func.remoteexec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.tinkerpop.pipes.transform.TransformFunctionPipe;
import com.tinkerpop.pipes.util.Pipeline;

import edu.mayo.bior.cli.func.CommandOutput;
import edu.mayo.bior.cli.func.remoteexec.helpers.RemoteFunctionalTest;
import edu.mayo.bior.pipeline.VEP.VEPEXE;
import edu.mayo.bior.pipeline.VEP.VEPPipeline;
import edu.mayo.exec.AbnormalExitException;
import edu.mayo.pipes.MergePipe;
import edu.mayo.pipes.UNIX.CatPipe;
import edu.mayo.pipes.history.HistoryInPipe;
import edu.mayo.pipes.history.HistoryOutPipe;
import edu.mayo.pipes.util.test.FileCompareUtils;
import edu.mayo.pipes.util.test.PipeTestUtils;

/** Required steps before being able to run these command:
 *  1) SSH to DragonRider dev server and setup the functestuser user
 *  	a) ssh bior@biordev.mayo.edu
 *  	b) su -
 *  	c) adduser functestuser --home /home/functestuser
 *  2) Setup environment scripts
 *  	a) vi /home/functestuser/.bashrc
 *  	b) (add these variables):
 *  			$xxxx=yyyy
 *  	c) 
 *  3) You MUST do the following BEFORE any of the tests relying on the command
 *     (or those commands will reference the old code that is in the target dir)
 *       (from terminal):
 *       cd bior_pipeline
 *       (this will put the latest code into the target directory where it can be run)
 *       mvn clean package -DskipTests
 * @author Michael Meiners (m054457)
 */
public class VEPITCase extends RemoteFunctionalTest {

	private final String VEPDIR = "src/test/resources/tools/vep/";
	
	@Test
	/** Test only the output of VEP itself based on input (no JSON conversion, just the raw output) */
	public void vepExeOnlyPipe() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException{
		System.out.println("-----------------------------------------");
		System.out.println("VEPITCase.vepExeOnlyPipe()");

		double start = System.currentTimeMillis();
		String[] vepCmd = VEPEXE.getVEPCommand(null);
		System.out.println("VEP Command: " + Arrays.asList(vepCmd));
		VEPEXE vepExe = new VEPEXE(vepCmd);
		Pipeline p = new Pipeline(
				new CatPipe(),               //raw file
				new HistoryInPipe(),         //get rid of the header
				new MergePipe("\t"),
				new TransformFunctionPipe(vepExe)
				);
		p.setStarts(Arrays.asList(VEPDIR + "example.vcf"));

		List<String> expected = FileCompareUtils.loadFile(VEPDIR + "example.vcf.vep.correct");
		// Remove the header (first 3 lines) from the expected output
		while(expected.size() > 0 && expected.get(0).startsWith("#"))
			expected.remove(0);
		
		List<String> actual = PipeTestUtils.getResults(p);
		
		vepExe.terminate();

		//printComparison(p, expected, actual);

		PipeTestUtils.assertListsEqual(expected, actual);
		
		double end = System.currentTimeMillis();
		System.out.println("VEPITCase.vepExeOnlyPipe() - Total runtime: " + (end-start)/1000.0);
	}
	
	@Test
	public void singleLine() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException {
		System.out.println("-----------------------------------------");
		System.out.println("VEPITCase.singleLine()");
		
		VEPPipeline vepPipe = new VEPPipeline(null, false);
		Pipeline pipe = new Pipeline( new HistoryInPipe(), vepPipe, new HistoryOutPipe() );
		pipe.setStarts(  Arrays.asList(
				"##fileformat=VCFv4.0",
				"#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO",
				"21	26960070	rs116645811	G	A	.	.	." ));
		List<String> actual = PipeTestUtils.getResults(pipe);
		// Expected from: CSQ=A|ENSG00000260583|ENST00000567517|Transcript|upstream_gene_variant|||||||LINC00515|4432|||,A|ENSG00000154719|ENST00000352957|Transcript|intron_variant|||||||MRPL39||||,A|ENSG00000154719|ENST00000307301|Transcript|missense_variant|1043|1001|334|T/M|aCg/aTg||MRPL39||tolerated(0.05)|benign(0.001)|
		List<String> expected = Arrays.asList(
				"##fileformat=VCFv4.0",
				"#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO	BIOR.VEP",
				"21	26960070	rs116645811	G	A	.	.	.	"
					+ "{\"CSQ\":"
					+  "[{\"Allele\":\"A\",\"Gene\":\"ENSG00000260583\",\"Feature\":\"ENST00000567517\",\"Feature_type\":\"Transcript\",\"Consequence\":\"upstream_gene_variant\",\"HGNC\":\"LINC00515\",\"DISTANCE\":\"4432\"},"
					+  "{\"Allele\":\"A\",\"Gene\":\"ENSG00000154719\",\"Feature\":\"ENST00000352957\",\"Feature_type\":\"Transcript\",\"Consequence\":\"intron_variant\",\"HGNC\":\"MRPL39\"},"
					+  "{\"Allele\":\"A\",\"Gene\":\"ENSG00000154719\",\"Feature\":\"ENST00000307301\",\"Feature_type\":\"Transcript\",\"Consequence\":\"missense_variant\",\"cDNA_position\":\"1043\",\"CDS_position\":\"1001\",\"Protein_position\":\"334\",\"Amino_acids\":\"T/M\",\"Codons\":\"aCg/aTg\",\"HGNC\":\"MRPL39\",\"SIFT\":\"tolerated(0.05)\",\"PolyPhen\":\"benign(0.001)\",\"SIFT_TERM\":\"tolerated\",\"SIFT_Score\":0.05,\"PolyPhen_TERM\":\"benign\",\"PolyPhen_Score\":0.001}]"
					+  "}"
				);
		vepPipe.terminate();

		//printComparison(pipe, expected, actual);
		
		PipeTestUtils.assertListsEqual(expected, actual);

	}
	
	@Test
	public void pipelineFanout() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException {
		System.out.println("-----------------------------------------");
		System.out.println("VEPITCase.pipelineFanout()");

		VEPPipeline vepPipe = new VEPPipeline(null, false);
		Pipeline pipe = new Pipeline( new HistoryInPipe(), vepPipe, new HistoryOutPipe() );
		List<String> input = FileCompareUtils.loadFile("src/test/resources/tools/vep/vepsample.vcf");
		pipe.setStarts(input);
		List<String> actual = PipeTestUtils.getResults(pipe);
		List<String> expected = FileCompareUtils.loadFile(VEPDIR + "vepsample.expected.fanout2.vcf");

		vepPipe.terminate();

		//printComparison(pipe, expected, actual);
		
		PipeTestUtils.assertListsEqual(expected, actual);
	}
	
	
	@Test
	public void pipelineWorst() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException {
		System.out.println("-----------------------------------------");
		System.out.println("VEPITCase.pipelineWorst()");

		List<String> input = FileCompareUtils.loadFile("src/test/resources/tools/vep/vepsample.vcf");
		VEPPipeline vepPipe = new VEPPipeline(null, true);
		Pipeline pipe = new Pipeline(
				new HistoryInPipe(),
				vepPipe,
				new HistoryOutPipe()
				);
		pipe.setStarts(input);
		List<String> actual = PipeTestUtils.getResults(pipe);
		List<String> expected = FileCompareUtils.loadFile(VEPDIR + "vepsample.expected.worstonly.vcf");

		vepPipe.terminate();

		//printComparison(pipe, expected, actual);
		
		PipeTestUtils.assertListsEqual(expected, actual);
	}
	
	
	@Test
	/** Test the whole bior_vep command with fanout of multiple effects
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void cmdFanout() throws IOException, InterruptedException {
		System.out.println("-----------------------------------------");
		System.out.println("VEPITCase.cmdFanout(): testing sample VEP vcf file");
		// NOTE:  This test case should only run on biordev - where it can run VEP
		String vcfIn = loadFile(new File(VEPDIR + "vepsample.vcf"));

		CommandOutput out = executeScript("bior_vep", vcfIn, "--all");

		assertEquals(out.stderr, 0, out.exit);
		assertEquals("", out.stderr);
		
		String actualStr = out.stdout;
		List<String> actual = Arrays.asList(actualStr.split("\n"));
		List<String> expected = FileCompareUtils.loadFile(VEPDIR + "vepsample.expected.fanout2.vcf");
		
		//printComparison(null, expected, actual);
		
		// The output should contain some sift and polyphen scores
		assertTrue("VEP lines should begin with CSQ JSON structure (without ['s): [\n" + actualStr + "]", actualStr.contains("{\"CSQ\":[{"));
		assertTrue("VEP lines should end with }]}: [\n" + actualStr + "]", actualStr.trim().endsWith("}]}"));
		assertTrue(actualStr, actualStr.contains("\"PolyPhen\":\"benign(0.001)\""));
		assertTrue(actualStr, actualStr.contains("\"SIFT\":\"tolerated(0.05)\""));
		assertTrue(actualStr, actualStr.contains("\"PolyPhen_TERM\":\"benign\""));
		assertTrue(actualStr, actualStr.contains("\"SIFT_TERM\":\"tolerated\""));

		PipeTestUtils.assertListsEqual(expected, actual);
		
	}

	@Test
	/** Test the whole bior_vep command with worst effect ONLY for each variant
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void cmdWorstEffectOnly() throws IOException, InterruptedException {
		System.out.println("VEPITCase.cmdWorstEffectOnly(): Running the command line call and only getting the worst effect per variant as output");
		// NOTE:  This test case should only run on biordev - where it can run VEP
		String vcfIn = loadFile(new File(VEPDIR + "vepsample.vcf"));

		CommandOutput out = executeScript("bior_vep", vcfIn);

		assertEquals(out.stderr, 0, out.exit);
		assertEquals("", out.stderr);
		
		String actualStr = out.stdout;
		List<String> actual = Arrays.asList(actualStr.split("\n"));
		List<String> expected = FileCompareUtils.loadFile(VEPDIR + "vepsample.expected.worstonly.vcf");

		//printComparison(null, expected, actual);

		// The output should contain some sift and polyphen scores
		assertTrue(actualStr.contains("\"PolyPhen\":\"benign(0.001)\""));
		assertTrue(actualStr.contains("\"SIFT\":\"tolerated(0.05)\""));
		assertTrue(actualStr.contains("\"PolyPhen_TERM\":\"benign\""));
		assertTrue(actualStr.contains("\"SIFT_TERM\":\"tolerated\""));

		PipeTestUtils.assertListsEqual(expected, actual);
	}

	@Test
	/** Test the whole bior_vep command with worst effect ONLY for each variant
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void testHeader() throws IOException, InterruptedException, URISyntaxException
	{
		// NOTE:  This test case should only run on biordev - where it can run VEP

		File biorLiteHome = new File(sHomePath);
		File dataSourceProps = new File(biorLiteHome, "conf/tools/vep.datasource.properties");
		File columnProps     = new File(biorLiteHome, "conf/tools/vep.columns.properties");		
		
		String stdin = 
				"##fileformat=VCFv4.0" + "\n" +
				"#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO" + "\n" +
				"21	26960070	rs116645811	G	A	.	.	.";

		CommandOutput out = executeScript("bior_vep", stdin, "--log");

		assertEquals(out.stderr, 0, out.exit);
		assertEquals("", out.stderr);
		
		String[] outputLines = out.stdout.split("\n");

		assertEquals(4, outputLines.length);
		assertEquals("##fileformat=VCFv4.0", outputLines[0]);
		assertEquals(String.format("##BIOR=<ID=\"bior.vep\",Operation=\"bior_vep\",DataType=\"JSON\",ShortUniqueName=\"vep\",Description=\"Tool from Ensembl that predicts the functional consequences of known and unknown variants.\",Version=\"2.7\",Build=\"Ensembl Release 69\",DataSourceProperties=\"%s\",ColumnProperties=\"%s\">", dataSourceProps.getCanonicalPath(), columnProps.getCanonicalPath()), outputLines[1]);		
		assertEquals("#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO	bior.vep", outputLines[2]);
	}	
	
	public static void printComparison(Pipeline pipeline, List<String> expected, List<String> actual) {
		if(pipeline != null)
			PipeTestUtils.walkPipeline(pipeline, 0);
		
		System.out.println("Expected: -----------------");
		PipeTestUtils.printLines(expected);
		System.out.println("Actual: -------------------");
		PipeTestUtils.printLines(actual);
		System.out.println("---------------------------");
	}


	@Test
	/** 
	 * Tests bypass logic for bior_vep.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void bypass() throws IOException, InterruptedException
	{
		System.out.println("VEPITCase.bypass(): Running the command line call and only getting the worst effect per variant as output");
		// NOTE:  This test case should only run on biordev - where it can run VEP
		String vcfIn = loadFile(new File(VEPDIR + "bypass.vcf"));

		CommandOutput out = executeScript("bior_vep", vcfIn);

		assertEquals(out.stderr, 0, out.exit);
		assertEquals("", out.stderr);
		
		String actualStr = out.stdout;
		List<String> lines = Arrays.asList(actualStr.split("\n"));
		
		assertEquals(5, lines.size());

		List<String> dataLines = lines.subList(1, lines.size());		
		for (String line: dataLines)
		{
			String[] cols = line.split("\t");
			assertEquals(9, cols.length);
			assertEquals("{}", cols[8]);
		}
	}	
	
	@Test
	public void test_VEPErrorMessage() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException {
		System.out.println("-----------------------------------------");
		System.out.println("VEPITCase.VEPErrorMEssage()");
		
		VEPPipeline vepPipe = new VEPPipeline(null, false);
		Pipeline pipe = new Pipeline( new HistoryInPipe(), vepPipe, new HistoryOutPipe() );
		pipe.setStarts(  Arrays.asList(
				"##fileformat=VCFv4.0",
				"#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO",
				"2126960070	rs116645811	G	A	.	.	." )); //line has error so VEP will hang
		List<String> actual = PipeTestUtils.getResults(pipe);
		String[] aCSQ = actual.get(2).split("\t");
		
        String expected = "{\"CSQ\":[{\"VEPMessage\":\"VEPERRORMessage\",\"Status\":\"VEP failed to assign function to this variant\"}]}";
        				
		vepPipe.terminate();
		
		assertEquals(aCSQ[7], expected);		
	}
}
