package edu.mayo.bior.pipeline;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;

import org.apache.log4j.Logger;

import com.tinkerpop.pipes.AbstractPipe;

import edu.mayo.pipes.history.ColumnMetaData;
import edu.mayo.pipes.history.History;
import edu.mayo.pipes.history.HistoryMetaData;

public class VCFGeneratorPipe extends AbstractPipe<History,History> {

	private static final Logger sLogger = Logger.getLogger(VCFGeneratorPipe.class);
	Map<Integer,String> biorindexes = new HashMap<Integer,String>();
	int totalcolumns;
	
	boolean modifyMetadata = false;
	
	
	
	@Override
	protected History processNextStart() throws NoSuchElementException {
		
		History history = this.starts.next();
		
	
	    // Modify Metadata only once
		if (modifyMetadata == false){
	    	totalcolumns = History.getMetaData().getColumns().size();
	    
	    		//check if INFO column is there and standard index is 7 and get indexes of BIOR Added Columns 
	    	    if (totalcolumns > 7 && History.getMetaData().getColumns().get(7).getColumnName().equalsIgnoreCase("INFO")){
	    	    	
	    	    	for (int i =7; i < totalcolumns;i++) {
	    	    	String colname =History.getMetaData().getColumns().get(i).getColumnName();
	    	    		if (colname.toLowerCase().contains("bior")) {
	    	    		  
	    	    		  biorindexes.put(i,colname) ;
	    	    	 } 
	    	    		
	    	    	} 
	    //remove the columns from Header    
	    removeColumnHeader(History.getMetaData(),biorindexes );
	    modifyMetadata = true;
	    }
	   
	    
	    
	    } else {
	    	
	    	//throw error
	    }
	    
	    history =  removecolumns(modifyhistory(history,biorindexes),biorindexes);
		return history;
	    	
		
		
	}

	private void addColumnheaders(Map<Integer, String> biorindexes2) {
	
		
	}

	
	//removes columns from header
	
	private HistoryMetaData removeColumnHeader(HistoryMetaData metaData,Map<Integer, String> biorindexes2) {
		List<Integer> indexes =   new ArrayList<Integer>(biorindexes.keySet());
		Collections.sort(indexes);
		int i = 0;
		for (Integer j : indexes) {
			
		 metaData.getColumns().remove(j - i);
			i++;
		}
		
		return metaData;
	}

	
	//removes columns from history after appending them to INFO column
	
	private History removecolumns(History modifyhistory, Map<Integer, String> biorindexes2) {
		
		List<Integer> indexes =   new ArrayList<Integer>(biorindexes.keySet());
		Collections.sort(indexes);
		int i = 0;
		for (Integer j : indexes) {
			
			modifyhistory.remove(j - i);
			i++;
		}
		
		return modifyhistory;
		
	}

	
	
	
	
	//Modify the history string(VCF row) by appending the columns into INFO 
	
	
	private History modifyhistory(History history, Map<Integer,String> biorindexes) {
		
		Set<Integer> indexes =   biorindexes.keySet();
		
		Iterator<Integer> iterator = indexes.iterator();
		
		while (iterator.hasNext()) {
			int value = iterator.next();
			String val = null;
		
			if (value < history.size()) {
				val = history.get(value);
			}
			
			if (val != null && !val.isEmpty() && !val.contentEquals(".")) {
			String newValue = history.get(7).concat(";" + biorindexes.get(value) +"=" + val);	
			history.set(7,newValue) ;
			
			}
		  	
			     
		}
		 return history;
		
	}

}