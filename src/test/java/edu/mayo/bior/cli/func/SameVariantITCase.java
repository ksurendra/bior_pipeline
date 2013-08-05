package edu.mayo.bior.cli.func;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.jayway.jsonpath.JsonPath;

public class SameVariantITCase extends BaseFunctionalTest
{
	
	 @Test
     public void testNoMatch() throws IOException, InterruptedException
     {
         String catRelativePath = "src/test/resources/sameVariantCatalog.tsv.bgz";
         String catCanonicalPath = (new File(catRelativePath)).getCanonicalPath();
         
         String stdin =
        		 "##fileformat=VCFv4.0\n" +
        		 "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tJUNIT_JSON\n" +
        		 "BADCHR\t999\trs116645811X\tX\tY\t.\t.\t.\t{\"CHROM\":\"BADCHR\",\"POS\":\"999\",\"ID\":\"rs116645811X\",\"REF\":\"X\",\"ALT\":\"Y\",\"QUAL\":\".\",\"FILTER\":\".\",\"_id\":\"rs116645811X\",\"_type\":\"variant\",\"_landmark\":\"BADCHR\",\"_refAllele\":\"X\",\"_altAlleles\":[\"Y\"],\"_minBP\":999,\"_maxBP\":999}";

         CommandOutput out = executeScript("bior_same_variant", stdin, "-d", catRelativePath);

         assertEquals(out.stderr, 0, out.exit);
         assertEquals("", out.stderr);

         String header = getHeader(out.stdout);             
         String[] headerLines = header.split("\n");
         assertEquals(3, headerLines.length);
         assertEquals("##fileformat=VCFv4.0", headerLines[0]);
         assertEquals(String.format("##BIOR=<ID=\"bior.sameVariantCatalog\",Operation=\"bior_same_variant\",DataType=\"JSON\",ShortUniqueName=\"sameVariantCatalog\",Path=\"%s\">", catCanonicalPath), headerLines[1]);
         assertEquals("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tJUNIT_JSON\tbior.sameVariantCatalog", headerLines[2]);         
         
         // pull out just data rows
         String data = out.stdout.replace(header, "");
         String[] cols = data.split("\t");

         // If variant is not found, results in empty json string
         assertEquals("{}", cols[9].trim());
     }


     @Test
     public void testMatch() throws IOException, InterruptedException
     {
         String catRelativePath = "src/test/resources/sameVariantCatalog.tsv.bgz";
         String catCanonicalPath = (new File(catRelativePath)).getCanonicalPath();

         String stdin = 
        		 "##fileformat=VCFv4.0\n" +
        		 "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tJUNIT_JSON\n" +            		 
        		 "21\t26960070\trs116645811\tG\tA\t.\t.\t.\t{\"CHROM\":\"21\",\"POS\":\"26960070\",\"ID\":\"rs116645811\",\"REF\":\"G\",\"ALT\":\"A\",\"QUAL\":\".\",\"FILTER\":\".\",\"_id\":\"rs116645811\",\"_type\":\"variant\",\"_landmark\":\"21\",\"_refAllele\":\"G\",\"_altAlleles\":[\"A\"],\"_minBP\":26960070,\"_maxBP\":26960070}";
         CommandOutput out = executeScript("bior_same_variant", stdin, "-d", catRelativePath);

         assertEquals(out.stderr, 0, out.exit);
         assertEquals("", out.stderr);

         String header = getHeader(out.stdout);
         String[] headerLines = header.split("\n");
         assertEquals(3, headerLines.length);
         assertEquals("##fileformat=VCFv4.0", headerLines[0]);
         assertEquals(String.format("##BIOR=<ID=\"bior.sameVariantCatalog\",Operation=\"bior_same_variant\",DataType=\"JSON\",ShortUniqueName=\"sameVariantCatalog\",Path=\"%s\">", catCanonicalPath), headerLines[1]);
         assertEquals("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tJUNIT_JSON\tbior.sameVariantCatalog", headerLines[2]);         

         // pull out just data rows
         String data = out.stdout.replace(header, "");
         String[] cols = data.split("\t");

         // If variant is not found, results in empty json string
         //System.out.println(cols.length + cols[9]);
         assertEquals(10, cols.length);
         String json = cols[cols.length - 1];
         assertEquals("21", JsonPath.compile("CHROM").read(json));
     }

     @Test
     public void testMultipleDataSources() throws IOException, InterruptedException
     {
         String catRelativePath1  = "src/test/resources/sameVariantCatalog.tsv.bgz";
         String catCanonicalPath1 = (new File(catRelativePath1)).getCanonicalPath();
         String catRelativePath2 = "src/test/resources/example_dbSNP_catalog.tsv.bgz";
         String catCanonicalPath2 = (new File(catRelativePath2)).getCanonicalPath();

         String stdin = 
        		 "##fileformat=VCFv4.0\n" +
        		 "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tJUNIT_JSON\n" +            		 
        		 "21\t26960070\trs116645811\tG\tA\t.\t.\t.\t{\"CHROM\":\"21\",\"POS\":\"26960070\",\"ID\":\"rs116645811\",\"REF\":\"G\",\"ALT\":\"A\",\"QUAL\":\".\",\"FILTER\":\".\",\"_id\":\"rs116645811\",\"_type\":\"variant\",\"_landmark\":\"21\",\"_refAllele\":\"G\",\"_altAlleles\":[\"A\"],\"_minBP\":26960070,\"_maxBP\":26960070}";
         CommandOutput out = executeScript("bior_same_variant", stdin, "-d", catRelativePath1, "-d", catRelativePath2);

         assertEquals(out.stderr, 0, out.exit);
         assertEquals("", out.stderr);

         String header = getHeader(out.stdout);
         String[] headerLines = header.split("\n");
         assertEquals(4, headerLines.length);
         assertEquals("##fileformat=VCFv4.0", headerLines[0]);
         assertEquals(String.format("##BIOR=<ID=\"bior.sameVariantCatalog\",Operation=\"bior_same_variant\",DataType=\"JSON\",ShortUniqueName=\"sameVariantCatalog\",Path=\"%s\">", catCanonicalPath1), headerLines[1]);
         assertEquals(String.format("##BIOR=<ID=\"bior.example_dbSNP_catalog\",Operation=\"bior_same_variant\",DataType=\"JSON\",ShortUniqueName=\"example_dbSNP_catalog\",Path=\"%s\">", catCanonicalPath2), headerLines[2]);
         assertEquals("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tJUNIT_JSON\tbior.sameVariantCatalog\tbior.example_dbSNP_catalog", headerLines[3]);         

         // pull out just data rows
         String data = out.stdout.replace(header, "");
         String[] cols = data.split("\t");

         // adding another data source should add another column
         assertEquals(11, cols.length);
         String json = cols[cols.length - 1];
         assertEquals("21", JsonPath.compile("CHROM").read(json));
     }
     
     @Test
     public void testCatalogWithProps() throws IOException, InterruptedException
     {
         String catRelativePath  = "src/test/resources/metadata/00-All_GRCh37.tsv.bgz";
         String catCanonicalPath = (new File(catRelativePath)).getCanonicalPath();
                  
         String stdin =
        		 "##fileformat=VCFv4.0\n" +
        		 "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tJUNIT_JSON\n" +
        		 "1\t10144\trs144773400\tTA\tT\t.\t.\t.\t{\"CHROM\":\"1\",\"POS\":\"10144\",\"ID\":\"rs144773400\",\"REF\":\"TA\",\"ALT\":\"T\",\"QUAL\":\".\",\"FILTER\":\".\",\"_id\":\"rs144773400\",\"_type\":\"variant\",\"_landmark\":\"BADCHR\",\"_refAllele\":\"TA\",\"_altAlleles\":[\"T\"],\"_minBP\":10144,\"_maxBP\":10144}";

         CommandOutput out = executeScript("bior_same_variant", stdin, "-d", catRelativePath);

         assertEquals(out.stderr, 0, out.exit);
         assertEquals("", out.stderr);

         String header = getHeader(out.stdout);
         String[] headerLines = header.split("\n");
         assertEquals(3, headerLines.length);
         assertEquals("##fileformat=VCFv4.0", headerLines[0]);
         assertEquals(String.format("##BIOR=<ID=\"bior.dbSNP137\",Operation=\"bior_same_variant\",DataType=\"JSON\",ShortUniqueName=\"dbSNP137\",Source=\"dbSNP\",Description=\"dbSNP version 137, Patch 10, Human\",Version=\"137\",Build=\"GRCh37.p10\",Path=\"%s\">", catCanonicalPath), headerLines[1]);
         assertEquals("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tJUNIT_JSON\tbior.dbSNP137", headerLines[2]);         
     }
 
}
