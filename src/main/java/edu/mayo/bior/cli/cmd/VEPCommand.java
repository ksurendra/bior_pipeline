package edu.mayo.bior.cli.cmd;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;

import edu.mayo.bior.cli.CommandPlugin;

public class VEPCommand extends GenericScriptCommand implements CommandPlugin {

	private static final String ENV_NAME_BIOR_LITE_HOME = "BIOR_LITE_HOME";
	private static final String ENV_VALUE_BIOR_LITE_HOME = System.getenv(ENV_NAME_BIOR_LITE_HOME);

	private static final String OPTION_BUFFER_SIZE = "b";
	
	public void init(Properties props) throws Exception {
	}

	@Override
	public String getScriptName(CommandLine line) {
		return ENV_VALUE_BIOR_LITE_HOME + "/bin/" + "_bior_vep.sh";
	}

	@Override
	public String[] getScriptArgs(CommandLine line) {
		
        Integer bufferSize = 10;
        if (line.hasOption(OPTION_BUFFER_SIZE)) {
            bufferSize = new Integer(line.getOptionValue(OPTION_BUFFER_SIZE));
        }		
		
		return new String[] { String.valueOf(bufferSize) };
	}

	@Override
	public Map<String, String> getEnvVars(CommandLine line) {
		
		Map<String, String> m = new HashMap<String, String>();		
		m.put(ENV_NAME_BIOR_LITE_HOME, ENV_VALUE_BIOR_LITE_HOME);
		return m;
	}
	
}