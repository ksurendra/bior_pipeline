package edu.mayo.bior.pipeline.Treat.format;

import java.util.Arrays;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import edu.mayo.bior.pipeline.Treat.JsonColumn;

public class UcscUniqueFormatter implements Formatter
{
	private static final String[] JSON_DRILL_PATHS = { "score" };
	
	// JSON paths
	private static final JsonPath PATH_SCORE  = JsonPath.compile(JSON_DRILL_PATHS[0]);	
	
	public JsonColumn getJSONColumn() {
		return JsonColumn.UCSC_UNIQUE;
	}

	public List<String> getHeaders() {
		return Arrays.asList( "UCSC.Alignability/Uniqueness" );
	}
	
	public List<String> format(String json)	{
		return Arrays.asList( FormatUtils.drill(PATH_SCORE, json) );
	}
	
	public List<String> getJsonDrillPaths() {
		return Arrays.asList(JSON_DRILL_PATHS);
	}	
}
