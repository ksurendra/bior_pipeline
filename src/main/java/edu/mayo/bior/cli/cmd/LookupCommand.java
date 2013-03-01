package edu.mayo.bior.cli.cmd;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import edu.mayo.bior.cli.CommandPlugin;
import edu.mayo.bior.cli.InvalidDataException;
import edu.mayo.bior.cli.InvalidOptionArgValueException;
import edu.mayo.bior.pipeline.UnixStreamPipeline;
import edu.mayo.pipes.JSON.lookup.LookupPipe;
import edu.mayo.pipes.JSON.lookup.lookupUtils.IndexUtils;

public class LookupCommand implements CommandPlugin {

	private static final char OPTION_DRILL_COLUMN = 'c';
	private static final char OPTION_INDEX_FILE = 'i';
	// JSON path to extract key (if column is specified, the json in that column is used.
	// If not, then the last column is used)
	private static final char OPTION_KEY = 'p';
	private static final char OPTION_CATALOG_FILE = 'd';
	
	private UnixStreamPipeline mPipeline = new UnixStreamPipeline();
	
	public void init(Properties props) throws Exception {
	}

	public void execute(CommandLine line, Options opts) throws InvalidOptionArgValueException, InvalidDataException {		
		String catalogFilePath = line.getOptionValue(OPTION_CATALOG_FILE);
		
		if ( ! new File(catalogFilePath).exists() ) {	
			throw new InvalidOptionArgValueException(
					opts.getOption(OPTION_CATALOG_FILE + ""),
					catalogFilePath, 
					"The catalog file path '" + catalogFilePath + "' does not exist. Please specify a valid catalog file path."
					);
		}			
		
		// key required
		String key = line.getOptionValue(OPTION_KEY);

		String indexFilePath = "";
		if (line.hasOption(OPTION_INDEX_FILE)) {
			indexFilePath = line.getOptionValue(OPTION_INDEX_FILE);
		} else {
			//find the index file based on catalog-name
			try {
				indexFilePath = IndexUtils.buildIndexPath(catalogFilePath, key);
			}catch(IOException ioe) {
				throw new InvalidOptionArgValueException(
						opts.getOption(OPTION_CATALOG_FILE + ""),
						indexFilePath,
						"Error locating the index file");
			}
		}

		String defaultIndexNotExistMsg = "The built-in index for " + line.getOptionValue(OPTION_KEY)
				+ " does not exist.  Please e-mail the BioR development team at bior@mayo.edu to add this, "
				+ "or create your own custom index using the bior_index command.";
		String specifiedIndexNotExistMsg = "The index file path you specified does not exist: " + indexFilePath;
		String specifiedIndexNotReadableMsg = "The index file path you specified does not have read access:  "
				+ indexFilePath + ".  Please verify file permissions.";
		File indexFile = new File(indexFilePath);
		boolean isIndexSpecified = line.hasOption(OPTION_INDEX_FILE);
		if ( ! indexFile.exists() ) {
			throw new InvalidOptionArgValueException(
					opts.getOption(OPTION_INDEX_FILE + ""), 
					indexFilePath,
					isIndexSpecified ? specifiedIndexNotExistMsg  :  defaultIndexNotExistMsg,
					isIndexSpecified
					);
		} else if( ! indexFile.canRead() ) {
			throw new InvalidOptionArgValueException(
					opts.getOption(OPTION_INDEX_FILE + ""), 
					indexFilePath,
					specifiedIndexNotReadableMsg,
					isIndexSpecified
					);
		}
                
        Integer column = -1;
        if (line.hasOption(OPTION_DRILL_COLUMN)) {
        	column = new Integer(line.getOptionValue(OPTION_DRILL_COLUMN));
        }

        LookupPipe pipe = new LookupPipe(catalogFilePath, indexFilePath, column);
		
		mPipeline.execute(pipe);		
	}
}
