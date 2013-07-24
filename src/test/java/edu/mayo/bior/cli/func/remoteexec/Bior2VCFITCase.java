package edu.mayo.bior.cli.func.remoteexec;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.tinkerpop.pipes.util.Pipeline;

import edu.mayo.bior.cli.func.remoteexec.helpers.RemoteFunctionalTest;
import edu.mayo.bior.pipeline.Treat.TreatPipeline;
import edu.mayo.exec.AbnormalExitException;
import edu.mayo.pipes.PrintPipe;
import edu.mayo.pipes.UNIX.CatPipe;
import edu.mayo.pipes.history.HistoryInPipe;
import edu.mayo.pipes.history.HistoryOutPipe;
import edu.mayo.pipes.util.test.PipeTestUtils;

public class Bior2VCFITCase extends RemoteFunctionalTest {
	
	@Test
	public void test() throws IOException, InterruptedException, BrokenBarrierException, TimeoutException, AbnormalExitException {
		
		Pipeline pipes = new Pipeline(
				new CatPipe(),
				new HistoryInPipe(),
				new TreatPipeline("src/test/resources/treat/configtest/smallSubset.config"),
				new HistoryOutPipe(),
				new PrintPipe()
				);
		pipes.setStarts(Arrays.asList("src/test/resources/treat/gold.vcf"));
		List<String> actual = PipeTestUtils.getResults(pipes);
		
		//System.out.println(Arrays.asList(actual));
		
	}

}