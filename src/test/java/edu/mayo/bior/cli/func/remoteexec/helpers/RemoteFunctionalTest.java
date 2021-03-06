package edu.mayo.bior.cli.func.remoteexec.helpers;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.management.BadAttributeValueExpException;
import javax.swing.JOptionPane;

import org.apache.commons.cli.MissingArgumentException;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;

import com.google.common.annotations.VisibleForTesting;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import edu.mayo.bior.cli.func.BaseFunctionalTest;

public class RemoteFunctionalTest extends BaseFunctionalTest {
	
	/** NOTE: All tests must be listed here if they are to be run!!!! */
	private static Class[] REMOTE_TESTS = {
		edu.mayo.bior.cli.func.remoteexec.Bior2VCFITCase.class,
		edu.mayo.bior.cli.func.remoteexec.ManyCmdsITCase.class,
		edu.mayo.bior.cli.func.remoteexec.SNPEffCommandITCase.class,
		edu.mayo.bior.cli.func.remoteexec.SNPEFFEXEITCase.class,
		edu.mayo.bior.cli.func.remoteexec.SNPEFFPipelineITCase.class,
		edu.mayo.bior.cli.func.remoteexec.TreatITCaseMultiCmd.class,
		edu.mayo.bior.cli.func.remoteexec.TreatITCaseExitCodes.class,
		edu.mayo.bior.cli.func.remoteexec.TreatITCaseSingleThread.class,
		edu.mayo.bior.cli.func.remoteexec.VEPCommandITCase.class, 
		edu.mayo.bior.cli.func.remoteexec.VEPEXEITCase.class, 
		edu.mayo.bior.cli.func.remoteexec.VEPPipelineITCase.class, 
	};

	public enum DevServerUserPropKeys { 
		devServerName, devServerUsername, devServerPassword, devServerPath, isFirstSync,
		urlSvnPipes, urlSvnPipeline, urlSvnCatalog, svnUser, svnPass };
	
	public final String DEV_SERVER_PROPS_FILE = System.getProperty("user.home") + "/bior.devserver.properties";
	public final String EXAMPLE_PROPS_FILE    = "src/test/resources/remoteexec/bior.devserver.example.properties";
	public final String BIOR_DEV_SERVER		  = "biordev.mayo.edu";
	
	private static boolean mIsDevServer  		= false;
	private static boolean mIsHostVerified	   	= false;
	// Do NOT run the remote tests more than once!
	private static boolean mIsFirstRemoteTest	= true; 
	
	private Properties mDevServerProperties = null;
	
	private static boolean mIsAllTestsSuccessful = false;
	private static ArrayList<RemoteTestResult> mTestResults = new ArrayList<RemoteTestResult>();

	public static void main(String[] args) {
		verifyAllItCasesCovered();
	}
	
	@BeforeClass
	public static void beforeAll() throws Exception {
		System.out.println("Make sure you have the required catalogs installed and in your path (or mounted over SMB) before you attempt to run the TREAT/ANNOTATE TESTS");

		verifyAllItCasesCovered();
		
		mIsDevServer = new RemoteFunctionalTest().isOnBiorDevServer();

		// If we are on the biordev server, then just return as the JUnit tests will be run individually.
		// Else, we need to upload them to the server and execute them
		if( mIsDevServer ) {
			System.out.println("Running on bior development server - all tests will be run locally (NOT remotely)");
			return;
		}
		
		// If we have already run the remote tests once, do NOT run them again for each subsequent class!
		if(! mIsFirstRemoteTest) {
			System.out.println("Remote functional tests have already been run - skipping.");
			return;
		}
		mIsFirstRemoteTest = false;
	
		System.out.println("=============================================================================");
		System.out.println("-----------------------------------------------------------------------------");
		System.out.println("Running remote functional tests on biordev.mayo.edu");
		System.out.println("  (this output is coming from DragonRider)");
		System.out.println("Functional tests started at: " + new Date());
		System.out.println("-----------------------------------------------------------------------------");
		System.out.println("=============================================================================");

		System.out.println("Running tests remotely...");
		new RemoteFunctionalTest().runTestsRemotely();
		
		System.out.println("=============================================================================");
		System.out.println("-----------------------------------------------------------------------------");
		System.out.println("Done running remote functional tests.");
		System.out.println("-----------------------------------------------------------------------------");
		System.out.println("=============================================================================");

	}
	

	/** Make sure all classes under "edu.mayo.bior.cli.func.remoteexec/" are covered by the REMOTE_TESTS variable */
	private static void verifyAllItCasesCovered() {
		File remoteExecDir = new File("src/test/java/edu/mayo/bior/cli/func/remoteexec/");
		File[] files = remoteExecDir.listFiles( new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".java");
			}
		});
		List<String> classesNotCovered = new ArrayList<String>();
		for(File file : files) {
			String fileNameWithoutExt = file.getName().replace(".java", "");
			boolean isCovered = false;
			for(Class cls : REMOTE_TESTS) {
				String clsName = cls.getSimpleName();
				if( fileNameWithoutExt.equals(clsName) )
					isCovered = true;
			}
			if( ! isCovered )
				classesNotCovered.add(file.getName());
		}
		Assert.assertTrue("ERROR: These Java classes are not covered by remote tests.\n"
			+ "Please add them to the REMOTE_TESTS variable of the RemoteFunctionalTest.java class:\n"
			+ classesNotCovered.toString(),
			classesNotCovered.size() == 0 );
		System.out.println("All remote tests accounted for.");
	}



	@Before
	public void beforeEach() {
		// Testcase will only execute if on biordev server 
		// (if not on biordev, this will exit the testcase before it runs)
		Assume.assumeTrue(mIsDevServer);
	}
	
//	@AfterClass
//	public static void afterAll() throws MissingArgumentException, IOException {
//		System.out.println("RemoteFunctionalTest.afterAll() ................");
//	}
//
//	@After
//	public void afterEach() {
//		System.out.println("RemoteFunctionalTest.afterEach ................");
//	}
//
	
	private boolean isOnBiorDevServer() throws MissingArgumentException, IOException {
		// Can we get the calling method and class and store it in a list 
		// Could then check these to execute
		
		if( ! mIsHostVerified ) {
			// Check if this is the dev server
			// Ex: hostnameShort = "biordev"
			String hostnameShort     = java.net.InetAddress.getLocalHost().getHostName();
			// Ex: hostnameCanonical = "biordev.mayo.edu"
			String hostnameCanonical = java.net.InetAddress.getLocalHost().getCanonicalHostName();
			System.out.println("Hostname:  " + hostnameShort);
			System.out.println("Canonical: " + hostnameCanonical);
			mIsDevServer = hostnameCanonical.equalsIgnoreCase(BIOR_DEV_SERVER);
			System.out.println("Is running on dev server?: " + mIsDevServer);
		}
		
		// Only run the testcase if on local system
		//return mIsDevServer;
		return true; //TODO: TEMP - this is to run all remote tests on your local machine.  Change this back before checking in code!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	}

	/** We are probably on a laptop or user system and thus need to copy all current
	  * files to the biordev box and execute all testcases there 
	 * @throws Exception */
	private void runTestsRemotely() throws Exception  {
		try {
			// Get properties for connecting to dev system (use specific user directory)
			if(mDevServerProperties == null)
				loadProperties();
			
			warnUserIfFirstSync();
			
			Ssh ssh = new Ssh();
			Session session = ssh.openSession(mDevServerProperties);
	
			// scp latest files to server  (only update files that have changed)
			System.out.println("Sync local project to biordev server...");
			copyProjectToServer(session);
			
			System.out.println("Run the Maven build on biordev server...");
			ArrayList<String> resultLines = runMavenBuild(session);
			System.out.println("Get results from Maven's surefire-reports xml files...");
			mTestResults = getTestResults(session);
	
			verifyTests(session, mTestResults);
			
			session.disconnect();
			System.out.println("Done.");
		} catch(BadAttributeValueExpException e1) {
			System.err.println("User chose to cancel sync operation");
			throw e1;
		} catch(Exception e2) {
			System.err.println("Error occurred while running remote test cases!");
			e2.printStackTrace();
			throw e2;
		}
		
	}
	
	

	private void warnUserIfFirstSync() throws BadAttributeValueExpException, IOException {
		boolean isFirstSync = ! "false".equalsIgnoreCase(mDevServerProperties.getProperty(DevServerUserPropKeys.isFirstSync.toString()));
		if( ! isFirstSync )
			return;

		String remoteDir = mDevServerProperties.getProperty(DevServerUserPropKeys.devServerPath.toString());
		int choice = JOptionPane.showConfirmDialog(null, 
				"WARNING!  This is the first time you have sync'd up with the biordev server!\n"
				+ "If you continue, the remote directory " + remoteDir + "\n"
				+ "will be wiped and all files and directories sync'd to your local project directory.\n"
				+ "Are you sure you want to continue?!", 
				"Warning", 
				JOptionPane.YES_NO_OPTION);
		if( choice == JOptionPane.NO_OPTION )
			throw new BadAttributeValueExpException("User chose to cancel sync operation");
		
		// Update the properties so it will NOT be the firstSync
		mDevServerProperties.setProperty(DevServerUserPropKeys.isFirstSync.toString(), "false");
		saveProperties();
	}



	private void verifyTests(Session session, ArrayList<RemoteTestResult> testResults) throws JSchException, IOException {
		System.out.println("\n================================================================================");
		System.out.println("Verify functional test cases...");
//      These are only run on the client side (laptop), NOT on biordev
//		System.out.println("Java version: " + System.getProperty("java.version"));
//		System.out.println("BIOR_LITE_HOME = " + System.getenv("BIOR_LITE_HOME"));
		System.out.println("================================================================================");

		int numTestsRun = 0;
		int numErrors = 0;
		int numSkipped = 0;
		double runTime = 0.0;
		for(RemoteTestResult testResult : testResults) {
			numTestsRun += testResult.numTestsRun;
			numErrors += testResult.numErrors + testResult.numFailures;
			numSkipped += testResult.numSkipped;
			runTime += testResult.runTime;
		}
		
		// Print out the number of tests run, skipped, errored
		System.out.println();
		System.out.println("# tests run:     " + numTestsRun);
		System.out.println("# test errors:   " + numErrors);
		System.out.println("# tests skipped: " + numSkipped);
		System.out.println("Total runtime:   " + runTime);
		System.out.println();
		
		// Verify that at least one test ran
		if( numTestsRun == 0 ) {
			System.out.println("\n============================================================================");
			System.out.println("WARNING: Zero functional tests were run!  **********************************");
			System.out.println("============================================================================");
			// Set flag to fail the build (this is hit in @After method)
			mIsAllTestsSuccessful = false;
			Assert.fail("FAILED: No tests were run");
		}
		// Verify that no tests had errors
		else if( numErrors > 0 ) {
			printErrors(getTestErrors(session));
			System.out.println("\n============================================================================");
			System.out.println("Doh!  Some tests failed! ************************************************");
			System.out.println("============================================================================");
			Assert.fail("FAILED: There were some errors when running the tests!");
		}

		// Warn user if some tests skipped
		if( numSkipped > 0 ) {
			System.out.println("WARNING!!!  Some tests were skipped!  Num skipped: " + numSkipped);
		}
		
		System.out.println("\n============================================================================");
		System.out.println("Congratulations - all tests passed!");
		System.out.println("============================================================================");
	}
		
	private void printErrors(ArrayList<String> testErrors) {
		for(String s : testErrors) {
			System.out.println(s);
		}
	}


	private void copyProjectToServer(Session session) throws JSchException, IOException, ParseException, SftpException  {
		RSync rsync = new RSync();
		rsync.syncSftp(session, mDevServerProperties);
		
		
		//File projectDir = new File(".").getCanonicalFile();
		//jazsync jaz = new jazsync();
		//jazsync.main(null);
		// TODO:.....
	}


	/** Run the "mvn clean integration-test" command on the biordev server and return result lines
	 * @throws IOException 
	 * @throws JSchException 
	 * @throws MissingArgumentException */
	public ArrayList<String> runMavenBuild(Session session) throws JSchException, IOException, MissingArgumentException  {
		String projectDir = mDevServerProperties.getProperty(RemoteFunctionalTest.DevServerUserPropKeys.devServerPath.toString());
		// NOTE: We should remove the -X flag from the mvn command when we are done verifying 
		//       the initial remote functional tests, since it prints out a LOT of lines
		//       but is useful for verifying the Java version 
		//       (Java 7 should be required to avoid the issue with Double.toString() that for "0.001" prints
		//       "0.001" in Java 6.24, "0.0010" in Java 6.35, and "0.001" again in Java 7)
		// BUT:  Java version is also being printed in the code at the end of the tests
		// NOTE: See this about running integration tests without unit tests:
		//       http://stackoverflow.com/questions/6612344/prevent-unit-tests-in-maven-but-allow-integration-tests
		String cmd = "source ~/.bashrc; "
				+ "JAVA_HOME=/home/bior/tools/jdk1.7.0_07/; "
				+ "PATH=/home/bior/tools/jdk1.7.0_07/bin:$PATH; "
				+ "echo \"User dir: $HOME\"; "
				+ "cd " + projectDir + "; "
				+ "java -version; " 
				+ "echo \"BIOR_LITE_HOME = $BIOR_LITE_HOME\"; "
				+ "mvn clean package -DskipTests=true; "
				+ "source setupEnv.sh; "
				+ "mvn test " + getAllRemoteFunctionalTestsStr() + "; "
				//+ "mvn clean integration-test -Dtest=SomePatternThatDoesntMatchAnything -DfailIfNoTests=false";
				//+ "mvn clean integration-test";
				;
		ArrayList<String> output = new Ssh().runRemoteCommand(session, cmd, true);
		return output;
	}
	
	/** Ex:  "-Dtest=SNPEffITCase,VEPITCase"
	 *  If you want to execute just a single test method, you can change the return string to:
	 *  "-Dtest=SNPEffITCase#testBadVariants"
	 * @return
	 */
	private String getAllRemoteFunctionalTestsStr() {
		String remoteTests = "-Dtest=";
		for(Class testClass : REMOTE_TESTS) {
			remoteTests += testClass.getName() + ",";
		}
		if(remoteTests.endsWith(","))
			remoteTests.substring(0, remoteTests.length()-1);
		
		//ArrayList<String> remoteTests = new ArrayList<String>();
		
		//this.getClass().getResource("edu.mayo.bior.cli.func.remoteexec");
		
//		ClassPath clsPath = new ClassPath();
//		
//		Reflections reflections = new Reflections("my.project.prefix");
//
//		 Set<Class<? extends Object>> allClasses = 
//		     reflections.getSubTypesOf(Object.class);
		return remoteTests;
	}
	
	
	/** Get the results from running "mvn clean install" on biordev 
	 * @throws IOException 
	 * @throws JSchException */
	public ArrayList<RemoteTestResult> getTestResults(Session session) throws JSchException, IOException {
		String xmlDir = mDevServerProperties.getProperty(RemoteFunctionalTest.DevServerUserPropKeys.devServerPath.toString()) + "/target/surefire-reports";
		String cmd = "grep -R \"<testsuite\" " + xmlDir + "/*.xml";
		ArrayList<String> output = new Ssh().runRemoteCommand(session, cmd, false);
		ArrayList<RemoteTestResult> testResults = new ArrayList<RemoteTestResult>();
		for(String line : output) {
			RemoteTestResult test = new RemoteTestResult();
			int idxPathEnd = line.indexOf(":<");
			if(idxPathEnd > 0)
				test.path = line.substring(0, idxPathEnd);
			test.numFailures = Integer.parseInt(getMidStr(line, "failures=\"", "\""));
			test.runTime    =Double.parseDouble(getMidStr(line, "time=\"",     "\""));
			test.numErrors 	 = Integer.parseInt(getMidStr(line, "errors=\"",   "\""));
			test.numSkipped  = Integer.parseInt(getMidStr(line, "skipped=\"",  "\""));
			test.numTestsRun = Integer.parseInt(getMidStr(line, "tests=\"",    "\""));
			test.testSuiteName=getMidStr(line, "name=\"", "\"");
			testResults.add(test);
		}
		return testResults;
	}
	
	public ArrayList<String> getTestErrors(Session session) throws JSchException, IOException {
		String xmlDir = mDevServerProperties.getProperty(RemoteFunctionalTest.DevServerUserPropKeys.devServerPath.toString()) + "/target/surefire-reports";
		String cmd = "egrep  -B 1 -A 10 \"<failure|<error\" " + xmlDir + "/*.xml";
		ArrayList<String> output = new Ssh().runRemoteCommand(session, cmd, false);
		return output;
	}
	
	private String getMidStr(String fullStr, String prefix, String post) {
		int idx = fullStr.indexOf(prefix) + prefix.length();
		return fullStr.substring(idx,  fullStr.indexOf(post, idx));
	}
	
	private void loadProperties() throws MissingArgumentException, IOException {
		// Check if properties file exists, if not then print a warning message about how to create it, but continue to run tests
		if( ! new File(DEV_SERVER_PROPS_FILE).exists() ) {
			System.err.println("ERROR:   Properties file does not exist: " + DEV_SERVER_PROPS_FILE);
			System.err.println("         You can copy the file");
			System.err.println("           " +  EXAMPLE_PROPS_FILE);
			System.err.println("         to");
			System.err.println("           " + DEV_SERVER_PROPS_FILE);
			System.err.println("         change the properties to fit your user on the biordev server, and try again.");
			//Files.copy(new File(), new File(DEV_SERVER_PROPS_FILE));
			throw new FileNotFoundException("Missing properties file: " + DEV_SERVER_PROPS_FILE);
		}
		
		FileInputStream fin = new FileInputStream(new File(DEV_SERVER_PROPS_FILE)); 
		mDevServerProperties = new Properties();
		mDevServerProperties.load(fin);
		fin.close();
		
		// Throw error if doesn't have all keys or values
		ArrayList<String> missingKeys = getMissingKeys();
		if( missingKeys.size() > 0 )
			throw new MissingArgumentException("Keys or values missing in dev server properties: " + missingKeys.toString());
	}
	
	private void saveProperties() throws IOException {
		FileOutputStream fout = new FileOutputStream(DEV_SERVER_PROPS_FILE);
		mDevServerProperties.store(fout, "Connection and SVN info for running remote tests on biordev development server");
		fout.close();
	}




	
	/** Find any missing keys in the properties file that the user should add */
	private ArrayList<String> getMissingKeys() throws MissingArgumentException {
		ArrayList<String> missingKeys = new ArrayList<String>();
		for(DevServerUserPropKeys key : DevServerUserPropKeys.values()) {
			if( ! mDevServerProperties.containsKey(key.toString()) ||  mDevServerProperties.getProperty(key.toString()) == null )
				missingKeys.add(key.toString());
		}
		return missingKeys;
	}

	/** Try to get the method that is calling this parent class' method */
	private String getCallingMethod() {
		StackTraceElement[] stacks = Thread.currentThread().getStackTrace();
		for(int i=0; i < stacks.length; i++)
			System.out.println(stacks[i]);
		return stacks.toString();
	}


}
