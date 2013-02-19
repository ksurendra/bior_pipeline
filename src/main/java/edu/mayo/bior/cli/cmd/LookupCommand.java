package edu.mayo.bior.cli.cmd;

import java.util.Properties;

import org.apache.commons.cli.CommandLine;

import edu.mayo.bior.cli.CommandPlugin;
import edu.mayo.bior.pipeline.UnixStreamPipeline;
import edu.mayo.pipes.JSON.lookup.LookupPipe;

public class LookupCommand implements CommandPlugin {

	private static final char OPTION_DRILL_COLUMN = 'c';
	private static final char INDEX_FILE = 'd';
		
	private UnixStreamPipeline mPipeline = new UnixStreamPipeline();
	
	public void init(Properties props) throws Exception {
	}

	public void execute(CommandLine line) throws Exception {
		String indexFile = "";
		if (line.hasOption(INDEX_FILE)) {
			indexFile  = line.getOptionValue(INDEX_FILE);
		}
                
        Integer col = -1;
        if (line.hasOption(OPTION_DRILL_COLUMN)) {
        	col = new Integer(line.getOptionValue(OPTION_DRILL_COLUMN));
        }
	
		//DrillPipe pipe = new DrillPipe(keepJSON, paths.toArray(new String[0]), col);
        
        //get the catalog filename from index filename
        String catalog = indexFile.substring(0, indexFile.indexOf(".")) + ".tsv.bgz";
        
        LookupPipe pipe = new LookupPipe(indexFile, catalog);
		
		mPipeline.execute(pipe);		
	}
}